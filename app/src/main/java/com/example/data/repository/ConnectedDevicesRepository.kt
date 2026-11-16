package com.example.data.repository

import com.example.data.model.ConnectedDeviceSession
import com.example.data.model.QrPairingRequest
import com.example.data.model.UserProfile
import com.google.firebase.Timestamp
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

class ConnectedDevicesRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    companion object {
        private val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{8,128}$")

        fun extractPairingRequestId(rawContent: String): String {
            val trimmed = rawContent.trim()
            if (trimmed.isBlank()) return ""
            if (ID_REGEX.matches(trimmed)) return trimmed

            val queryMatch = Regex("[?&](?:requestId|req|pairingId)=([a-zA-Z0-9_\\-]{8,128})")
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)
            if (!queryMatch.isNullOrBlank()) return queryMatch

            val pathCandidate = trimmed.substringAfterLast("/").substringBefore("?").trim()
            return if (ID_REGEX.matches(pathCandidate)) pathCandidate else ""
        }
    }

    fun observeConnectedDevices(uid: String): Flow<List<ConnectedDeviceSession>> {
        return db.collection("connected_devices")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ConnectedDeviceSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.connectedAt?.seconds ?: 0L }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "connected_devices", auth)
                }
                emit(emptyList())
            }
    }

    suspend fun fetchPairingRequest(requestId: String): Result<QrPairingRequest> = runCatching {
        val cleanId = extractPairingRequestId(requestId)
        require(cleanId.isNotBlank()) { "Code QR invalide ou illisible." }

        val snap = db.collection("qr_pairing_requests").document(cleanId).get().await()
        require(snap.exists()) { "Demande d'association introuvable ou expirée." }

        val req = snap.toObject(
            QrPairingRequest::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: error("Format de demande invalide.")

        require(req.status == "pending") {
            when (req.status) {
                "approved", "consumed" -> "Ce QR Code a déjà été utilisé."
                "rejected" -> "Cette demande a déjà été refusée."
                else -> "Ce QR Code a expiré."
            }
        }
        require(!req.consumed) { "Ce QR Code a déjà été consommé." }

        val exp = req.expiresAt
        if (exp != null && exp.toDate().time <= Timestamp.now().toDate().time) {
            error("Ce QR Code a expiré (validité 120 secondes dépassée). Veuillez rafraîchir la page Web.")
        }
        req
    }.onFailure {
        if (it is FirebaseFirestoreException) {
            handleFirestoreError(it, OperationType.GET, "qr_pairing_requests/$requestId", auth)
        }
    }

    suspend fun approvePairingRequest(
        actor: UserProfile,
        request: QrPairingRequest
    ): Result<String> = runCatching {
        val requestId = request.requestId.ifBlank { request.id }
        require(requestId.isNotBlank()) { "Identifiant de demande manquant." }

        val sessionId = UUID.randomUUID().toString()
        val reqRef = db.collection("qr_pairing_requests").document(requestId)
        val sessionRef = db.collection("connected_devices").document(sessionId)
        val auditRef = db.collection("audit_logs").document(UUID.randomUUID().toString())

        val batch = db.batch()
        val sessionData = mutableMapOf<String, Any>(
            "sessionId" to sessionId,
            "userId" to actor.userId,
            "userName" to actor.fullName,
            "userRole" to actor.role,
            "browserName" to request.browserName.ifBlank { "Navigateur Web" },
            "osName" to request.osName.ifBlank { "Ordinateur" },
            "userAgentLabel" to request.userAgentLabel,
            "requestId" to requestId,
            "revoked" to false,
            "connectedAt" to FieldValue.serverTimestamp(),
            "lastActiveAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (actor.schoolId.isNotBlank()) sessionData["schoolId"] = actor.schoolId
        if (actor.tenantId.isNotBlank()) sessionData["tenantId"] = actor.tenantId

        batch.set(sessionRef, sessionData)
        batch.update(
            reqRef,
            mapOf(
                "status" to "approved",
                "approvedByUid" to actor.userId,
                "approvedByName" to actor.fullName,
                "approvedByRole" to actor.role,
                "sessionId" to sessionId,
                "approvedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val auditData = mutableMapOf<String, Any>(
            "actorUid" to actor.userId,
            "actorName" to actor.fullName,
            "actorRole" to actor.role,
            "action" to "APPROVE_WEB_PAIRING",
            "targetType" to "connected_device",
            "targetId" to sessionId,
            "details" to "Association Web autorisée : ${request.browserName.ifBlank { "Navigateur" }} sur ${request.osName.ifBlank { "Ordinateur" }}",
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (actor.schoolId.isNotBlank()) auditData["schoolId"] = actor.schoolId
        if (actor.tenantId.isNotBlank()) auditData["tenantId"] = actor.tenantId
        if (actor.directorUid.isNotBlank()) auditData["directorUid"] = actor.directorUid
        if (actor.nationalAdminUid.isNotBlank()) auditData["nationalAdminUid"] = actor.nationalAdminUid
        batch.set(auditRef, auditData)
        batch.commit().await()
        sessionId
    }.onFailure {
        handleFirestoreError(it, OperationType.WRITE, "connected_devices", auth)
    }

    suspend fun rejectPairingRequest(
        actor: UserProfile,
        request: QrPairingRequest
    ): Result<Unit> = runCatching {
        val requestId = request.requestId.ifBlank { request.id }
        require(requestId.isNotBlank()) { "Identifiant de demande manquant." }

        val reqRef = db.collection("qr_pairing_requests").document(requestId)
        val auditRef = db.collection("audit_logs").document(UUID.randomUUID().toString())

        val batch = db.batch()
        batch.update(
            reqRef,
            mapOf(
                "status" to "rejected",
                "approvedByUid" to actor.userId,
                "approvedByName" to actor.fullName,
                "approvedByRole" to actor.role,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val auditData = mutableMapOf<String, Any>(
            "actorUid" to actor.userId,
            "actorName" to actor.fullName,
            "actorRole" to actor.role,
            "action" to "REJECT_WEB_PAIRING",
            "targetType" to "qr_pairing_request",
            "targetId" to requestId,
            "details" to "Association Web refusée (${request.browserName.ifBlank { "Navigateur" }})",
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (actor.schoolId.isNotBlank()) auditData["schoolId"] = actor.schoolId
        if (actor.tenantId.isNotBlank()) auditData["tenantId"] = actor.tenantId
        batch.set(auditRef, auditData)
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "qr_pairing_requests/${request.id}", auth)
    }

    suspend fun revokeDeviceSession(
        actor: UserProfile,
        session: ConnectedDeviceSession
    ): Result<Unit> = runCatching {
        val sessionId = session.sessionId.ifBlank { session.id }
        val sessionRef = db.collection("connected_devices").document(sessionId)
        val auditRef = db.collection("audit_logs").document(UUID.randomUUID().toString())

        val batch = db.batch()
        batch.update(
            sessionRef,
            mapOf(
                "revoked" to true,
                "revokedAt" to FieldValue.serverTimestamp(),
                "revokedByUid" to actor.userId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        val auditData = mutableMapOf<String, Any>(
            "actorUid" to actor.userId,
            "actorName" to actor.fullName,
            "actorRole" to actor.role,
            "action" to "REVOKE_WEB_DEVICE",
            "targetType" to "connected_device",
            "targetId" to sessionId,
            "details" to "Déconnexion à distance : ${session.browserName} (${session.osName})",
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (actor.schoolId.isNotBlank()) auditData["schoolId"] = actor.schoolId
        if (actor.tenantId.isNotBlank()) auditData["tenantId"] = actor.tenantId
        batch.set(auditRef, auditData)
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "connected_devices/${session.id}", auth)
    }

    suspend fun revokeAllDeviceSessions(
        actor: UserProfile,
        sessions: List<ConnectedDeviceSession>
    ): Result<Int> = runCatching {
        val activeSessions = sessions.filter { !it.revoked }
        if (activeSessions.isEmpty()) return@runCatching 0

        val batch = db.batch()
        activeSessions.forEach { session ->
            val sid = session.sessionId.ifBlank { session.id }
            batch.update(
                db.collection("connected_devices").document(sid),
                mapOf(
                    "revoked" to true,
                    "revokedAt" to FieldValue.serverTimestamp(),
                    "revokedByUid" to actor.userId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        val auditData = mutableMapOf<String, Any>(
            "actorUid" to actor.userId,
            "actorName" to actor.fullName,
            "actorRole" to actor.role,
            "action" to "REVOKE_ALL_WEB_DEVICES",
            "targetType" to "connected_device",
            "targetId" to actor.userId,
            "details" to "${activeSessions.size} session(s) Web déconnectée(s) à distance",
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (actor.schoolId.isNotBlank()) auditData["schoolId"] = actor.schoolId
        if (actor.tenantId.isNotBlank()) auditData["tenantId"] = actor.tenantId
        batch.set(db.collection("audit_logs").document(UUID.randomUUID().toString()), auditData)
        batch.commit().await()
        activeSessions.size
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "connected_devices", auth)
    }
}
