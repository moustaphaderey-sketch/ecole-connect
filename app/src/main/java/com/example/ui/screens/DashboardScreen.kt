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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Announcement
import com.example.data.model.AssessmentGrade
import com.example.data.model.AttendanceRecord
import com.example.data.model.DifficultyAlert
import com.example.data.model.LessonEntry
import com.example.data.model.NotificationItem
import com.example.data.model.PlatformSettings
import com.example.data.model.Region
import com.example.data.model.ScheduleEntry
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.ChildSelectorBar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoleBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatKpiCard
import com.example.ui.viewmodel.AppSection
import com.example.util.DateUtils
import com.example.util.GradeCalculator
import java.util.Locale

@Composable
fun DashboardScreen(
    profile: UserProfile,
    settings: PlatformSettings?,
    regions: List<Region>,
    schools: List<School>,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    schedules: List<ScheduleEntry>,
    students: List<Student>,
    lessons: List<LessonEntry>,
    assessments: List<AssessmentGrade>,
    attendance: List<AttendanceRecord>,
    difficulties: List<DifficultyAlert>,
    announcements: List<Announcement>,
    notifications: List<NotificationItem>,
    selectedChildId: String?,
    onSelectChild: (String) -> Unit,
    onNavigate: (AppSection) -> Unit,
    onUpdateSettings: (String, Double, Boolean, Boolean) -> Unit
) {
    val role = profile.roleEnum()
    var showSettingsDialog by remember { mutableStateOf(false) }

    val activeChild: Student? = remember(students, selectedChildId) {
        students.firstOrNull { it.id == selectedChildId } ?: students.firstOrNull()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoleBadge(role = role)
                        if (role == UserRole.ADMIN_NATIONAL) {
                            OutlinedButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier.testTag("platform_settings_button")
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Paramètres généraux",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    " Paramètres",
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                    Text(
                        text = "Bonjour, ${profile.fullName}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = when {
                            profile.schoolName.isNotBlank() -> "${profile.schoolName} • Année ${settings?.academicYear ?: "2026-2027"}"
                            role == UserRole.ADMIN_NATIONAL -> "Pilotage national ÉCOLE CONNECTÉE • Année ${settings?.academicYear ?: "2026-2027"}"
                            role == UserRole.ADMIN_REGIONAL -> "Supervision régionale • Année ${settings?.academicYear ?: "2026-2027"}"
                            else -> "Suivi scolaire en temps réel • ${DateUtils.formatFrenchLong(DateUtils.todayIso())}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
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

        // KPIs spécifiques à chaque rôle (strictement calculés sur les données réelles saisies)
        item {
            when (role) {
                UserRole.ADMIN_NATIONAL -> {
                    val avgGlobal = GradeCalculator.computeOverallAverage(
                        GradeCalculator.computeSubjectAverages(assessments, subjects)
                    )
                    val absenceCount = attendance.count { it.status == "absent" }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard("Régions", "${regions.size}", Icons.Filled.Map, modifier = Modifier.weight(1f))
                            StatKpiCard("Établissements", "${schools.size}", Icons.Filled.School, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.secondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard("Élèves inscrits", "${students.size}", Icons.Filled.Groups, modifier = Modifier.weight(1f))
                            StatKpiCard(
                                "Moyenne nationale",
                                avgGlobal?.let { String.format(Locale.FRANCE, "%.2f/20", it) } ?: "—",
                                Icons.Filled.Assessment,
                                modifier = Modifier.weight(1f),
                                accentColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard("Cours enregistrés", "${lessons.size}", Icons.AutoMirrored.Filled.MenuBook, modifier = Modifier.weight(1f))
                            StatKpiCard("Absences signalées", "$absenceCount", Icons.Filled.EventBusy, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                UserRole.ADMIN_REGIONAL -> {
                    val avgRegional = GradeCalculator.computeOverallAverage(
                        GradeCalculator.computeSubjectAverages(assessments, subjects)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard("Établissements", "${schools.size}", Icons.Filled.School, modifier = Modifier.weight(1f))
                            StatKpiCard("Classes actives", "${classes.size}", Icons.Filled.Class, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.secondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard("Élèves suivis", "${students.size}", Icons.Filled.Groups, modifier = Modifier.weight(1f))
                            StatKpiCard(
                                "Moyenne régionale",
                                avgRegional?.let { String.format(Locale.FRANCE, "%.2f/20", it) } ?: "—",
                                Icons.Filled.Assessment,
                                modifier = Modifier.weight(1f),
                                accentColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                UserRole.DIRECTEUR -> {
                    val absenceCount = attendance.count { it.status == "absent" }
                    val openAlerts = difficulties.count { !it.resolved }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard("Classes", "${classes.size}", Icons.Filled.Class, modifier = Modifier.weight(1f))
                            StatKpiCard("Élèves inscrits", "${students.size}", Icons.Filled.Groups, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.secondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard("Matières", "${subjects.size}", Icons.AutoMirrored.Filled.MenuBook, modifier = Modifier.weight(1f))
                            StatKpiCard("Absences / Alertes", "$absenceCount / $openAlerts", Icons.Filled.Warning, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                UserRole.ENSEIGNANT -> {
                    val todayLessons = lessons.count { it.lessonDate == DateUtils.todayIso() }
                    val openAlerts = difficulties.count { !it.resolved }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard("Mes classes", "${classes.size}", Icons.Filled.Class, modifier = Modifier.weight(1f))
                            StatKpiCard("Mes élèves", "${students.size}", Icons.Filled.Groups, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.secondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard("Leçons du jour", "$todayLessons", Icons.AutoMirrored.Filled.MenuBook, modifier = Modifier.weight(1f))
                            StatKpiCard("Alertes ouvertes", "$openAlerts", Icons.Filled.Warning, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                UserRole.PARENT -> {
                    val childLessons = if (activeChild != null) lessons.filter { it.classId == activeChild.classId } else emptyList()
                    val childGrades = if (activeChild != null) assessments.filter { it.studentId == activeChild.id } else emptyList()
                    val childAtt = if (activeChild != null) attendance.filter { it.studentId == activeChild.id } else emptyList()
                    val childAlerts = if (activeChild != null) difficulties.filter { it.studentId == activeChild.id && !it.resolved } else emptyList()
                    val childAvg = GradeCalculator.computeOverallAverage(
                        GradeCalculator.computeSubjectAverages(childGrades, subjects)
                    )
                    val todayLessons = childLessons.count { it.lessonDate == DateUtils.todayIso() }
                    val absences = childAtt.count { it.status == "absent" }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard(
                                "Moyenne générale",
                                childAvg?.let { String.format(Locale.FRANCE, "%.2f/20", it) } ?: "—",
                                Icons.Filled.Assessment,
                                modifier = Modifier.weight(1f)
                            )
                            StatKpiCard("Cours du jour", "$todayLessons", Icons.AutoMirrored.Filled.MenuBook, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.secondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard("Absences", "$absences", Icons.Filled.EventBusy, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.error)
                            StatKpiCard("Alertes actives", "${childAlerts.size}", Icons.Filled.NotificationsActive, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }

                UserRole.ELEVE -> {
                    val studentAvg = GradeCalculator.computeOverallAverage(
                        GradeCalculator.computeSubjectAverages(assessments, subjects)
                    )
                    val homeworks = lessons.count { it.homework.isNotBlank() }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard("Leçons disponibles", "${lessons.size}", Icons.AutoMirrored.Filled.MenuBook, modifier = Modifier.weight(1f))
                            StatKpiCard("Devoirs à faire", "$homeworks", Icons.Filled.Schedule, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.secondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            StatKpiCard(
                                "Ma moyenne",
                                studentAvg?.let { String.format(Locale.FRANCE, "%.2f/20", it) } ?: "—",
                                Icons.Filled.Assessment,
                                modifier = Modifier.weight(1f)
                            )
                            StatKpiCard("Créneaux cours", "${schedules.size}", Icons.Filled.Schedule, modifier = Modifier.weight(1f), accentColor = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }

        // Actions rapides
        item {
            SectionHeader(
                title = "Accès rapide",
                subtitle = "Modules autorisés pour votre rôle",
                icon = Icons.AutoMirrored.Filled.MenuBook
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onNavigate(AppSection.CAHIER_DE_TEXTE) },
                    modifier = Modifier.weight(1f).testTag("quick_cahier_button")
                ) {
                    Text("Cahier de texte")
                }
                OutlinedButton(
                    onClick = { onNavigate(AppSection.NOTES_ET_BULLETINS) },
                    modifier = Modifier.weight(1f).testTag("quick_notes_button")
                ) {
                    Text("Notes & Bulletins")
                }
            }
            if (role == UserRole.ADMIN_NATIONAL || role == UserRole.ADMIN_REGIONAL || role == UserRole.DIRECTEUR) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onNavigate(AppSection.ADMINISTRATION) },
                    modifier = Modifier.fillMaxWidth().testTag("quick_admin_button")
                ) {
                    Text("Gérer les établissements, classes & utilisateurs")
                }
            }
        }

        // Dernières leçons du cahier de texte
        item {
            SectionHeader(
                title = "Dernières leçons enregistrées",
                subtitle = "Cahier de texte numérique en temps réel",
                icon = Icons.AutoMirrored.Filled.MenuBook
            )
        }

        val recentLessons = if (role == UserRole.PARENT && activeChild != null) {
            lessons.filter { it.classId == activeChild.classId }.sortedByDescending { it.lessonDate }.take(3)
        } else {
            lessons.sortedByDescending { it.lessonDate }.take(3)
        }

        if (recentLessons.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = "Aucun cours enregistré.",
                    subtitle = "Les leçons, chapitres, exercices et devoirs apparaîtront ici dès leur saisie par les enseignants."
                )
            }
        } else {
            items(recentLessons, key = { it.id }) { lesson ->
                ElevatedCard(onClick = { onNavigate(AppSection.CAHIER_DE_TEXTE) }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = "${lesson.subjectName} • ${lesson.className}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = DateUtils.formatFrenchShort(lesson.lessonDate),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(lesson.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Chapitre : ${lesson.chapter}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (lesson.homework.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "Devoir : ${lesson.homework}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Annonces
        item {
            SectionHeader(
                title = "Annonces récentes",
                subtitle = "Communications officielles",
                icon = Icons.Filled.Campaign
            )
        }

        if (announcements.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Campaign,
                    title = "Aucune annonce publiée.",
                    subtitle = "Les communications de la direction et du ministère s'afficheront ici."
                )
            }
        } else {
            items(announcements.take(3), key = { it.id }) { ann ->
                ElevatedCard(onClick = { onNavigate(AppSection.MESSAGERIE) }) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ann.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(ann.content, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                        Text("Par ${ann.authorName} (${ann.priority})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            val unreadCount = notifications.count { !it.read }
            if (unreadCount > 0) {
                Surface(
                    onClick = { onNavigate(AppSection.NOTIFICATIONS) },
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            text = "Vous avez $unreadCount notification(s) non lue(s). Appuyez pour consulter.",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSettingsDialog) {
        PlatformSettingsDialog(
            settings = settings,
            onDismiss = { showSettingsDialog = false },
            onSave = { year, scale, sms, wa ->
                onUpdateSettings(year, scale, sms, wa)
                showSettingsDialog = false
            }
        )
    }
}

@Composable
private fun PlatformSettingsDialog(
    settings: PlatformSettings?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Boolean, Boolean) -> Unit
) {
    var academicYear by remember { mutableStateOf(settings?.academicYear ?: "2026-2027") }
    var scaleText by remember { mutableStateOf((settings?.gradingScale ?: 20.0).toInt().toString()) }
    var smsEnabled by remember { mutableStateOf(settings?.smsGatewayEnabled ?: false) }
    var whatsappEnabled by remember { mutableStateOf(settings?.whatsappGatewayEnabled ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paramètres généraux nationaux") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = academicYear,
                    onValueChange = { academicYear = it },
                    label = { Text("Année scolaire en cours") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = scaleText,
                    onValueChange = { scaleText = it },
                    label = { Text("Barème national par défaut (ex: 20)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Passerelle SMS configurée", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = smsEnabled, onCheckedChange = { smsEnabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("WhatsApp Business configuré", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = whatsappEnabled, onCheckedChange = { whatsappEnabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val scale = scaleText.toDoubleOrNull()?.coerceIn(10.0, 100.0) ?: 20.0
                    onSave(academicYear, scale, smsEnabled, whatsappEnabled)
                }
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
