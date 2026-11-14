package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.PendingRegistrationDraft
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.AuthScreen
import com.example.ui.components.OnboardingAndConsentGate
import com.example.ui.components.RoleBadge
import com.example.ui.components.signOut
import com.example.ui.screens.AboutUsScreen
import com.example.ui.screens.AdministrationScreen
import com.example.ui.screens.AttendanceAndAlertsScreen
import com.example.ui.screens.CahierTexteScreen
import com.example.ui.screens.CommunicationScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.GradesAndBulletinsScreen
import com.example.ui.screens.NotificationsAndAuditScreen
import com.example.ui.theme.EcoleConnecteeTheme
import com.example.ui.viewmodel.AccountGateState
import com.example.ui.viewmodel.AppSection
import com.example.ui.viewmodel.AppViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EcoleConnecteeTheme {
                EcoleConnecteeRoot()
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
private fun EcoleConnecteeRoot() {
    val auth = remember { Firebase.auth }
    val currentUser by remember(auth) { auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var pendingDraft by remember { mutableStateOf<PendingRegistrationDraft?>(null) }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onPrepareRegistration = { draft -> pendingDraft = draft },
            onAuthSuccess = {}
        )
    } else {
        key(user.uid) {
            AuthenticatedAppHost(
                user = user,
                pendingDraft = pendingDraft,
                onClearDraft = { pendingDraft = null }
            )
        }
    }
}

@Composable
private fun AuthenticatedAppHost(
    user: FirebaseUser,
    pendingDraft: PendingRegistrationDraft?,
    onClearDraft: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val vm: AppViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                AppViewModel(
                    db = db,
                    auth = Firebase.auth,
                    currentUserId = user.uid,
                    currentUserEmail = user.email ?: "",
                    currentUserDisplayName = user.displayName ?: ""
                )
            }
        }
    )

    val gateState by vm.accountGateState.collectAsStateWithLifecycle()
    val isBusy by vm.isBusy.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()
    val feedbackMessage by vm.feedbackMessage.collectAsStateWithLifecycle()

    LaunchedEffect(gateState) {
        if (gateState is AccountGateState.Active && pendingDraft != null) {
            onClearDraft()
        }
    }

    val handleSignOut: () -> Unit = {
        onClearDraft()
        signOut(credentialManager = credentialManager, scope = scope)
    }

    when (val state = gateState) {
        is AccountGateState.Active -> {
            EcoleConnecteeMainShell(
                profile = state.profile,
                contact = state.contact,
                viewModel = vm,
                feedbackMessage = feedbackMessage,
                errorMessage = errorMessage,
                onClearFeedback = vm::clearFeedback,
                onSignOut = handleSignOut
            )
        }

        else -> {
            OnboardingAndConsentGate(
                gateState = state,
                userEmail = user.email ?: "",
                userDisplayName = user.displayName ?: "",
                pendingDraft = pendingDraft,
                isBusy = isBusy,
                errorMessage = errorMessage,
                onBootstrapAdmin = vm::bootstrapNationalAdmin,
                onClaimInvitation = vm::claimInvitation,
                onSubmitAccessRequest = vm::submitAccessRequest,
                onSaveConsent = vm::acceptConsentAndSaveChannel,
                onRefreshStatus = vm::refreshOnboardingChecks,
                onSignOut = handleSignOut
            )
        }
    }
}

private fun sectionIcon(section: AppSection): ImageVector = when (section) {
    AppSection.TABLEAU_DE_BORD -> Icons.Filled.Dashboard
    AppSection.CAHIER_DE_TEXTE -> Icons.AutoMirrored.Filled.MenuBook
    AppSection.NOTES_ET_BULLETINS -> Icons.Filled.Assessment
    AppSection.PRESENCES_ET_ALERTES -> Icons.Filled.EventBusy
    AppSection.ADMINISTRATION -> Icons.Filled.School
    AppSection.MESSAGERIE -> Icons.AutoMirrored.Filled.Chat
    AppSection.NOTIFICATIONS -> Icons.Filled.Notifications
    AppSection.A_PROPOS -> Icons.Filled.Info
}

private fun sectionShortLabel(section: AppSection): String = when (section) {
    AppSection.TABLEAU_DE_BORD -> "Accueil"
    AppSection.CAHIER_DE_TEXTE -> "Cahier"
    AppSection.NOTES_ET_BULLETINS -> "Notes"
    AppSection.PRESENCES_ET_ALERTES -> "Absences"
    AppSection.ADMINISTRATION -> "Gestion"
    AppSection.MESSAGERIE -> "Messages"
    AppSection.NOTIFICATIONS -> "Alertes"
    AppSection.A_PROPOS -> "À propos"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcoleConnecteeMainShell(
    profile: UserProfile,
    contact: com.example.data.model.UserPrivateContact?,
    viewModel: AppViewModel,
    feedbackMessage: String?,
    errorMessage: String?,
    onClearFeedback: () -> Unit,
    onSignOut: () -> Unit
) {
    val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
    val targetAdminTabName by viewModel.targetAdminTabName.collectAsStateWithLifecycle()
    val selectedChildId by viewModel.selectedChildId.collectAsStateWithLifecycle()
    val settings by viewModel.platformSettings.collectAsStateWithLifecycle()
    val regions by viewModel.regions.collectAsStateWithLifecycle()
    val schools by viewModel.schools.collectAsStateWithLifecycle()
    val classes by viewModel.classes.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val students by viewModel.students.collectAsStateWithLifecycle()
    val lessons by viewModel.lessons.collectAsStateWithLifecycle()
    val assessments by viewModel.assessments.collectAsStateWithLifecycle()
    val attendance by viewModel.attendance.collectAsStateWithLifecycle()
    val difficulties by viewModel.difficulties.collectAsStateWithLifecycle()
    val reportCards by viewModel.reportCards.collectAsStateWithLifecycle()
    val invitations by viewModel.invitations.collectAsStateWithLifecycle()
    val accessRequests by viewModel.accessRequests.collectAsStateWithLifecycle()
    val managedUsers by viewModel.managedUsers.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val role = profile.roleEnum()
    val unreadNotificationsCount = remember(notifications) { notifications.count { !it.read } }
    val pendingRequestsCount = remember(role, accessRequests) {
        if (role == UserRole.ADMIN_NATIONAL) accessRequests.count { it.status == "pending" } else 0
    }
    val totalAlertBadgeCount = unreadNotificationsCount + pendingRequestsCount
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage, errorMessage) {
        val msg = errorMessage ?: feedbackMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            onClearFeedback()
        }
    }

    BackHandler(enabled = selectedSection != AppSection.TABLEAU_DE_BORD) {
        viewModel.selectSection(AppSection.TABLEAU_DE_BORD)
    }

    val adminLabel = when (role) {
        UserRole.ADMIN_NATIONAL, UserRole.ADMIN_REGIONAL, UserRole.DIRECTEUR -> "Établissements & Classes"
        else -> "Emploi du temps"
    }

    val bottomSections = remember {
        listOf(
            AppSection.TABLEAU_DE_BORD,
            AppSection.CAHIER_DE_TEXTE,
            AppSection.NOTES_ET_BULLETINS,
            AppSection.PRESENCES_ET_ALERTES,
            AppSection.MESSAGERIE
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_ecole_embleme),
                                contentDescription = "Emblème officiel ÉCOLE CONNECTÉE",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ÉCOLE CONNECTÉE",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = profile.fullName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            RoleBadge(role = role)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                AppSection.entries.forEach { section ->
                    val label = if (section == AppSection.ADMINISTRATION) adminLabel else section.labelFr
                    NavigationDrawerItem(
                        label = { Text(label) },
                        selected = selectedSection == section,
                        onClick = {
                            viewModel.selectSection(section)
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(sectionIcon(section), contentDescription = null) },
                        badge = when {
                            section == AppSection.NOTIFICATIONS && totalAlertBadgeCount > 0 -> {
                                { Badge { Text("$totalAlertBadgeCount") } }
                            }
                            section == AppSection.ADMINISTRATION && pendingRequestsCount > 0 -> {
                                { Badge { Text("$pendingRequestsCount") } }
                            }
                            else -> null
                        },
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .testTag("drawer_item_${section.name.lowercase()}")
                    )
                }
                Spacer(Modifier.weight(1f))
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Se déconnecter") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSignOut()
                    },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("drawer_sign_out")
                )
            }
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpandedScreen = maxWidth >= 840.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = if (selectedSection == AppSection.ADMINISTRATION) adminLabel else selectedSection.labelFr,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "ÉCOLE CONNECTÉE • ${role.labelFr}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("open_drawer_button")
                            ) {
                                Icon(Icons.Filled.Menu, contentDescription = "Ouvrir le menu latéral")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.selectSection(AppSection.NOTIFICATIONS) },
                                modifier = Modifier.testTag("topbar_notifications_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (totalAlertBadgeCount > 0) {
                                            Badge { Text("$totalAlertBadgeCount") }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                },
                bottomBar = {
                    if (!isExpandedScreen) {
                        NavigationBar {
                            bottomSections.forEach { section ->
                                NavigationBarItem(
                                    selected = selectedSection == section,
                                    onClick = { viewModel.selectSection(section) },
                                    icon = { Icon(sectionIcon(section), contentDescription = section.labelFr) },
                                    label = {
                                        Text(
                                            text = sectionShortLabel(section),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("bottom_nav_${section.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedScreen) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            AppSection.entries.forEach { section ->
                                NavigationRailItem(
                                    selected = selectedSection == section,
                                    onClick = { viewModel.selectSection(section) },
                                    icon = {
                                        when {
                                            section == AppSection.NOTIFICATIONS && totalAlertBadgeCount > 0 -> {
                                                BadgedBox(badge = { Badge { Text("$totalAlertBadgeCount") } }) {
                                                    Icon(sectionIcon(section), contentDescription = section.labelFr)
                                                }
                                            }
                                            section == AppSection.ADMINISTRATION && pendingRequestsCount > 0 -> {
                                                BadgedBox(badge = { Badge { Text("$pendingRequestsCount") } }) {
                                                    Icon(sectionIcon(section), contentDescription = section.labelFr)
                                                }
                                            }
                                            else -> {
                                                Icon(sectionIcon(section), contentDescription = section.labelFr)
                                            }
                                        }
                                    },
                                    label = { Text(sectionShortLabel(section)) }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        when (selectedSection) {
                            AppSection.TABLEAU_DE_BORD -> DashboardScreen(
                                profile = profile,
                                settings = settings,
                                regions = regions,
                                schools = schools,
                                classes = classes,
                                subjects = subjects,
                                schedules = schedules,
                                students = students,
                                lessons = lessons,
                                assessments = assessments,
                                attendance = attendance,
                                difficulties = difficulties,
                                announcements = announcements,
                                notifications = notifications,
                                pendingAccessRequestsCount = pendingRequestsCount,
                                isBusy = isBusy,
                                selectedChildId = selectedChildId,
                                onSelectChild = viewModel::selectChild,
                                onNavigate = viewModel::selectSection,
                                onOpenPendingApprovals = viewModel::openAdminApprovals,
                                onOpenDirectorSchoolSetup = viewModel::openDirectorSchoolSetup,
                                onImportOfficialSchools = viewModel::importOfficialDjiboutiSchools,
                                onUpdateSettings = viewModel::updatePlatformSettings
                            )

                            AppSection.CAHIER_DE_TEXTE -> CahierTexteScreen(
                                profile = profile,
                                schools = schools,
                                classes = classes,
                                subjects = subjects,
                                students = students,
                                lessons = lessons,
                                selectedChildId = selectedChildId,
                                onSelectChild = viewModel::selectChild,
                                onPublishLesson = viewModel::publishLesson,
                                onUpdateLesson = viewModel::updateLesson
                            )

                            AppSection.NOTES_ET_BULLETINS -> GradesAndBulletinsScreen(
                                profile = profile,
                                schools = schools,
                                students = students,
                                subjects = subjects,
                                assessments = assessments,
                                attendance = attendance,
                                reportCards = reportCards,
                                selectedChildId = selectedChildId,
                                onSelectChild = viewModel::selectChild,
                                onRecordAssessment = viewModel::recordAssessment,
                                onGenerateReportCard = viewModel::generateReportCard,
                                onUpdateAssessment = viewModel::updateAssessment,
                                onUpdateReportCard = viewModel::updateReportCard
                            )

                            AppSection.PRESENCES_ET_ALERTES -> AttendanceAndAlertsScreen(
                                profile = profile,
                                students = students,
                                subjects = subjects,
                                attendance = attendance,
                                difficulties = difficulties,
                                selectedChildId = selectedChildId,
                                onSelectChild = viewModel::selectChild,
                                onRecordAttendance = viewModel::recordAttendance,
                                onReportDifficulty = viewModel::reportDifficulty,
                                onResolveDifficulty = viewModel::resolveDifficulty,
                                onUpdateAttendance = viewModel::updateAttendance,
                                onUpdateDifficulty = viewModel::updateDifficulty
                            )

                            AppSection.ADMINISTRATION -> AdministrationScreen(
                                profile = profile,
                                regions = regions,
                                schools = schools,
                                classes = classes,
                                subjects = subjects,
                                schedules = schedules,
                                students = students,
                                invitations = invitations,
                                accessRequests = accessRequests,
                                managedUsers = managedUsers,
                                isBusy = isBusy,
                                targetTabName = targetAdminTabName,
                                onTargetTabConsumed = viewModel::consumeTargetAdminTab,
                                onCreateRegion = viewModel::createRegion,
                                onCreateSchool = viewModel::createSchool,
                                onSetupDirectorSchool = viewModel::setupDirectorSchool,
                                onImportOfficialSchools = viewModel::importOfficialDjiboutiSchools,
                                onCreateClass = viewModel::createClass,
                                onCreateSubject = viewModel::createSubject,
                                onCreateScheduleEntry = viewModel::createScheduleEntry,
                                onEnrollStudent = viewModel::enrollStudent,
                                onLinkParent = viewModel::linkParentToStudent,
                                onCreateInvitation = viewModel::createInvitation,
                                onSetUserActive = viewModel::setUserActive,
                                onAssignDirector = viewModel::assignDirector,
                                onApproveAccessRequest = viewModel::approveAccessRequest,
                                onRejectAccessRequest = viewModel::rejectAccessRequest,
                                onUpdateRegion = viewModel::updateRegion,
                                onUpdateSchool = viewModel::updateSchool,
                                onUpdateClass = viewModel::updateClass,
                                onUpdateSubject = viewModel::updateSubject,
                                onUpdateScheduleEntry = viewModel::updateScheduleEntry,
                                onUpdateStudent = viewModel::updateStudent,
                                onUpdateInvitation = viewModel::updateRoleInvitation,
                                onUpdateUser = viewModel::updateManagedUser
                            )

                            AppSection.MESSAGERIE -> CommunicationScreen(
                                profile = profile,
                                regions = regions,
                                schools = schools,
                                classes = classes,
                                subjects = subjects,
                                lessons = lessons,
                                students = students,
                                managedUsers = managedUsers,
                                messages = messages,
                                announcements = announcements,
                                onSendMessage = viewModel::sendMessage,
                                onMarkMessageRead = viewModel::markMessageRead,
                                onPublishAnnouncement = viewModel::publishAnnouncement,
                                onUpdateAnnouncement = viewModel::updateAnnouncement
                            )

                            AppSection.NOTIFICATIONS -> NotificationsAndAuditScreen(
                                profile = profile,
                                contact = contact,
                                settings = settings,
                                notifications = notifications,
                                auditLogs = auditLogs,
                                accessRequests = accessRequests,
                                regions = regions,
                                schools = schools,
                                onMarkNotificationRead = viewModel::markNotificationRead,
                                onApproveAccessRequest = viewModel::approveAccessRequest,
                                onRejectAccessRequest = viewModel::rejectAccessRequest,
                                onOpenApprovals = viewModel::openAdminApprovals,
                                onSavePreferences = viewModel::acceptConsentAndSaveChannel
                            )

                            AppSection.A_PROPOS -> AboutUsScreen()
                        }
                    }
                }
            }
        }
    }
}
