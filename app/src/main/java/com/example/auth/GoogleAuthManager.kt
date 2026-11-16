package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.model.UserRole
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class RegistrationCategory(
    val titleFr: String,
    val subtitleFr: String,
    val defaultRole: UserRole
) {
    ADMIN(
        titleFr = "Administrateur",
        subtitleFr = "Direction d'établissement, administration régionale ou nationale",
        defaultRole = UserRole.DIRECTEUR
    ),
    ENSEIGNANT(
        titleFr = "Enseignant",
        subtitleFr = "Cahier de texte numérique, notes, présences et suivi des élèves",
        defaultRole = UserRole.ENSEIGNANT
    ),
    PARENT(
        titleFr = "Parent d'élève",
        subtitleFr = "Suivi quotidien des cours, notes, absences, alertes et bulletins PDF",
        defaultRole = UserRole.PARENT
    )
}

data class PendingRegistrationDraft(
    val category: RegistrationCategory,
    val role: UserRole,
    val lastName: String,
    val firstName: String,
    val emailHint: String = "",
    val phone: String,
    val schoolId: String = "",
    val schoolName: String = "",
    val schoolOrRegionHint: String,
    val extraDetail: String,
    val consentAccepted: Boolean
) {
    val fullName: String
        get() = listOf(firstName.trim(), lastName.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")

    fun formattedSchoolHint(): String {
        val base = schoolName.ifBlank { schoolOrRegionHint }.trim()
        val extra = extraDetail.trim()
        val combined = when {
            base.isNotBlank() && extra.isNotBlank() -> "$base — $extra"
            base.isNotBlank() -> base
            else -> extra
        }
        return combined.take(160)
    }
}

object GoogleAuthManager {

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        val existingUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        if (existingUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = resolveWebClientId(context)
        if (clientId == null) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val credentialContext = (context as? Activity) ?: context
                val result = credentialManager.getCredential(credentialContext, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val firebaseCred = GoogleAuthProvider.getCredential(googleIdToken, null)
                    Firebase.auth.signInWithCredential(firebaseCred).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: NoCredentialException) {
                onUnauthenticated()
            } catch (_: GetCredentialException) {
                onUnauthenticated()
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun signInWithGoogleCredentials(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        val clientId = resolveWebClientId(context)
        if (clientId == null) {
            onAuthError("Configuration Google Sign-In manquante (default_web_client_id introuvable).")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val activityContext = context as? Activity
                    ?: throw IllegalStateException("Contexte Activity requis pour la connexion Google.")
                val result = credentialManager.getCredential(activityContext, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val firebaseCred = GoogleAuthProvider.getCredential(googleIdToken, null)
                    Firebase.auth.signInWithCredential(firebaseCred).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Type d'identifiant Google inattendu.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: NoCredentialException) {
                Log.w("Auth", "No Google credentials available on device: ${e.message}", e)
                onAuthError("Aucun compte Google configuré sur cet appareil. Ajoutez un compte Google dans les paramètres du téléphone.")
            } catch (e: GetCredentialException) {
                Log.e("Auth", "Google Credential Manager error", e)
                onAuthError(e.localizedMessage ?: "Échec de récupération des identifiants Google.")
            } catch (e: FirebaseAuthException) {
                Log.e("Auth", "Firebase Auth error (${e.errorCode})", e)
                onAuthError("Erreur d'authentification Firebase : ${e.localizedMessage ?: e.errorCode}")
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Échec de la connexion Google.")
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("Auth", "Failed to clear credential state: ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                    onSignOutComplete()
                }
            }
        }
    }

    private fun resolveWebClientId(context: Context): String? {
        return try {
            context.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
