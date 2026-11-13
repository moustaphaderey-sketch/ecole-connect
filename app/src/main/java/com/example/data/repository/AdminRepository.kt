package com.example.data.repository

import com.example.data.model.AccessRequest
import com.example.data.model.AuditLog
import com.example.data.model.Region
import com.example.data.model.RoleInvitation
import com.example.data.model.ScheduleEntry
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AdminRepository(
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

    private fun scopedQuery(collection: String, profile: UserProfile): Query {
        val base = db.collection(collection)
        val uid = profile.userId
        return when (profile.roleEnum()) {
            UserRole.ADMIN_NATIONAL -> base.whereEqualTo("nationalAdminUid", profile.nationalAdminUid.ifBlank { uid })
            UserRole.ADMIN_REGIONAL -> base.whereEqualTo("regionalAdminUid", uid)
            UserRole.DIRECTEUR -> base.whereEqualTo("directorUid", uid)
            UserRole.ENSEIGNANT -> base.whereArrayContains("authorizedUids", uid)
            UserRole.PARENT -> base.whereArrayContains("authorizedUids", uid)
            UserRole.ELEVE -> base.whereArrayContains("authorizedUids", uid)
        }
    }

    fun observeRegions(profile: UserProfile): Flow<List<Region>> =
        observeCollection("regions", scopedQuery("regions", profile), profile.userId)

    fun observeSchools(profile: UserProfile): Flow<List<School>> =
        observeCollection("schools", scopedQuery("schools", profile), profile.userId)

    fun observeClasses(profile: UserProfile): Flow<List<SchoolClass>> =
        observeCollection("classes", scopedQuery("classes", profile), profile.userId)

    fun observeSubjects(profile: UserProfile): Flow<List<Subject>> =
        observeCollection("subjects", scopedQuery("subjects", profile), profile.userId)

    fun observeSchedules(profile: UserProfile): Flow<List<ScheduleEntry>> =
        observeCollection("schedules", scopedQuery("schedules", profile), profile.userId)

    fun observeStudents(profile: UserProfile): Flow<List<Student>> {
        val base = db.collection("students")
        val uid = profile.userId
        val query = when (profile.roleEnum()) {
            UserRole.ADMIN_NATIONAL -> base.whereEqualTo("nationalAdminUid", profile.nationalAdminUid.ifBlank { uid })
            UserRole.ADMIN_REGIONAL -> base.whereEqualTo("regionalAdminUid", uid)
            UserRole.DIRECTEUR -> base.whereEqualTo("directorUid", uid)
            UserRole.ENSEIGNANT -> base.whereArrayContains("authorizedUids", uid)
            UserRole.PARENT -> base.whereArrayContains("parentUids", uid)
            UserRole.ELEVE -> base.whereEqualTo("studentUid", uid)
        }
        return observeCollection("students", query, uid)
    }

    fun observeInvitations(profile: UserProfile): Flow<List<RoleInvitation>> {
        val base = db.collection("role_invitations")
        val uid = profile.userId
        val query = when (profile.roleEnum()) {
            UserRole.ADMIN_NATIONAL -> base.whereEqualTo("nationalAdminUid", profile.nationalAdminUid.ifBlank { uid })
            else -> base.whereEqualTo("createdByUid", uid)
        }
        return observeCollection("role_invitations", query, uid)
    }

    fun observeManagedUsers(profile: UserProfile): Flow<List<UserProfile>> {
        val base = db.collection("users")
        val uid = profile.userId
        val query = when (profile.roleEnum()) {
            UserRole.ADMIN_NATIONAL -> base.whereEqualTo("nationalAdminUid", profile.nationalAdminUid.ifBlank { uid })
            UserRole.ADMIN_REGIONAL -> base.whereEqualTo("regionalAdminUid", uid)
            UserRole.DIRECTEUR -> base.whereEqualTo("directorUid", uid)
            else -> base.whereEqualTo("userId", uid)
        }
        return observeCollection("users", query, uid)
    }

    fun observeAccessRequests(profile: UserProfile): Flow<List<AccessRequest>> {
        if (profile.roleEnum() != UserRole.ADMIN_NATIONAL) return flowOf(emptyList())
        val base = db.collection("access_requests")
        return observeCollection("access_requests", base, profile.userId)
    }

    fun observeAuditLogs(profile: UserProfile): Flow<List<AuditLog>> {
        val base = db.collection("audit_logs")
        val uid = profile.userId
        val query = when (profile.roleEnum()) {
            UserRole.ADMIN_NATIONAL -> base.whereEqualTo("nationalAdminUid", profile.nationalAdminUid.ifBlank { uid })
            UserRole.DIRECTEUR -> base.whereEqualTo("directorUid", uid)
            else -> base.whereEqualTo("actorUid", uid)
        }
        return observeCollection("audit_logs", query, uid)
    }

    suspend fun createRegion(actor: UserProfile, name: String, code: String): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val natUid = actor.nationalAdminUid.ifBlank { actor.userId }
        val payload = mapOf(
            "name" to name.trim(),
            "code" to code.trim().uppercase(),
            "nationalAdminUid" to natUid,
            "createdByUid" to actor.userId,
            "authorizedUids" to listOf(actor.userId),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("regions").document(docId).set(payload).await()
        logAudit(actor, "CREATE_REGION", "region", docId, "", "Création région ${name.trim()} ($code)")
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "regions", auth)
    }

    suspend fun createSchool(
        actor: UserProfile,
        region: Region,
        name: String,
        code: String,
        schoolType: String,
        address: String
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val natUid = region.nationalAdminUid.ifBlank { actor.nationalAdminUid.ifBlank { actor.userId } }
        val authUids = listOfNotNull(
            actor.userId,
            natUid.takeIf { it.isNotBlank() },
            region.regionalAdminUid.takeIf { it.isNotBlank() }
        ).distinct()
        val payload = mutableMapOf<String, Any>(
            "name" to name.trim(),
            "code" to code.trim().uppercase(),
            "schoolType" to schoolType,
            "regionId" to region.id,
            "tenantId" to docId,
            "regionName" to region.name,
            "address" to address.trim(),
            "nationalAdminUid" to natUid,
            "authorizedUids" to authUids,
            "isActive" to true,
            "createdByUid" to actor.userId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (region.regionalAdminUid.isNotBlank()) payload["regionalAdminUid"] = region.regionalAdminUid
        db.collection("schools").document(docId).set(payload).await()
        logAudit(actor, "CREATE_SCHOOL", "school", docId, docId, "Création établissement ${name.trim()} (${region.name})")
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "schools", auth)
    }

    suspend fun createClass(
        actor: UserProfile,
        school: School,
        name: String,
        level: String,
        academicYear: String
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val dirUid = school.directorUid.ifBlank { actor.userId }
        val natUid = school.nationalAdminUid.ifBlank { actor.nationalAdminUid }
        val authUids = (school.authorizedUids + listOf(actor.userId, dirUid)).filter { it.isNotBlank() }.distinct()
        val tenantId = school.tenantId.ifBlank { school.id }
        val payload = mutableMapOf<String, Any>(
            "schoolId" to school.id,
            "tenantId" to tenantId,
            "schoolName" to school.name,
            "regionId" to school.regionId,
            "name" to name.trim(),
            "level" to level.trim(),
            "academicYear" to academicYear.trim(),
            "directorUid" to dirUid,
            "teacherUids" to emptyList<String>(),
            "parentUids" to emptyList<String>(),
            "studentUids" to emptyList<String>(),
            "authorizedUids" to authUids,
            "createdByUid" to actor.userId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (natUid.isNotBlank()) payload["nationalAdminUid"] = natUid
        if (school.regionalAdminUid.isNotBlank()) payload["regionalAdminUid"] = school.regionalAdminUid
        db.collection("classes").document(docId).set(payload).await()
        logAudit(actor, "CREATE_CLASS", "class", docId, school.id, "Création classe ${name.trim()} (${school.name})")
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "classes", auth)
    }

    suspend fun createSubject(
        actor: UserProfile,
        school: School,
        schoolClass: SchoolClass,
        name: String,
        code: String,
        coefficient: Double,
        teacherUid: String,
        teacherName: String
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val dirUid = school.directorUid.ifBlank { actor.userId }
        val authUids = (schoolClass.authorizedUids + listOfNotNull(actor.userId, dirUid, teacherUid.takeIf { it.isNotBlank() }))
            .filter { it.isNotBlank() }.distinct()
        val tenantId = school.tenantId.ifBlank { school.id }
        val payload = mutableMapOf<String, Any>(
            "schoolId" to school.id,
            "tenantId" to tenantId,
            "regionId" to school.regionId,
            "classId" to schoolClass.id,
            "className" to schoolClass.name,
            "name" to name.trim(),
            "code" to code.trim().uppercase(),
            "coefficient" to coefficient,
            "directorUid" to dirUid,
            "authorizedUids" to authUids,
            "createdByUid" to actor.userId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (school.nationalAdminUid.isNotBlank()) payload["nationalAdminUid"] = school.nationalAdminUid
        if (school.regionalAdminUid.isNotBlank()) payload["regionalAdminUid"] = school.regionalAdminUid
        if (teacherUid.isNotBlank()) {
            payload["teacherUid"] = teacherUid
            payload["teacherName"] = teacherName
        }
        val batch = db.batch()
        batch.set(db.collection("subjects").document(docId), payload)
        if (teacherUid.isNotBlank()) {
            batch.update(
                db.collection("classes").document(schoolClass.id),
                mapOf(
                    "teacherUids" to FieldValue.arrayUnion(teacherUid),
                    "authorizedUids" to FieldValue.arrayUnion(teacherUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit().await()
        logAudit(actor, "CREATE_SUBJECT", "subject", docId, school.id, "Matière ${name.trim()} (coeff $coefficient) en ${schoolClass.name}")
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "subjects", auth)
    }

    suspend fun createScheduleEntry(
        actor: UserProfile,
        school: School,
        schoolClass: SchoolClass,
        subject: Subject,
        dayOfWeek: String,
        startTime: String,
        endTime: String,
        room: String
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val dirUid = school.directorUid.ifBlank { actor.userId }
        val authUids = (schoolClass.authorizedUids + subject.authorizedUids).filter { it.isNotBlank() }.distinct()
        val tenantId = school.tenantId.ifBlank { school.id }
        val payload = mutableMapOf<String, Any>(
            "schoolId" to school.id,
            "tenantId" to tenantId,
            "regionId" to school.regionId,
            "classId" to schoolClass.id,
            "className" to schoolClass.name,
            "subjectId" to subject.id,
            "subjectName" to subject.name,
            "dayOfWeek" to dayOfWeek,
            "startTime" to startTime.trim(),
            "endTime" to endTime.trim(),
            "room" to room.trim(),
            "directorUid" to dirUid,
            "authorizedUids" to authUids,
            "createdByUid" to actor.userId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (subject.teacherUid.isNotBlank()) {
            payload["teacherUid"] = subject.teacherUid
            payload["teacherName"] = subject.teacherName
        }
        if (school.nationalAdminUid.isNotBlank()) payload["nationalAdminUid"] = school.nationalAdminUid
        if (school.regionalAdminUid.isNotBlank()) payload["regionalAdminUid"] = school.regionalAdminUid
        db.collection("schedules").document(docId).set(payload).await()
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "schedules", auth)
    }

    suspend fun enrollStudent(
        actor: UserProfile,
        school: School,
        schoolClass: SchoolClass,
        firstName: String,
        lastName: String,
        matricule: String,
        dateOfBirth: String,
        gender: String,
        parentUids: List<String>,
        studentUid: String,
        gradesVisibleToStudent: Boolean
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val dirUid = school.directorUid.ifBlank { actor.userId }
        val authUids = (schoolClass.authorizedUids + parentUids + listOfNotNull(
            actor.userId,
            dirUid,
            studentUid.takeIf { it.isNotBlank() }
        )).filter { it.isNotBlank() }.distinct()

        val tenantId = school.tenantId.ifBlank { school.id }
        val payload = mutableMapOf<String, Any>(
            "schoolId" to school.id,
            "tenantId" to tenantId,
            "schoolName" to school.name,
            "regionId" to school.regionId,
            "classId" to schoolClass.id,
            "className" to schoolClass.name,
            "firstName" to firstName.trim(),
            "lastName" to lastName.trim(),
            "matricule" to matricule.trim().uppercase(),
            "dateOfBirth" to dateOfBirth.trim(),
            "gender" to gender,
            "parentUids" to parentUids.distinct(),
            "gradesVisibleToStudent" to gradesVisibleToStudent,
            "isActive" to true,
            "directorUid" to dirUid,
            "teacherUids" to schoolClass.teacherUids,
            "authorizedUids" to authUids,
            "createdByUid" to actor.userId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (studentUid.isNotBlank()) payload["studentUid"] = studentUid
        if (school.nationalAdminUid.isNotBlank()) payload["nationalAdminUid"] = school.nationalAdminUid
        if (school.regionalAdminUid.isNotBlank()) payload["regionalAdminUid"] = school.regionalAdminUid

        val batch = db.batch()
        batch.set(db.collection("students").document(docId), payload)
        if (authUids.isNotEmpty() || parentUids.isNotEmpty()) {
            val classUpdates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
            if (parentUids.isNotEmpty()) {
                classUpdates["parentUids"] = FieldValue.arrayUnion(*parentUids.toTypedArray())
            }
            if (studentUid.isNotBlank()) {
                classUpdates["studentUids"] = FieldValue.arrayUnion(studentUid)
            }
            if (authUids.isNotEmpty()) {
                classUpdates["authorizedUids"] = FieldValue.arrayUnion(*authUids.toTypedArray())
            }
            batch.update(db.collection("classes").document(schoolClass.id), classUpdates)
        }
        batch.commit().await()
        logAudit(actor, "ENROLL_STUDENT", "student", docId, school.id, "Inscription ${firstName.trim()} ${lastName.trim()} (${schoolClass.name})")
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "students", auth)
    }

    suspend fun linkParentToStudent(
        actor: UserProfile,
        student: Student,
        parentUid: String
    ): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.update(
            db.collection("students").document(student.id),
            mapOf(
                "parentUids" to FieldValue.arrayUnion(parentUid),
                "authorizedUids" to FieldValue.arrayUnion(parentUid),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("classes").document(student.classId),
            mapOf(
                "parentUids" to FieldValue.arrayUnion(parentUid),
                "authorizedUids" to FieldValue.arrayUnion(parentUid),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        logAudit(actor, "LINK_PARENT_STUDENT", "student", student.id, student.schoolId, "Liaison parent $parentUid à ${student.fullName}")
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "students/${student.id}", auth)
    }

    suspend fun createRoleInvitation(
        actor: UserProfile,
        email: String,
        fullName: String,
        role: UserRole,
        regionId: String,
        school: School?,
        phone: String,
        studentIds: List<String> = emptyList(),
        classIds: List<String> = emptyList()
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val natUid = actor.nationalAdminUid.ifBlank { school?.nationalAdminUid ?: actor.userId }
        val payload = mutableMapOf<String, Any>(
            "email" to email.trim().lowercase(),
            "fullName" to fullName.trim(),
            "role" to role.key,
            "phone" to phone.trim(),
            "status" to "pending",
            "createdByUid" to actor.userId,
            "nationalAdminUid" to natUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (regionId.isNotBlank()) payload["regionId"] = regionId
        if (school != null) {
            payload["schoolId"] = school.id
            payload["tenantId"] = school.tenantId.ifBlank { school.id }
            payload["schoolName"] = school.name
            if (school.directorUid.isNotBlank()) payload["directorUid"] = school.directorUid
            if (school.regionalAdminUid.isNotBlank()) payload["regionalAdminUid"] = school.regionalAdminUid
        } else if (actor.roleEnum() == UserRole.DIRECTEUR) {
            payload["schoolId"] = actor.schoolId
            payload["tenantId"] = actor.tenantId.ifBlank { actor.schoolId }
            payload["schoolName"] = actor.schoolName
            payload["directorUid"] = actor.userId
        } else {
            payload["tenantId"] = regionId.ifBlank { "national" }
        }
        if (studentIds.isNotEmpty()) payload["studentIds"] = studentIds
        if (classIds.isNotEmpty()) payload["classIds"] = classIds

        db.collection("role_invitations").document(docId).set(payload).await()
        logAudit(actor, "CREATE_INVITATION", "role_invitation", docId, school?.id ?: actor.schoolId, "Invitation ${role.labelFr} pour ${email.trim().lowercase()}")
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "role_invitations", auth)
    }

    suspend fun setUserActiveStatus(
        actor: UserProfile,
        targetUser: UserProfile,
        isActive: Boolean
    ): Result<Unit> = runCatching {
        db.collection("users").document(targetUser.userId)
            .update(
                mapOf(
                    "isActive" to isActive,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        logAudit(
            actor,
            if (isActive) "REACTIVATE_USER" else "DEACTIVATE_USER",
            "user",
            targetUser.userId,
            targetUser.schoolId,
            "Statut du compte ${targetUser.fullName} modifié à actif=$isActive"
        )
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "users/${targetUser.userId}", auth)
    }

    suspend fun assignSchoolDirector(
        actor: UserProfile,
        school: School,
        directorUser: UserProfile
    ): Result<Unit> = runCatching {
        val batch = db.batch()
        batch.update(
            db.collection("schools").document(school.id),
            mapOf(
                "directorUid" to directorUser.userId,
                "directorName" to directorUser.fullName,
                "authorizedUids" to FieldValue.arrayUnion(directorUser.userId),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("users").document(directorUser.userId),
            mapOf(
                "schoolId" to school.id,
                "tenantId" to school.tenantId.ifBlank { school.id },
                "schoolName" to school.name,
                "regionId" to school.regionId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        logAudit(actor, "ASSIGN_DIRECTOR", "school", school.id, school.id, "Affectation directeur ${directorUser.fullName} à ${school.name}")
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "schools/${school.id}", auth)
    }

    suspend fun approveAccessRequest(
        actor: UserProfile,
        request: AccessRequest,
        assignedRole: UserRole,
        regionId: String,
        school: School?
    ): Result<String> = runCatching {
        val invitationId = createRoleInvitation(
            actor = actor,
            email = request.email,
            fullName = request.fullName,
            role = assignedRole,
            regionId = regionId,
            school = school,
            phone = request.phone
        ).getOrThrow()

        db.collection("access_requests").document(request.userId)
            .update(
                mapOf(
                    "status" to "approved",
                    "reviewerUid" to actor.userId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()

        logAudit(
            actor = actor,
            action = "APPROVE_ACCESS_REQUEST",
            targetType = "access_request",
            targetId = request.userId,
            schoolId = school?.id ?: "",
            details = "Validation inscription ${request.fullName} (${assignedRole.labelFr})"
        )
        invitationId
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "access_requests/${request.userId}", auth)
    }

    suspend fun rejectAccessRequest(
        actor: UserProfile,
        request: AccessRequest
    ): Result<Unit> = runCatching {
        db.collection("access_requests").document(request.userId)
            .update(
                mapOf(
                    "status" to "rejected",
                    "reviewerUid" to actor.userId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        logAudit(
            actor = actor,
            action = "REJECT_ACCESS_REQUEST",
            targetType = "access_request",
            targetId = request.userId,
            schoolId = "",
            details = "Refus demande d'inscription ${request.fullName}"
        )
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "access_requests/${request.userId}", auth)
    }

    suspend fun logAudit(
        actor: UserProfile,
        action: String,
        targetType: String,
        targetId: String,
        schoolId: String,
        details: String
    ) {
        val logId = UUID.randomUUID().toString()
        val payload = mutableMapOf<String, Any>(
            "actorUid" to actor.userId,
            "actorName" to actor.fullName,
            "actorRole" to actor.role,
            "action" to action,
            "targetType" to targetType,
            "targetId" to targetId,
            "details" to details,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (schoolId.isNotBlank()) {
            payload["schoolId"] = schoolId
            payload["tenantId"] = schoolId
        } else {
            payload["tenantId"] = actor.tenantId.ifBlank { "national" }
        }
        if (actor.directorUid.isNotBlank()) payload["directorUid"] = actor.directorUid
        else if (actor.roleEnum() == UserRole.DIRECTEUR) payload["directorUid"] = actor.userId
        if (actor.nationalAdminUid.isNotBlank()) payload["nationalAdminUid"] = actor.nationalAdminUid
        else if (actor.roleEnum() == UserRole.ADMIN_NATIONAL) payload["nationalAdminUid"] = actor.userId

        try {
            db.collection("audit_logs").document(logId).set(payload).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "audit_logs/$logId", auth)
        }
    }
}
