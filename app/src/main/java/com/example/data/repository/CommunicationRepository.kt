package com.example.data.repository

import com.example.data.model.Announcement
import com.example.data.model.Message
import com.example.data.model.NotificationItem
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

class CommunicationRepository(
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

    fun observeMessages(uid: String): Flow<List<Message>> =
        observeCollection(
            "messages",
            db.collection("messages").whereArrayContains("participantUids", uid),
            uid
        )

    fun observeAnnouncements(profile: UserProfile): Flow<List<Announcement>> {
        val base = db.collection("announcements")
        val uid = profile.userId
        val query = when (profile.roleEnum()) {
            UserRole.ADMIN_NATIONAL -> base.whereEqualTo("nationalAdminUid", profile.nationalAdminUid.ifBlank { uid })
            UserRole.ADMIN_REGIONAL -> base.whereEqualTo("regionalAdminUid", uid)
            UserRole.DIRECTEUR -> base.whereEqualTo("directorUid", uid)
            else -> base.whereArrayContains("authorizedUids", uid)
        }
        return observeCollection("announcements", query, uid)
    }

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> =
        observeCollection(
            "notifications",
            db.collection("notifications").whereEqualTo("recipientUid", uid),
            uid
        )

    suspend fun sendMessage(
        actor: UserProfile,
        schoolId: String,
        recipientUid: String,
        recipientName: String,
        studentId: String,
        studentName: String,
        subject: String,
        body: String
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val resolvedTenant = schoolId.ifBlank { actor.tenantId.ifBlank { actor.schoolId.ifBlank { "national" } } }
        val payload = mutableMapOf<String, Any>(
            "schoolId" to schoolId.ifBlank { actor.schoolId.ifBlank { "national" } },
            "tenantId" to resolvedTenant,
            "participantUids" to listOf(actor.userId, recipientUid),
            "senderUid" to actor.userId,
            "senderName" to actor.fullName,
            "senderRole" to actor.role,
            "recipientUid" to recipientUid,
            "recipientName" to recipientName,
            "subject" to subject.trim(),
            "body" to body.trim(),
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (studentId.isNotBlank()) payload["studentId"] = studentId
        if (studentName.isNotBlank()) payload["studentName"] = studentName

        val batch = db.batch()
        batch.set(db.collection("messages").document(docId), payload)
        batch.set(
            db.collection("notifications").document(UUID.randomUUID().toString()),
            mapOf(
                "recipientUid" to recipientUid,
                "senderUid" to actor.userId,
                "schoolId" to (schoolId.ifBlank { actor.schoolId }),
                "tenantId" to resolvedTenant,
                "type" to "message",
                "title" to "Nouveau message de ${actor.fullName}",
                "body" to "${subject.trim()} : ${body.trim().take(120)}",
                "studentId" to studentId,
                "relatedId" to docId,
                "read" to false,
                "channelPreference" to "app",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()
        docId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "messages", auth)
    }

    suspend fun markMessageRead(messageId: String): Result<Unit> = runCatching {
        db.collection("messages").document(messageId)
            .update(mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "messages/$messageId", auth)
    }

    suspend fun publishAnnouncement(
        actor: UserProfile,
        scope: String,
        regionId: String,
        schoolId: String,
        title: String,
        content: String,
        priority: String,
        authorizedUids: List<String>
    ): Result<String> = runCatching {
        val docId = UUID.randomUUID().toString()
        val allAuthorized = (authorizedUids + actor.userId).filter { it.isNotBlank() }.distinct().take(145)
        val resolvedTenant = schoolId.ifBlank { regionId.ifBlank { actor.tenantId.ifBlank { "national" } } }
        val payload = mutableMapOf<String, Any>(
            "scope" to scope,
            "tenantId" to resolvedTenant,
            "authorUid" to actor.userId,
            "authorName" to actor.fullName,
            "authorRole" to actor.role,
            "authorizedUids" to allAuthorized,
            "title" to title.trim(),
            "content" to content.trim(),
            "priority" to priority,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (regionId.isNotBlank()) payload["regionId"] = regionId
        if (schoolId.isNotBlank()) payload["schoolId"] = schoolId
        if (actor.nationalAdminUid.isNotBlank()) payload["nationalAdminUid"] = actor.nationalAdminUid
        else if (actor.roleEnum() == UserRole.ADMIN_NATIONAL) payload["nationalAdminUid"] = actor.userId
        if (actor.regionalAdminUid.isNotBlank()) payload["regionalAdminUid"] = actor.regionalAdminUid
        else if (actor.roleEnum() == UserRole.ADMIN_REGIONAL) payload["regionalAdminUid"] = actor.userId
        if (actor.directorUid.isNotBlank()) payload["directorUid"] = actor.directorUid
        else if (actor.roleEnum() == UserRole.DIRECTEUR) payload["directorUid"] = actor.userId

        val batch = db.batch()
        batch.set(db.collection("announcements").document(docId), payload)
        allAuthorized.filter { it != actor.userId }.take(50).forEach { targetUid ->
            batch.set(
                db.collection("notifications").document(UUID.randomUUID().toString()),
                mapOf(
                    "recipientUid" to targetUid,
                    "senderUid" to actor.userId,
                    "schoolId" to schoolId,
                    "tenantId" to resolvedTenant,
                    "type" to "annonce",
                    "title" to "Annonce : ${title.trim()}",
                    "body" to content.trim().take(150),
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
        handleFirestoreError(it, OperationType.CREATE, "announcements", auth)
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> = runCatching {
        db.collection("notifications").document(notificationId)
            .update(mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "notifications/$notificationId", auth)
    }
}
