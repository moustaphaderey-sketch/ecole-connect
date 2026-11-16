package com.example.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthManager
import com.example.auth.PendingRegistrationDraft
import com.example.auth.RegistrationCategory
import com.example.data.model.UserRole
import com.example.ui.components.DropdownSelector
import com.example.ui.components.OfficialSchoolPickerDialog
import com.example.ui.screens.AboutUsDialog

private sealed interface AuthRoute {
    data object Login : AuthRoute
    data object RoleChooser : AuthRoute
    data class RoleSignUp(val category: RegistrationCategory) : AuthRoute
}

@Composable
fun AuthEntryHost(
    onPrepareRegistration: (PendingRegistrationDraft?) -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    var route by remember { mutableStateOf<AuthRoute>(AuthRoute.Login) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        GoogleAuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    BackHandler(enabled = route !is AuthRoute.Login) {
        errorMessage = null
        route = when (route) {
            is AuthRoute.RoleSignUp -> AuthRoute.RoleChooser
            AuthRoute.RoleChooser -> AuthRoute.Login
            AuthRoute.Login -> AuthRoute.Login
        }
    }

    val launchGoogleSignIn: (PendingRegistrationDraft?) -> Unit = { draft ->
        onPrepareRegistration(draft)
        isLoading = true
        errorMessage = null
        GoogleAuthManager.signInWithGoogleCredentials(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
            },
            onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
            },
            scope = scope,
            onAuthCancelled = {
                isLoading = false
            }
        )
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
            ) {
                AnimatedContent(
                    targetState = route,
                    label = "auth_route_transition"
                ) { currentRoute ->
                    when (currentRoute) {
                        AuthRoute.Login -> LoginScreen(
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onSignInWithGoogle = { launchGoogleSignIn(null) },
                            onGoToRegister = {
                                errorMessage = null
                                route = AuthRoute.RoleChooser
                            },
                            onQuickRegisterRole = { category ->
                                errorMessage = null
                                route = AuthRoute.RoleSignUp(category)
                            }
                        )

                        AuthRoute.RoleChooser -> RoleSelectionScreen(
                            onBackToLogin = {
                                errorMessage = null
                                route = AuthRoute.Login
                            },
                            onSelectCategory = { category ->
                                errorMessage = null
                                route = AuthRoute.RoleSignUp(category)
                            }
                        )

                        is AuthRoute.RoleSignUp -> RoleSignUpScreen(
                            category = currentRoute.category,
                            isLoading = isLoading,
                            errorMessage = errorMessage,
                            onBack = {
                                errorMessage = null
                                route = AuthRoute.RoleChooser
                            },
                            onGoToLogin = {
                                errorMessage = null
                                route = AuthRoute.Login
                            },
                            onSubmitGoogleRegistration = { draft ->
                                launchGoogleSignIn(draft)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onSignInWithGoogle: () -> Unit,
    onGoToRegister: () -> Unit,
    onQuickRegisterRole: (RegistrationCategory) -> Unit
) {
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_school_hero),
            contentDescription = "Illustration ÉCOLE CONNECTÉE",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(MaterialTheme.shapes.medium)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
                shadowElevation = 2.dp,
                modifier = Modifier.size(56.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_ecole_embleme),
                    contentDescription = "Emblème ÉCOLE CONNECTÉE",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
            Column {
                Text(
                    text = "ÉCOLE CONNECTÉE",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Plateforme Numérique Nationale — Djibouti",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Text(
            text = "Connectez-vous avec votre compte Google pour accéder à votre espace Admin, Enseignant ou Parent.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Authentification sécurisée Firebase + Google Credentials et cloisonnement strict des données des mineurs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        AuthErrorBanner(errorMessage)

        Button(
            onClick = onSignInWithGoogle,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("google_sign_in_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Se connecter avec Google")
            }
        }

        OutlinedButton(
            onClick = onGoToRegister,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("go_to_register_button")
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Créer un compte (Inscription par rôle)")
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Accès rapide à l'inscription :",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RegistrationCategory.entries.forEach { category ->
                    FilterChip(
                        selected = false,
                        onClick = { onQuickRegisterRole(category) },
                        label = { Text(category.shortLabel()) },
                        leadingIcon = {
                            Icon(
                                imageVector = category.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_register_${category.name.lowercase()}")
                    )
                }
            }
        }

        TextButton(
            onClick = { showAboutDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("login_about_us_button")
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("À propos de nous • ÉCOLE CONNECTÉE")
        }
    }

    if (showAboutDialog) {
        AboutUsDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
private fun RoleSelectionScreen(
    onBackToLogin: () -> Unit,
    onSelectCategory: (RegistrationCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onBackToLogin,
                modifier = Modifier.testTag("role_chooser_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour à la connexion")
            }
            Column {
                Text(
                    text = "Inscription par profil",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Sélectionnez votre rôle pour créer votre compte",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        RegistrationCategory.entries.forEach { category ->
            val accentColor = category.accentColor()
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectCategory(category) }
                    .testTag("select_role_${category.name.lowercase()}"),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.14f),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = category.icon(),
                                contentDescription = category.titleFr,
                                tint = accentColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = category.titleFr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = category.subtitleFr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = accentColor
                    )
                }
            }
        }

        TextButton(
            onClick = onBackToLogin,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Vous avez déjà un compte ? Se connecter")
        }
    }
}

@Composable
private fun RoleSignUpScreen(
    category: RegistrationCategory,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onGoToLogin: () -> Unit,
    onSubmitGoogleRegistration: (PendingRegistrationDraft) -> Unit
) {
    var selectedRole by remember(category) { mutableStateOf(category.defaultRole) }
    var lastName by remember(category) { mutableStateOf("") }
    var firstName by remember(category) { mutableStateOf("") }
    var emailHint by remember(category) { mutableStateOf("") }
    var phone by remember(category) { mutableStateOf("") }
    var selectedSchoolId by remember(category) { mutableStateOf("") }
    var selectedSchoolName by remember(category) { mutableStateOf("") }
    var schoolOrRegionHint by remember(category) { mutableStateOf("") }
    var extraDetail by remember(category) { mutableStateOf("") }
    var consentAccepted by remember(category) { mutableStateOf(false) }
    var showSchoolPicker by remember { mutableStateOf(false) }

    val accent = category.accentColor()
    val requiresAuthorizedSchool = selectedRole == UserRole.DIRECTEUR ||
        selectedRole == UserRole.ENSEIGNANT ||
        selectedRole == UserRole.PARENT
    val schoolReady = if (requiresAuthorizedSchool) selectedSchoolId.isNotBlank() else schoolOrRegionHint.trim().length >= 2
    val isFormValid = lastName.trim().length >= 2 &&
        firstName.trim().length >= 2 &&
        phone.trim().length >= 4 &&
        schoolReady &&
        consentAccepted

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("signup_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Changer de rôle")
            }
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = category.icon(),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inscription ${category.shortLabel()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = category.subtitleFr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            color = accent.copy(alpha = 0.08f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                category.highlights().forEach { point ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = point,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (category == RegistrationCategory.ADMIN) {
            DropdownSelector(
                label = "Fonction demandée *",
                options = listOf(
                    UserRole.DIRECTEUR,
                    UserRole.ADMIN_REGIONAL,
                    UserRole.ADMIN_NATIONAL
                ),
                selectedOption = selectedRole,
                optionLabel = { it.labelFr },
                onOptionSelected = { selectedRole = it }
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Fonction : ${selectedRole.labelFr}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it.take(60) },
                label = { Text("Nom *") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("signup_lastname_input")
            )
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it.take(60) },
                label = { Text("Prénom *") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("signup_firstname_input")
            )
        }

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.take(40) },
            label = {
                Text(
                    when (category) {
                        RegistrationCategory.ADMIN -> "Téléphone professionnel *"
                        RegistrationCategory.ENSEIGNANT -> "Téléphone *"
                        RegistrationCategory.PARENT -> "Téléphone / WhatsApp *"
                    }
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("signup_phone_input")
        )

        OutlinedTextField(
            value = emailHint,
            onValueChange = { emailHint = it.take(120) },
            label = { Text("Adresse e-mail Google") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("signup_email_input")
        )

        if (requiresAuthorizedSchool) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(
                    1.dp,
                    if (selectedSchoolId.isNotBlank()) accent else MaterialTheme.colorScheme.outline
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (selectedSchoolName.isNotBlank()) {
                            selectedSchoolName
                        } else {
                            "Aucun établissement scolaire sélectionné *"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (selectedSchoolId.isNotBlank()) {
                            "Structure autorisée sélectionnée ($schoolOrRegionHint)"
                        } else {
                            "Sélection obligatoire depuis le répertoire officiel des écoles, collèges et lycées."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showSchoolPicker = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("signup_open_school_picker_button")
                    ) {
                        Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (selectedSchoolId.isBlank()) "Choisir votre établissement scolaire *"
                            else "Changer d'établissement"
                        )
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = schoolOrRegionHint,
                onValueChange = { schoolOrRegionHint = it.take(160) },
                label = { Text("Direction régionale ou service national *") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("signup_school_input")
            )
        }

        OutlinedTextField(
            value = extraDetail,
            onValueChange = { extraDetail = it.take(250) },
            label = {
                Text(
                    when (category) {
                        RegistrationCategory.ADMIN -> "Précisions / service (optionnel)"
                        RegistrationCategory.ENSEIGNANT -> "Classe(s) et matière(s) enseignées"
                        RegistrationCategory.PARENT -> "Nom(s) et classe(s) de votre/vos enfant(s)"
                    }
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("signup_extra_input")
        )

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when (selectedRole) {
                    UserRole.PARENT, UserRole.ENSEIGNANT ->
                        "Circuit de validation : compte créé en statut « en attente » (aucune donnée scolaire accessible), puis vérifié par la Direction de votre établissement."
                    else ->
                        "Circuit de validation : compte créé en statut « en attente », puis vérifié par l'Administration nationale."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(10.dp)
            )
        }

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
                    checked = consentAccepted,
                    onCheckedChange = { consentAccepted = it },
                    modifier = Modifier.testTag("signup_consent_checkbox")
                )
                Text(
                    text = "J'accepte la politique de confidentialité et les règles strictes de protection des données scolaires et des mineurs d'ÉCOLE CONNECTÉE.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        AuthErrorBanner(errorMessage)

        Button(
            onClick = {
                onSubmitGoogleRegistration(
                    PendingRegistrationDraft(
                        category = category,
                        role = selectedRole,
                        lastName = lastName.trim(),
                        firstName = firstName.trim(),
                        emailHint = emailHint.trim(),
                        phone = phone.trim(),
                        schoolId = selectedSchoolId,
                        schoolName = selectedSchoolName,
                        schoolOrRegionHint = schoolOrRegionHint.trim(),
                        extraDetail = extraDetail.trim(),
                        consentAccepted = consentAccepted
                    )
                )
            },
            enabled = !isLoading && isFormValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("role_signup_google_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("S'inscrire avec Google (${category.shortLabel()})")
            }
        }

        TextButton(
            onClick = onGoToLogin,
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Déjà inscrit ? Se connecter avec Google")
        }
    }

    if (showSchoolPicker) {
        OfficialSchoolPickerDialog(
            onDismiss = { showSchoolPicker = false },
            onSelectSchool = { entry ->
                selectedSchoolId = entry.id
                selectedSchoolName = entry.name
                schoolOrRegionHint = "${entry.name} (${entry.levelLabelFr})"
                showSchoolPicker = false
            }
        )
    }
}

@Composable
private fun AuthErrorBanner(errorMessage: String?) {
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
}

private fun RegistrationCategory.shortLabel(): String = when (this) {
    RegistrationCategory.ADMIN -> "Admin"
    RegistrationCategory.ENSEIGNANT -> "Enseignant"
    RegistrationCategory.PARENT -> "Parent"
}

private fun RegistrationCategory.icon(): ImageVector = when (this) {
    RegistrationCategory.ADMIN -> Icons.Filled.AdminPanelSettings
    RegistrationCategory.ENSEIGNANT -> Icons.Filled.School
    RegistrationCategory.PARENT -> Icons.Filled.FamilyRestroom
}

@Composable
private fun RegistrationCategory.accentColor(): Color = when (this) {
    RegistrationCategory.ADMIN -> MaterialTheme.colorScheme.primary
    RegistrationCategory.ENSEIGNANT -> MaterialTheme.colorScheme.secondary
    RegistrationCategory.PARENT -> MaterialTheme.colorScheme.tertiary
}

private fun RegistrationCategory.highlights(): List<String> = when (this) {
    RegistrationCategory.ADMIN -> listOf(
        "Pilotage des régions, établissements, classes et emplois du temps",
        "Habilitation des directeurs, enseignants et parents",
        "Statistiques, bulletins scolaires et journal d'audit"
    )
    RegistrationCategory.ENSEIGNANT -> listOf(
        "Publication quotidienne du cahier de texte et des devoirs",
        "Saisie des notes d'évaluations, présences et retards",
        "Signalement des difficultés et messagerie avec les parents"
    )
    RegistrationCategory.PARENT -> listOf(
        "Suivi en temps réel des leçons apprises et devoirs à faire",
        "Consultation des notes, moyennes et bulletins PDF",
        "Alertes d'absences, difficultés signalées et messagerie directe"
    )
}
