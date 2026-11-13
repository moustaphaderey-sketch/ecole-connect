package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class LessonEntry(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val schoolName: String = "",
    val regionId: String = "",
    val classId: String = "",
    val className: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val teacherUid: String = "",
    val teacherName: String = "",
    val lessonDate: String = "",
    val chapter: String = "",
    val title: String = "",
    val summary: String = "",
    val skillsWorked: String = "",
    val classExercises: String = "",
    val homework: String = "",
    val homeworkDueDate: String = "",
    val attachmentLabel: String = "",
    val nationalAdminUid: String = "",
    val regionalAdminUid: String = "",
    val directorUid: String = "",
    val parentUids: List<String> = emptyList(),
    val authorizedUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AssessmentGrade(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val regionId: String = "",
    val classId: String = "",
    val className: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentUid: String = "",
    val gradesVisibleToStudent: Boolean = true,
    val parentUids: List<String> = emptyList(),
    val teacherUid: String = "",
    val teacherName: String = "",
    val directorUid: String = "",
    val regionalAdminUid: String = "",
    val nationalAdminUid: String = "",
    val authorizedUids: List<String> = emptyList(),
    val title: String = "",
    val assessmentType: String = "devoir",
    val score: Double = 0.0,
    val maxScore: Double = 20.0,
    val coefficient: Double = 1.0,
    val period: String = "Trimestre 1",
    val date: String = "",
    val comment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val regionId: String = "",
    val classId: String = "",
    val className: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentUid: String = "",
    val parentUids: List<String> = emptyList(),
    val teacherUid: String = "",
    val teacherName: String = "",
    val directorUid: String = "",
    val regionalAdminUid: String = "",
    val nationalAdminUid: String = "",
    val authorizedUids: List<String> = emptyList(),
    val date: String = "",
    val status: String = "present",
    val minutesLate: Int = 0,
    val justified: Boolean = false,
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DifficultyAlert(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val regionId: String = "",
    val classId: String = "",
    val className: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentUid: String = "",
    val parentUids: List<String> = emptyList(),
    val teacherUid: String = "",
    val teacherName: String = "",
    val directorUid: String = "",
    val regionalAdminUid: String = "",
    val nationalAdminUid: String = "",
    val authorizedUids: List<String> = emptyList(),
    val category: String = "comprehension",
    val severity: String = "attention",
    val observation: String = "",
    val recommendation: String = "",
    val resolved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ReportCard(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val schoolName: String = "",
    val regionId: String = "",
    val classId: String = "",
    val className: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentUid: String = "",
    val parentUids: List<String> = emptyList(),
    val directorUid: String = "",
    val regionalAdminUid: String = "",
    val nationalAdminUid: String = "",
    val authorizedUids: List<String> = emptyList(),
    val period: String = "Trimestre 1",
    val overallAverage: Double = 0.0,
    val classAverage: Double = 0.0,
    val rankLabel: String = "",
    val absencesCount: Int = 0,
    val tardiesCount: Int = 0,
    val councilDecision: String = "",
    val directorObservation: String = "",
    val generatedByUid: String = "",
    val published: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val participantUids: List<String> = emptyList(),
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "",
    val recipientUid: String = "",
    val recipientName: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val subject: String = "",
    val body: String = "",
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val scope: String = "school",
    val regionId: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorRole: String = "",
    val nationalAdminUid: String = "",
    val regionalAdminUid: String = "",
    val directorUid: String = "",
    val authorizedUids: List<String> = emptyList(),
    val title: String = "",
    val content: String = "",
    val priority: String = "normale",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val senderUid: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val type: String = "lecon",
    val title: String = "",
    val body: String = "",
    val studentId: String = "",
    val relatedId: String = "",
    val read: Boolean = false,
    val channelPreference: String = "app",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val directorUid: String = "",
    val nationalAdminUid: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
