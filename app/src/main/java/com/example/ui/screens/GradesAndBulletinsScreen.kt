package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AssessmentGrade
import com.example.data.model.AttendanceRecord
import com.example.data.model.ReportCard
import com.example.data.model.School
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.ChildSelectorBar
import com.example.ui.components.DropdownSelector
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.TabChipsRow
import com.example.util.DateUtils
import com.example.util.GradeCalculator
import com.example.util.PdfBulletinGenerator
import java.util.Locale

@Composable
fun GradesAndBulletinsScreen(
    profile: UserProfile,
    schools: List<School>,
    students: List<Student>,
    subjects: List<Subject>,
    assessments: List<AssessmentGrade>,
    attendance: List<AttendanceRecord>,
    reportCards: List<ReportCard>,
    selectedChildId: String?,
    onSelectChild: (String) -> Unit,
    onRecordAssessment: (
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
    ) -> Unit,
    onGenerateReportCard: (
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
    ) -> Unit
) {
    val context = LocalContext.current
    val role = profile.roleEnum()
    val canAddGrade = role == UserRole.ENSEIGNANT || role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
    val canGenerateBulletin = role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedPeriod by remember { mutableStateOf("Trimestre 1") }
    var showGradeDialog by remember { mutableStateOf(false) }
    var showBulletinDialog by remember { mutableStateOf(false) }

    val activeChild = remember(students, selectedChildId) {
        students.firstOrNull { it.id == selectedChildId } ?: students.firstOrNull()
    }

    val scopedGrades = remember(assessments, role, activeChild, selectedPeriod) {
        assessments.filter { g ->
            val childMatch = if (role == UserRole.PARENT && activeChild != null) g.studentId == activeChild.id else true
            childMatch && g.period == selectedPeriod
        }.sortedByDescending { it.date }
    }

    val subjectAverages = remember(scopedGrades, subjects, selectedPeriod) {
        GradeCalculator.computeSubjectAverages(scopedGrades, subjects, selectedPeriod)
    }
    val overallAvg = remember(subjectAverages) {
        GradeCalculator.computeOverallAverage(subjectAverages)
    }

    val scopedBulletins = remember(reportCards, role, activeChild) {
        if (role == UserRole.PARENT && activeChild != null) {
            reportCards.filter { it.studentId == activeChild.id }
        } else {
            reportCards
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0 && canAddGrade) {
                FloatingActionButton(
                    onClick = { showGradeDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag("add_grade_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Saisir une note")
                }
            } else if (selectedTab == 2 && canGenerateBulletin) {
                FloatingActionButton(
                    onClick = { showBulletinDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("generate_bulletin_fab")
                ) {
                    Icon(Icons.Filled.Description, contentDescription = "Générer un bulletin")
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
                    title = "Évaluations, Moyennes & Bulletins",
                    subtitle = "Suivi des notes par matière, progression et bulletins PDF officiels",
                    icon = Icons.Filled.Assessment
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
                    tabs = listOf("Notes & Moyennes", "Progression", "Bulletins PDF"),
                    selectedIndex = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }

            if (selectedTab != 2) {
                item {
                    DropdownSelector(
                        label = "Période scolaire",
                        options = listOf("Trimestre 1", "Trimestre 2", "Trimestre 3"),
                        selectedOption = selectedPeriod,
                        optionLabel = { it },
                        onOptionSelected = { selectedPeriod = it }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    if (overallAvg != null) {
                        item {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Moyenne pondérée ($selectedPeriod)",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "${scopedGrades.size} évaluation(s) prise(s) en compte",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    Text(
                                        text = String.format(Locale.FRANCE, "%.2f / 20", overallAvg),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (subjectAverages.isNotEmpty()) {
                        item {
                            Text(
                                text = "Moyennes par matière",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        items(subjectAverages, key = { it.subjectId }) { avg ->
                            ElevatedCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(avg.subjectName, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Coeff. ${avg.coefficient} • ${avg.gradesCount} note(s)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = String.format(Locale.FRANCE, "%.2f / 20", avg.averageOn20),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Détail des évaluations",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (scopedGrades.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Assessment,
                                title = "Aucune note disponible.",
                                subtitle = "Les notes et appréciations des évaluations s'afficheront ici après leur enregistrement."
                            )
                        }
                    } else {
                        items(scopedGrades, key = { it.id }) { grade ->
                            ElevatedCard {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${grade.subjectName} — ${grade.title}",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "${grade.score} / ${grade.maxScore}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        "${grade.studentName} • ${grade.assessmentType} (coeff. ${grade.coefficient}) • ${DateUtils.formatFrenchShort(grade.date)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (grade.comment.isNotBlank()) {
                                        Text(
                                            "Observation : ${grade.comment}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    if (subjectAverages.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                title = "Aucune note disponible.",
                                subtitle = "La progression par matière et par compétence sera calculée dès les premières évaluations."
                            )
                        }
                    } else {
                        items(subjectAverages, key = { "prog_${it.subjectId}" }) { avg ->
                            val ratio = (avg.averageOn20 / 20.0).toFloat().coerceIn(0f, 1f)
                            ElevatedCard {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(avg.subjectName, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            String.format(Locale.FRANCE, "%.2f / 20", avg.averageOn20),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier.fillMaxWidth().height(10.dp),
                                        color = if (avg.averageOn20 >= 10.0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = when {
                                            avg.averageOn20 >= 16.0 -> "Niveau : Très bien maîtrisé"
                                            avg.averageOn20 >= 12.0 -> "Niveau : Satisfaisant"
                                            avg.averageOn20 >= 10.0 -> "Niveau : En cours de consolidation"
                                            else -> "Niveau : Accompagnement recommandé"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    if (scopedBulletins.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Description,
                                title = "Aucun bulletin scolaire disponible.",
                                subtitle = if (canGenerateBulletin) {
                                    "Utilisez le bouton ci-dessous pour générer et publier un bulletin trimestriel."
                                } else {
                                    "Les bulletins validés par la direction seront téléchargeables ici au format PDF."
                                }
                            )
                        }
                    } else {
                        items(scopedBulletins, key = { it.id }) { bulletin ->
                            ElevatedCard {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(bulletin.studentName, style = MaterialTheme.typography.titleLarge)
                                            Text("${bulletin.className} • ${bulletin.period}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                        }
                                        Text(
                                            String.format(Locale.FRANCE, "%.2f / 20", bulletin.overallAverage),
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        "Moyenne classe : ${String.format(Locale.FRANCE, "%.2f", bulletin.classAverage)}/20 • Absences : ${bulletin.absencesCount} • Retards : ${bulletin.tardiesCount}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (bulletin.councilDecision.isNotBlank()) {
                                        Text("Décision : ${bulletin.councilDecision}", style = MaterialTheme.typography.labelLarge)
                                    }
                                    if (bulletin.directorObservation.isNotBlank()) {
                                        Text("Observation : ${bulletin.directorObservation}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val stuGrades = assessments.filter { it.studentId == bulletin.studentId }
                                            val result = PdfBulletinGenerator.generateAndOpenPdf(context, bulletin, stuGrades, subjects)
                                            result.onFailure {
                                                Toast.makeText(context, "Erreur PDF : ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }.onSuccess {
                                                Toast.makeText(context, "Bulletin PDF prêt : ${it.name}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("download_pdf_${bulletin.id}")
                                    ) {
                                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text("  Télécharger / Ouvrir le bulletin PDF")
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

    if (showGradeDialog) {
        RecordGradeDialog(
            students = students,
            subjects = subjects,
            onDismiss = { showGradeDialog = false },
            onSubmit = { stu, subj, title, type, score, maxScore, coeff, period, date, comment ->
                onRecordAssessment(stu, subj, title, type, score, maxScore, coeff, period, date, comment)
                showGradeDialog = false
            }
        )
    }

    if (showBulletinDialog) {
        GenerateBulletinDialog(
            schools = schools,
            students = students,
            subjects = subjects,
            assessments = assessments,
            attendance = attendance,
            onDismiss = { showBulletinDialog = false },
            onSubmit = { school, stu, period, avg, classAvg, rank, abs, tardies, decision, obs, pub ->
                onGenerateReportCard(school, stu, period, avg, classAvg, rank, abs, tardies, decision, obs, pub)
                showBulletinDialog = false
            }
        )
    }
}

@Composable
private fun RecordGradeDialog(
    students: List<Student>,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSubmit: (Student, Subject, String, String, Double, Double, Double, String, String, String) -> Unit
) {
    var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
    val stuSubjects = remember(subjects, selectedStudent) {
        subjects.filter { selectedStudent == null || it.classId == selectedStudent!!.classId }
    }
    var selectedSubject by remember(stuSubjects) { mutableStateOf(stuSubjects.firstOrNull()) }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("devoir") }
    var scoreText by remember { mutableStateOf("") }
    var maxScoreText by remember { mutableStateOf("20") }
    var coeffText by remember { mutableStateOf("1") }
    var period by remember { mutableStateOf("Trimestre 1") }
    var date by remember { mutableStateOf(DateUtils.todayIso()) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saisir une note d'évaluation") },
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
                    label = "Matière",
                    options = stuSubjects,
                    selectedOption = selectedSubject,
                    optionLabel = { it.name },
                    onOptionSelected = { selectedSubject = it },
                    emptyLabel = "Aucune matière"
                )
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Intitulé de l'évaluation *") }, modifier = Modifier.fillMaxWidth())
                DropdownSelector(
                    label = "Type",
                    options = listOf("devoir", "interrogation", "composition", "oral"),
                    selectedOption = type,
                    optionLabel = { it.replaceFirstChar { c -> c.uppercase() } },
                    onOptionSelected = { type = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = scoreText, onValueChange = { scoreText = it }, label = { Text("Note *") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = maxScoreText, onValueChange = { maxScoreText = it }, label = { Text("Sur") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = coeffText, onValueChange = { coeffText = it }, label = { Text("Coeff.") }, modifier = Modifier.weight(1f))
                }
                DropdownSelector(
                    label = "Période",
                    options = listOf("Trimestre 1", "Trimestre 2", "Trimestre 3"),
                    selectedOption = period,
                    optionLabel = { it },
                    onOptionSelected = { period = it }
                )
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (AAAA-MM-JJ)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = comment, onValueChange = { comment = it }, label = { Text("Appréciation / observation") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val score = scoreText.replace(",", ".").toDoubleOrNull()
            val maxScore = maxScoreText.replace(",", ".").toDoubleOrNull() ?: 20.0
            val coeff = coeffText.replace(",", ".").toDoubleOrNull() ?: 1.0
            Button(
                onClick = {
                    val stu = selectedStudent ?: return@Button
                    val subj = selectedSubject ?: return@Button
                    if (score != null) {
                        onSubmit(stu, subj, title, type, score, maxScore, coeff, period, date, comment)
                    }
                },
                enabled = selectedStudent != null && selectedSubject != null && title.isNotBlank() &&
                    score != null && score >= 0.0 && score <= maxScore && date.length == 10
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
private fun GenerateBulletinDialog(
    schools: List<School>,
    students: List<Student>,
    subjects: List<Subject>,
    assessments: List<AssessmentGrade>,
    attendance: List<AttendanceRecord>,
    onDismiss: () -> Unit,
    onSubmit: (School?, Student, String, Double, Double, String, Int, Int, String, String, Boolean) -> Unit
) {
    var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
    var period by remember { mutableStateOf("Trimestre 1") }

    val stuGrades = remember(assessments, selectedStudent, period) {
        assessments.filter { it.studentId == selectedStudent?.id && it.period == period }
    }
    val computedAvg = remember(stuGrades, subjects, period) {
        GradeCalculator.computeOverallAverage(GradeCalculator.computeSubjectAverages(stuGrades, subjects, period)) ?: 0.0
    }
    val classGrades = remember(assessments, selectedStudent, period) {
        assessments.filter { it.classId == selectedStudent?.classId && it.period == period }
    }
    val computedClassAvg = remember(classGrades, subjects, period) {
        GradeCalculator.computeOverallAverage(GradeCalculator.computeSubjectAverages(classGrades, subjects, period)) ?: 0.0
    }
    val absCount = remember(attendance, selectedStudent) {
        attendance.count { it.studentId == selectedStudent?.id && it.status == "absent" }
    }
    val tardyCount = remember(attendance, selectedStudent) {
        attendance.count { it.studentId == selectedStudent?.id && it.status == "retard" }
    }

    var rankLabel by remember { mutableStateOf("") }
    var decision by remember { mutableStateOf("Encouragements") }
    var observation by remember { mutableStateOf("") }
    var publishNow by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Générer un bulletin scolaire") },
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
                    emptyLabel = "Aucun élève"
                )
                DropdownSelector(
                    label = "Période",
                    options = listOf("Trimestre 1", "Trimestre 2", "Trimestre 3"),
                    selectedOption = period,
                    optionLabel = { it },
                    onOptionSelected = { period = it }
                )
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                        Text(
                            "Moyenne calculée : ${String.format(Locale.FRANCE, "%.2f / 20", computedAvg)}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "Moyenne classe : ${String.format(Locale.FRANCE, "%.2f / 20", computedClassAvg)} • Absences : $absCount • Retards : $tardyCount",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                OutlinedTextField(value = rankLabel, onValueChange = { rankLabel = it }, label = { Text("Rang (ex: 3e / 32)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = decision, onValueChange = { decision = it }, label = { Text("Décision du conseil") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = observation, onValueChange = { observation = it }, label = { Text("Observation générale du directeur") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Publier aux parents immédiatement")
                    Switch(checked = publishNow, onCheckedChange = { publishNow = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val stu = selectedStudent ?: return@Button
                    val school = schools.firstOrNull { it.id == stu.schoolId }
                    onSubmit(school, stu, period, computedAvg, computedClassAvg, rankLabel, absCount, tardyCount, decision, observation, publishNow)
                },
                enabled = selectedStudent != null
            ) {
                Text("Générer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
