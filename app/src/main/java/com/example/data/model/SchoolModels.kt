package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val key: String, val labelFr: String) {
    ADMIN_NATIONAL("admin_national", "Administrateur national"),
    ADMIN_REGIONAL("admin_regional", "Administrateur régional"),
    DIRECTEUR("directeur", "Directeur d'établissement"),
    ENSEIGNANT("enseignant", "Enseignant"),
    PARENT("parent", "Parent d'élève"),
    ELEVE("eleve", "Élève");

    companion object {
        fun fromKey(key: String?): UserRole =
            entries.firstOrNull { it.key == key } ?: PARENT
    }
}

@IgnoreExtraProperties
data class PlatformSettings(
    @DocumentId val id: String = "settings",
    val ownerUid: String = "",
    val platformName: String = "ÉCOLE CONNECTÉE",
    val academicYear: String = "2026-2027",
    val gradingScale: Double = 20.0,
    val smsGatewayEnabled: Boolean = false,
    val whatsappGatewayEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val role: String = UserRole.PARENT.key,
    val invitationId: String = "",
    val regionId: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val schoolName: String = "",
    val nationalAdminUid: String = "",
    val regionalAdminUid: String = "",
    val directorUid: String = "",
    val classIds: List<String> = emptyList(),
    val studentIds: List<String> = emptyList(),
    @get:PropertyName("isActive")
    @field:PropertyName("isActive")
    val isActive: Boolean = true,
    val consentAccepted: Boolean = false,
    val preferredChannel: String = "app",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleEnum(): UserRole = UserRole.fromKey(role)
}

@IgnoreExtraProperties
data class UserPrivateContact(
    @DocumentId val id: String = "contact",
    val userId: String = "",
    val directorUid: String = "",
    val email: String = "",
    val phone: String = "",
    val whatsappNumber: String = "",
    val address: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RoleInvitation(
    @DocumentId val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val role: String = UserRole.ENSEIGNANT.key,
    val regionId: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val schoolName: String = "",
    val nationalAdminUid: String = "",
    val regionalAdminUid: String = "",
    val directorUid: String = "",
    val classIds: List<String> = emptyList(),
    val studentIds: List<String> = emptyList(),
    val phone: String = "",
    val status: String = "pending",
    val claimedByUid: String = "",
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AccessRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val requestedRole: String = UserRole.PARENT.key,
    val schoolHint: String = "",
    val phone: String = "",
    val status: String = "pending",
    val reviewerUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Region(
    @DocumentId val id: String = "",
    val name: String = "",
    val code: String = "",
    val nationalAdminUid: String = "",
    val regionalAdminUid: String = "",
    val regionalAdminName: String = "",
    val createdByUid: String = "",
    val authorizedUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class School(
    @DocumentId val id: String = "",
    val name: String = "",
    val code: String = "",
    val schoolType: String = "primaire",
    val regionId: String = "",
    val tenantId: String = "",
    val regionName: String = "",
    val address: String = "",
    val sector: String = "",
    val city: String = "Djibouti-Ville",
    val country: String = "Djibouti",
    val description: String = "",
    @get:PropertyName("isOfficialCatalog")
    @field:PropertyName("isOfficialCatalog")
    val isOfficialCatalog: Boolean = false,
    val nationalAdminUid: String = "",
    val regionalAdminUid: String = "",
    val directorUid: String = "",
    val directorName: String = "",
    val authorizedUids: List<String> = emptyList(),
    @get:PropertyName("isActive")
    @field:PropertyName("isActive")
    val isActive: Boolean = true,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SchoolClass(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val schoolName: String = "",
    val regionId: String = "",
    val name: String = "",
    val level: String = "",
    val academicYear: String = "2026-2027",
    val nationalAdminUid: String = "",
    val regionalAdminUid: String = "",
    val directorUid: String = "",
    val teacherUid: String = "",
    val teacherUids: List<String> = emptyList(),
    val parentUids: List<String> = emptyList(),
    val studentUids: List<String> = emptyList(),
    val authorizedUids: List<String> = emptyList(),
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Subject(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val regionId: String = "",
    val classId: String = "",
    val className: String = "",
    val name: String = "",
    val code: String = "",
    val coefficient: Double = 1.0,
    val nationalAdminUid: String = "",
    val regionalAdminUid: String = "",
    val directorUid: String = "",
    val teacherUid: String = "",
    val teacherName: String = "",
    val authorizedUids: List<String> = emptyList(),
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ScheduleEntry(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val regionId: String = "",
    val classId: String = "",
    val className: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val teacherUid: String = "",
    val teacherName: String = "",
    val dayOfWeek: String = "lundi",
    val startTime: String = "08:00",
    val endTime: String = "09:00",
    val room: String = "",
    val nationalAdminUid: String = "",
    val regionalAdminUid: String = "",
    val directorUid: String = "",
    val authorizedUids: List<String> = emptyList(),
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Student(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val tenantId: String = "",
    val schoolName: String = "",
    val regionId: String = "",
    val classId: String = "",
    val className: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val matricule: String = "",
    val dateOfBirth: String = "",
    val gender: String = "M",
    val parentUids: List<String> = emptyList(),
    val studentUid: String = "",
    val gradesVisibleToStudent: Boolean = true,
    @get:PropertyName("isActive")
    @field:PropertyName("isActive")
    val isActive: Boolean = true,
    val nationalAdminUid: String = "",
    val regionalAdminUid: String = "",
    val directorUid: String = "",
    val teacherUids: List<String> = emptyList(),
    val authorizedUids: List<String> = emptyList(),
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val fullName: String get() = "$firstName $lastName".trim()
}
