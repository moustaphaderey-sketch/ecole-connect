package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.data.model.LessonEntry
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.ChildSelectorBar
import com.example.ui.components.DropdownSelector
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.util.DateUtils

@Composable
fun CahierTexteScreen(
    profile: UserProfile,
    schools: List<School>,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    students: List<Student>,
    lessons: List<LessonEntry>,
    selectedChildId: String?,
    onSelectChild: (String) -> Unit,
    onPublishLesson: (
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
    ) -> Unit
) {
    val role = profile.roleEnum()
    val canCreateLesson = role == UserRole.ENSEIGNANT || role == UserRole.DIRECTEUR || role == UserRole.ADMIN_NATIONAL
    var showCreateDialog by remember { mutableStateOf(false) }

    var dateMode by remember { mutableStateOf("all") } // today, yesterday, custom, all
    var customDate by remember { mutableStateOf(DateUtils.todayIso()) }
    var selectedClassFilter by remember { mutableStateOf<String?>(null) }

    val activeChild = remember(students, selectedChildId) {
        students.firstOrNull { it.id == selectedChildId } ?: students.firstOrNull()
    }

    val filteredLessons = remember(lessons, dateMode, customDate, selectedClassFilter, role, activeChild) {
        lessons.filter { lesson ->
            val matchesChild = if (role == UserRole.PARENT && activeChild != null) {
                lesson.classId == activeChild.classId
            } else true
            val matchesClass = selectedClassFilter == null || lesson.classId == selectedClassFilter
            val matchesDate = when (dateMode) {
                "today" -> lesson.lessonDate == DateUtils.todayIso()
                "yesterday" -> lesson.lessonDate == DateUtils.daysAgoIso(1)
                "custom" -> lesson.lessonDate == customDate.trim()
                else -> true
            }
            matchesChild && matchesClass && matchesDate
        }.sortedByDescending { it.lessonDate }
    }

    Scaffold(
        floatingActionButton = {
            if (canCreateLesson) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag("add_lesson_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Enregistrer une leçon")
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
                    title = "Cahier de texte numérique",
                    subtitle = if (role == UserRole.PARENT) {
                        "Consultez les cours réellement enseignés chaque jour et les devoirs donnés"
                    } else {
                        "Enregistrement quotidien des leçons, chapitres, compétences, exercices et devoirs"
                    },
                    icon = Icons.AutoMirrored.Filled.MenuBook
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = dateMode == "all",
                            onClick = { dateMode = "all" },
                            label = { Text("Toutes les dates") }
                        )
                        FilterChip(
                            selected = dateMode == "today",
                            onClick = { dateMode = "today" },
                            label = { Text("Aujourd'hui") },
                            leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = dateMode == "yesterday",
                            onClick = { dateMode = "yesterday" },
                            label = { Text("Hier") }
                        )
                        FilterChip(
                            selected = dateMode == "custom",
                            onClick = { dateMode = "custom" },
                            label = { Text("Date précise") }
                        )
                    }

                    if (dateMode == "custom") {
                        OutlinedTextField(
                            value = customDate,
                            onValueChange = { customDate = it },
                            label = { Text("Filtrer par date (AAAA-MM-JJ)") },
                            modifier = Modifier.fillMaxWidth().testTag("lesson_date_filter_input")
                        )
                    }

                    if (classes.size > 1 && role != UserRole.PARENT && role != UserRole.ELEVE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedClassFilter == null,
                                onClick = { selectedClassFilter = null },
                                label = { Text("Toutes les classes") }
                            )
                            classes.forEach { sc ->
                                FilterChip(
                                    selected = selectedClassFilter == sc.id,
                                    onClick = { selectedClassFilter = sc.id },
                                    label = { Text(sc.name) }
                                )
                            }
                        }
                    }
                }
            }

            if (filteredLessons.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        title = "Aucun cours enregistré.",
                        subtitle = if (canCreateLesson) {
                            "Appuyez sur le bouton + pour publier une leçon dans le cahier de texte numérique."
                        } else {
                            "Aucune leçon n'a encore été publiée pour cette date ou cette classe."
                        }
                    )
                }
            } else {
                items(filteredLessons, key = { it.id }) { lesson ->
                    LessonDetailCard(lesson)
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showCreateDialog) {
        CreateLessonDialog(
            schools = schools,
            classes = classes,
            subjects = subjects,
            onDismiss = { showCreateDialog = false },
            onSubmit = { school, sc, subj, date, chapter, title, summary, skills, exercises, hw, due, attach ->
                onPublishLesson(school, sc, subj, date, chapter, title, summary, skills, exercises, hw, due, attach)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun LessonDetailCard(lesson: LessonEntry) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${lesson.subjectName} • ${lesson.className}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = DateUtils.formatFrenchShort(lesson.lessonDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = lesson.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Chapitre : ${lesson.chapter}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = lesson.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (lesson.skillsWorked.isNotBlank()) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Column {
                        Text("Compétences travaillées", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(lesson.skillsWorked, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (lesson.classExercises.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Column {
                        Text("Exercices réalisés en classe", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Text(lesson.classExercises, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (lesson.homework.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Devoirs à faire à la maison" + if (lesson.homeworkDueDate.isNotBlank()) " (pour le ${DateUtils.formatFrenchShort(lesson.homeworkDueDate)})" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = lesson.homework,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (lesson.attachmentLabel.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Document joint : ${lesson.attachmentLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Enseignant : ${lesson.teacherName} • ${lesson.schoolName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreateLessonDialog(
    schools: List<School>,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSubmit: (
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
    ) -> Unit
) {
    var selectedClass by remember { mutableStateOf(classes.firstOrNull()) }
    val classSubjects = remember(subjects, selectedClass) {
        subjects.filter { selectedClass == null || it.classId == selectedClass!!.id }
    }
    var selectedSubject by remember(classSubjects) { mutableStateOf(classSubjects.firstOrNull()) }

    var lessonDate by remember { mutableStateOf(DateUtils.todayIso()) }
    var chapter by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var skillsWorked by remember { mutableStateOf("") }
    var classExercises by remember { mutableStateOf("") }
    var homework by remember { mutableStateOf("") }
    var homeworkDueDate by remember { mutableStateOf("") }
    var attachmentLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle entrée — Cahier de texte") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DropdownSelector(
                    label = "Classe",
                    options = classes,
                    selectedOption = selectedClass,
                    optionLabel = { "${it.name} (${it.schoolName})" },
                    onOptionSelected = { selectedClass = it },
                    emptyLabel = "Aucune classe enregistrée"
                )
                DropdownSelector(
                    label = "Matière",
                    options = classSubjects,
                    selectedOption = selectedSubject,
                    optionLabel = { it.name },
                    onOptionSelected = { selectedSubject = it },
                    emptyLabel = "Aucune matière pour cette classe"
                )
                OutlinedTextField(
                    value = lessonDate,
                    onValueChange = { lessonDate = it },
                    label = { Text("Date du cours (AAAA-MM-JJ)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = chapter,
                    onValueChange = { chapter = it },
                    label = { Text("Chapitre étudié *") },
                    modifier = Modifier.fillMaxWidth().testTag("lesson_chapter_input")
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de la leçon *") },
                    modifier = Modifier.fillMaxWidth().testTag("lesson_title_input")
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Résumé du contenu enseigné *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("lesson_summary_input")
                )
                OutlinedTextField(
                    value = skillsWorked,
                    onValueChange = { skillsWorked = it },
                    label = { Text("Compétences travaillées") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = classExercises,
                    onValueChange = { classExercises = it },
                    label = { Text("Exercices réalisés en classe") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = homework,
                    onValueChange = { homework = it },
                    label = { Text("Devoirs à faire à la maison") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = homeworkDueDate,
                    onValueChange = { homeworkDueDate = it },
                    label = { Text("Date limite devoir (AAAA-MM-JJ)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = attachmentLabel,
                    onValueChange = { attachmentLabel = it },
                    label = { Text("Document pédagogique joint (nom/référence)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sc = selectedClass ?: return@Button
                    val subj = selectedSubject ?: return@Button
                    val school = schools.firstOrNull { it.id == sc.schoolId }
                    onSubmit(
                        school, sc, subj, lessonDate, chapter, title, summary,
                        skillsWorked, classExercises, homework, homeworkDueDate, attachmentLabel
                    )
                },
                enabled = selectedClass != null && selectedSubject != null &&
                    lessonDate.length == 10 && chapter.isNotBlank() && title.length >= 2 && summary.length >= 2,
                modifier = Modifier.testTag("save_lesson_button")
            ) {
                Text("Publier")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
