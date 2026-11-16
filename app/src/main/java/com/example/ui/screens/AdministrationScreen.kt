package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.catalog.DjiboutiSchoolsCatalog
import com.example.data.catalog.SchoolLevelFilter
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
import com.example.ui.components.OfficialSchoolPickerDialog
import com.example.ui.components.SchoolLevelFilterRow
import com.example.ui.components.SchoolTypeBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.TabChipsRow
import com.example.util.CsvStudentHelper

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
    isBusy: Boolean = false,
    targetTabName: String? = null,
    onTargetTabConsumed: () -> Unit = {},
    onCreateRegion: (String, String) -> Unit,
    onCreateSchool: (Region, String, String, String, String, String, String) -> Unit,
    onSetupDirectorSchool: (Region?, String, String, String, String, String, String, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onImportOfficialSchools: () -> Unit = {},
    onCreateClass: (School, String, String, String) -> Unit,
    onCreateSubject: (School, SchoolClass, String, String, Double, String, String) -> Unit,
    onCreateScheduleEntry: (School, SchoolClass, Subject, String, String, String, String) -> Unit,
    onEnrollStudent: (School, SchoolClass, String, String, String, String, String, List<String>, String, Boolean, String, String, String, String, String) -> Unit,
    onImportStudentsCsv: (School, SchoolClass, String) -> Unit = { _, _, _ -> },
    onLinkParent: (Student, String) -> Unit,
    onCreateInvitation: (String, String, UserRole, String, School?, String, List<String>, List<String>) -> Unit,
    onSetUserActive: (UserProfile, Boolean) -> Unit,
    onAssignDirector: (School, UserProfile) -> Unit,
    onApproveAccessRequest: (AccessRequest, UserRole, String, School?, List<String>, List<String>, List<String>, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onRequestMoreInfoAccessRequest: (AccessRequest, String) -> Unit = { _, _ -> },
    onRejectAccessRequest: (AccessRequest, String) -> Unit = { _, _ -> },
    onUpdateRegion: (Region, String, String) -> Unit = { _, _, _ -> },
    onUpdateSchool: (School, Region?, String, String, String, String, String, String, Boolean, String, String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    onUpdateClass: (SchoolClass, String, String, String) -> Unit = { _, _, _, _ -> },
    onUpdateSubject: (Subject, String, String, Double, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateScheduleEntry: (ScheduleEntry, Subject?, String, String, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateStudent: (Student, SchoolClass?, String, String, String, String, String, Boolean, Boolean, String, String, String, String, String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onUpdateInvitation: (RoleInvitation, String, String, String) -> Unit = { _, _, _, _ -> },
    onUpdateUser: (UserProfile, String, UserRole?, School?, Boolean) -> Unit = { _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val role = profile.roleEnum()
    val tabs = remember(role) {
        when (role) {
            UserRole.ADMIN_NATIONAL -> listOf("Régions", "Établissements", "Classes & Matières", "Élèves", "Comptes & Invitations")
            UserRole.ADMIN_REGIONAL -> listOf("Établissements", "Classes & Emplois du temps", "Élèves")
            UserRole.DIRECTEUR -> listOf("Classes", "Mon établissement", "Matières", "Emploi du temps", "Élèves & Parents", "Comptes & Invitations")
            else -> listOf("Emploi du temps", "Matières & Classes")
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(targetTabName, tabs) {
        if (targetTabName != null) {
            val idx = tabs.indexOf(targetTabName)
            if (idx >= 0) selectedTabIndex = idx
            onTargetTabConsumed()
        }
    }
    val safeIndex = selectedTabIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val activeTabName = tabs[safeIndex]

    var showRegionDialog by remember { mutableStateOf(false) }
    var showSchoolDialog by remember { mutableStateOf(false) }
    var showDirectorSchoolDialog by remember { mutableStateOf(false) }
    var showClassDialog by remember { mutableStateOf(false) }
    var showSubjectDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showEnrollStudentDialog by remember { mutableStateOf(false) }
    var showInvitationDialog by remember { mutableStateOf(false) }
    var linkParentTarget by remember { mutableStateOf<Student?>(null) }
    var assignDirectorSchool by remember { mutableStateOf<School?>(null) }
    var approveRequestTarget by remember { mutableStateOf<AccessRequest?>(null) }
    var requestInfoTarget by remember { mutableStateOf<AccessRequest?>(null) }
    var rejectRequestTarget by remember { mutableStateOf<AccessRequest?>(null) }
    var schoolLevelFilter by remember { mutableStateOf(SchoolLevelFilter.ALL) }
    var schoolSearchQuery by remember { mutableStateOf("") }
    var showDirectoryDialog by remember { mutableStateOf(false) }

    var editingRegion by remember { mutableStateOf<Region?>(null) }
    var editingSchool by remember { mutableStateOf<School?>(null) }
    var editingClass by remember { mutableStateOf<SchoolClass?>(null) }
    var editingSubject by remember { mutableStateOf<Subject?>(null) }
    var editingSchedule by remember { mutableStateOf<ScheduleEntry?>(null) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var editingInvitation by remember { mutableStateOf<RoleInvitation?>(null) }
    var editingUser by remember { mutableStateOf<UserProfile?>(null) }
    var pendingCsvContent by remember { mutableStateOf<String?>(null) }

    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!content.isNullOrBlank()) {
                pendingCsvContent = content
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(CsvStudentHelper.exportStudentsToCsv(students))
                }
            }
        }
    }

    val canAddInTab = when (activeTabName) {
        "Régions" -> role == UserRole.ADMIN_NATIONAL
        "Établissements" -> role == UserRole.ADMIN_NATIONAL
        "Mon établissement" -> role == UserRole.DIRECTEUR && schools.isEmpty()
        "Classes", "Classes & Matières" -> role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
        "Matières" -> role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
        "Emploi du temps" -> role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
        "Élèves", "Élèves & Parents" -> role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
        "Comptes & Invitations" -> role == UserRole.ADMIN_NATIONAL || role == UserRole.DIRECTEUR
        else -> false
    }

    val missingOfficialCount = remember(schools) {
        DjiboutiSchoolsCatalog.allSchools.count { entry ->
            schools.none { existing ->
                existing.id == entry.id ||
                    existing.code.equals(entry.code, ignoreCase = true) ||
                    existing.name.equals(entry.name, ignoreCase = true)
            }
        }
    }
    val levelCounts = remember(schools) {
        mapOf(
            SchoolLevelFilter.ALL to schools.size,
            SchoolLevelFilter.PRIMAIRE to schools.count { it.schoolType.equals("primaire", ignoreCase = true) },
            SchoolLevelFilter.COLLEGE to schools.count { it.schoolType.equals("college", ignoreCase = true) },
            SchoolLevelFilter.LYCEE to schools.count { it.schoolType.equals("lycee", ignoreCase = true) }
        )
    }
    val filteredSchools = remember(schools, schoolLevelFilter, schoolSearchQuery) {
        val cleanQ = schoolSearchQuery.trim().lowercase()
        schools.filter { school ->
            val levelOk = schoolLevelFilter.typeKey == null ||
                school.schoolType.equals(schoolLevelFilter.typeKey, ignoreCase = true)
            val queryOk = cleanQ.isBlank() ||
                school.name.lowercase().contains(cleanQ) ||
                school.code.lowercase().contains(cleanQ) ||
                school.address.lowercase().contains(cleanQ) ||
                school.regionName.lowercase().contains(cleanQ)
            levelOk && queryOk
        }.sortedWith(compareBy({ it.schoolType }, { it.name }))
    }

    Scaffold(
        floatingActionButton = {
            if (canAddInTab) {
                FloatingActionButton(
                    onClick = {
                        when (activeTabName) {
                            "Régions" -> showRegionDialog = true
                            "Établissements" -> showSchoolDialog = true
                            "Mon établissement" -> showDirectorSchoolDialog = true
                            "Classes", "Classes & Matières" -> {
                                if (role == UserRole.DIRECTEUR && schools.isEmpty()) {
                                    showDirectorSchoolDialog = true
                                } else {
                                    showClassDialog = true
                                }
                            }
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
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${region.name} (${region.code})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            text = if (region.regionalAdminName.isNotBlank()) "Admin régional : ${region.regionalAdminName}" else "Aucun administrateur régional affecté",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (role == UserRole.ADMIN_NATIONAL) {
                                        IconButton(
                                            onClick = { editingRegion = region },
                                            modifier = Modifier.testTag("edit_region_${region.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Modifier la région ${region.name}",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Établissements" -> {
                    if (role == UserRole.ADMIN_NATIONAL && missingOfficialCount > 0) {
                        item {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Répertoire officiel de Djibouti-Ville",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "$missingOfficialCount établissement(s) officiel(s) prêt(s) à importer (18 écoles primaires, 17 collèges publics, 4 lycées nationaux).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Button(
                                        onClick = onImportOfficialSchools,
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("import_official_schools_button")
                                    ) {
                                        Text(
                                            if (isBusy) "Importation en cours..."
                                            else "Importer les $missingOfficialCount établissements officiels"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedButton(
                            onClick = { showDirectoryDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_official_directory_button")
                        ) {
                            Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Consulter l'annuaire officiel Djibouti-Ville (${DjiboutiSchoolsCatalog.allSchools.size})")
                        }
                    }

                    if (schools.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.School,
                                title = "Aucun établissement enregistré.",
                                subtitle = "Importez en un clic les 39 écoles primaires, collèges et lycées de Djibouti-Ville ci-dessus ou créez un établissement manuellement."
                            )
                        }
                    } else {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = schoolSearchQuery,
                                    onValueChange = { schoolSearchQuery = it },
                                    label = { Text("Filtrer par nom, code ou quartier") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_schools_search_input")
                                )
                                SchoolLevelFilterRow(
                                    selectedFilter = schoolLevelFilter,
                                    onSelectFilter = { schoolLevelFilter = it },
                                    countsByLevel = levelCounts
                                )
                            }
                        }

                        if (filteredSchools.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.Search,
                                    title = "Aucun établissement trouvé.",
                                    subtitle = "Essayez un autre niveau (Primaire, Collège, Lycée) ou modifiez votre recherche."
                                )
                            }
                        } else {
                            items(filteredSchools, key = { it.id }) { school ->
                                val classesCount = remember(classes, school.id) { classes.count { it.schoolId == school.id } }
                                val studentsCount = remember(students, school.id) { students.count { it.schoolId == school.id } }
                                ElevatedCard {
                                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(school.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                            Spacer(Modifier.size(8.dp))
                                            SchoolTypeBadge(schoolType = school.schoolType)
                                            if (role == UserRole.ADMIN_NATIONAL || role == UserRole.ADMIN_REGIONAL) {
                                                IconButton(
                                                    onClick = { editingSchool = school },
                                                    modifier = Modifier.testTag("edit_school_${school.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Edit,
                                                        contentDescription = "Modifier l'établissement ${school.name}",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                        Text("Code : ${school.code} • Région : ${school.regionName}", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "Classes : $classesCount • Élèves : $studentsCount",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        if (school.sector.isNotBlank() || school.address.isNotBlank()) {
                                            val locationLine = listOf(school.sector, school.address)
                                                .filter { it.isNotBlank() }
                                                .distinct()
                                                .joinToString(" • ")
                                            Text("Secteur / Adresse : $locationLine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (school.phone.isNotBlank() || school.email.isNotBlank()) {
                                            val contactLine = listOfNotNull(
                                                school.phone.takeIf { it.isNotBlank() }?.let { "Tél : $it" },
                                                school.email.takeIf { it.isNotBlank() }?.let { "E-mail : $it" }
                                            ).joinToString(" • ")
                                            Text(contactLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (school.description.isNotBlank()) {
                                            Text(school.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                }

                "Mon établissement" -> {
                    if (schools.isEmpty()) {
                        item {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Aucun établissement rattaché à votre direction",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Créez votre établissement ou sélectionnez-le dans le répertoire officiel de Djibouti-Ville pour pouvoir créer vos classes, matières et inscriptions.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Button(
                                        onClick = { showDirectorSchoolDialog = true },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("director_setup_school_button")
                                    ) {
                                        Text("Créer / associer mon établissement")
                                    }
                                }
                            }
                        }
                    } else {
                        items(schools, key = { it.id }) { school ->
                            val classesCount = remember(classes, school.id) { classes.count { it.schoolId == school.id } }
                            val studentsCount = remember(students, school.id) { students.count { it.schoolId == school.id } }
                            ElevatedCard {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(school.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Spacer(Modifier.size(8.dp))
                                        SchoolTypeBadge(schoolType = school.schoolType)
                                        if (role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL) {
                                            IconButton(
                                                onClick = { editingSchool = school },
                                                modifier = Modifier.testTag("edit_director_school_${school.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Edit,
                                                    contentDescription = "Modifier mon établissement",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Text("Code : ${school.code} • Région : ${school.regionName.ifBlank { "Djibouti-Ville" }}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Classes : $classesCount • Élèves : $studentsCount",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    if (school.address.isNotBlank()) {
                                        Text("Secteur / Adresse : ${school.address}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (school.phone.isNotBlank() || school.email.isNotBlank()) {
                                        val contactLine = listOfNotNull(
                                            school.phone.takeIf { it.isNotBlank() }?.let { "Tél : $it" },
                                            school.email.takeIf { it.isNotBlank() }?.let { "E-mail : $it" }
                                        ).joinToString(" • ")
                                        Text(contactLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = { showClassDialog = true },
                                            modifier = Modifier.weight(1f).testTag("director_school_card_create_class")
                                        ) {
                                            Text("+ Créer une classe")
                                        }
                                        OutlinedButton(
                                            onClick = { showSubjectDialog = true },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("+ Matière")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Classes", "Classes & Matières", "Matières & Classes" -> {
                    if (role == UserRole.DIRECTEUR && schools.isEmpty()) {
                        item {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Établissement requis pour créer une classe",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "Votre compte directeur n'est pas encore lié à un établissement. Associez ou créez votre établissement ci-dessous en un instant.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Button(
                                        onClick = { showDirectorSchoolDialog = true },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("director_setup_school_banner_button")
                                    ) {
                                        Text("Configurer / Créer mon établissement")
                                    }
                                }
                            }
                        }
                    } else if (role == UserRole.ADMIN_NATIONAL || role == UserRole.DIRECTEUR) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { showClassDialog = true },
                                    modifier = Modifier.weight(1f).testTag("open_create_class_button")
                                ) {
                                    Text("+ Classe")
                                }
                                OutlinedButton(
                                    onClick = { showSubjectDialog = true },
                                    modifier = Modifier.weight(1f).testTag("open_create_subject_button")
                                ) {
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
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("${sc.name} • ${sc.level}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text("${sc.schoolName} • Année ${sc.academicYear}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "${sc.studentUids.size} compte(s) élève • ${sc.parentUids.size} parent(s) • ${classSubjects.size} matière(s)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (role == UserRole.ADMIN_NATIONAL || role == UserRole.DIRECTEUR) {
                                        IconButton(
                                            onClick = { editingClass = sc },
                                            modifier = Modifier.testTag("edit_class_${sc.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Modifier la classe ${sc.name}",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (activeTabName != "Classes" && subjects.isNotEmpty()) {
                        item {
                            Text("Matières configurées", style = MaterialTheme.typography.titleMedium)
                        }
                        items(subjects, key = { it.id }) { subj ->
                            SubjectRowCard(
                                subj = subj,
                                canEdit = role == UserRole.ADMIN_NATIONAL || role == UserRole.DIRECTEUR,
                                onEdit = { editingSubject = subj }
                            )
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
                            SubjectRowCard(
                                subj = subj,
                                canEdit = role == UserRole.ADMIN_NATIONAL || role == UserRole.DIRECTEUR,
                                onEdit = { editingSubject = subj }
                            )
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
                                    if (role == UserRole.ADMIN_NATIONAL || role == UserRole.DIRECTEUR) {
                                        IconButton(
                                            onClick = { editingSchedule = entry },
                                            modifier = Modifier.testTag("edit_schedule_${entry.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Modifier le créneau",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Élèves", "Élèves & Parents" -> {
                    if (role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL) {
                        item {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                                    },
                                    enabled = !isBusy && schools.isNotEmpty() && classes.isNotEmpty(),
                                    modifier = Modifier.weight(1f).testTag("import_students_csv_button")
                                ) {
                                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Importer CSV")
                                }
                                OutlinedButton(
                                    onClick = { csvExportLauncher.launch("eleves_ecole_connectee.csv") },
                                    enabled = students.isNotEmpty(),
                                    modifier = Modifier.weight(1f).testTag("export_students_csv_button")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("Exporter CSV")
                                }
                            }
                        }
                    }
                    if (students.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Groups,
                                title = "Aucun élève enregistré.",
                                subtitle = "Inscrivez les élèves dans leurs classes ou importez une liste CSV."
                            )
                        }
                    } else {
                        items(students, key = { it.id }) { student ->
                            ElevatedCard {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                                                Text(student.matricule, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                            if (role == UserRole.ADMIN_NATIONAL || role == UserRole.DIRECTEUR) {
                                                IconButton(
                                                    onClick = { editingStudent = student },
                                                    modifier = Modifier.testTag("edit_student_${student.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Edit,
                                                        contentDescription = "Modifier l'élève ${student.fullName}",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Text("${student.schoolName} • ${student.className} • Année ${student.academicYear.ifBlank { "2026-2027" }}", style = MaterialTheme.typography.bodySmall)
                                    if (student.dateOfBirth.isNotBlank() || student.legalGuardianName.isNotBlank()) {
                                        val guardianSummary = listOfNotNull(
                                            student.dateOfBirth.takeIf { it.isNotBlank() }?.let { "Né(e) le $it" },
                                            student.legalGuardianName.takeIf { it.isNotBlank() }?.let {
                                                val rel = student.legalGuardianRelation.takeIf { r -> r.isNotBlank() }?.let { " ($it)" } ?: ""
                                                val tel = student.legalGuardianPhone.takeIf { p -> p.isNotBlank() }?.let { " • $it" } ?: ""
                                                "Resp. : $it$rel$tel"
                                            }
                                        ).joinToString(" • ")
                                        Text(guardianSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (student.academicHistory.isNotBlank()) {
                                        Text("Historique : ${student.academicHistory}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
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
                    val actionableStatuses = setOf("pending", "info_requested")
                    val directorActionable = accessRequests.filter {
                        it.status in actionableStatuses &&
                            it.requestedRole in listOf(UserRole.PARENT.key, UserRole.ENSEIGNANT.key, UserRole.ELEVE.key)
                    }
                    val nationalActionable = accessRequests.filter {
                        it.status in actionableStatuses &&
                            it.requestedRole in listOf(UserRole.DIRECTEUR.key, UserRole.ADMIN_REGIONAL.key, UserRole.ADMIN_NATIONAL.key)
                    }

                    if (role == UserRole.DIRECTEUR) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Demandes à valider par la Direction (${directorActionable.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "La direction vérifie les comptes Parents et Enseignants rattachés à son établissement avant toute activation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (directorActionable.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.SupervisorAccount,
                                    title = "Aucune demande en attente pour votre établissement.",
                                    subtitle = "Les demandes d'inscription Parent et Enseignant rattachées à votre école apparaîtront ici."
                                )
                            }
                        } else {
                            items(directorActionable, key = { "dir_req_${it.userId}" }) { req ->
                                AccessRequestValidationCard(
                                    request = req,
                                    authorityLabel = "Validation Direction d'école",
                                    onApprove = { approveRequestTarget = req },
                                    onRequestMoreInfo = { requestInfoTarget = req },
                                    onReject = { rejectRequestTarget = req }
                                )
                            }
                        }
                    } else if (role == UserRole.ADMIN_NATIONAL) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Demandes Directions & Admins (${nationalActionable.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "L'Administration nationale valide les comptes des Directeurs d'école et Administrateurs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (nationalActionable.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Filled.SupervisorAccount,
                                    title = "Aucune demande Direction / Admin en attente.",
                                    subtitle = "Les demandes soumises par les directeurs ou administrateurs apparaîtront ici."
                                )
                            }
                        } else {
                            items(nationalActionable, key = { "nat_req_${it.userId}" }) { req ->
                                AccessRequestValidationCard(
                                    request = req,
                                    authorityLabel = "Validation Administration nationale",
                                    onApprove = { approveRequestTarget = req },
                                    onRequestMoreInfo = { requestInfoTarget = req },
                                    onReject = { rejectRequestTarget = req }
                                )
                            }
                        }

                        if (directorActionable.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Suivi national — Parents & Enseignants (${directorActionable.size})",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            items(directorActionable, key = { "nat_sub_req_${it.userId}" }) { req ->
                                AccessRequestValidationCard(
                                    request = req,
                                    authorityLabel = "Autorité : Direction d'école",
                                    onApprove = { approveRequestTarget = req },
                                    onRequestMoreInfo = { requestInfoTarget = req },
                                    onReject = { rejectRequestTarget = req }
                                )
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
                            val canEditInv = inv.status != "claimed" &&
                                (role == UserRole.ADMIN_NATIONAL || inv.createdByUid == profile.userId)
                            ElevatedCard {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(inv.fullName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Surface(
                                                color = if (inv.status == "claimed") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Text(inv.status, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                            }
                                            if (canEditInv) {
                                                IconButton(
                                                    onClick = { editingInvitation = inv },
                                                    modifier = Modifier.testTag("edit_invitation_${inv.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Edit,
                                                        contentDescription = "Modifier l'invitation",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
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
                            val canEditUser = user.userId != profile.userId && (
                                role == UserRole.ADMIN_NATIONAL ||
                                    (role == UserRole.DIRECTEUR && user.roleEnum() in listOf(UserRole.ENSEIGNANT, UserRole.PARENT, UserRole.ELEVE))
                                )
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
                                    if (canEditUser) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = { editingUser = user },
                                                modifier = Modifier.testTag("edit_user_${user.userId}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Edit,
                                                    contentDescription = "Modifier le compte de ${user.fullName}",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
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
            onSubmit = { region, name, code, type, address, phone, email ->
                if (region != null) {
                    onCreateSchool(region, name, code, type, address, phone, email)
                }
                showSchoolDialog = false
            }
        )
    }

    if (showDirectorSchoolDialog) {
        CreateDirectorSchoolDialog(
            regions = regions,
            defaultNameHint = profile.schoolName,
            onDismiss = { showDirectorSchoolDialog = false },
            onSubmit = { region, name, code, type, address, officialId, phone, email ->
                onSetupDirectorSchool(region, name, code, type, address, officialId, phone, email)
                showDirectorSchoolDialog = false
            }
        )
    }

    if (showClassDialog) {
        CreateClassDialog(
            schools = schools,
            allowDirectorSchoolSetup = role == UserRole.DIRECTEUR,
            onSetupSchool = {
                showClassDialog = false
                showDirectorSchoolDialog = true
            },
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
            onSubmit = { school, sc, first, last, mat, dob, gender, parents, stuUid, visible, year, gName, gPhone, gRel, history ->
                onEnrollStudent(school, sc, first, last, mat, dob, gender, parents, stuUid, visible, year, gName, gPhone, gRel, history)
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
            actorRole = role,
            actorSchoolId = profile.schoolId,
            request = targetReq,
            regions = regions,
            schools = schools,
            classes = classes,
            subjects = subjects,
            students = students,
            onNavigateToTab = { tabName ->
                val idx = tabs.indexOfFirst { it.equals(tabName, ignoreCase = true) || it.startsWith(tabName.substringBefore(" ")) }
                if (idx >= 0) selectedTabIndex = idx
            },
            onDismiss = { approveRequestTarget = null },
            onSubmit = { assignedRole, regionId, school, studentIds, classIds, subjectIds, decisionNote ->
                onApproveAccessRequest(targetReq, assignedRole, regionId, school, studentIds, classIds, subjectIds, decisionNote)
                approveRequestTarget = null
            }
        )
    }

    if (requestInfoTarget != null) {
        val targetReq = requestInfoTarget!!
        RequestMoreInfoDialog(
            request = targetReq,
            onDismiss = { requestInfoTarget = null },
            onSubmit = { note ->
                onRequestMoreInfoAccessRequest(targetReq, note)
                requestInfoTarget = null
            }
        )
    }

    if (rejectRequestTarget != null) {
        val targetReq = rejectRequestTarget!!
        RejectAccessRequestDialog(
            request = targetReq,
            onDismiss = { rejectRequestTarget = null },
            onSubmit = { reason ->
                onRejectAccessRequest(targetReq, reason)
                rejectRequestTarget = null
            }
        )
    }

    if (showDirectoryDialog) {
        OfficialSchoolPickerDialog(
            onDismiss = { showDirectoryDialog = false },
            onSelectSchool = { showDirectoryDialog = false }
        )
    }

    if (editingRegion != null) {
        val target = editingRegion!!
        EditRegionDialog(
            region = target,
            onDismiss = { editingRegion = null },
            onSubmit = { name, code ->
                onUpdateRegion(target, name, code)
                editingRegion = null
            }
        )
    }

    if (editingSchool != null) {
        val target = editingSchool!!
        EditSchoolDialog(
            school = target,
            regions = regions,
            onDismiss = { editingSchool = null },
            onSubmit = { reg, name, code, type, address, sector, description, isActive, phone, email ->
                onUpdateSchool(target, reg, name, code, type, address, sector, description, isActive, phone, email)
                editingSchool = null
            }
        )
    }

    if (editingClass != null) {
        val target = editingClass!!
        EditClassDialog(
            schoolClass = target,
            onDismiss = { editingClass = null },
            onSubmit = { name, level, year ->
                onUpdateClass(target, name, level, year)
                editingClass = null
            }
        )
    }

    if (editingSubject != null) {
        val target = editingSubject!!
        EditSubjectDialog(
            subject = target,
            managedUsers = managedUsers,
            onDismiss = { editingSubject = null },
            onSubmit = { name, code, coeff, teacherUid, teacherName ->
                onUpdateSubject(target, name, code, coeff, teacherUid, teacherName)
                editingSubject = null
            }
        )
    }

    if (editingSchedule != null) {
        val target = editingSchedule!!
        EditScheduleDialog(
            entry = target,
            subjects = subjects,
            onDismiss = { editingSchedule = null },
            onSubmit = { subj, day, start, end, room ->
                onUpdateScheduleEntry(target, subj, day, start, end, room)
                editingSchedule = null
            }
        )
    }

    if (editingStudent != null) {
        val target = editingStudent!!
        EditStudentDialog(
            student = target,
            classes = classes,
            onDismiss = { editingStudent = null },
            onSubmit = { targetClass, first, last, mat, dob, gender, visible, isActive, year, gName, gPhone, gRel, history ->
                onUpdateStudent(target, targetClass, first, last, mat, dob, gender, visible, isActive, year, gName, gPhone, gRel, history)
                editingStudent = null
            }
        )
    }

    if (editingInvitation != null) {
        val target = editingInvitation!!
        EditInvitationDialog(
            invitation = target,
            onDismiss = { editingInvitation = null },
            onSubmit = { fullName, phone, status ->
                onUpdateInvitation(target, fullName, phone, status)
                editingInvitation = null
            }
        )
    }

    if (editingUser != null) {
        val target = editingUser!!
        EditManagedUserDialog(
            actorRole = role,
            targetUser = target,
            schools = schools,
            onDismiss = { editingUser = null },
            onSubmit = { fullName, newRole, newSchool, isActive ->
                onUpdateUser(target, fullName, newRole, newSchool, isActive)
                editingUser = null
            }
        )
    }

    if (pendingCsvContent != null) {
        val csvText = pendingCsvContent!!
        CsvImportConfirmDialog(
            csvContent = csvText,
            schools = schools,
            classes = classes,
            onDismiss = { pendingCsvContent = null },
            onConfirm = { school, fallbackClass ->
                onImportStudentsCsv(school, fallbackClass, csvText)
                pendingCsvContent = null
            }
        )
    }
}

@Composable
private fun SubjectRowCard(
    subj: Subject,
    canEdit: Boolean = false,
    onEdit: () -> Unit = {}
) {
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
            if (canEdit) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("edit_subject_${subj.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Modifier la matière ${subj.name}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
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
    onSubmit: (Region?, String, String, String, String, String, String) -> Unit
) {
    var selectedRegion by remember(regions) { mutableStateOf(regions.firstOrNull()) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("primaire") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var showCatalogPrefill by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvel établissement scolaire") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showCatalogPrefill = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pré-remplir depuis le répertoire Djibouti-Ville")
                }
                DropdownSelector("Région *", regions, selectedRegion, { "${it.name} (${it.code})" }, { selectedRegion = it }, emptyLabel = "Créez d'abord une région")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de l'établissement *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Code officiel *") }, modifier = Modifier.fillMaxWidth())
                DropdownSelector("Type", listOf("primaire", "college", "lycee"), type, { it.replaceFirstChar { c -> c.uppercase() } }, { type = it })
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse / Quartier") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Téléphone") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedRegion?.let { onSubmit(it, name, code, type, address, phone, email) } },
                enabled = selectedRegion != null && name.trim().length >= 2 && code.isNotBlank()
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )

    if (showCatalogPrefill) {
        OfficialSchoolPickerDialog(
            title = "Sélectionner un établissement officiel",
            onDismiss = { showCatalogPrefill = false },
            onSelectSchool = { entry ->
                name = entry.name
                code = entry.code
                type = entry.schoolType
                address = "${entry.address} — ${entry.sector}"
                showCatalogPrefill = false
            }
        )
    }
}

@Composable
private fun CreateDirectorSchoolDialog(
    regions: List<Region>,
    defaultNameHint: String = "",
    onDismiss: () -> Unit,
    onSubmit: (Region?, String, String, String, String, String, String, String) -> Unit
) {
    var selectedRegion by remember(regions) {
        mutableStateOf(
            regions.firstOrNull { it.id == DjiboutiSchoolsCatalog.REGION_ID || it.name.contains("Djibouti", ignoreCase = true) }
                ?: regions.firstOrNull()
        )
    }
    var name by remember { mutableStateOf(defaultNameHint) }
    var code by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("primaire") }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var officialSchoolId by remember { mutableStateOf("") }
    var showCatalogPrefill by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mon établissement scolaire") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showCatalogPrefill = true },
                    modifier = Modifier.fillMaxWidth().testTag("director_school_pick_official")
                ) {
                    Text("Choisir dans le répertoire Djibouti-Ville (39)")
                }
                if (regions.isNotEmpty()) {
                    DropdownSelector("Région *", regions, selectedRegion, { "${it.name} (${it.code})" }, { selectedRegion = it })
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Région : Djibouti-Ville (DJV) — sélectionnée automatiquement",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        officialSchoolId = ""
                    },
                    label = { Text("Nom de l'établissement *") },
                    modifier = Modifier.fillMaxWidth().testTag("director_school_name_input")
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Code officiel (ex: EP-REP, CB01) *") },
                    modifier = Modifier.fillMaxWidth().testTag("director_school_code_input")
                )
                DropdownSelector("Type", listOf("primaire", "college", "lycee"), type, { it.replaceFirstChar { c -> c.uppercase() } }, { type = it })
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse / Quartier") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-mail") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedRegion, name, code, type, address, officialSchoolId, phone, email) },
                enabled = name.trim().length >= 2 && code.isNotBlank(),
                modifier = Modifier.testTag("director_school_submit_button")
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )

    if (showCatalogPrefill) {
        OfficialSchoolPickerDialog(
            title = "Choisir mon établissement (Djibouti-Ville)",
            onDismiss = { showCatalogPrefill = false },
            onSelectSchool = { entry ->
                officialSchoolId = entry.id
                name = entry.name
                code = entry.code
                type = entry.schoolType
                address = "${entry.address} — ${entry.sector}"
                showCatalogPrefill = false
            }
        )
    }
}

@Composable
private fun CreateClassDialog(
    schools: List<School>,
    allowDirectorSchoolSetup: Boolean = false,
    onSetupSchool: () -> Unit = {},
    onDismiss: () -> Unit,
    onSubmit: (School, String, String, String) -> Unit
) {
    var selectedSchool by remember(schools) { mutableStateOf(schools.firstOrNull()) }
    var name by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var academicYear by remember { mutableStateOf("2026-2027") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Créer une classe") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector("Établissement *", schools, selectedSchool, { it.name }, { selectedSchool = it }, emptyLabel = "Aucun établissement")
                if (schools.isEmpty() && allowDirectorSchoolSetup) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Aucun établissement rattaché à votre compte. Configurez votre établissement d'abord pour activer la création de classes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            OutlinedButton(
                                onClick = onSetupSchool,
                                modifier = Modifier.fillMaxWidth().testTag("create_class_setup_school_button")
                            ) {
                                Text("Créer / associer mon établissement")
                            }
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom de la classe (ex: 6ème A, CM2) *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = level, onValueChange = { level = it }, label = { Text("Niveau (ex: 6ème, Primaire) *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = academicYear, onValueChange = { academicYear = it }, label = { Text("Année scolaire") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedSchool?.let { onSubmit(it, name, level, academicYear) } },
                enabled = selectedSchool != null && name.isNotBlank() && level.isNotBlank(),
                modifier = Modifier.testTag("create_class_confirm_button")
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
    var selectedClass by remember(classes) { mutableStateOf(classes.firstOrNull()) }
    val teachers = remember(managedUsers) { managedUsers.filter { it.roleEnum() == UserRole.ENSEIGNANT } }
    var selectedTeacher by remember(teachers) { mutableStateOf<UserProfile?>(teachers.firstOrNull()) }
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
    onSubmit: (School, SchoolClass, String, String, String, String, String, List<String>, String, Boolean, String, String, String, String, String) -> Unit
) {
    var selectedClass by remember { mutableStateOf(classes.firstOrNull()) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var matricule by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("M") }
    var academicYear by remember { mutableStateOf("2026-2027") }
    var guardianName by remember { mutableStateOf("") }
    var guardianPhone by remember { mutableStateOf("") }
    var guardianRelation by remember { mutableStateOf("") }
    var academicHistory by remember { mutableStateOf("") }
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
                OutlinedTextField(value = academicYear, onValueChange = { academicYear = it }, label = { Text("Année scolaire") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = guardianName, onValueChange = { guardianName = it }, label = { Text("Responsable légal (nom)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = guardianPhone, onValueChange = { guardianPhone = it }, label = { Text("Téléphone du responsable") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = guardianRelation, onValueChange = { guardianRelation = it }, label = { Text("Lien de parenté") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = academicHistory, onValueChange = { academicHistory = it }, label = { Text("Historique scolaire") }, modifier = Modifier.fillMaxWidth())
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
                        listOfNotNull(selectedParent?.userId), selectedStudentAccount?.userId ?: "", gradesVisible,
                        academicYear, guardianName, guardianPhone, guardianRelation, academicHistory
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
private fun CsvImportConfirmDialog(
    csvContent: String,
    schools: List<School>,
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onConfirm: (School, SchoolClass) -> Unit
) {
    val parsedRows = remember(csvContent) { CsvStudentHelper.parseStudentsFromCsv(csvContent) }
    var selectedSchool by remember(schools) { mutableStateOf(schools.firstOrNull()) }
    val schoolClasses = remember(classes, selectedSchool) {
        classes.filter { selectedSchool == null || it.schoolId == selectedSchool!!.id }
    }
    var fallbackClass by remember(schoolClasses) { mutableStateOf(schoolClasses.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importer des élèves (CSV)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${parsedRows.size} élève(s) valide(s) détecté(s) dans le fichier.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (schools.size > 1) {
                    DropdownSelector(
                        label = "Établissement cible",
                        options = schools,
                        selectedOption = selectedSchool,
                        optionLabel = { it.name },
                        onOptionSelected = { selectedSchool = it }
                    )
                }
                DropdownSelector(
                    label = "Classe par défaut (si colonne classe absente)",
                    options = schoolClasses,
                    selectedOption = fallbackClass,
                    optionLabel = { "${it.name} (${it.level})" },
                    onOptionSelected = { fallbackClass = it },
                    emptyLabel = "Aucune classe"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = selectedSchool
                    val c = fallbackClass
                    if (s != null && c != null) onConfirm(s, c)
                },
                enabled = parsedRows.isNotEmpty() && selectedSchool != null && fallbackClass != null,
                modifier = Modifier.testTag("confirm_csv_import_button")
            ) {
                Text("Importer (${parsedRows.size})")
            }
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
    var selectedRegion by remember(regions) { mutableStateOf(regions.firstOrNull()) }
    var selectedSchool by remember(schools) { mutableStateOf(schools.firstOrNull()) }
    var selectedStudent by remember(students) { mutableStateOf<Student?>(null) }
    var selectedClass by remember(classes) { mutableStateOf<SchoolClass?>(null) }

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
                enabled = email.contains("@") &&
                    fullName.trim().length >= 2 &&
                    ((selectedRole != UserRole.DIRECTEUR && selectedRole != UserRole.ENSEIGNANT) || selectedSchool != null)
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
internal fun AccessRequestValidationCard(
    request: AccessRequest,
    authorityLabel: String,
    onApprove: () -> Unit,
    onRequestMoreInfo: () -> Unit,
    onReject: () -> Unit
) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        color = if (request.status == "info_requested") {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (request.status == "info_requested") "Infos demandées" else "En attente",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = UserRole.fromKey(request.requestedRole).labelFr,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Text(
                text = authorityLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = request.email + if (request.phone.isNotBlank()) " • ${request.phone}" else "",
                style = MaterialTheme.typography.bodySmall
            )
            val schoolDisplay = request.schoolName.ifBlank { request.schoolHint }
            if (schoolDisplay.isNotBlank()) {
                Text(
                    text = "Établissement demandé : $schoolDisplay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (request.extraDetails.isNotBlank()) {
                Text(
                    text = "Précisions : ${request.extraDetails}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (request.applicantResponse.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Réponse du demandeur : ${request.applicantResponse}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            if (request.decisionNote.isNotBlank()) {
                Text(
                    text = "Dernière remarque : ${request.decisionNote}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onApprove,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("approve_request_${request.userId}")
                ) {
                    Text("Approuver")
                }
                OutlinedButton(
                    onClick = onRequestMoreInfo,
                    modifier = Modifier.testTag("request_info_${request.userId}")
                ) {
                    Text("Infos")
                }
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.testTag("reject_request_${request.userId}")
                ) {
                    Text("Refuser")
                }
            }
        }
    }
}

@Composable
internal fun ApproveAccessRequestDialog(
    actorRole: UserRole = UserRole.ADMIN_NATIONAL,
    actorSchoolId: String = "",
    request: AccessRequest,
    regions: List<Region>,
    schools: List<School>,
    classes: List<SchoolClass> = emptyList(),
    subjects: List<Subject> = emptyList(),
    students: List<Student> = emptyList(),
    onNavigateToTab: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSubmit: (UserRole, String, School?, List<String>, List<String>, List<String>, String) -> Unit
) {
    val allowedRoles = remember(actorRole) {
        if (actorRole == UserRole.DIRECTEUR) {
            listOf(UserRole.PARENT, UserRole.ENSEIGNANT, UserRole.ELEVE)
        } else {
            listOf(
                UserRole.DIRECTEUR,
                UserRole.ADMIN_REGIONAL,
                UserRole.ENSEIGNANT,
                UserRole.PARENT,
                UserRole.ELEVE,
                UserRole.ADMIN_NATIONAL
            )
        }
    }
    var assignedRole by remember(request.userId, actorRole) {
        val reqRole = UserRole.fromKey(request.requestedRole)
        mutableStateOf(if (reqRole in allowedRoles) reqRole else allowedRoles.first())
    }
    val matchedSchool = remember(request.schoolId, request.schoolName, request.schoolHint, schools, actorRole, actorSchoolId) {
        if (actorRole == UserRole.DIRECTEUR && actorSchoolId.isNotBlank()) {
            schools.firstOrNull { it.id == actorSchoolId } ?: schools.firstOrNull()
        } else {
            schools.firstOrNull { request.schoolId.isNotBlank() && it.id == request.schoolId }
                ?: schools.firstOrNull { request.schoolName.isNotBlank() && it.name.equals(request.schoolName, ignoreCase = true) }
                ?: schools.firstOrNull { request.schoolHint.isNotBlank() && request.schoolHint.contains(it.name, ignoreCase = true) }
                ?: schools.firstOrNull {
                    val cleanHint = request.schoolHint.substringBefore("(").trim()
                    cleanHint.isNotBlank() && it.name.contains(cleanHint, ignoreCase = true)
                }
                ?: schools.firstOrNull()
        }
    }
    var selectedSchool by remember(matchedSchool) { mutableStateOf(matchedSchool) }
    var selectedRegion by remember(regions, matchedSchool) {
        mutableStateOf(regions.firstOrNull { it.id == matchedSchool?.regionId } ?: regions.firstOrNull())
    }
    var selectedStudentIds by remember(selectedSchool?.id, assignedRole) { mutableStateOf(emptySet<String>()) }
    var selectedClassIds by remember(selectedSchool?.id, assignedRole) { mutableStateOf(emptySet<String>()) }
    var selectedSubjectIds by remember(selectedSchool?.id, assignedRole) { mutableStateOf(emptySet<String>()) }
    var decisionNote by remember { mutableStateOf("") }

    val schoolStudents = remember(students, selectedSchool) {
        students.filter { selectedSchool == null || it.schoolId == selectedSchool?.id }
    }
    val schoolClasses = remember(classes, selectedSchool) {
        classes.filter { selectedSchool == null || it.schoolId == selectedSchool?.id }
    }
    val candidateSubjects = remember(subjects, selectedSchool, selectedClassIds) {
        subjects.filter {
            (selectedSchool == null || it.schoolId == selectedSchool?.id) &&
                (selectedClassIds.isEmpty() || it.classId in selectedClassIds)
        }
    }

    val schoolRequired = assignedRole == UserRole.DIRECTEUR ||
        assignedRole == UserRole.ENSEIGNANT ||
        assignedRole == UserRole.PARENT ||
        assignedRole == UserRole.ELEVE
    val linkagesValid = when (assignedRole) {
        UserRole.DIRECTEUR -> selectedSchool != null
        UserRole.ENSEIGNANT -> selectedSchool != null && selectedClassIds.isNotEmpty()
        UserRole.PARENT -> selectedSchool != null && selectedStudentIds.isNotEmpty()
        UserRole.ELEVE -> selectedSchool != null
        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Approuver l'inscription de ${request.fullName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "E-mail : ${request.email}" + if (request.phone.isNotBlank()) " • ${request.phone}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val hintSummary = listOf(request.schoolName.ifBlank { request.schoolHint }, request.extraDetails)
                    .filter { it.isNotBlank() }
                    .joinToString(" — ")
                if (hintSummary.isNotBlank()) {
                    Text(
                        text = "Informations déclarées : $hintSummary",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                DropdownSelector(
                    label = "Rôle attribué *",
                    options = allowedRoles,
                    selectedOption = assignedRole,
                    optionLabel = { it.labelFr },
                    onOptionSelected = { assignedRole = it }
                )
                if (assignedRole == UserRole.ADMIN_REGIONAL) {
                    DropdownSelector(
                        label = "Région *",
                        options = regions,
                        selectedOption = selectedRegion,
                        optionLabel = { it.name },
                        onOptionSelected = { selectedRegion = it },
                        emptyLabel = "Aucune région enregistrée"
                    )
                }
                if (schoolRequired) {
                    if (actorRole == UserRole.DIRECTEUR && selectedSchool != null) {
                        Text(
                            text = "Établissement de rattachement : ${selectedSchool?.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        DropdownSelector(
                            label = "Établissement autorisé *",
                            options = schools,
                            selectedOption = selectedSchool,
                            optionLabel = { it.name },
                            onOptionSelected = { selectedSchool = it },
                            emptyLabel = "Aucun établissement autorisé enregistré"
                        )
                    }
                }

                if (assignedRole == UserRole.PARENT) {
                    Text(
                        text = "Enfant(s) rattaché(s) *",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (schoolStudents.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Aucun élève inscrit dans cet établissement. Inscrivez d'abord l'élève avant d'activer un parent.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                if (onNavigateToTab != null) {
                                    OutlinedButton(onClick = {
                                        onDismiss()
                                        onNavigateToTab("Élèves")
                                    }) {
                                        Text("Aller inscrire un élève")
                                    }
                                }
                            }
                        }
                    } else {
                        schoolStudents.forEach { stu ->
                            val checked = stu.id in selectedStudentIds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedStudentIds = if (isChecked) selectedStudentIds + stu.id else selectedStudentIds - stu.id
                                    },
                                    modifier = Modifier.testTag("approve_link_student_${stu.id}")
                                )
                                Text(
                                    text = "${stu.fullName} (${stu.className} • ${stu.matricule})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (assignedRole == UserRole.ENSEIGNANT) {
                    Text(
                        text = "Classe(s) assignée(s) *",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (schoolClasses.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Aucune classe trouvée dans cet établissement. Créez d'abord au moins une classe avant d'activer un enseignant.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                if (onNavigateToTab != null) {
                                    OutlinedButton(onClick = {
                                        onDismiss()
                                        onNavigateToTab("Classes")
                                    }) {
                                        Text("Aller créer une classe")
                                    }
                                }
                            }
                        }
                    } else {
                        schoolClasses.forEach { cls ->
                            val checked = cls.id in selectedClassIds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedClassIds = if (isChecked) selectedClassIds + cls.id else selectedClassIds - cls.id
                                    },
                                    modifier = Modifier.testTag("approve_link_class_${cls.id}")
                                )
                                Text(
                                    text = "${cls.name} (${cls.level})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    if (candidateSubjects.isNotEmpty()) {
                        Text(
                            text = "Matière(s) attribuée(s)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        candidateSubjects.forEach { subj ->
                            val checked = subj.id in selectedSubjectIds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        selectedSubjectIds = if (isChecked) selectedSubjectIds + subj.id else selectedSubjectIds - subj.id
                                    },
                                    modifier = Modifier.testTag("approve_link_subject_${subj.id}")
                                )
                                Text(
                                    text = "${subj.name} (${subj.className})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = decisionNote,
                    onValueChange = { decisionNote = it.take(300) },
                    label = { Text("Note d'approbation (optionnel)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("approve_decision_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val regId = selectedSchool?.regionId ?: selectedRegion?.id ?: ""
                    onSubmit(
                        assignedRole,
                        regId,
                        if (assignedRole == UserRole.ADMIN_REGIONAL || assignedRole == UserRole.ADMIN_NATIONAL) null else selectedSchool,
                        selectedStudentIds.toList(),
                        selectedClassIds.toList(),
                        selectedSubjectIds.toList(),
                        decisionNote.trim()
                    )
                },
                enabled = linkagesValid,
                modifier = Modifier.testTag("confirm_approve_request_button")
            ) {
                Text("Valider et activer l'accès")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
internal fun RequestMoreInfoDialog(
    request: AccessRequest,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Demander des informations à ${request.fullName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Précisez les éléments ou justificatifs attendus (classe de l'enfant, matière, attestation, etc.). Le demandeur pourra modifier et renvoyer sa demande.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("Informations complémentaires attendues *") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("request_info_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(note.trim()) },
                enabled = note.trim().length >= 3,
                modifier = Modifier.testTag("confirm_request_info_button")
            ) {
                Text("Envoyer la demande")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
internal fun RejectAccessRequestDialog(
    request: AccessRequest,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Refuser la demande de ${request.fullName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Indiquez le motif du refus qui sera affiché au demandeur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text("Motif du refus *") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reject_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(reason.trim()) },
                enabled = reason.trim().length >= 3,
                modifier = Modifier.testTag("confirm_reject_request_button")
            ) {
                Text("Confirmer le refus")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
