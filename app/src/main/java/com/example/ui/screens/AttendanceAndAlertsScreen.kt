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
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
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
import com.example.data.model.AttendanceRecord
import com.example.data.model.DifficultyAlert
import com.example.data.model.Student
import com.example.data.model.Subject
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
    onResolveDifficulty: (difficultyId: String, resolved: Boolean) -> Unit
) {
    val role = profile.roleEnum()
    val canRecord = role == UserRole.ENSEIGNANT || role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAttendanceDialog by remember { mutableStateOf(false) }
    var showDifficultyDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        floatingActionButton = {
            if (canRecord) {
                FloatingActionButton(
                    onClick = {
                        if (selectedTab == 0) showAttendanceDialog = true
                        else showDifficultyDialog = true
                    },
                    containerColor = if (selectedTab == 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag(if (selectedTab == 0) "add_attendance_fab" else "add_difficulty_fab")
                ) {
                    Icon(
                        imageVector = if (selectedTab == 0) Icons.Filled.Add else Icons.Filled.Warning,
                        contentDescription = if (selectedTab == 0) "Enregistrer une présence ou absence" else "Signaler une difficulté"
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
                    tabs = listOf("Présences & Retards", "Difficultés & Observations"),
                    selectedIndex = selectedTab,
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
                        AttendanceCard(rec)
                    }
                }
            } else {
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
                        DifficultyAlertCard(
                            alert = alert,
                            canResolve = canRecord,
                            onToggleResolved = { onResolveDifficulty(alert.id, !alert.resolved) }
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
                onRecordAttendance(stu, date, status, mins, justified, reason)
                showAttendanceDialog = false
            }
        )
    }

    if (showDifficultyDialog) {
        ReportDifficultyDialog(
            students = students,
            subjects = subjects,
            onDismiss = { showDifficultyDialog = false },
            onSubmit = { stu, subj, cat, sev, obs, rec ->
                onReportDifficulty(stu, subj, cat, sev, obs, rec)
                showDifficultyDialog = false
            }
        )
    }
}

@Composable
private fun AttendanceCard(record: AttendanceRecord) {
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
                Surface(color = badgeColor, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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
    onToggleResolved: () -> Unit
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
                Surface(color = severityBg, shape = MaterialTheme.shapes.small) {
                    Text(
                        text = if (alert.resolved) "Résolu" else severityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
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
    onDismiss: () -> Unit,
    onSubmit: (Student, String, String, Int, Boolean, String) -> Unit
) {
    var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
    var date by remember { mutableStateOf(DateUtils.todayIso()) }
    var status by remember { mutableStateOf("absent") }
    var minutesLateText by remember { mutableStateOf("10") }
    var justified by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enregistrer présence / absence") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropdownSelector(
                    label = "Élève",
                    options = students,
                    selectedOption = selectedStudent,
                    optionLabel = { "${it.fullName} (${it.className})" },
                    onOptionSelected = { selectedStudent = it },
                    emptyLabel = "Aucun élève inscrit"
                )
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val stu = selectedStudent ?: return@Button
                    val mins = if (status == "retard") (minutesLateText.toIntOrNull() ?: 5).coerceIn(1, 480) else 0
                    onSubmit(stu, date, status, mins, justified, reason)
                },
                enabled = selectedStudent != null && date.length == 10,
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
    onDismiss: () -> Unit,
    onSubmit: (Student, Subject?, String, String, String, String) -> Unit
) {
    var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
    val classSubjects = remember(subjects, selectedStudent) {
        subjects.filter { selectedStudent == null || it.classId == selectedStudent!!.classId }
    }
    var selectedSubject by remember(classSubjects) { mutableStateOf(classSubjects.firstOrNull()) }
    var category by remember { mutableStateOf("comprehension") }
    var severity by remember { mutableStateOf("attention") }
    var observation by remember { mutableStateOf("") }
    var recommendation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signaler une difficulté scolaire") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val stu = selectedStudent ?: return@Button
                    onSubmit(stu, selectedSubject, category, severity, observation, recommendation)
                },
                enabled = selectedStudent != null && observation.trim().length >= 3,
                modifier = Modifier.testTag("save_difficulty_button")
            ) {
                Text("Transmettre")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
