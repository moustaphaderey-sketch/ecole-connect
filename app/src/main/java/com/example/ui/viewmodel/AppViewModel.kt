package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AccessRequest
import com.example.data.model.Announcement
import com.example.data.model.AssessmentGrade
import com.example.data.model.AttendanceRecord
import com.example.data.model.AuditLog
import com.example.data.model.DifficultyAlert
import com.example.data.model.LessonEntry
import com.example.data.model.Message
import com.example.data.model.NotificationItem
import com.example.data.model.PlatformSettings
import com.example.data.model.Region
import com.example.data.model.ReportCard
import com.example.data.model.RoleInvitation
import com.example.data.model.ScheduleEntry
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.UserPrivateContact
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.AcademicRepository
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.CommunicationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AccountGateState {
    data object Loading : AccountGateState
    data class NeedsOnboarding(
        val canBootstrapAdmin: Boolean,
        val pendingInvitation: RoleInvitation?,
        val accessRequest: AccessRequest?
    ) : AccountGateState
    data class Deactivated(val profile: UserProfile) : AccountGateState
    data class NeedsConsent(val profile: UserProfile, val contact: UserPrivateContact?) : AccountGateState
    data class Active(val profile: UserProfile, val contact: UserPrivateContact?) : AccountGateState
    data class Error(val message: String) : AccountGateState
}

enum class AppSection(val labelFr: String) {
    TABLEAU_DE_BORD("Tableau de bord"),
    CAHIER_DE_TEXTE("Cahier de texte"),
    NOTES_ET_BULLETINS("Notes & Bulletins"),
    PRESENCES_ET_ALERTES("Présences & Alertes"),
    ADMINISTRATION("Établissements & Classes"),
    MESSAGERIE("Messagerie & Annonces"),
    NOTIFICATIONS("Notifications & Audit"),
    A_PROPOS("À propos de nous")
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val currentUserId: String,
    private val currentUserEmail: String,
    private val currentUserDisplayName: String
) : ViewModel() {

    private val authRepo = AuthRepository(db, auth)
    private val adminRepo = AdminRepository(db, auth)
    private val academicRepo = AcademicRepository(db, auth)
    private val commRepo = CommunicationRepository(db, auth)

    private val _selectedSection = MutableStateFlow(AppSection.TABLEAU_DE_BORD)
    val selectedSection: StateFlow<AppSection> = _selectedSection.asStateFlow()

    private val _targetAdminTabName = MutableStateFlow<String?>(null)
    val targetAdminTabName: StateFlow<String?> = _targetAdminTabName.asStateFlow()

    private val _selectedChildId = MutableStateFlow<String?>(null)
    val selectedChildId: StateFlow<String?> = _selectedChildId.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _onboardingBootstrapAvailable = MutableStateFlow(false)
    private val _invitationRefreshTrigger = MutableStateFlow(0)

    private val observedPendingInvitation: Flow<RoleInvitation?> = _invitationRefreshTrigger.flatMapLatest {
        if (currentUserEmail.isBlank()) {
            flowOf(null)
        } else {
            authRepo.observePendingInvitationForEmail(currentUserEmail)
                .onStart { emit(authRepo.findPendingInvitationForEmail(currentUserEmail)) }
        }
    }

    init {
        refreshOnboardingChecks()
    }

    fun refreshOnboardingChecks() {
        viewModelScope.launch {
            _onboardingBootstrapAvailable.value = authRepo.isBootstrapAvailable()
            _invitationRefreshTrigger.value = _invitationRefreshTrigger.value + 1
        }
    }

    private fun <T> safeListFlow(source: Flow<List<T>>, tag: String): StateFlow<List<T>> =
        source
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                Log.w("AppViewModel", "Erreur flux $tag: ${(fsError ?: error).message}")
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userProfile: StateFlow<UserProfile?> = authRepo.observeUserProfile(currentUserId)
        .catch { error ->
            val fsError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsError == null && error is CancellationException) throw error
            _errorMessage.value = (fsError ?: error).localizedMessage ?: "Erreur chargement profil"
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val privateContact: StateFlow<UserPrivateContact?> = authRepo.observePrivateContact(currentUserId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val accessRequest: StateFlow<AccessRequest?> = authRepo.observeMyAccessRequest(currentUserId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val platformSettings: StateFlow<PlatformSettings?> = userProfile
        .flatMapLatest { profile ->
            if (profile != null && profile.isActive) authRepo.observePlatformSettings()
            else flowOf(null)
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val accountGateState: StateFlow<AccountGateState> = kotlinx.coroutines.flow.combine(
        authRepo.observeUserProfile(currentUserId).map<UserProfile?, Result<UserProfile?>> { Result.success(it) }
            .catch { e -> emit(Result.failure(e)) },
        privateContact,
        accessRequest,
        _onboardingBootstrapAvailable,
        observedPendingInvitation
    ) { profileResult, contact, req, canBootstrap, invitation ->
        if (profileResult.isFailure) {
            val err = profileResult.exceptionOrNull()
            AccountGateState.Error(err?.localizedMessage ?: "Impossible de charger le compte.")
        } else {
            val profile = profileResult.getOrNull()
            when {
                profile == null -> AccountGateState.NeedsOnboarding(
                    canBootstrapAdmin = canBootstrap || currentUserEmail.equals("moustaphaderey@gmail.com", ignoreCase = true),
                    pendingInvitation = invitation,
                    accessRequest = req
                )
                !profile.isActive -> AccountGateState.Deactivated(profile)
                !profile.consentAccepted -> AccountGateState.NeedsConsent(profile, contact)
                else -> AccountGateState.Active(profile, contact)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AccountGateState.Loading)

    private inline fun <T> activeProfileFlow(crossinline block: (UserProfile) -> Flow<List<T>>): Flow<List<T>> =
        userProfile.flatMapLatest { profile ->
            if (profile != null && profile.isActive && profile.consentAccepted) {
                block(profile)
            } else {
                flowOf(emptyList())
            }
        }

    val regions: StateFlow<List<Region>> = safeListFlow(activeProfileFlow { adminRepo.observeRegions(it) }, "regions")
    val schools: StateFlow<List<School>> = safeListFlow(activeProfileFlow { adminRepo.observeSchools(it) }, "schools")
    val classes: StateFlow<List<SchoolClass>> = safeListFlow(activeProfileFlow { adminRepo.observeClasses(it) }, "classes")
    val subjects: StateFlow<List<Subject>> = safeListFlow(activeProfileFlow { adminRepo.observeSubjects(it) }, "subjects")
    val schedules: StateFlow<List<ScheduleEntry>> = safeListFlow(activeProfileFlow { adminRepo.observeSchedules(it) }, "schedules")
    val students: StateFlow<List<Student>> = safeListFlow(activeProfileFlow { adminRepo.observeStudents(it) }, "students")
    val lessons: StateFlow<List<LessonEntry>> = safeListFlow(activeProfileFlow { academicRepo.observeLessons(it) }, "lessons")
    val assessments: StateFlow<List<AssessmentGrade>> = safeListFlow(activeProfileFlow { academicRepo.observeAssessments(it) }, "assessments")
    val attendance: StateFlow<List<AttendanceRecord>> = safeListFlow(activeProfileFlow { academicRepo.observeAttendance(it) }, "attendance")
    val difficulties: StateFlow<List<DifficultyAlert>> = safeListFlow(activeProfileFlow { academicRepo.observeDifficulties(it) }, "difficulties")
    val reportCards: StateFlow<List<ReportCard>> = safeListFlow(activeProfileFlow { academicRepo.observeReportCards(it) }, "reportCards")
    val invitations: StateFlow<List<RoleInvitation>> = safeListFlow(activeProfileFlow { adminRepo.observeInvitations(it) }, "invitations")
    val accessRequests: StateFlow<List<AccessRequest>> = safeListFlow(activeProfileFlow { adminRepo.observeAccessRequests(it) }, "accessRequests")
    val managedUsers: StateFlow<List<UserProfile>> = safeListFlow(activeProfileFlow { adminRepo.observeManagedUsers(it) }, "managedUsers")
    val auditLogs: StateFlow<List<AuditLog>> = safeListFlow(activeProfileFlow { adminRepo.observeAuditLogs(it) }, "auditLogs")
    val announcements: StateFlow<List<Announcement>> = safeListFlow(activeProfileFlow { commRepo.observeAnnouncements(it) }, "announcements")
    val messages: StateFlow<List<Message>> = safeListFlow(
        userProfile.flatMapLatest { p -> if (p != null && p.isActive && p.consentAccepted) commRepo.observeMessages(p.userId) else flowOf(emptyList()) },
        "messages"
    )
    val notifications: StateFlow<List<NotificationItem>> = safeListFlow(
        userProfile.flatMapLatest { p -> if (p != null && p.isActive && p.consentAccepted) commRepo.observeNotifications(p.userId) else flowOf(emptyList()) },
        "notifications"
    )

    fun selectSection(section: AppSection) {
        _selectedSection.value = section
    }

    fun openAdminApprovals() {
        _targetAdminTabName.value = "Comptes & Invitations"
        _selectedSection.value = AppSection.ADMINISTRATION
    }

    fun openDirectorSchoolSetup() {
        _targetAdminTabName.value = "Mon établissement"
        _selectedSection.value = AppSection.ADMINISTRATION
    }

    fun consumeTargetAdminTab() {
        _targetAdminTabName.value = null
    }

    fun selectChild(studentId: String?) {
        _selectedChildId.value = studentId
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
        _errorMessage.value = null
    }

    private fun runAction(successMessage: String, block: suspend () -> Result<Any?>) {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            val res = block()
            _isBusy.value = false
            res.onSuccess {
                _feedbackMessage.value = successMessage
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Une erreur est survenue."
            }
        }
    }

    fun bootstrapNationalAdmin(fullName: String, phone: String, consentAccepted: Boolean) {
        runAction("Espace Administrateur National initialisé.") {
            authRepo.bootstrapNationalAdmin(
                uid = currentUserId,
                fullName = fullName.ifBlank { currentUserDisplayName },
                email = currentUserEmail,
                phone = phone,
                consentAccepted = consentAccepted
            )
        }
    }

    fun claimInvitation(invitation: RoleInvitation, fullName: String, phone: String, consentAccepted: Boolean) {
        runAction("Compte activé avec succès (${UserRole.fromKey(invitation.role).labelFr}).") {
            authRepo.claimRoleInvitation(
                uid = currentUserId,
                invitation = invitation,
                fullName = fullName.ifBlank { currentUserDisplayName },
                email = currentUserEmail,
                phone = phone,
                consentAccepted = consentAccepted
            )
        }
    }

    fun submitAccessRequest(fullName: String, role: UserRole, schoolHint: String, phone: String) {
        runAction("Demande d'habilitation transmise à l'administration.") {
            authRepo.submitAccessRequest(
                uid = currentUserId,
                fullName = fullName.ifBlank { currentUserDisplayName },
                email = currentUserEmail,
                requestedRole = role,
                schoolHint = schoolHint,
                phone = phone
            )
        }
    }

    fun acceptConsentAndSaveChannel(
        fullName: String,
        preferredChannel: String,
        phone: String,
        whatsappNumber: String
    ) {
        runAction("Préférences et consentement enregistrés.") {
            authRepo.updateProfilePreferences(
                uid = currentUserId,
                fullName = fullName,
                consentAccepted = true,
                preferredChannel = preferredChannel,
                phone = phone,
                whatsappNumber = whatsappNumber
            )
        }
    }

    fun updatePlatformSettings(academicYear: String, gradingScale: Double, smsEnabled: Boolean, whatsappEnabled: Boolean) {
        val actor = userProfile.value ?: return
        runAction("Paramètres nationaux mis à jour.") {
            authRepo.updatePlatformSettings(actor, academicYear, gradingScale, smsEnabled, whatsappEnabled)
        }
    }

    fun createRegion(name: String, code: String) {
        val actor = userProfile.value ?: return
        runAction("Région « $name » enregistrée.") {
            adminRepo.createRegion(actor, name, code)
        }
    }

    fun createSchool(region: Region, name: String, code: String, schoolType: String, address: String) {
        val actor = userProfile.value ?: return
        runAction("Établissement « $name » enregistré.") {
            adminRepo.createSchool(actor, region, name, code, schoolType, address)
        }
    }

    fun setupDirectorSchool(
        region: Region?,
        name: String,
        code: String,
        schoolType: String,
        address: String,
        officialSchoolId: String = ""
    ) {
        val actor = userProfile.value ?: return
        runAction("Établissement « $name » configuré pour votre direction.") {
            adminRepo.createOrClaimSchoolForDirector(
                actor = actor,
                region = region,
                name = name,
                code = code,
                schoolType = schoolType,
                address = address,
                officialSchoolId = officialSchoolId
            )
        }
    }

    fun importOfficialDjiboutiSchools() {
        val actor = userProfile.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            val res = adminRepo.importOfficialDjiboutiSchools(actor, regions.value, schools.value)
            _isBusy.value = false
            res.onSuccess { count ->
                _feedbackMessage.value = if (count > 0) {
                    "$count établissement(s) officiel(s) de Djibouti-Ville importé(s)."
                } else {
                    "Tous les établissements officiels de Djibouti-Ville sont déjà enregistrés."
                }
            }.onFailure { err ->
                _errorMessage.value = err.localizedMessage ?: "Erreur lors de l'import des établissements."
            }
        }
    }

    fun createClass(school: School, name: String, level: String, academicYear: String) {
        val actor = userProfile.value ?: return
        runAction("Classe « $name » créée.") {
            adminRepo.createClass(actor, school, name, level, academicYear)
        }
    }

    fun createSubject(
        school: School,
        schoolClass: SchoolClass,
        name: String,
        code: String,
        coefficient: Double,
        teacherUid: String,
        teacherName: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Matière « $name » ajoutée.") {
            adminRepo.createSubject(actor, school, schoolClass, name, code, coefficient, teacherUid, teacherName)
        }
    }

    fun createScheduleEntry(
        school: School,
        schoolClass: SchoolClass,
        subject: Subject,
        dayOfWeek: String,
        startTime: String,
        endTime: String,
        room: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Créneau d'emploi du temps ajouté.") {
            adminRepo.createScheduleEntry(actor, school, schoolClass, subject, dayOfWeek, startTime, endTime, room)
        }
    }

    fun enrollStudent(
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
    ) {
        val actor = userProfile.value ?: return
        runAction("Élève $firstName $lastName inscrit.") {
            adminRepo.enrollStudent(
                actor, school, schoolClass, firstName, lastName, matricule,
                dateOfBirth, gender, parentUids, studentUid, gradesVisibleToStudent
            )
        }
    }

    fun linkParentToStudent(student: Student, parentUid: String) {
        val actor = userProfile.value ?: return
        runAction("Parent associé à ${student.fullName}.") {
            adminRepo.linkParentToStudent(actor, student, parentUid)
        }
    }

    fun createInvitation(
        email: String,
        fullName: String,
        role: UserRole,
        regionId: String,
        school: School?,
        phone: String,
        studentIds: List<String> = emptyList(),
        classIds: List<String> = emptyList()
    ) {
        val actor = userProfile.value ?: return
        runAction("Compte pré-enregistré pour $email.") {
            adminRepo.createRoleInvitation(actor, email, fullName, role, regionId, school, phone, studentIds, classIds)
        }
    }

    fun setUserActive(target: UserProfile, isActive: Boolean) {
        val actor = userProfile.value ?: return
        runAction(if (isActive) "Compte réactivé." else "Compte désactivé.") {
            adminRepo.setUserActiveStatus(actor, target, isActive)
        }
    }

    fun assignDirector(school: School, director: UserProfile) {
        val actor = userProfile.value ?: return
        runAction("Directeur affecté à ${school.name}.") {
            adminRepo.assignSchoolDirector(actor, school, director)
        }
    }

    fun approveAccessRequest(request: AccessRequest, assignedRole: UserRole, regionId: String, school: School?) {
        val actor = userProfile.value ?: return
        runAction("Inscription approuvée pour ${request.fullName} (${assignedRole.labelFr}).") {
            adminRepo.approveAccessRequest(actor, request, assignedRole, regionId, school)
        }
    }

    fun rejectAccessRequest(request: AccessRequest) {
        val actor = userProfile.value ?: return
        runAction("Demande d'inscription refusée pour ${request.fullName}.") {
            adminRepo.rejectAccessRequest(actor, request)
        }
    }

    fun publishLesson(
        school: School?,
        schoolClass: SchoolClass,
        subject: Subject,
        lessonDate: String,
        chapter: String,
        title: String,
        summary: String,
        skillsWorked: String,
        classExercises: String,
        homework: String,
        homeworkDueDate: String,
        attachmentLabel: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Leçon enregistrée dans le cahier de texte numérique.") {
            academicRepo.publishLesson(
                actor, school, schoolClass, subject, lessonDate, chapter, title,
                summary, skillsWorked, classExercises, homework, homeworkDueDate, attachmentLabel
            )
        }
    }

    fun recordAssessment(
        student: Student,
        subject: Subject,
        title: String,
        assessmentType: String,
        score: Double,
        maxScore: Double,
        coefficient: Double,
        period: String,
        date: String,
        comment: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Note enregistrée pour ${student.fullName}.") {
            academicRepo.recordAssessment(
                actor, student, subject, title, assessmentType, score, maxScore, coefficient, period, date, comment
            )
        }
    }

    fun recordAttendance(
        student: Student,
        date: String,
        status: String,
        minutesLate: Int,
        justified: Boolean,
        reason: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Présence / absence enregistrée pour ${student.fullName}.") {
            academicRepo.recordAttendance(actor, student, date, status, minutesLate, justified, reason)
        }
    }

    fun reportDifficulty(
        student: Student,
        subject: Subject?,
        category: String,
        severity: String,
        observation: String,
        recommendation: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Signalement transmis aux parents et à la direction.") {
            academicRepo.reportDifficulty(actor, student, subject, category, severity, observation, recommendation)
        }
    }

    fun resolveDifficulty(difficultyId: String, resolved: Boolean) {
        runAction(if (resolved) "Difficulté marquée comme résolue." else "Difficulté réouverte.") {
            academicRepo.resolveDifficulty(difficultyId, resolved)
        }
    }

    fun generateReportCard(
        school: School?,
        student: Student,
        period: String,
        overallAverage: Double,
        classAverage: Double,
        rankLabel: String,
        absencesCount: Int,
        tardiesCount: Int,
        councilDecision: String,
        directorObservation: String,
        published: Boolean
    ) {
        val actor = userProfile.value ?: return
        runAction("Bulletin scolaire généré pour ${student.fullName}.") {
            academicRepo.generateReportCard(
                actor, school, student, period, overallAverage, classAverage,
                rankLabel, absencesCount, tardiesCount, councilDecision, directorObservation, published
            )
        }
    }

    fun sendMessage(
        schoolId: String,
        recipientUid: String,
        recipientName: String,
        studentId: String,
        studentName: String,
        subject: String,
        body: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Message envoyé à $recipientName.") {
            commRepo.sendMessage(actor, schoolId, recipientUid, recipientName, studentId, studentName, subject, body)
        }
    }

    fun markMessageRead(messageId: String) {
        viewModelScope.launch { commRepo.markMessageRead(messageId) }
    }

    fun publishAnnouncement(
        scope: String,
        regionId: String,
        schoolId: String,
        title: String,
        content: String,
        priority: String,
        authorizedUids: List<String>
    ) {
        val actor = userProfile.value ?: return
        runAction("Annonce publiée.") {
            commRepo.publishAnnouncement(actor, scope, regionId, schoolId, title, content, priority, authorizedUids)
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch { commRepo.markNotificationRead(notificationId) }
    }

    fun updateRegion(region: Region, name: String, code: String) {
        val actor = userProfile.value ?: return
        runAction("Région « $name » mise à jour.") {
            adminRepo.updateRegion(actor, region, name, code)
        }
    }

    fun updateSchool(
        school: School,
        region: Region?,
        name: String,
        code: String,
        schoolType: String,
        address: String,
        sector: String,
        description: String,
        isActive: Boolean
    ) {
        val actor = userProfile.value ?: return
        runAction("Établissement « $name » mis à jour.") {
            adminRepo.updateSchool(actor, school, region, name, code, schoolType, address, sector, description, isActive)
        }
    }

    fun updateClass(schoolClass: SchoolClass, name: String, level: String, academicYear: String) {
        val actor = userProfile.value ?: return
        runAction("Classe « $name » mise à jour.") {
            adminRepo.updateClass(actor, schoolClass, name, level, academicYear)
        }
    }

    fun updateSubject(
        subject: Subject,
        name: String,
        code: String,
        coefficient: Double,
        teacherUid: String,
        teacherName: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Matière « $name » mise à jour.") {
            adminRepo.updateSubject(actor, subject, name, code, coefficient, teacherUid, teacherName)
        }
    }

    fun updateScheduleEntry(
        entry: ScheduleEntry,
        subject: Subject?,
        dayOfWeek: String,
        startTime: String,
        endTime: String,
        room: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Créneau d'emploi du temps mis à jour.") {
            adminRepo.updateScheduleEntry(actor, entry, subject, dayOfWeek, startTime, endTime, room)
        }
    }

    fun updateStudent(
        student: Student,
        targetClass: SchoolClass?,
        firstName: String,
        lastName: String,
        matricule: String,
        dateOfBirth: String,
        gender: String,
        gradesVisibleToStudent: Boolean,
        isActive: Boolean
    ) {
        val actor = userProfile.value ?: return
        runAction("Fiche élève de $firstName $lastName mise à jour.") {
            adminRepo.updateStudent(
                actor, student, targetClass, firstName, lastName, matricule,
                dateOfBirth, gender, gradesVisibleToStudent, isActive
            )
        }
    }

    fun updateRoleInvitation(invitation: RoleInvitation, fullName: String, phone: String, status: String) {
        val actor = userProfile.value ?: return
        runAction("Invitation mise à jour.") {
            adminRepo.updateRoleInvitation(actor, invitation, fullName, phone, status)
        }
    }

    fun updateManagedUser(
        targetUser: UserProfile,
        fullName: String,
        role: UserRole?,
        school: School?,
        isActive: Boolean
    ) {
        val actor = userProfile.value ?: return
        runAction("Compte de $fullName mis à jour.") {
            adminRepo.updateManagedUser(actor, targetUser, fullName, role, school, isActive)
        }
    }

    fun updateLesson(
        lesson: LessonEntry,
        lessonDate: String,
        chapter: String,
        title: String,
        summary: String,
        skillsWorked: String,
        classExercises: String,
        homework: String,
        homeworkDueDate: String,
        attachmentLabel: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Leçon « $title » mise à jour.") {
            academicRepo.updateLesson(
                actor, lesson, lessonDate, chapter, title, summary,
                skillsWorked, classExercises, homework, homeworkDueDate, attachmentLabel
            )
        }
    }

    fun updateAssessment(
        grade: AssessmentGrade,
        title: String,
        assessmentType: String,
        score: Double,
        maxScore: Double,
        coefficient: Double,
        period: String,
        date: String,
        comment: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Note « $title » mise à jour.") {
            academicRepo.updateAssessment(actor, grade, title, assessmentType, score, maxScore, coefficient, period, date, comment)
        }
    }

    fun updateAttendance(
        record: AttendanceRecord,
        date: String,
        status: String,
        minutesLate: Int,
        justified: Boolean,
        reason: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Enregistrement de présence mis à jour.") {
            academicRepo.updateAttendance(actor, record, date, status, minutesLate, justified, reason)
        }
    }

    fun updateDifficulty(
        alert: DifficultyAlert,
        category: String,
        severity: String,
        observation: String,
        recommendation: String,
        resolved: Boolean
    ) {
        val actor = userProfile.value ?: return
        runAction("Signalement pédagogique mis à jour.") {
            academicRepo.updateDifficulty(actor, alert, category, severity, observation, recommendation, resolved)
        }
    }

    fun updateReportCard(
        reportCard: ReportCard,
        overallAverage: Double,
        classAverage: Double,
        rankLabel: String,
        absencesCount: Int,
        tardiesCount: Int,
        councilDecision: String,
        directorObservation: String,
        published: Boolean
    ) {
        val actor = userProfile.value ?: return
        runAction("Bulletin de ${reportCard.studentName} mis à jour.") {
            academicRepo.updateReportCard(
                actor, reportCard, overallAverage, classAverage, rankLabel,
                absencesCount, tardiesCount, councilDecision, directorObservation, published
            )
        }
    }

    fun updateAnnouncement(
        announcement: Announcement,
        title: String,
        content: String,
        priority: String
    ) {
        val actor = userProfile.value ?: return
        runAction("Annonce « $title » mise à jour.") {
            commRepo.updateAnnouncement(actor, announcement, title, content, priority)
        }
    }
}
