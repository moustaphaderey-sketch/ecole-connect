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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
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
import com.example.data.model.AttendanceRecord
import com.example.data.model.DifficultyAlert
import com.example.data.model.School
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.TeacherAttendanceRecord
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.ChildSelectorBar
import com.example.ui.components.DropdownSelector
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatKpiCard
import com.example.ui.components.TabChipsRow
import com.example.util.DateUtils

@Composable
fun AttendanceAndAlertsScreen(
    profile: UserProfile,
    students: List<Student>,
    subjects: List<Subject>,
    attendance: List<AttendanceRecord>,
    difficulties: List<DifficultyAlert>,
    selectedChildId: String?,
    onSelectChild: (String) -> Unit,
    onRecordAttendance: (
        student: Student,
        date: String,
        status: String,
        minutesLate: Int,
        justified: Boolean,
        reason: String
    ) -> Unit,
    onReportDifficulty: (
        student: Student,
        subject: Subject?,
        category: String,
        severity: String,
        observation: String,
        recommendation: String
    ) -> Unit,
    onResolveDifficulty: (difficultyId: String, resolved: Boolean) -> Unit,
    schools: List<School> = emptyList(),
    managedUsers: List<UserProfile> = emptyList(),
    teacherAttendance: List<TeacherAttendanceRecord> = emptyList(),
    onRecordTeacherAttendance: (
        school: School,
        teacher: UserProfile,
        date: String,
        status: String,
        minutesLate: Int,
        justified: Boolean,
        reason: String
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onUpdateTeacherAttendance: (
        record: TeacherAttendanceRecord,
        date: String,
        status: String,
        minutesLate: Int,
        justified: Boolean,
        reason: String
    ) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteTeacherAttendance: (TeacherAttendanceRecord) -> Unit = {},
    onUpdateAttendance: (
        record: AttendanceRecord,
        date: String,
        status: String,
        minutesLate: Int,
        justified: Boolean,
        reason: String
    ) -> Unit = { _, _, _, _, _, _ -> },
    onUpdateDifficulty: (
        alert: DifficultyAlert,
        category: String,
        severity: String,
        observation: String,
        recommendation: String,
        resolved: Boolean
    ) -> Unit = { _, _, _, _, _, _ -> }
) {
    val role = profile.roleEnum()
    val canRecord = role == UserRole.ENSEIGNANT || role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
    val canManageTeacherAttendance = role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
    val canViewTeacherAttendance = canManageTeacherAttendance || role == UserRole.ENSEIGNANT
    val tabs = remember(canViewTeacherAttendance) {
        if (canViewTeacherAttendance) {
            listOf("Présences & Retards", "Difficultés & Observations", "Présences enseignants")
        } else {
            listOf("Présences & Retards", "Difficultés & Observations")
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAttendanceDialog by remember { mutableStateOf(false) }
    var showDifficultyDialog by remember { mutableStateOf(false) }
    var showTeacherAttendanceDialog by remember { mutableStateOf(false) }
    var editingAttendance by remember { mutableStateOf<AttendanceRecord?>(null) }
    var editingDifficulty by remember { mutableStateOf<DifficultyAlert?>(null) }
    var editingTeacherAttendance by remember { mutableStateOf<TeacherAttendanceRecord?>(null) }

    val activeChild = remember(students, selectedChildId) {
        students.firstOrNull { it.id == selectedChildId } ?: students.firstOrNull()
    }

    val scopedAttendance = remember(attendance, role, activeChild) {
        if (role == UserRole.PARENT && activeChild != null) {
            attendance.filter { it.studentId == activeChild.id }
        } else {
            attendance
        }.sortedByDescending { it.date }
    }

    val scopedDifficulties = remember(difficulties, role, activeChild) {
        if (role == UserRole.PARENT && activeChild != null) {
            difficulties.filter { it.studentId == activeChild.id }
        } else {
            difficulties
        }
    }

    val sortedTeacherAttendance = remember(teacherAttendance) {
        teacherAttendance.sortedByDescending { it.date }
    }

    val fabVisible = when (selectedTab) {
        0, 1 -> canRecord
        2 -> canManageTeacherAttendance
        else -> false
    }

    Scaffold(
        floatingActionButton = {
            if (fabVisible) {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            0 -> showAttendanceDialog = true
                            1 -> showDifficultyDialog = true
                            2 -> showTeacherAttendanceDialog = true
                        }
                    },
                    containerColor = if (selectedTab == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag(
                        when (selectedTab) {
                            0 -> "add_attendance_fab"
                            1 -> "add_difficulty_fab"
                            else -> "add_teacher_attendance_fab"
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (selectedTab == 1) Icons.Filled.Warning else Icons.Filled.Add,
                        contentDescription = when (selectedTab) {
                            0 -> "Enregistrer une présence ou absence"
                            1 -> "Signaler une difficulté"
                            else -> "Enregistrer présence enseignant"
                        }
                    )
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
                    title = "Présences, Retards & Difficultés",
                    subtitle = "Suivi de l'assiduité et détection précoce des difficultés scolaires",
                    icon = Icons.Filled.EventBusy
                )
            }

            if (role == UserRole.PARENT && students.isNotEmpty()) {
                item {
                    ChildSelectorBar(
                        students = students,
                        selectedChildId = activeChild?.id,
                        onSelectChild = onSelectChild
                    )
                }
            }

            item {
                TabChipsRow(
                    tabs = tabs,
                    selectedIndex = selectedTab.coerceIn(0, tabs.lastIndex),
                    onSelectTab = { selectedTab = it }
                )
            }

            if (selectedTab == 0) {
                val absCount = scopedAttendance.count { it.status == "absent" }
                val lateCount = scopedAttendance.count { it.status == "retard" }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatKpiCard(
                            label = "Absences",
                            value = "$absCount",
                            icon = Icons.Filled.EventBusy,
                            accentColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        StatKpiCard(
                            label = "Retards",
                            value = "$lateCount",
                            icon = Icons.Filled.Schedule,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (scopedAttendance.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.EventBusy,
                            title = "Aucune absence enregistrée.",
                            subtitle = if (canRecord) {
                                "Utilisez le bouton + pour enregistrer les présences, absences ou retards d'un élève."
                            } else {
                                "Les absences et retards signalés par les enseignants apparaîtront ici."
                            }
                        )
                    }
                } else {
                    items(scopedAttendance, key = { it.id }) { rec ->
                        val canEditRec = canRecord && (role != UserRole.ENSEIGNANT || rec.teacherUid == profile.userId)
                        AttendanceCard(
                            record = rec,
                            canEdit = canEditRec,
                            onEdit = { editingAttendance = rec }
                        )
                    }
                }
            } else if (selectedTab == 1) {
                if (scopedDifficulties.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Warning,
                            title = "Aucune difficulté signalée.",
                            subtitle = if (canRecord) {
                                "Signalez une difficulté pédagogique ou comportementale pour alerter le parent et la direction."
                            } else {
                                "Les observations et alertes pédagogiques des enseignants s'afficheront ici."
                            }
                        )
                    }
                } else {
                    items(scopedDifficulties, key = { it.id }) { alert ->
                        val canEditAlert = canRecord && (role != UserRole.ENSEIGNANT || alert.teacherUid == profile.userId)
                        DifficultyAlertCard(
                            alert = alert,
                            canResolve = canRecord,
                            canEdit = canEditAlert,
                            onToggleResolved = { onResolveDifficulty(alert.id, !alert.resolved) },
                            onEdit = { editingDifficulty = alert }
                        )
                    }
                }
            } else {
                val teacherAbs = sortedTeacherAttendance.count { it.status == "absent" }
                val teacherLate = sortedTeacherAttendance.count { it.status == "retard" }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatKpiCard(
                            label = "Absences ens.",
                            value = "$teacherAbs",
                            icon = Icons.Filled.Person,
                            accentColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        StatKpiCard(
                            label = "Retards ens.",
                            value = "$teacherLate",
                            icon = Icons.Filled.Schedule,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (sortedTeacherAttendance.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Person,
                            title = "Aucune présence enseignant enregistrée.",
                            subtitle = if (canManageTeacherAttendance) {
                                "Appuyez sur + pour enregistrer la présence, l'absence ou le retard d'un enseignant."
                            } else {
                                "Vos relevés de présence et ponctualité enregistrés par la direction s'afficheront ici."
                            }
                        )
                    }
                } else {
                    items(sortedTeacherAttendance, key = { it.id }) { rec ->
                        TeacherAttendanceCard(
                            record = rec,
                            canManage = canManageTeacherAttendance,
                            onEdit = { editingTeacherAttendance = rec },
                            onDelete = { onDeleteTeacherAttendance(rec) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showAttendanceDialog) {
        RecordAttendanceDialog(
            students = students,
            onDismiss = { showAttendanceDialog = false },
            onSubmit = { stu, date, status, mins, justified, reason ->
                if (stu != null) {
                    onRecordAttendance(stu, date, status, mins, justified, reason)
                }
                showAttendanceDialog = false
            }
        )
    }

    if (editingAttendance != null) {
        val target = editingAttendance!!
        RecordAttendanceDialog(
            students = students,
            existingRecord = target,
            onDismiss = { editingAttendance = null },
            onSubmit = { _, date, status, mins, justified, reason ->
                onUpdateAttendance(target, date, status, mins, justified, reason)
                editingAttendance = null
            }
        )
    }

    if (showDifficultyDialog) {
        ReportDifficultyDialog(
            students = students,
            subjects = subjects,
            onDismiss = { showDifficultyDialog = false },
            onSubmit = { stu, subj, cat, sev, obs, rec, _ ->
                if (stu != null) {
                    onReportDifficulty(stu, subj, cat, sev, obs, rec)
                }
                showDifficultyDialog = false
            }
        )
    }

    if (editingDifficulty != null) {
        val target = editingDifficulty!!
        ReportDifficultyDialog(
            students = students,
            subjects = subjects,
            existingAlert = target,
            onDismiss = { editingDifficulty = null },
            onSubmit = { _, _, cat, sev, obs, rec, resolved ->
                onUpdateDifficulty(target, cat, sev, obs, rec, resolved)
                editingDifficulty = null
            }
        )
    }

    if (showTeacherAttendanceDialog) {
        RecordTeacherAttendanceDialog(
            schools = schools,
            managedUsers = managedUsers,
            onDismiss = { showTeacherAttendanceDialog = false },
            onSubmit = { school, teacher, date, status, mins, justified, reason ->
                if (school != null && teacher != null) {
                    onRecordTeacherAttendance(school, teacher, date, status, mins, justified, reason)
                }
                showTeacherAttendanceDialog = false
            }
        )
    }

    if (editingTeacherAttendance != null) {
        val target = editingTeacherAttendance!!
        RecordTeacherAttendanceDialog(
            schools = schools,
            managedUsers = managedUsers,
            existingRecord = target,
            onDismiss = { editingTeacherAttendance = null },
            onSubmit = { _, _, date, status, mins, justified, reason ->
                onUpdateTeacherAttendance(target, date, status, mins, justified, reason)
                editingTeacherAttendance = null
            }
        )
    }
}

@Composable
private fun AttendanceCard(
    record: AttendanceRecord,
    canEdit: Boolean,
    onEdit: () -> Unit
) {
    val badgeColor = when (record.status) {
        "absent" -> MaterialTheme.colorScheme.errorContainer
        "retard" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val textColor = when (record.status) {
        "absent" -> MaterialTheme.colorScheme.error
        "retard" -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val statusLabel = when (record.status) {
        "absent" -> if (record.justified) "Absent (justifié)" else "Absent (non justifié)"
        "retard" -> "Retard (${record.minutesLate} min)"
        else -> "Présent"
    }

    ElevatedCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${record.studentName} • ${record.className}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(color = badgeColor, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (canEdit) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.testTag("edit_attendance_${record.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Modifier l'enregistrement de présence",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Text(
                text = "Date : ${DateUtils.formatFrenchShort(record.date)} • Enregistré par ${record.teacherName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (record.reason.isNotBlank()) {
                Text(text = "Motif : ${record.reason}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DifficultyAlertCard(
    alert: DifficultyAlert,
    canResolve: Boolean,
    canEdit: Boolean,
    onToggleResolved: () -> Unit,
    onEdit: () -> Unit
) {
    val severityLabel = when (alert.severity) {
        "urgent" -> "Urgent"
        "attention" -> "Attention requise"
        else -> "Information"
    }
    val severityBg = when {
        alert.resolved -> MaterialTheme.colorScheme.primaryContainer
        alert.severity == "urgent" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    ElevatedCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(alert.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = listOfNotNull(
                            alert.className.takeIf { it.isNotBlank() },
                            alert.subjectName.takeIf { it.isNotBlank() },
                            alert.category.replaceFirstChar { it.uppercase() }
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(color = severityBg, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = if (alert.resolved) "Résolu" else severityLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (canEdit) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.testTag("edit_difficulty_${alert.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Modifier le signalement",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Text(alert.observation, style = MaterialTheme.typography.bodyMedium)
            if (alert.recommendation.isNotBlank()) {
                Text(
                    text = "Recommandation : ${alert.recommendation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = "Signalé par ${alert.teacherName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canResolve) {
                OutlinedButton(
                    onClick = onToggleResolved,
                    modifier = Modifier.fillMaxWidth().testTag("resolve_difficulty_${alert.id}")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(if (alert.resolved) "  Réouvrir le signalement" else "  Marquer comme résolu")
                }
            }
        }
    }
}

@Composable
private fun RecordAttendanceDialog(
    students: List<Student>,
    existingRecord: AttendanceRecord? = null,
    onDismiss: () -> Unit,
    onSubmit: (Student?, String, String, Int, Boolean, String) -> Unit
) {
    val isEdit = existingRecord != null
    var selectedStudent by remember(existingRecord?.id) {
        mutableStateOf(students.firstOrNull { it.id == existingRecord?.studentId } ?: students.firstOrNull())
    }
    var date by remember(existingRecord?.id) { mutableStateOf(existingRecord?.date ?: DateUtils.todayIso()) }
    var status by remember(existingRecord?.id) { mutableStateOf(existingRecord?.status ?: "absent") }
    var minutesLateText by remember(existingRecord?.id) {
        mutableStateOf((existingRecord?.minutesLate?.takeIf { it > 0 } ?: 10).toString())
    }
    var justified by remember(existingRecord?.id) { mutableStateOf(existingRecord?.justified ?: false) }
    var reason by remember(existingRecord?.id) { mutableStateOf(existingRecord?.reason ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Modifier présence / absence" else "Enregistrer présence / absence") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isEdit && existingRecord != null) {
                    Text(
                        text = "${existingRecord.studentName} (${existingRecord.className})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    DropdownSelector(
                        label = "Élève",
                        options = students,
                        selectedOption = selectedStudent,
                        optionLabel = { "${it.fullName} (${it.className})" },
                        onOptionSelected = { selectedStudent = it },
                        emptyLabel = "Aucun élève inscrit"
                    )
                }
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (AAAA-MM-JJ)") },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelector(
                    label = "Statut",
                    options = listOf("present", "absent", "retard"),
                    selectedOption = status,
                    optionLabel = {
                        when (it) {
                            "absent" -> "Absent"
                            "retard" -> "En retard"
                            else -> "Présent"
                        }
                    },
                    onOptionSelected = { status = it }
                )
                if (status == "retard") {
                    OutlinedTextField(
                        value = minutesLateText,
                        onValueChange = { minutesLateText = it },
                        label = { Text("Minutes de retard") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Motif justifié")
                    Switch(checked = justified, onCheckedChange = { justified = it })
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motif / remarque") },
                    modifier = Modifier.fillMaxWidth().testTag("attendance_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = if (status == "retard") (minutesLateText.toIntOrNull() ?: 5).coerceIn(1, 480) else 0
                    if (isEdit || selectedStudent != null) {
                        onSubmit(selectedStudent, date, status, mins, justified, reason)
                    }
                },
                enabled = (isEdit || selectedStudent != null) && date.trim().length == 10,
                modifier = Modifier.testTag("save_attendance_button")
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun ReportDifficultyDialog(
    students: List<Student>,
    subjects: List<Subject>,
    existingAlert: DifficultyAlert? = null,
    onDismiss: () -> Unit,
    onSubmit: (Student?, Subject?, String, String, String, String, Boolean) -> Unit
) {
    val isEdit = existingAlert != null
    var selectedStudent by remember(existingAlert?.id) {
        mutableStateOf(students.firstOrNull { it.id == existingAlert?.studentId } ?: students.firstOrNull())
    }
    val classSubjects = remember(subjects, selectedStudent) {
        subjects.filter { selectedStudent == null || it.classId == selectedStudent!!.classId }
    }
    var selectedSubject by remember(classSubjects, existingAlert?.id) {
        mutableStateOf(classSubjects.firstOrNull { it.id == existingAlert?.subjectId } ?: classSubjects.firstOrNull())
    }
    var category by remember(existingAlert?.id) { mutableStateOf(existingAlert?.category ?: "comprehension") }
    var severity by remember(existingAlert?.id) { mutableStateOf(existingAlert?.severity ?: "attention") }
    var observation by remember(existingAlert?.id) { mutableStateOf(existingAlert?.observation ?: "") }
    var recommendation by remember(existingAlert?.id) { mutableStateOf(existingAlert?.recommendation ?: "") }
    var resolved by remember(existingAlert?.id) { mutableStateOf(existingAlert?.resolved ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Modifier le signalement pédagogique" else "Signaler une difficulté scolaire") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isEdit && existingAlert != null) {
                    Text(
                        text = "${existingAlert.studentName} (${existingAlert.className})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    DropdownSelector(
                        label = "Élève",
                        options = students,
                        selectedOption = selectedStudent,
                        optionLabel = { "${it.fullName} (${it.className})" },
                        onOptionSelected = { selectedStudent = it },
                        emptyLabel = "Aucun élève inscrit"
                    )
                    DropdownSelector(
                        label = "Matière (optionnel)",
                        options = classSubjects,
                        selectedOption = selectedSubject,
                        optionLabel = { it.name },
                        onOptionSelected = { selectedSubject = it },
                        emptyLabel = "Aucune matière"
                    )
                }
                DropdownSelector(
                    label = "Catégorie",
                    options = listOf("comprehension", "attention", "comportement", "assiduite", "methode", "autre"),
                    selectedOption = category,
                    optionLabel = {
                        when (it) {
                            "comprehension" -> "Compréhension du cours"
                            "attention" -> "Attention en classe"
                            "comportement" -> "Comportement"
                            "assiduite" -> "Assiduité"
                            "methode" -> "Méthode de travail"
                            else -> "Autre"
                        }
                    },
                    onOptionSelected = { category = it }
                )
                DropdownSelector(
                    label = "Niveau d'alerte",
                    options = listOf("info", "attention", "urgent"),
                    selectedOption = severity,
                    optionLabel = {
                        when (it) {
                            "urgent" -> "Urgent"
                            "attention" -> "Attention requise"
                            else -> "Information"
                        }
                    },
                    onOptionSelected = { severity = it }
                )
                OutlinedTextField(
                    value = observation,
                    onValueChange = { observation = it },
                    label = { Text("Observation détaillée *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("difficulty_observation_input")
                )
                OutlinedTextField(
                    value = recommendation,
                    onValueChange = { recommendation = it },
                    label = { Text("Recommandation aux parents") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isEdit) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Signalement résolu")
                        Switch(checked = resolved, onCheckedChange = { resolved = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isEdit || selectedStudent != null) {
                        onSubmit(selectedStudent, selectedSubject, category, severity, observation, recommendation, resolved)
                    }
                },
                enabled = (isEdit || selectedStudent != null) && observation.trim().length >= 3,
                modifier = Modifier.testTag("save_difficulty_button")
            ) {
                Text(if (isEdit) "Enregistrer" else "Transmettre")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun TeacherAttendanceCard(
    record: TeacherAttendanceRecord,
    canManage: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val badgeColor = when (record.status) {
        "absent" -> MaterialTheme.colorScheme.errorContainer
        "retard" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val textColor = when (record.status) {
        "absent" -> MaterialTheme.colorScheme.error
        "retard" -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val statusLabel = when (record.status) {
        "absent" -> if (record.justified) "Absent (justifié)" else "Absent (non justifié)"
        "retard" -> "Retard (${record.minutesLate} min)"
        else -> "Présent"
    }

    ElevatedCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.teacherName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(color = badgeColor, shape = MaterialTheme.shapes.small) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (canManage) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.testTag("edit_teacher_attendance_${record.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Modifier la présence enseignant",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("delete_teacher_attendance_${record.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Supprimer la présence enseignant",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            Text(
                text = "Date : ${DateUtils.formatFrenchShort(record.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (record.reason.isNotBlank()) {
                Text(text = "Motif : ${record.reason}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RecordTeacherAttendanceDialog(
    schools: List<School>,
    managedUsers: List<UserProfile>,
    existingRecord: TeacherAttendanceRecord? = null,
    onDismiss: () -> Unit,
    onSubmit: (School?, UserProfile?, String, String, Int, Boolean, String) -> Unit
) {
    val isEdit = existingRecord != null
    val teachers = remember(managedUsers) {
        managedUsers.filter { it.roleEnum() == UserRole.ENSEIGNANT && it.isActive }
    }
    var selectedSchool by remember(schools, existingRecord?.id) {
        mutableStateOf(schools.firstOrNull { it.id == existingRecord?.schoolId } ?: schools.firstOrNull())
    }
    val schoolTeachers = remember(teachers, selectedSchool) {
        if (selectedSchool == null) teachers
        else teachers.filter { it.schoolId.isBlank() || it.schoolId == selectedSchool!!.id }
    }
    var selectedTeacher by remember(schoolTeachers, existingRecord?.id) {
        mutableStateOf(
            schoolTeachers.firstOrNull { it.userId == existingRecord?.teacherUid }
                ?: schoolTeachers.firstOrNull()
        )
    }
    var date by remember(existingRecord?.id) { mutableStateOf(existingRecord?.date ?: DateUtils.todayIso()) }
    var status by remember(existingRecord?.id) { mutableStateOf(existingRecord?.status ?: "absent") }
    var minutesLateText by remember(existingRecord?.id) {
        mutableStateOf((existingRecord?.minutesLate?.takeIf { it > 0 } ?: 10).toString())
    }
    var justified by remember(existingRecord?.id) { mutableStateOf(existingRecord?.justified ?: false) }
    var reason by remember(existingRecord?.id) { mutableStateOf(existingRecord?.reason ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Modifier présence enseignant" else "Présence / absence enseignant") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isEdit && existingRecord != null) {
                    Text(
                        text = existingRecord.teacherName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    if (schools.size > 1) {
                        DropdownSelector(
                            label = "Établissement *",
                            options = schools,
                            selectedOption = selectedSchool,
                            optionLabel = { it.name },
                            onOptionSelected = { selectedSchool = it },
                            emptyLabel = "Aucun établissement"
                        )
                    }
                    DropdownSelector(
                        label = "Enseignant *",
                        options = schoolTeachers,
                        selectedOption = selectedTeacher,
                        optionLabel = { it.fullName },
                        onOptionSelected = { selectedTeacher = it },
                        emptyLabel = "Aucun enseignant actif"
                    )
                }
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (AAAA-MM-JJ)") },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelector(
                    label = "Statut",
                    options = listOf("present", "absent", "retard"),
                    selectedOption = status,
                    optionLabel = {
                        when (it) {
                            "absent" -> "Absent"
                            "retard" -> "En retard"
                            else -> "Présent"
                        }
                    },
                    onOptionSelected = { status = it }
                )
                if (status == "retard") {
                    OutlinedTextField(
                        value = minutesLateText,
                        onValueChange = { minutesLateText = it },
                        label = { Text("Minutes de retard") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Motif justifié")
                    Switch(checked = justified, onCheckedChange = { justified = it })
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motif / observation") },
                    modifier = Modifier.fillMaxWidth().testTag("teacher_attendance_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = if (status == "retard") (minutesLateText.toIntOrNull() ?: 5).coerceIn(1, 480) else 0
                    if (isEdit || (selectedSchool != null && selectedTeacher != null)) {
                        onSubmit(selectedSchool, selectedTeacher, date, status, mins, justified, reason)
                    }
                },
                enabled = (isEdit || (selectedSchool != null && selectedTeacher != null)) && date.trim().length == 10,
                modifier = Modifier.testTag("save_teacher_attendance_button")
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

