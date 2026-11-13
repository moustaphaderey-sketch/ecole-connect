package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AccessRequest
import com.example.data.model.Region
import com.example.data.model.RoleInvitation
import com.example.data.model.ScheduleEntry
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.DropdownSelector
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.TabChipsRow

@Composable
fun AdministrationScreen(
    profile: UserProfile,
    regions: List<Region>,
    schools: List<School>,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    schedules: List<ScheduleEntry>,
    students: List<Student>,
    invitations: List<RoleInvitation>,
    accessRequests: List<AccessRequest> = emptyList(),
    managedUsers: List<UserProfile>,
    onCreateRegion: (String, String) -> Unit,
    onCreateSchool: (Region, String, String, String, String) -> Unit,
    onCreateClass: (School, String, String, String) -> Unit,
    onCreateSubject: (School, SchoolClass, String, String, Double, String, String) -> Unit,
    onCreateScheduleEntry: (School, SchoolClass, Subject, String, String, String, String) -> Unit,
    onEnrollStudent: (School, SchoolClass, String, String, String, String, String, List<String>, String, Boolean) -> Unit,
    onLinkParent: (Student, String) -> Unit,
    onCreateInvitation: (String, String, UserRole, String, School?, String, List<String>, List<String>) -> Unit,
    onSetUserActive: (UserProfile, Boolean) -> Unit,
    onAssignDirector: (School, UserProfile) -> Unit,
    onApproveAccessRequest: (AccessRequest, UserRole, String, School?) -> Unit = { _, _, _, _ -> },
    onRejectAccessRequest: (AccessRequest) -> Unit = {}
) {
    val role = profile.roleEnum()
    val tabs = remember(role) {
        when (role) {
            UserRole.ADMIN_NATIONAL -> listOf("Régions", "Établissements", "Classes & Matières", "Élèves", "Comptes & Invitations")
            UserRole.ADMIN_REGIONAL -> listOf("Établissements", "Classes & Emplois du temps", "Élèves")
            UserRole.DIRECTEUR -> listOf("Classes", "Matières", "Emploi du temps", "Élèves & Parents", "Comptes & Invitations")
            else -> listOf("Emploi du temps", "Matières & Classes")
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val safeIndex = selectedTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val activeTabName = tabs[safeIndex]

    var showRegionDialog by remember { mutableStateOf(false) }
    var showSchoolDialog by remember { mutableStateOf(false) }
    var showClassDialog by remember { mutableStateOf(false) }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showEnrollStudentDialog by remember { mutableStateOf(false) }
    var showInvitationDialog by remember { mutableStateOf(false) }
    var linkParentTarget by remember { mutableStateOf<Student?>(null) }
    var assignDirectorSchool by remember { mutableStateOf<School?>(null) }
    var approveRequestTarget by remember { mutableStateOf<AccessRequest?>(null) }

    val canAddInTab = when (activeTabName) {
        "Régions" -> role == UserRole.ADMIN_NATIONAL
        "Établissements" -> role == UserRole.ADMIN_NATIONAL
        "Classes", "Classes & Matières" -> role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
        "Matières" -> role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
        "Emploi du temps" -> role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
        "Élèves", "Élèves & Parents" -> role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
        "Comptes & Invitations" -> role == UserRole.ADMIN_NATIONAL || role == UserRole.DIRECTEUR
        else -> false
    }

    Scaffold(
        floatingActionButton = {
            if (canAddInTab) {
                FloatingActionButton(
                    onClick = {
                        when (activeTabName) {
                            "Régions" -> showRegionDialog = true
                            "Établissements" -> showSchoolDialog = true
                            "Classes", "Classes & Matières" -> showClassDialog = true
                            "Matières" -> showSubjectDialog = true
                            "Emploi du temps" -> showScheduleDialog = true
                            "Élèves", "Élèves & Parents" -> showEnrollStudentDialog = true
                            "Comptes & Invitations" -> showInvitationDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("admin_add_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Ajouter ($activeTabName)")
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                SectionHeader(
                    title = if (role == UserRole.PARENT || role == UserRole.ELEVE || role == UserRole.ENSEIGNANT) {
                        "Emploi du temps & Organisation"
                    } else {
                        "Gestion Établissements, Classes & Comptes"
                    },
                    subtitle = "Structure scolaire nationale, inscriptions et habilitations",
                    icon = Icons.Filled.School
                )
            }

            item {
                TabChipsRow(
                    tabs = tabs,
                    selectedIndex = safeIndex,
                    onSelectTab = { selectedTabIndex = it }
                )
            }

            when (activeTabName) {
                "Régions" -> {
                    if (regions.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Map,
                                title = "Aucune région enregistrée.",
                                subtitle = "Ajoutez une région administrative de Djibouti pour structurer le déploiement national."
                            )
                        }
                    } else {
                        items(regions, key = { it.id }) { region ->
                            ElevatedCard {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${region.name} (${region.code})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (region.regionalAdminName.isNotBlank()) "Admin régional : ${region.regionalAdminName}" else "Aucun administrateur régional affecté",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                "Établissements" -> {
                    if (schools.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.School,
                                title = "Aucun établissement enregistré.",
                                subtitle = "Créez d'abord une région puis ajoutez les écoles primaires, collèges et lycées."
                            )
                        }
                    } else {
                        items(schools, key = { it.id }) { school ->
                            ElevatedCard {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(school.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                            Text(
                                                text = school.schoolType.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Text("Code : ${school.code} • Région : ${school.regionName}", style = MaterialTheme.typography.bodySmall)
                                    if (school.address.isNotBlank()) {
                                        Text("Adresse : ${school.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = if (school.directorName.isNotBlank()) "Directeur : ${school.directorName}" else "Directeur non affecté",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (role == UserRole.ADMIN_NATIONAL) {
                                        OutlinedButton(
                                            onClick = { assignDirectorSchool = school },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.SupervisorAccount, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Text("  Affecter un directeur")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Classes", "Classes & Matières", "Matières & Classes" -> {
                    if (role == UserRole.ADMIN_NATIONAL) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { showClassDialog = true }, modifier = Modifier.weight(1f)) {
                                    Text("+ Classe")
                                }
                                OutlinedButton(onClick = { showSubjectDialog = true }, modifier = Modifier.weight(1f)) {
                                    Text("+ Matière")
                                }
                            }
                        }
                    }
                    if (classes.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Class,
                                title = "Aucune classe enregistrée.",
                                subtitle = "Les classes (ex: CP1, CM2, 6ème A) apparaîtront ici dès leur création."
                            )
                        }
                    } else {
                        items(classes, key = { it.id }) { sc ->
                            val classSubjects = subjects.filter { it.classId == sc.id }
                            ElevatedCard {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${sc.name} • ${sc.level}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${sc.schoolName} • Année ${sc.academicYear}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${sc.studentUids.size} compte(s) élève • ${sc.parentUids.size} parent(s) • ${classSubjects.size} matière(s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    if (activeTabName != "Classes" && subjects.isNotEmpty()) {
                        item {
                            Text("Matières configurées", style = MaterialTheme.typography.titleMedium)
                        }
                        items(subjects, key = { it.id }) { subj ->
                            SubjectRowCard(subj)
                        }
                    }
                }

                "Matières" -> {
                    if (subjects.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                title = "Aucune matière enregistrée.",
                                subtitle = "Ajoutez les matières par classe avec leur coefficient et l'enseignant responsable."
                            )
                        }
                    } else {
                        items(subjects, key = { it.id }) { subj ->
                            SubjectRowCard(subj)
                        }
                    }
                }

                "Emploi du temps", "Classes & Emplois du temps" -> {
                    if (schedules.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Schedule,
                                title = "Aucun créneau d'emploi du temps.",
                                subtitle = "L'emploi du temps hebdomadaire par classe apparaîtra ici."
                            )
                        }
                    } else {
                        items(schedules, key = { it.id }) { entry ->
                            ElevatedCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${entry.dayOfWeek.replaceFirstChar { it.uppercase() }} • ${entry.startTime} - ${entry.endTime}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                        Text("${entry.subjectName} (${entry.className})", style = MaterialTheme.typography.titleMedium)
                                        val details = listOfNotNull(
                                            entry.teacherName.takeIf { it.isNotBlank() },
                                            entry.room.takeIf { it.isNotBlank() }?.let { "Salle $it" }
                                        ).joinToString(" • ")
                                        if (details.isNotBlank()) {
                                            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Élèves", "Élèves & Parents" -> {
                    if (students.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Groups,
                                title = "Aucun élève enregistré.",
                                subtitle = "Inscrivez les élèves dans leurs classes et associez leurs parents pour activer le suivi en temps réel."
                            )
                        }
                    } else {
                        items(students, key = { it.id }) { student ->
                            ElevatedCard {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                                            Text(student.matricule, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                    Text("${student.schoolName} • ${student.className}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Parents liés : ${student.parentUids.size} • Notes visibles élève : ${if (student.gradesVisibleToStudent) "Oui" else "Non"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL) {
                                        OutlinedButton(
                                            onClick = { linkParentTarget = student },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Text("  Associer un compte parent")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Comptes & Invitations" -> {
                    if (role == UserRole.ADMIN_NATIONAL) {
                        val pendingRequests = accessRequests.filter { it.status == "pending" }
                        item {
                            Text("Demandes d'inscription en attente", style = MaterialTheme.typography.titleMedium)
                        }
                        if (pendingRequests.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.SupervisorAccount,
                                    title = "Aucune demande d'inscription en attente.",
                                    subtitle = "Les demandes soumises depuis l'écran d'inscription par rôle apparaîtront ici."
                                )
                            }
                        } else {
                            items(pendingRequests, key = { "req_${it.userId}" }) { req ->
                                ElevatedCard {
                                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(req.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                                                Text(
                                                    text = UserRole.fromKey(req.requestedRole).labelFr,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = req.email + if (req.phone.isNotBlank()) " • ${req.phone}" else "",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        if (req.schoolHint.isNotBlank()) {
                                            Text(
                                                text = "Établissement / Détails : ${req.schoolHint}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { approveRequestTarget = req },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Approuver")
                                            }
                                            OutlinedButton(
                                                onClick = { onRejectAccessRequest(req) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Refuser")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Invitations par e-mail vérifié", style = MaterialTheme.typography.titleMedium)
                    }
                    if (invitations.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.PersonAdd,
                                title = "Aucune invitation envoyée.",
                                subtitle = "Pré-enregistrez les comptes directeurs, enseignants, parents ou élèves par adresse Google."
                            )
                        }
                    } else {
                        items(invitations, key = { it.id }) { inv ->
                            ElevatedCard {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(inv.fullName, style = MaterialTheme.typography.titleMedium)
                                        Surface(
                                            color = if (inv.status == "claimed") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(inv.status, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                    Text("${inv.email} • ${UserRole.fromKey(inv.role).labelFr}", style = MaterialTheme.typography.bodySmall)
                                    if (inv.schoolName.isNotBlank()) {
                                        Text("Établissement : ${inv.schoolName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Comptes actifs et désactivés", style = MaterialTheme.typography.titleMedium)
                    }
                    if (managedUsers.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Badge,
                                title = "Aucun autre compte géré.",
                                subtitle = "Les comptes activés apparaîtront ici."
                            )
                        }
                    } else {
                        items(managedUsers, key = { it.userId }) { user ->
                            ElevatedCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.fullName, style = MaterialTheme.typography.titleMedium)
                                        Text("${user.roleEnum().labelFr} • ${user.schoolName.ifBlank { "National/Régional" }}", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            if (user.isActive) "Statut : Actif" else "Statut : Désactivé",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (user.isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (user.userId != profile.userId) {
                                        OutlinedButton(onClick = { onSetUserActive(user, !user.isActive) }) {
                                            Text(if (user.isActive) "Désactiver" else "Réactiver")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showRegionDialog) {
        CreateRegionDialog(
            onDismiss = { showRegionDialog = false },
            onSubmit = { name, code ->
                onCreateRegion(name, code)
                showRegionDialog = false
            }
        )
    }

    if (showSchoolDialog) {
        CreateSchoolDialog(
            regions = regions,
            onDismiss = { showSchoolDialog = false },
            onSubmit = { region, name, code, type, address ->
                onCreateSchool(region, name, code, type, address)
                showSchoolDialog = false
            }
        )
    }

    if (showClassDialog) {
        CreateClassDialog(
            schools = schools,
            onDismiss = { showClassDialog = false },
            onSubmit = { school, name, level, year ->
                onCreateClass(school, name, level, year)
                showClassDialog = false
            }
        )
    }

    if (showSubjectDialog) {
        CreateSubjectDialog(
            schools = schools,
            classes = classes,
            managedUsers = managedUsers,
            onDismiss = { showSubjectDialog = false },
            onSubmit = { school, sc, name, code, coeff, teacherUid, teacherName ->
                onCreateSubject(school, sc, name, code, coeff, teacherUid, teacherName)
                showSubjectDialog = false
            }
        )
    }

    if (showScheduleDialog) {
        CreateScheduleDialog(
            schools = schools,
            classes = classes,
            subjects = subjects,
            onDismiss = { showScheduleDialog = false },
            onSubmit = { school, sc, subj, day, start, end, room ->
                onCreateScheduleEntry(school, sc, subj, day, start, end, room)
                showScheduleDialog = false
            }
        )
    }

    if (showEnrollStudentDialog) {
        EnrollStudentDialog(
            schools = schools,
            classes = classes,
            managedUsers = managedUsers,
            onDismiss = { showEnrollStudentDialog = false },
            onSubmit = { school, sc, first, last, mat, dob, gender, parents, stuUid, visible ->
                onEnrollStudent(school, sc, first, last, mat, dob, gender, parents, stuUid, visible)
                showEnrollStudentDialog = false
            }
        )
    }

    if (showInvitationDialog) {
        CreateInvitationDialog(
            actorRole = role,
            regions = regions,
            schools = schools,
            students = students,
            classes = classes,
            onDismiss = { showInvitationDialog = false },
            onSubmit = { email, fullName, invRole, regId, school, phone, stuIds, clsIds ->
                onCreateInvitation(email, fullName, invRole, regId, school, phone, stuIds, clsIds)
                showInvitationDialog = false
            }
        )
    }

    if (linkParentTarget != null) {
        val targetStudent = linkParentTarget!!
        val parentCandidates = managedUsers.filter { it.roleEnum() == UserRole.PARENT }
        LinkParentDialog(
            student = targetStudent,
            parentCandidates = parentCandidates,
            onDismiss = { linkParentTarget = null },
            onSubmit = { parentUid ->
                onLinkParent(targetStudent, parentUid)
                linkParentTarget = null
            }
        )
    }

    if (assignDirectorSchool != null) {
        val targetSchool = assignDirectorSchool!!
        val directorCandidates = managedUsers.filter { it.roleEnum() == UserRole.DIRECTEUR }
        AssignDirectorDialog(
            school = targetSchool,
            directors = directorCandidates,
            onDismiss = { assignDirectorSchool = null },
            onSubmit = { dir ->
                onAssignDirector(targetSchool, dir)
                assignDirectorSchool = null
            }
        )
    }

    if (approveRequestTarget != null) {
        val targetReq = approveRequestTarget!!
        ApproveAccessRequestDialog(
            request = targetReq,
            regions = regions,
            schools = schools,
            onDismiss = { approveRequestTarget = null },
            onSubmit = { assignedRole, regionId, school ->
                onApproveAccessRequest(targetReq, assignedRole, regionId, school)
                approveRequestTarget = null
            }
        )
    }
}

@Composable
private fun SubjectRowCard(subj: Subject) {
    ElevatedCard {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("${subj.name} (${subj.code})", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Classe : ${subj.className} • Coeff. ${subj.coefficient}" +
                        if (subj.teacherName.isNotBlank()) " • Enseignant : ${subj.teacherName}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CreateRegionDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle région") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de la région *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code (ex: DJ01, ARTA) *") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(name, code) }, enabled = name.trim().length >= 2 && code.isNotBlank()) { Text("Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun CreateSchoolDialog(
    regions: List<Region>,
    onDismiss: () -> Unit,
    onSubmit: (Region, String, String, String, String) -> Unit
) {
    var selectedRegion by remember { mutableStateOf(regions.firstOrNull()) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("primaire") }
    var address by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvel établissement scolaire") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector("Région *", regions, selectedRegion, { "${it.name} (${it.code})" }, { selectedRegion = it }, emptyLabel = "Créez d'abord une région")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de l'établissement *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code officiel *") }, modifier = Modifier.fillMaxWidth())
                DropdownSelector("Type", listOf("primaire", "college", "lycee"), type, { it.replaceFirstChar { c -> c.uppercase() } }, { type = it })
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse / Quartier") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedRegion?.let { onSubmit(it, name, code, type, address) } },
                enabled = selectedRegion != null && name.trim().length >= 2 && code.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun CreateClassDialog(
    schools: List<School>,
    onDismiss: () -> Unit,
    onSubmit: (School, String, String, String) -> Unit
) {
    var selectedSchool by remember { mutableStateOf(schools.firstOrNull()) }
    var name by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var academicYear by remember { mutableStateOf("2026-2027") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créer une classe") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector("Établissement *", schools, selectedSchool, { it.name }, { selectedSchool = it }, emptyLabel = "Aucun établissement")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de la classe (ex: 6ème A, CM2) *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = level, onValueChange = { level = it }, label = { Text("Niveau (ex: 6ème, Primaire) *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = academicYear, onValueChange = { academicYear = it }, label = { Text("Année scolaire") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedSchool?.let { onSubmit(it, name, level, academicYear) } },
                enabled = selectedSchool != null && name.isNotBlank() && level.isNotBlank()
            ) { Text("Créer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun CreateSubjectDialog(
    schools: List<School>,
    classes: List<SchoolClass>,
    managedUsers: List<UserProfile>,
    onDismiss: () -> Unit,
    onSubmit: (School, SchoolClass, String, String, Double, String, String) -> Unit
) {
    var selectedClass by remember { mutableStateOf(classes.firstOrNull()) }
    val teachers = remember(managedUsers) { managedUsers.filter { it.roleEnum() == UserRole.ENSEIGNANT } }
    var selectedTeacher by remember { mutableStateOf<UserProfile?>(teachers.firstOrNull()) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var coeffText by remember { mutableStateOf("2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter une matière") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector("Classe *", classes, selectedClass, { "${it.name} (${it.schoolName})" }, { selectedClass = it }, emptyLabel = "Aucune classe")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de la matière *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code (ex: MATH, FR)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = coeffText, onValueChange = { coeffText = it }, label = { Text("Coefficient") }, modifier = Modifier.fillMaxWidth())
                DropdownSelector("Enseignant (optionnel)", teachers, selectedTeacher, { it.fullName }, { selectedTeacher = it }, emptyLabel = "Aucun enseignant actif")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sc = selectedClass ?: return@Button
                    val school = schools.firstOrNull { it.id == sc.schoolId } ?: return@Button
                    val coeff = coeffText.replace(",", ".").toDoubleOrNull()?.coerceIn(0.5, 20.0) ?: 1.0
                    onSubmit(school, sc, name, code, coeff, selectedTeacher?.userId ?: "", selectedTeacher?.fullName ?: "")
                },
                enabled = selectedClass != null && schools.any { it.id == selectedClass?.schoolId } && name.trim().length >= 2
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun CreateScheduleDialog(
    schools: List<School>,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSubmit: (School, SchoolClass, Subject, String, String, String, String) -> Unit
) {
    var selectedClass by remember { mutableStateOf(classes.firstOrNull()) }
    val classSubjects = remember(subjects, selectedClass) {
        subjects.filter { selectedClass == null || it.classId == selectedClass!!.id }
    }
    var selectedSubject by remember(classSubjects) { mutableStateOf(classSubjects.firstOrNull()) }
    var day by remember { mutableStateOf("lundi") }
    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("09:00") }
    var room by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un créneau d'emploi du temps") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector("Classe *", classes, selectedClass, { "${it.name} (${it.schoolName})" }, { selectedClass = it }, emptyLabel = "Aucune classe")
                DropdownSelector("Matière *", classSubjects, selectedSubject, { it.name }, { selectedSubject = it }, emptyLabel = "Aucune matière")
                DropdownSelector("Jour", listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi"), day, { it.replaceFirstChar { c -> c.uppercase() } }, { day = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Début") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("Fin") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Salle") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sc = selectedClass ?: return@Button
                    val subj = selectedSubject ?: return@Button
                    val school = schools.firstOrNull { it.id == sc.schoolId } ?: return@Button
                    onSubmit(school, sc, subj, day, startTime, endTime, room)
                },
                enabled = selectedClass != null && selectedSubject != null && startTime.length >= 4 && endTime.length >= 4
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun EnrollStudentDialog(
    schools: List<School>,
    classes: List<SchoolClass>,
    managedUsers: List<UserProfile>,
    onDismiss: () -> Unit,
    onSubmit: (School, SchoolClass, String, String, String, String, String, List<String>, String, Boolean) -> Unit
) {
    var selectedClass by remember { mutableStateOf(classes.firstOrNull()) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var matricule by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("M") }
    val parents = remember(managedUsers) { managedUsers.filter { it.roleEnum() == UserRole.PARENT } }
    val studentUsers = remember(managedUsers) { managedUsers.filter { it.roleEnum() == UserRole.ELEVE } }
    var selectedParent by remember { mutableStateOf<UserProfile?>(null) }
    var selectedStudentAccount by remember { mutableStateOf<UserProfile?>(null) }
    var gradesVisible by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inscrire un élève") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector("Classe *", classes, selectedClass, { "${it.name} (${it.schoolName})" }, { selectedClass = it }, emptyLabel = "Aucune classe")
                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text("Prénom *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text("Nom *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = matricule, onValueChange = { matricule = it }, label = { Text("Matricule *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = dob, onValueChange = { dob = it }, label = { Text("Date de naissance (AAAA-MM-JJ)") }, modifier = Modifier.fillMaxWidth())
                DropdownSelector("Genre", listOf("M", "F"), gender, { if (it == "M") "Masculin" else "Féminin" }, { gender = it })
                if (parents.isNotEmpty()) {
                    DropdownSelector("Compte parent (optionnel)", parents, selectedParent, { it.fullName }, { selectedParent = it })
                }
                if (studentUsers.isNotEmpty()) {
                    DropdownSelector("Compte élève (optionnel)", studentUsers, selectedStudentAccount, { it.fullName }, { selectedStudentAccount = it })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Autoriser l'élève à voir ses notes", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = gradesVisible, onCheckedChange = { gradesVisible = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sc = selectedClass ?: return@Button
                    val school = schools.firstOrNull { it.id == sc.schoolId } ?: return@Button
                    onSubmit(
                        school, sc, firstName, lastName, matricule, dob, gender,
                        listOfNotNull(selectedParent?.userId), selectedStudentAccount?.userId ?: "", gradesVisible
                    )
                },
                enabled = selectedClass != null && schools.any { it.id == selectedClass?.schoolId } &&
                    firstName.isNotBlank() && lastName.isNotBlank() && matricule.isNotBlank()
            ) { Text("Inscrire") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun CreateInvitationDialog(
    actorRole: UserRole,
    regions: List<Region>,
    schools: List<School>,
    students: List<Student>,
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onSubmit: (String, String, UserRole, String, School?, String, List<String>, List<String>) -> Unit
) {
    val allowedRoles = remember(actorRole) {
        if (actorRole == UserRole.ADMIN_NATIONAL) {
            listOf(UserRole.ADMIN_REGIONAL, UserRole.DIRECTEUR, UserRole.ENSEIGNANT, UserRole.PARENT, UserRole.ELEVE)
        } else {
            listOf(UserRole.ENSEIGNANT, UserRole.PARENT, UserRole.ELEVE)
        }
    }
    var selectedRole by remember { mutableStateOf(allowedRoles.first()) }
    var email by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedRegion by remember { mutableStateOf(regions.firstOrNull()) }
    var selectedSchool by remember { mutableStateOf(schools.firstOrNull()) }
    var selectedStudent by remember { mutableStateOf<Student?>(null) }
    var selectedClass by remember { mutableStateOf<SchoolClass?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pré-enregistrer / Inviter un utilisateur") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector("Rôle *", allowedRoles, selectedRole, { it.labelFr }, { selectedRole = it })
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Nom complet *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Adresse e-mail Google *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone") }, modifier = Modifier.fillMaxWidth())
                if (regions.isNotEmpty() && selectedRole == UserRole.ADMIN_REGIONAL) {
                    DropdownSelector("Région", regions, selectedRegion, { it.name }, { selectedRegion = it })
                }
                if (schools.isNotEmpty() && selectedRole != UserRole.ADMIN_REGIONAL) {
                    DropdownSelector("Établissement", schools, selectedSchool, { it.name }, { selectedSchool = it })
                }
                if (classes.isNotEmpty() && selectedRole == UserRole.ENSEIGNANT) {
                    DropdownSelector("Classe assignée (optionnel)", classes, selectedClass, { it.name }, { selectedClass = it })
                }
                if (students.isNotEmpty() && (selectedRole == UserRole.PARENT || selectedRole == UserRole.ELEVE)) {
                    DropdownSelector("Élève concerné (optionnel)", students, selectedStudent, { it.fullName }, { selectedStudent = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val regId = selectedSchool?.regionId ?: selectedRegion?.id ?: ""
                    onSubmit(
                        email, fullName, selectedRole, regId,
                        if (selectedRole == UserRole.ADMIN_REGIONAL) null else selectedSchool,
                        phone, listOfNotNull(selectedStudent?.id), listOfNotNull(selectedClass?.id)
                    )
                },
                enabled = email.contains("@") && fullName.trim().length >= 2
            ) { Text("Envoyer l'invitation") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun LinkParentDialog(
    student: Student,
    parentCandidates: List<UserProfile>,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var selectedParent by remember { mutableStateOf(parentCandidates.firstOrNull()) }
    var manualParentUid by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Associer un parent à ${student.fullName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (parentCandidates.isNotEmpty()) {
                    DropdownSelector("Compte parent inscrit", parentCandidates, selectedParent, { it.fullName }, { selectedParent = it })
                }
                OutlinedTextField(
                    value = manualParentUid,
                    onValueChange = { manualParentUid = it },
                    label = { Text("Ou UID du compte parent") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val uid = manualParentUid.trim().ifBlank { selectedParent?.userId ?: "" }
            Button(onClick = { onSubmit(uid) }, enabled = uid.isNotBlank()) { Text("Associer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun AssignDirectorDialog(
    school: School,
    directors: List<UserProfile>,
    onDismiss: () -> Unit,
    onSubmit: (UserProfile) -> Unit
) {
    var selectedDirector by remember { mutableStateOf(directors.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Affecter un directeur à ${school.name}") },
        text = {
            DropdownSelector(
                label = "Directeur inscrit",
                options = directors,
                selectedOption = selectedDirector,
                optionLabel = { it.fullName },
                onOptionSelected = { selectedDirector = it },
                emptyLabel = "Aucun compte directeur actif. Invitez d'abord un directeur."
            )
        },
        confirmButton = {
            Button(
                onClick = { selectedDirector?.let(onSubmit) },
                enabled = selectedDirector != null
            ) { Text("Affecter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun ApproveAccessRequestDialog(
    request: AccessRequest,
    regions: List<Region>,
    schools: List<School>,
    onDismiss: () -> Unit,
    onSubmit: (UserRole, String, School?) -> Unit
) {
    var assignedRole by remember { mutableStateOf(UserRole.fromKey(request.requestedRole)) }
    var selectedRegion by remember { mutableStateOf(regions.firstOrNull()) }
    var selectedSchool by remember { mutableStateOf(schools.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Approuver l'inscription de ${request.fullName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "E-mail : ${request.email}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (request.schoolHint.isNotBlank()) {
                    Text(
                        text = "Rattachement indiqué : ${request.schoolHint}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                DropdownSelector(
                    label = "Rôle attribué",
                    options = listOf(
                        UserRole.DIRECTEUR,
                        UserRole.ENSEIGNANT,
                        UserRole.PARENT,
                        UserRole.ADMIN_REGIONAL,
                        UserRole.ELEVE,
                        UserRole.ADMIN_NATIONAL
                    ),
                    selectedOption = assignedRole,
                    optionLabel = { it.labelFr },
                    onOptionSelected = { assignedRole = it }
                )
                if (assignedRole == UserRole.ADMIN_REGIONAL) {
                    DropdownSelector(
                        label = "Région",
                        options = regions,
                        selectedOption = selectedRegion,
                        optionLabel = { it.name },
                        onOptionSelected = { selectedRegion = it },
                        emptyLabel = "Aucune région enregistrée"
                    )
                }
                if (assignedRole == UserRole.DIRECTEUR || assignedRole == UserRole.ENSEIGNANT || assignedRole == UserRole.PARENT || assignedRole == UserRole.ELEVE) {
                    DropdownSelector(
                        label = "Établissement de rattachement",
                        options = schools,
                        selectedOption = selectedSchool,
                        optionLabel = { it.name },
                        onOptionSelected = { selectedSchool = it },
                        emptyLabel = "Aucun établissement enregistré (optionnel)"
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val regId = selectedSchool?.regionId ?: selectedRegion?.id ?: ""
                    onSubmit(assignedRole, regId, selectedSchool)
                }
            ) {
                Text("Valider et créer l'accès")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
