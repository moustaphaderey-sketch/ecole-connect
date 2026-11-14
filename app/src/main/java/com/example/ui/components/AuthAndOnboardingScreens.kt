package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthManager
import com.example.auth.PendingRegistrationDraft
import com.example.data.model.RoleInvitation
import com.example.data.model.UserPrivateContact
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.screens.auth.AuthEntryHost
import com.example.ui.viewmodel.AccountGateState
import kotlinx.coroutines.CoroutineScope

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) = GoogleAuthManager.attemptAutoSignIn(
    context = context,
    credentialManager = credentialManager,
    onAuthSuccess = onAuthSuccess,
    onUnauthenticated = onUnauthenticated,
    scope = scope
)

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) = GoogleAuthManager.signInWithGoogleCredentials(
    context = context,
    credentialManager = credentialManager,
    onAuthSuccess = onAuthSuccess,
    onAuthError = onAuthError,
    scope = scope,
    onAuthCancelled = onAuthCancelled
)

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) = GoogleAuthManager.signOut(
    credentialManager = credentialManager,
    scope = scope,
    onSignOutComplete = onSignOutComplete
)

@Composable
fun AuthScreen(
    onPrepareRegistration: (PendingRegistrationDraft?) -> Unit = {},
    onAuthSuccess: () -> Unit
) {
    AuthEntryHost(
        onPrepareRegistration = onPrepareRegistration,
        onAuthSuccess = onAuthSuccess
    )
}

@Composable
fun OnboardingAndConsentGate(
    gateState: AccountGateState,
    userEmail: String,
    userDisplayName: String,
    pendingDraft: PendingRegistrationDraft? = null,
    isBusy: Boolean,
    errorMessage: String?,
    onBootstrapAdmin: (String, String, Boolean) -> Unit,
    onClaimInvitation: (RoleInvitation, String, String, Boolean) -> Unit,
    onSubmitAccessRequest: (String, UserRole, String, String) -> Unit,
    onSaveConsent: (String, String, String, String) -> Unit,
    onRefreshStatus: () -> Unit = {},
    onSignOut: () -> Unit
) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_ecole_embleme),
                                contentDescription = "Emblème ÉCOLE CONNECTÉE",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                            )
                            Text(
                                text = "ÉCOLE CONNECTÉE",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("sign_out_gate_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Déconnexion")
                        }
                    }

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    when (gateState) {
                        is AccountGateState.Loading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text("Vérification de votre profil sécurisé...")
                            }
                        }

                        is AccountGateState.Error -> {
                            Text(
                                text = gateState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        is AccountGateState.Deactivated -> {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Compte désactivé",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Votre compte (${gateState.profile.fullName}) a été désactivé par l'administration. Veuillez contacter votre directeur d'établissement ou l'administration nationale.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        is AccountGateState.NeedsConsent -> {
                            ConsentSection(
                                profile = gateState.profile,
                                contact = gateState.contact,
                                isBusy = isBusy,
                                onSaveConsent = onSaveConsent
                            )
                        }

                        is AccountGateState.NeedsOnboarding -> {
                            val initialName = pendingDraft?.fullName?.ifBlank { null } ?: userDisplayName.ifBlank { "" }
                            val initialPhone = pendingDraft?.phone ?: ""
                            val initialConsent = pendingDraft?.consentAccepted ?: false

                            var fullName by remember(pendingDraft) { mutableStateOf(initialName) }
                            var phone by remember(pendingDraft) { mutableStateOf(initialPhone) }
                            var consentChecked by remember(pendingDraft) { mutableStateOf(initialConsent) }

                            if (gateState.pendingInvitation != null) {
                                val inv = gateState.pendingInvitation
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Invitation vérifiée détectée",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = "Vous avez été pré-enregistré(e) en tant que ${UserRole.fromKey(inv.role).labelFr}" +
                                        if (inv.schoolName.isNotBlank()) " pour ${inv.schoolName}." else ".",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedTextField(
                                    value = fullName.ifBlank { inv.fullName },
                                    onValueChange = { fullName = it },
                                    label = { Text("Nom complet") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = phone.ifBlank { inv.phone },
                                    onValueChange = { phone = it },
                                    label = { Text("Téléphone (optionnel)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                PrivacyConsentRow(consentChecked) { consentChecked = it }
                                Button(
                                    onClick = {
                                        onClaimInvitation(inv, fullName.ifBlank { inv.fullName }, phone, consentChecked)
                                    },
                                    enabled = !isBusy && consentChecked && (fullName.isNotBlank() || inv.fullName.isNotBlank()),
                                    modifier = Modifier.fillMaxWidth().testTag("claim_invitation_button")
                                ) {
                                    Text("Activer mon compte")
                                }
                            } else if (gateState.canBootstrapAdmin && (pendingDraft == null || pendingDraft.role == UserRole.ADMIN_NATIONAL)) {
                                Icon(
                                    imageVector = Icons.Filled.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Initialisation de la plateforme nationale",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = "Compte authentifié : $userEmail. Aucune donnée fictive n'est préremplie. Créez le compte Administrateur National pour commencer à enregistrer les régions, établissements, directeurs et utilisateurs.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedTextField(
                                    value = fullName,
                                    onValueChange = { fullName = it },
                                    label = { Text("Nom complet de l'administrateur") },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_fullname_input")
                                )
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text("Téléphone professionnel") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                PrivacyConsentRow(consentChecked) { consentChecked = it }
                                Button(
                                    onClick = { onBootstrapAdmin(fullName, phone, consentChecked) },
                                    enabled = !isBusy && consentChecked && fullName.trim().length >= 2,
                                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("bootstrap_admin_button")
                                ) {
                                    Text("Initialiser l'Administrateur National")
                                }
                            } else {
                                val initialRole = pendingDraft?.role
                                    ?.takeIf { it != UserRole.ADMIN_NATIONAL }
                                    ?: UserRole.PARENT
                                var requestedRole by remember(pendingDraft) { mutableStateOf(initialRole) }
                                var schoolHint by remember(pendingDraft) {
                                    mutableStateOf(pendingDraft?.formattedSchoolHint() ?: "")
                                }
                                var showGateSchoolPicker by remember { mutableStateOf(false) }

                                Text(
                                    text = if (pendingDraft != null) {
                                        "Finaliser votre inscription (${requestedRole.labelFr})"
                                    } else {
                                        "Inscription et demande d'habilitation"
                                    },
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = "Compte Google vérifié : $userEmail. Complétez ou confirmez vos informations ci-dessous pour rattacher votre compte à votre établissement.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (gateState.accessRequest != null) {
                                    val req = gateState.accessRequest
                                    val statusLabel = when (req.status) {
                                        "approved" -> "Approuvée (activation en cours)"
                                        "rejected" -> "Refusée par l'administration"
                                        else -> "En attente de validation par l'Administrateur National"
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                                Text(
                                                    text = "Inscription enregistrée (${UserRole.fromKey(req.requestedRole).labelFr}) — Statut : $statusLabel",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            if (req.schoolHint.isNotBlank()) {
                                                Text(
                                                    text = "Rattachement demandé : ${req.schoolHint}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                            if (req.status == "pending") {
                                                Text(
                                                    text = "Dès que l'Administrateur National approuvera votre demande dans Établissements & Classes → Comptes & Invitations, l'activation de votre compte s'affichera automatiquement ici.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = onRefreshStatus,
                                        enabled = !isBusy,
                                        modifier = Modifier.fillMaxWidth().testTag("refresh_onboarding_button")
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(8.dp))
                                        Text("Actualiser le statut de validation")
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = fullName,
                                        onValueChange = { fullName = it },
                                        label = { Text("Nom complet") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DropdownSelector(
                                        label = "Rôle demandé",
                                        options = listOf(
                                            UserRole.PARENT,
                                            UserRole.ENSEIGNANT,
                                            UserRole.DIRECTEUR,
                                            UserRole.ADMIN_REGIONAL,
                                            UserRole.ELEVE
                                        ),
                                        selectedOption = requestedRole,
                                        optionLabel = { it.labelFr },
                                        onOptionSelected = { requestedRole = it }
                                    )
                                    OutlinedTextField(
                                        value = schoolHint,
                                        onValueChange = { schoolHint = it.take(160) },
                                        label = { Text("Établissement / Région / Détails") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedButton(
                                        onClick = { showGateSchoolPicker = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(8.dp))
                                        Text("Choisir une école, collège ou lycée (Djibouti-Ville)")
                                    }
                                    OutlinedTextField(
                                        value = phone,
                                        onValueChange = { phone = it.take(40) },
                                        label = { Text("Téléphone") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = { onSubmitAccessRequest(fullName, requestedRole, schoolHint, phone) },
                                        enabled = !isBusy && fullName.trim().length >= 2,
                                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("submit_access_request_button")
                                    ) {
                                        Text("Envoyer ma demande d'inscription")
                                    }
                                }

                                if (showGateSchoolPicker) {
                                    OfficialSchoolPickerDialog(
                                        onDismiss = { showGateSchoolPicker = false },
                                        onSelectSchool = { entry ->
                                            schoolHint = "${entry.name} (${entry.levelLabelFr})".take(160)
                                            showGateSchoolPicker = false
                                        }
                                    )
                                }
                            }
                        }

                        is AccountGateState.Active -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag("consent_checkbox")
            )
            Text(
                text = "J'accepte la politique de confidentialité et les règles strictes de protection des données scolaires et des mineurs d'ÉCOLE CONNECTÉE.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ConsentSection(
    profile: UserProfile,
    contact: UserPrivateContact?,
    isBusy: Boolean,
    onSaveConsent: (String, String, String, String) -> Unit
) {
    var fullName by remember { mutableStateOf(profile.fullName) }
    var preferredChannel by remember { mutableStateOf(profile.preferredChannel.ifBlank { "app" }) }
    var phone by remember { mutableStateOf(contact?.phone ?: "") }
    var whatsapp by remember { mutableStateOf(contact?.whatsappNumber ?: "") }
    var accepted by remember { mutableStateOf(false) }

    Text(
        text = "Politique de confidentialité et préférences",
        style = MaterialTheme.typography.headlineSmall
    )
    Text(
        text = "Conformément aux exigences de sécurité des données scolaires de mineurs, veuillez confirmer votre consentement et votre canal de notification préféré.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = fullName,
        onValueChange = { fullName = it },
        label = { Text("Nom complet") },
        modifier = Modifier.fillMaxWidth()
    )
    DropdownSelector(
        label = "Canal de notification prioritaire",
        options = listOf("app", "sms", "whatsapp"),
        selectedOption = preferredChannel,
        optionLabel = {
            when (it) {
                "sms" -> "Application + SMS (si passerelle active)"
                "whatsapp" -> "Application + WhatsApp Business (si configuré)"
                else -> "Notifications dans l'application"
            }
        },
        onOptionSelected = { preferredChannel = it }
    )
    OutlinedTextField(
        value = phone,
        onValueChange = { phone = it },
        label = { Text("Numéro de téléphone") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = whatsapp,
        onValueChange = { whatsapp = it },
        label = { Text("Numéro WhatsApp (optionnel)") },
        modifier = Modifier.fillMaxWidth()
    )
    PrivacyConsentRow(accepted) { accepted = it }
    Button(
        onClick = { onSaveConsent(fullName, preferredChannel, phone, whatsapp) },
        enabled = !isBusy && accepted && fullName.trim().length >= 2,
        modifier = Modifier.fillMaxWidth().testTag("confirm_consent_button")
    ) {
        Text("Valider et accéder à mon espace")
    }
}
