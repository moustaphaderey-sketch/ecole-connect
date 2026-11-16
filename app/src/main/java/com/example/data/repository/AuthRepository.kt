package com.example.data.repository

import com.example.data.model.AccessRequest
import com.example.data.model.PlatformSettings
import com.example.data.model.RoleInvitation
import com.example.data.model.SchoolDirectoryEntry
import com.example.data.model.UserPrivateContact
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.seed.DjiboutiSchoolsSeeder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AuthRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    fun observeUserProfile(uid: String): Flow<UserProfile?> {
        val path = "users/$uid"
        return db.collection("users").document(uid)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path, auth)
                }
                throw error
            }
    }

    fun observePrivateContact(uid: String): Flow<UserPrivateContact?> {
        val path = "users/$uid/private/contact"
        return db.collection("users").document(uid)
            .collection("private").document("contact")
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(UserPrivateContact::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path, auth)
                }
                throw error
            }
    }

    fun observePlatformSettings(): Flow<PlatformSettings?> {
        val path = "platform_config/settings"
        return db.collection("platform_config").document("settings")
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(PlatformSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path, auth)
                }
                throw error
            }
    }

    suspend fun isBootstrapAvailable(): Boolean {
        return try {
            val doc = db.collection("platform_config").document("bootstrap").get().await()
            !doc.exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun findPendingInvitationForEmail(email: String): RoleInvitation? {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) return null
        return try {
            val snapshot = db.collection("role_invitations")
                .whereEqualTo("email", normalized)
                .whereEqualTo("status", "pending")
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()
                ?.toObject(RoleInvitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "role_invitations", auth)
            null
        }
    }

    fun observePendingInvitationForEmail(email: String): Flow<RoleInvitation?> {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) return kotlinx.coroutines.flow.flowOf(null)
        return db.collection("role_invitations")
            .whereEqualTo("email", normalized)
            .whereEqualTo("status", "pending")
            .limit(1)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.firstOrNull()
                    ?.toObject(RoleInvitation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "role_invitations", auth)
                }
                emit(null)
            }
    }

    suspend fun bootstrapNationalAdmin(
        uid: String,
        fullName: String,
        email: String,
        phone: String,
        consentAccepted: Boolean,
        seedOfficialSchools: Boolean = false
    ): Result<Unit> = runCatching {
        val cleanName = fullName.trim().ifBlank { "Administrateur National" }
        val bootstrapRef = db.collection("platform_config").document("bootstrap")
        val settingsRef = db.collection("platform_config").document("settings")
        val userRef = db.collection("users").document(uid)
        val contactRef = userRef.collection("private").document("contact")
        val auditRef = db.collection("audit_logs").document(UUID.randomUUID().toString())

        val batch = db.batch()
        val existingBootstrap = bootstrapRef.get().await()
        if (!existingBootstrap.exists()) {
            batch.set(
                bootstrapRef,
                mapOf(
                    "ownerUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                settingsRef,
                mapOf(
                    "ownerUid" to uid,
                    "platformName" to "ÉCOLE CONNECTÉE",
                    "academicYear" to "2026-2027",
                    "gradingScale" to 20.0,
                    "smsGatewayEnabled" to false,
                    "whatsappGatewayEnabled" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        batch.set(
            userRef,
            mapOf(
                "userId" to uid,
                "fullName" to cleanName,
                "role" to UserRole.ADMIN_NATIONAL.key,
                "tenantId" to "national",
                "nationalAdminUid" to uid,
                "isActive" to true,
                "consentAccepted" to consentAccepted,
                "preferredChannel" to "app",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            contactRef,
            mapOf(
                "userId" to uid,
                "email" to email.trim().lowercase(),
                "phone" to phone.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            auditRef,
            mapOf(
                "actorUid" to uid,
                "actorName" to cleanName,
                "actorRole" to UserRole.ADMIN_NATIONAL.key,
                "action" to "BOOTSTRAP_NATIONAL_ADMIN",
                "targetType" to "user",
                "targetId" to uid,
                "tenantId" to "national",
                "nationalAdminUid" to uid,
                "details" to "Initialisation du compte Administrateur National et acceptation de la politique de confidentialité.",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        if (seedOfficialSchools) {
            DjiboutiSchoolsSeeder(db, auth)
                .seed(
                    actorUid = uid,
                    actorName = cleanName,
                    actorRole = UserRole.ADMIN_NATIONAL.key,
                    nationalAdminUid = uid
                )
                .getOrThrow()
        }
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.WRITE, "users/$uid", auth)
    }

    suspend fun claimRoleInvitation(
        uid: String,
        invitation: RoleInvitation,
        fullName: String,
        email: String,
        phone: String,
        consentAccepted: Boolean
    ): Result<Unit> = runCatching {
        val cleanName = fullName.trim().ifBlank { invitation.fullName }
        val userRef = db.collection("users").document(uid)
        val contactRef = userRef.collection("private").document("contact")
        val invRef = db.collection("role_invitations").document(invitation.id)
        val auditRef = db.collection("audit_logs").document(UUID.randomUUID().toString())

        val resolvedTenantId = invitation.tenantId.ifBlank {
            invitation.schoolId.ifBlank { invitation.regionId.ifBlank { "national" } }
        }
        val userPayload = mutableMapOf<String, Any>(
            "userId" to uid,
            "fullName" to cleanName,
            "role" to invitation.role,
            "invitationId" to invitation.id,
            "tenantId" to resolvedTenantId,
            "isActive" to true,
            "consentAccepted" to consentAccepted,
            "preferredChannel" to "app",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (invitation.regionId.isNotBlank()) userPayload["regionId"] = invitation.regionId
        if (invitation.schoolId.isNotBlank()) userPayload["schoolId"] = invitation.schoolId
        if (invitation.schoolName.isNotBlank()) userPayload["schoolName"] = invitation.schoolName
        if (invitation.nationalAdminUid.isNotBlank()) userPayload["nationalAdminUid"] = invitation.nationalAdminUid
        if (invitation.regionalAdminUid.isNotBlank()) userPayload["regionalAdminUid"] = invitation.regionalAdminUid
        val resolvedDirUid = if (invitation.role == UserRole.DIRECTEUR.key) uid else invitation.directorUid
        if (resolvedDirUid.isNotBlank()) userPayload["directorUid"] = resolvedDirUid
        if (invitation.classIds.isNotEmpty()) userPayload["classIds"] = invitation.classIds
        if (invitation.subjectIds.isNotEmpty()) userPayload["subjectIds"] = invitation.subjectIds
        if (invitation.studentIds.isNotEmpty()) userPayload["studentIds"] = invitation.studentIds

        val contactPayload = mutableMapOf<String, Any>(
            "userId" to uid,
            "email" to email.trim().lowercase(),
            "phone" to phone.ifBlank { invitation.phone },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (resolvedDirUid.isNotBlank()) contactPayload["directorUid"] = resolvedDirUid

        val batch = db.batch()
        batch.set(userRef, userPayload)
        batch.set(contactRef, contactPayload)
        batch.update(
            invRef,
            mapOf(
                "status" to "claimed",
                "claimedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            auditRef,
            mapOf(
                "actorUid" to uid,
                "actorName" to cleanName,
                "actorRole" to invitation.role,
                "action" to "CLAIM_INVITATION",
                "targetType" to "role_invitation",
                "targetId" to invitation.id,
                "schoolId" to invitation.schoolId,
                "tenantId" to resolvedTenantId,
                "directorUid" to resolvedDirUid,
                "nationalAdminUid" to invitation.nationalAdminUid,
                "details" to "Activation du compte ${invitation.role} via invitation vérifiée.",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()

        if (invitation.role == UserRole.DIRECTEUR.key && invitation.schoolId.isNotBlank()) {
            runCatching {
                val schoolRef = db.collection("schools").document(invitation.schoolId)
                val schoolSnap = schoolRef.get().await()
                if (schoolSnap.exists()) {
                    val existingDirector = schoolSnap.getString("directorUid").orEmpty()
                    if (existingDirector.isBlank() || existingDirector == uid) {
                        schoolRef.update(
                            mapOf(
                                "directorUid" to uid,
                                "directorName" to cleanName,
                                "authorizedUids" to FieldValue.arrayUnion(uid),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }
        }
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.WRITE, "users/$uid", auth)
    }

    fun observeSchoolDirectory(): Flow<List<SchoolDirectoryEntry>> {
        return db.collection("school_directory")
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(SchoolDirectoryEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.name }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                emit(emptyList())
            }
    }

    fun observeMyAccessRequest(uid: String): Flow<AccessRequest?> {
        val path = "access_requests/$uid"
        return db.collection("access_requests").document(uid)
            .snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(AccessRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, path, auth)
                }
                throw error
            }
    }

    suspend fun submitAccessRequest(
        uid: String,
        fullName: String,
        email: String,
        requestedRole: UserRole,
        schoolHint: String,
        phone: String,
        firstName: String = "",
        lastName: String = "",
        schoolId: String = "",
        schoolName: String = "",
        extraDetails: String = ""
    ): Result<Unit> = runCatching {
        val cleanFirst = firstName.trim().take(80)
        val cleanLast = lastName.trim().take(80)
        val resolvedFullName = fullName.trim().ifBlank { "$cleanFirst $cleanLast".trim() }.take(120)
        val cleanSchoolName = schoolName.trim().take(160)
        val resolvedHint = schoolHint.trim().ifBlank {
            listOf(cleanSchoolName, extraDetails.trim()).filter { it.isNotBlank() }.joinToString(" • ")
        }.take(200)
        val docRef = db.collection("access_requests").document(uid)
        val payload = mapOf(
            "userId" to uid,
            "firstName" to cleanFirst,
            "lastName" to cleanLast,
            "fullName" to resolvedFullName,
            "email" to email.trim().lowercase().take(160),
            "requestedRole" to requestedRole.key,
            "schoolId" to schoolId.trim().take(128),
            "schoolName" to cleanSchoolName,
            "schoolHint" to resolvedHint,
            "extraDetails" to extraDetails.trim().take(300),
            "phone" to phone.trim().take(40),
            "status" to "pending",
            "reviewerUid" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "access_requests/$uid", auth)
    }

    suspend fun resubmitAccessRequest(
        uid: String,
        firstName: String,
        lastName: String,
        fullName: String,
        requestedRole: UserRole,
        schoolId: String,
        schoolName: String,
        schoolHint: String,
        extraDetails: String,
        applicantResponse: String,
        phone: String
    ): Result<Unit> = runCatching {
        val cleanFirst = firstName.trim().take(80)
        val cleanLast = lastName.trim().take(80)
        val resolvedFullName = fullName.trim().ifBlank { "$cleanFirst $cleanLast".trim() }.take(120)
        val cleanSchoolName = schoolName.trim().take(160)
        val resolvedHint = schoolHint.trim().ifBlank {
            listOf(cleanSchoolName, extraDetails.trim()).filter { it.isNotBlank() }.joinToString(" • ")
        }.take(200)
        val updates = mapOf(
            "firstName" to cleanFirst,
            "lastName" to cleanLast,
            "fullName" to resolvedFullName,
            "phone" to phone.trim().take(40),
            "requestedRole" to requestedRole.key,
            "schoolId" to schoolId.trim().take(128),
            "schoolName" to cleanSchoolName,
            "schoolHint" to resolvedHint,
            "extraDetails" to extraDetails.trim().take(300),
            "applicantResponse" to applicantResponse.trim().take(500),
            "status" to "pending",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("access_requests").document(uid).update(updates).await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "access_requests/$uid", auth)
    }

    suspend fun updateProfilePreferences(
        uid: String,
        fullName: String,
        consentAccepted: Boolean,
        preferredChannel: String,
        phone: String,
        whatsappNumber: String
    ): Result<Unit> = runCatching {
        val userRef = db.collection("users").document(uid)
        val contactRef = userRef.collection("private").document("contact")
        val batch = db.batch()
        batch.update(
            userRef,
            mapOf(
                "fullName" to fullName.trim(),
                "consentAccepted" to consentAccepted,
                "preferredChannel" to preferredChannel,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            contactRef,
            mapOf(
                "phone" to phone.trim(),
                "whatsappNumber" to whatsappNumber.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "users/$uid", auth)
    }

    suspend fun updatePlatformSettings(
        actor: UserProfile,
        academicYear: String,
        gradingScale: Double,
        smsEnabled: Boolean,
        whatsappEnabled: Boolean
    ): Result<Unit> = runCatching {
        val settingsRef = db.collection("platform_config").document("settings")
        settingsRef.update(
            mapOf(
                "academicYear" to academicYear.trim(),
                "gradingScale" to gradingScale,
                "smsGatewayEnabled" to smsEnabled,
                "whatsappGatewayEnabled" to whatsappEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        db.collection("audit_logs").document(UUID.randomUUID().toString()).set(
            mapOf(
                "actorUid" to actor.userId,
                "actorName" to actor.fullName,
                "actorRole" to actor.role,
                "action" to "UPDATE_PLATFORM_SETTINGS",
                "targetType" to "platform_config",
                "targetId" to "settings",
                "nationalAdminUid" to actor.userId,
                "details" to "Année scolaire: $academicYear, Barème: $gradingScale, SMS: $smsEnabled, WhatsApp: $whatsappEnabled",
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "platform_config/settings", auth)
    }
}
