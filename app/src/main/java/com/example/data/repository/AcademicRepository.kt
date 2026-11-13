package com.example.data.repository

import com.example.data.model.AssessmentGrade
import com.example.data.model.AttendanceRecord
import com.example.data.model.DifficultyAlert
import com.example.data.model.LessonEntry
import com.example.data.model.ReportCard
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AcademicRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private inline fun <reified T> observeCollection(
        collection: String,
        query: Query,
        uid: String
    ): Flow<List<T>> {
        return query.snapshots()
            .map { snapshot ->
                snapshot.toObjects(T::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, collection, auth)
                }
                throw error
            }
    }

    private fun roleScopedQuery(collection: String, profile: UserProfile): Query {
        val base = db.collection(collection)
        val uid = profile.userId
        return when (profile.roleEnum()) {
            UserRole.ADMIN_NATIONAL -> base.whereEqualTo("nationalAdminUid", profile.nationalAdminUid.ifBlank { uid })
            UserRole.ADMIN_REGIONAL -> base.whereEqualTo("regionalAdminUid", uid)
            UserRole.DIRECTEUR -> base.whereEqualTo("directorUid", uid)
            UserRole.ENSEIGNANT -> base.whereEqualTo("teacherUid", uid)
            UserRole.PARENT -> base.whereArrayContains("parentUids", uid)
            UserRole.ELEVE -> base.whereArrayContains("authorizedUids", uid)
        }
    }

    fun observeLessons(profile: UserProfile): Flow<List<LessonEntry>> =
        observeCollection("lessons", roleScopedQuery("lessons", profile), profile.userId)

    fun observeAssessments(profile: UserProfile): Flow<List<AssessmentGrade>> {
        val base = db.collection("assessments")
        val uid = profile.userId
        val query = when (profile.roleEnum()) {
            UserRole.ELEVE -> base.whereEqualTo("studentUid", uid).whereEqualTo("gradesVisibleToStudent", true)
            else -> roleScopedQuery("assessments", profile)
        }
        return observeCollection("assessments", query, uid)
    }

    fun observeAttendance(profile: UserProfile): Flow<List<AttendanceRecord>> {
        val base = db.collection("attendance")
        val uid = profile.userId
        val query = when (profile.roleEnum()) {
            UserRole.ELEVE -> base.whereEqualTo("studentUid", uid)
            else -> roleScopedQuery("attendance", profile)
        }
        return observeCollection("attendance", query, uid)
    }

    fun observeDifficulties(profile: UserProfile): Flow<List<DifficultyAlert>> =
        observeCollection("difficulties", roleScopedQuery("difficulties", profile), profile.userId)

    fun observeReportCards(profile: UserProfile): Flow<List<ReportCard>> {
        val base = db.collection("report_cards")
        val uid = profile.userId
        val query = when (profile.roleEnum()) {
            UserRole.PARENT -> base.whereArrayContains("parentUids", uid).whereEqualTo("published", true)
            UserRole.ELEVE -> base.whereEqualTo("studentUid", uid).whereEqualTo("published", true)
            else -> roleScopedQuery("report_cards", profile)
        }
        return observeCollection("report_cards", query, uid)
    }

    suspend fun publishLesson(
        actor: UserProfile,
        school: School?,
        schoolClass: SchoolClass,
        subject: Subject,
        lessonDate: String,
        chapter: String,
        title: String,
        summary: String,
        skillsWorked: String,
        classExercises: String,
        homework: String,
        homeworkDueDate: String,
        attachmentLabel: String
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val parentUids = schoolClass.parentUids.distinct()
        val dirUid = schoolClass.directorUid.ifBlank { actor.directorUid }
        val natUid = schoolClass.nationalAdminUid.ifBlank { actor.nationalAdminUid }
        val regUid = schoolClass.regionalAdminUid.ifBlank { actor.regionalAdminUid }
        val authUids = (schoolClass.authorizedUids + parentUids + schoolClass.studentUids + listOf(actor.userId, dirUid))
            .filter { it.isNotBlank() }.distinct()

        val tenantId = schoolClass.tenantId.ifBlank { schoolClass.schoolId }
        val payload = mutableMapOf<String, Any>(
            "schoolId" to schoolClass.schoolId,
            "tenantId" to tenantId,
            "schoolName" to (school?.name ?: schoolClass.schoolName),
            "classId" to schoolClass.id,
            "className" to schoolClass.name,
            "subjectId" to subject.id,
            "subjectName" to subject.name,
            "teacherUid" to actor.userId,
            "teacherName" to actor.fullName,
            "lessonDate" to lessonDate.trim(),
            "chapter" to chapter.trim(),
            "title" to title.trim(),
            "summary" to summary.trim(),
            "skillsWorked" to skillsWorked.trim(),
            "classExercises" to classExercises.trim(),
            "homework" to homework.trim(),
            "homeworkDueDate" to homeworkDueDate.trim(),
            "attachmentLabel" to attachmentLabel.trim(),
            "parentUids" to parentUids,
            "authorizedUids" to authUids,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (schoolClass.regionId.isNotBlank()) payload["regionId"] = schoolClass.regionId
        if (dirUid.isNotBlank()) payload["directorUid"] = dirUid
        if (regUid.isNotBlank()) payload["regionalAdminUid"] = regUid
        if (natUid.isNotBlank()) payload["nationalAdminUid"] = natUid

        val batch = db.batch()
        batch.set(db.collection("lessons").document(docId), payload)
        val notifType = if (homework.isNotBlank()) "devoir" else "lecon"
        val notifTitle = if (homework.isNotBlank()) {
            "Nouvelle leçon et devoir (${subject.name})"
        } else {
            "Nouvelle leçon enregistrée (${subject.name})"
        }
        parentUids.forEach { parentUid ->
            val notifRef = db.collection("notifications").document(UUID.randomUUID().toString())
            batch.set(
                notifRef,
                mapOf(
                    "recipientUid" to parentUid,
                    "senderUid" to actor.userId,
                    "schoolId" to schoolClass.schoolId,
                    "tenantId" to tenantId,
                    "type" to notifType,
                    "title" to notifTitle,
                    "body" to "${schoolClass.name} — $title ($lessonDate)",
                    "relatedId" to docId,
                    "read" to false,
                    "channelPreference" to "app",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "lessons", auth)
    }

    suspend fun recordAssessment(
        actor: UserProfile,
        student: Student,
        subject: Subject,
        title: String,
        assessmentType: String,
        score: Double,
        maxScore: Double,
        coefficient: Double,
        period: String,
        date: String,
        comment: String
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val authUids = (student.authorizedUids + student.parentUids + listOf(actor.userId, student.directorUid))
            .filter { it.isNotBlank() }.distinct()
        val tenantId = student.tenantId.ifBlank { student.schoolId }
        val payload = mutableMapOf<String, Any>(
            "schoolId" to student.schoolId,
            "tenantId" to tenantId,
            "classId" to student.classId,
            "className" to student.className,
            "subjectId" to subject.id,
            "subjectName" to subject.name,
            "studentId" to student.id,
            "studentName" to student.fullName,
            "gradesVisibleToStudent" to student.gradesVisibleToStudent,
            "parentUids" to student.parentUids,
            "teacherUid" to actor.userId,
            "teacherName" to actor.fullName,
            "authorizedUids" to authUids,
            "title" to title.trim(),
            "assessmentType" to assessmentType,
            "score" to score,
            "maxScore" to maxScore,
            "coefficient" to coefficient,
            "period" to period,
            "date" to date.trim(),
            "comment" to comment.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (student.regionId.isNotBlank()) payload["regionId"] = student.regionId
        if (student.studentUid.isNotBlank()) payload["studentUid"] = student.studentUid
        if (student.directorUid.isNotBlank()) payload["directorUid"] = student.directorUid
        if (student.regionalAdminUid.isNotBlank()) payload["regionalAdminUid"] = student.regionalAdminUid
        if (student.nationalAdminUid.isNotBlank()) payload["nationalAdminUid"] = student.nationalAdminUid

        val batch = db.batch()
        batch.set(db.collection("assessments").document(docId), payload)
        student.parentUids.forEach { parentUid ->
            val notifRef = db.collection("notifications").document(UUID.randomUUID().toString())
            batch.set(
                notifRef,
                mapOf(
                    "recipientUid" to parentUid,
                    "senderUid" to actor.userId,
                    "schoolId" to student.schoolId,
                    "tenantId" to tenantId,
                    "type" to "note",
                    "title" to "Nouvelle note pour ${student.firstName}",
                    "body" to "${subject.name} — $title : $score / $maxScore ($period)",
                    "studentId" to student.id,
                    "relatedId" to docId,
                    "read" to false,
                    "channelPreference" to "app",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "assessments", auth)
    }

    suspend fun recordAttendance(
        actor: UserProfile,
        student: Student,
        date: String,
        status: String,
        minutesLate: Int,
        justified: Boolean,
        reason: String
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val authUids = (student.authorizedUids + student.parentUids + listOf(actor.userId, student.directorUid))
            .filter { it.isNotBlank() }.distinct()
        val tenantId = student.tenantId.ifBlank { student.schoolId }
        val payload = mutableMapOf<String, Any>(
            "schoolId" to student.schoolId,
            "tenantId" to tenantId,
            "classId" to student.classId,
            "className" to student.className,
            "studentId" to student.id,
            "studentName" to student.fullName,
            "parentUids" to student.parentUids,
            "teacherUid" to actor.userId,
            "teacherName" to actor.fullName,
            "authorizedUids" to authUids,
            "date" to date.trim(),
            "status" to status,
            "minutesLate" to minutesLate,
            "justified" to justified,
            "reason" to reason.trim(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (student.regionId.isNotBlank()) payload["regionId"] = student.regionId
        if (student.studentUid.isNotBlank()) payload["studentUid"] = student.studentUid
        if (student.directorUid.isNotBlank()) payload["directorUid"] = student.directorUid
        if (student.regionalAdminUid.isNotBlank()) payload["regionalAdminUid"] = student.regionalAdminUid
        if (student.nationalAdminUid.isNotBlank()) payload["nationalAdminUid"] = student.nationalAdminUid

        val batch = db.batch()
        batch.set(db.collection("attendance").document(docId), payload)
        if (status == "absent" || status == "retard") {
            val type = if (status == "absent") "absence" else "retard"
            val title = if (status == "absent") "Absence signalée : ${student.firstName}" else "Retard signalé : ${student.firstName}"
            val body = if (status == "absent") {
                "Absence enregistrée le $date en ${student.className}."
            } else {
                "Retard de $minutesLate min enregistré le $date en ${student.className}."
            }
            student.parentUids.forEach { parentUid ->
                val notifRef = db.collection("notifications").document(UUID.randomUUID().toString())
                batch.set(
                    notifRef,
                    mapOf(
                        "recipientUid" to parentUid,
                        "senderUid" to actor.userId,
                        "schoolId" to student.schoolId,
                        "tenantId" to tenantId,
                        "type" to type,
                        "title" to title,
                        "body" to body,
                        "studentId" to student.id,
                        "relatedId" to docId,
                        "read" to false,
                        "channelPreference" to "app",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }
        batch.commit().await()
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "attendance", auth)
    }

    suspend fun reportDifficulty(
        actor: UserProfile,
        student: Student,
        subject: Subject?,
        category: String,
        severity: String,
        observation: String,
        recommendation: String
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val authUids = (student.authorizedUids + student.parentUids + listOf(actor.userId, student.directorUid))
            .filter { it.isNotBlank() }.distinct()
        val tenantId = student.tenantId.ifBlank { student.schoolId }
        val payload = mutableMapOf<String, Any>(
            "schoolId" to student.schoolId,
            "tenantId" to tenantId,
            "classId" to student.classId,
            "className" to student.className,
            "studentId" to student.id,
            "studentName" to student.fullName,
            "parentUids" to student.parentUids,
            "teacherUid" to actor.userId,
            "teacherName" to actor.fullName,
            "authorizedUids" to authUids,
            "category" to category,
            "severity" to severity,
            "observation" to observation.trim(),
            "recommendation" to recommendation.trim(),
            "resolved" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (subject != null) {
            payload["subjectId"] = subject.id
            payload["subjectName"] = subject.name
        }
        if (student.regionId.isNotBlank()) payload["regionId"] = student.regionId
        if (student.studentUid.isNotBlank()) payload["studentUid"] = student.studentUid
        if (student.directorUid.isNotBlank()) payload["directorUid"] = student.directorUid
        if (student.regionalAdminUid.isNotBlank()) payload["regionalAdminUid"] = student.regionalAdminUid
        if (student.nationalAdminUid.isNotBlank()) payload["nationalAdminUid"] = student.nationalAdminUid

        val batch = db.batch()
        batch.set(db.collection("difficulties").document(docId), payload)
        student.parentUids.forEach { parentUid ->
            val notifRef = db.collection("notifications").document(UUID.randomUUID().toString())
            batch.set(
                notifRef,
                mapOf(
                    "recipientUid" to parentUid,
                    "senderUid" to actor.userId,
                    "schoolId" to student.schoolId,
                    "tenantId" to tenantId,
                    "type" to "difficulte",
                    "title" to "Alerte pédagogique : ${student.firstName}",
                    "body" to observation.trim().take(180),
                    "studentId" to student.id,
                    "relatedId" to docId,
                    "read" to false,
                    "channelPreference" to "app",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "difficulties", auth)
    }

    suspend fun resolveDifficulty(difficultyId: String, resolved: Boolean): Result<Unit> = runCatching {
        db.collection("difficulties").document(difficultyId)
            .update(
                mapOf(
                    "resolved" to resolved,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "difficulties/$difficultyId", auth)
    }

    suspend fun generateReportCard(
        actor: UserProfile,
        school: School?,
        student: Student,
        period: String,
        overallAverage: Double,
        classAverage: Double,
        rankLabel: String,
        absencesCount: Int,
        tardiesCount: Int,
        councilDecision: String,
        directorObservation: String,
        published: Boolean
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val dirUid = student.directorUid.ifBlank { actor.userId }
        val authUids = (student.authorizedUids + student.parentUids + listOf(actor.userId, dirUid))
            .filter { it.isNotBlank() }.distinct()
        val tenantId = student.tenantId.ifBlank { student.schoolId }
        val payload = mutableMapOf<String, Any>(
            "schoolId" to student.schoolId,
            "tenantId" to tenantId,
            "schoolName" to (school?.name ?: student.schoolName),
            "classId" to student.classId,
            "className" to student.className,
            "studentId" to student.id,
            "studentName" to student.fullName,
            "parentUids" to student.parentUids,
            "directorUid" to dirUid,
            "authorizedUids" to authUids,
            "period" to period,
            "overallAverage" to overallAverage,
            "classAverage" to classAverage,
            "rankLabel" to rankLabel.trim(),
            "absencesCount" to absencesCount,
            "tardiesCount" to tardiesCount,
            "councilDecision" to councilDecision.trim(),
            "directorObservation" to directorObservation.trim(),
            "generatedByUid" to actor.userId,
            "published" to published,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (student.regionId.isNotBlank()) payload["regionId"] = student.regionId
        if (student.studentUid.isNotBlank()) payload["studentUid"] = student.studentUid
        if (student.regionalAdminUid.isNotBlank()) payload["regionalAdminUid"] = student.regionalAdminUid
        if (student.nationalAdminUid.isNotBlank()) payload["nationalAdminUid"] = student.nationalAdminUid

        val batch = db.batch()
        batch.set(db.collection("report_cards").document(docId), payload)
        if (published) {
            student.parentUids.forEach { parentUid ->
                val notifRef = db.collection("notifications").document(UUID.randomUUID().toString())
                batch.set(
                    notifRef,
                    mapOf(
                        "recipientUid" to parentUid,
                        "senderUid" to actor.userId,
                        "schoolId" to student.schoolId,
                        "tenantId" to tenantId,
                        "type" to "bulletin",
                        "title" to "Nouveau bulletin disponible ($period)",
                        "body" to "Bulletin de ${student.fullName} — Moyenne : ${"%.2f".format(overallAverage)}/20",
                        "studentId" to student.id,
                        "relatedId" to docId,
                        "read" to false,
                        "channelPreference" to "app",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }
        batch.commit().await()
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "report_cards", auth)
    }
}
