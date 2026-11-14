package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.data.model.Announcement
import com.example.data.model.LessonEntry
import com.example.data.model.Message
import com.example.data.model.Region
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

private data class RecipientChoice(
    val uid: String,
    val label: String,
    val schoolId: String
)

@Composable
fun CommunicationScreen(
    profile: UserProfile,
    regions: List<Region>,
    schools: List<School>,
    classes: List<SchoolClass>,
    subjects: List<Subject>,
    lessons: List<LessonEntry>,
    students: List<Student>,
    managedUsers: List<UserProfile>,
    messages: List<Message>,
    announcements: List<Announcement>,
    onSendMessage: (
        schoolId: String,
        recipientUid: String,
        recipientName: String,
        studentId: String,
        studentName: String,
        subject: String,
        body: String
    ) -> Unit,
    onMarkMessageRead: (String) -> Unit,
    onPublishAnnouncement: (
        scope: String,
        regionId: String,
        schoolId: String,
        title: String,
        content: String,
        priority: String,
        authorizedUids: List<String>
    ) -> Unit,
    onUpdateAnnouncement: (
        announcement: Announcement,
        title: String,
        content: String,
        priority: String
    ) -> Unit = { _, _, _, _ -> }
) {
    val role = profile.roleEnum()
    val canPublishAnnouncement = role == UserRole.ADMIN_NATIONAL || role == UserRole.ADMIN_REGIONAL || role == UserRole.DIRECTEUR
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMessageDialog by remember { mutableStateOf(false) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var editingAnnouncement by remember { mutableStateOf<Announcement?>(null) }

    val recipientChoices = remember(profile.userId, managedUsers, schools, subjects, lessons, messages) {
        val map = linkedMapOf<String, RecipientChoice>()
        managedUsers.filter { it.userId != profile.userId }.forEach { u ->
            map[u.userId] = RecipientChoice(u.userId, "${u.fullName} (${u.roleEnum().labelFr})", u.schoolId)
        }
        schools.forEach { s ->
            if (s.directorUid.isNotBlank() && s.directorUid != profile.userId && !map.containsKey(s.directorUid)) {
                map[s.directorUid] = RecipientChoice(
                    s.directorUid,
                    "${s.directorName.ifBlank { "Direction" }} — Directeur (${s.name})",
                    s.id
                )
            }
        }
        subjects.forEach { sub ->
            if (sub.teacherUid.isNotBlank() && sub.teacherUid != profile.userId && !map.containsKey(sub.teacherUid)) {
                map[sub.teacherUid] = RecipientChoice(
                    sub.teacherUid,
                    "${sub.teacherName.ifBlank { "Enseignant" }} — ${sub.name}",
                    sub.schoolId
                )
            }
        }
        lessons.forEach { l ->
            if (l.teacherUid.isNotBlank() && l.teacherUid != profile.userId && !map.containsKey(l.teacherUid)) {
                map[l.teacherUid] = RecipientChoice(
                    l.teacherUid,
                    "${l.teacherName} — ${l.subjectName}",
                    l.schoolId
                )
            }
        }
        messages.forEach { m ->
            val otherUid = if (m.senderUid == profile.userId) m.recipientUid else m.senderUid
            val otherName = if (m.senderUid == profile.userId) m.recipientName else m.senderName
            if (otherUid.isNotBlank() && otherUid != profile.userId && !map.containsKey(otherUid)) {
                map[otherUid] = RecipientChoice(otherUid, otherName.ifBlank { "Interlocuteur" }, m.schoolId)
            }
        }
        map.values.toList()
    }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showMessageDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("compose_message_fab")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Nouveau message")
                }
            } else if (canPublishAnnouncement) {
                FloatingActionButton(
                    onClick = { showAnnouncementDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag("publish_announcement_fab")
                ) {
                    Icon(Icons.Filled.Campaign, contentDescription = "Publier une annonce")
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
                    title = "Messagerie & Annonces",
                    subtitle = "Échanges directs parents-enseignants-direction et annonces officielles",
                    icon = Icons.AutoMirrored.Filled.Chat
                )
            }

            item {
                TabChipsRow(
                    tabs = listOf("Messagerie directe", "Annonces générales"),
                    selectedIndex = selectedTab,
                    onSelectTab = { selectedTab = it }
                )
            }

            if (selectedTab == 0) {
                if (messages.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            title = "Aucun message pour le moment.",
                            subtitle = "Utilisez le bouton d'envoi pour contacter un enseignant, un parent ou la direction."
                        )
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        val isIncoming = msg.recipientUid == profile.userId
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
                                        text = if (isIncoming) "De : ${msg.senderName}" else "À : ${msg.recipientName}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Surface(
                                        color = if (!msg.read && isIncoming) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = if (!msg.read && isIncoming) "Nouveau" else if (isIncoming) "Lu" else "Envoyé",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(msg.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (msg.studentName.isNotBlank()) {
                                    Text("Concernant l'élève : ${msg.studentName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                }
                                Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                                if (isIncoming && !msg.read) {
                                    OutlinedButton(
                                        onClick = { onMarkMessageRead(msg.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.MarkEmailRead, contentDescription = null)
                                        Text("  Marquer comme lu")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (announcements.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Campaign,
                            title = "Aucune annonce publiée.",
                            subtitle = if (canPublishAnnouncement) {
                                "Publiez une annonce pour informer les parents, enseignants et élèves."
                            } else {
                                "Les annonces officielles de votre établissement ou du ministère apparaîtront ici."
                            }
                        )
                    }
                } else {
                    items(announcements, key = { it.id }) { ann ->
                        val canEditAnnouncement = role == UserRole.ADMIN_NATIONAL || ann.authorUid == profile.userId
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
                                    Text(ann.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Surface(
                                            color = if (ann.priority == "urgente") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                text = ann.priority.replaceFirstChar { it.uppercase() },
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        if (canEditAnnouncement) {
                                            IconButton(
                                                onClick = { editingAnnouncement = ann },
                                                modifier = Modifier.testTag("edit_announcement_${ann.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Edit,
                                                    contentDescription = "Modifier l'annonce",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(ann.content, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Publiée par ${ann.authorName} • Portée : ${ann.scope}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showMessageDialog) {
        ComposeMessageDialog(
            choices = recipientChoices,
            students = students,
            defaultSchoolId = profile.schoolId,
            onDismiss = { showMessageDialog = false },
            onSubmit = { schoolId, recUid, recName, stuId, stuName, subject, body ->
                onSendMessage(schoolId, recUid, recName, stuId, stuName, subject, body)
                showMessageDialog = false
            }
        )
    }

    if (showAnnouncementDialog) {
        PublishAnnouncementDialog(
            role = role,
            profile = profile,
            regions = regions,
            schools = schools,
            classes = classes,
            students = students,
            managedUsers = managedUsers,
            onDismiss = { showAnnouncementDialog = false },
            onSubmit = { scope, regId, schId, title, content, priority, authUids ->
                onPublishAnnouncement(scope, regId, schId, title, content, priority, authUids)
                showAnnouncementDialog = false
            }
        )
    }

    if (editingAnnouncement != null) {
        val target = editingAnnouncement!!
        PublishAnnouncementDialog(
            role = role,
            profile = profile,
            regions = regions,
            schools = schools,
            classes = classes,
            students = students,
            managedUsers = managedUsers,
            existingAnnouncement = target,
            onDismiss = { editingAnnouncement = null },
            onSubmit = { _, _, _, title, content, priority, _ ->
                onUpdateAnnouncement(target, title, content, priority)
                editingAnnouncement = null
            }
        )
    }
}

@Composable
private fun ComposeMessageDialog(
    choices: List<RecipientChoice>,
    students: List<Student>,
    defaultSchoolId: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String, String, String) -> Unit
) {
    var selectedChoice by remember { mutableStateOf(choices.firstOrNull()) }
    var selectedStudent by remember { mutableStateOf(students.firstOrNull()) }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau message") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector(
                    label = "Destinataire *",
                    options = choices,
                    selectedOption = selectedChoice,
                    optionLabel = { it.label },
                    onOptionSelected = { selectedChoice = it },
                    emptyLabel = "Aucun destinataire disponible pour le moment"
                )
                if (students.isNotEmpty()) {
                    DropdownSelector(
                        label = "Élève concerné (optionnel)",
                        options = students,
                        selectedOption = selectedStudent,
                        optionLabel = { "${it.fullName} (${it.className})" },
                        onOptionSelected = { selectedStudent = it }
                    )
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Objet *") },
                    modifier = Modifier.fillMaxWidth().testTag("message_subject_input")
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Message *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("message_body_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rec = selectedChoice ?: return@Button
                    onSubmit(
                        rec.schoolId.ifBlank { defaultSchoolId },
                        rec.uid,
                        rec.label.substringBefore(" — ").substringBefore(" ("),
                        selectedStudent?.id ?: "",
                        selectedStudent?.fullName ?: "",
                        subject,
                        body
                    )
                },
                enabled = selectedChoice != null && subject.isNotBlank() && body.isNotBlank(),
                modifier = Modifier.testTag("send_message_button")
            ) { Text("Envoyer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun PublishAnnouncementDialog(
    role: UserRole,
    profile: UserProfile,
    regions: List<Region>,
    schools: List<School>,
    classes: List<SchoolClass>,
    students: List<Student>,
    managedUsers: List<UserProfile>,
    existingAnnouncement: Announcement? = null,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String, String, List<String>) -> Unit
) {
    val isEdit = existingAnnouncement != null
    val allowedScopes = remember(role) {
        when (role) {
            UserRole.ADMIN_NATIONAL -> listOf("national", "regional", "school")
            UserRole.ADMIN_REGIONAL -> listOf("regional", "school")
            else -> listOf("school")
        }
    }
    var scope by remember(existingAnnouncement?.id) { mutableStateOf(existingAnnouncement?.scope ?: allowedScopes.first()) }
    var selectedRegion by remember(existingAnnouncement?.id) { mutableStateOf(regions.firstOrNull()) }
    var selectedSchool by remember(existingAnnouncement?.id) { mutableStateOf(schools.firstOrNull()) }
    var title by remember(existingAnnouncement?.id) { mutableStateOf(existingAnnouncement?.title ?: "") }
    var content by remember(existingAnnouncement?.id) { mutableStateOf(existingAnnouncement?.content ?: "") }
    var priority by remember(existingAnnouncement?.id) { mutableStateOf(existingAnnouncement?.priority ?: "normale") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Modifier l'annonce officielle" else "Publier une annonce officielle") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isEdit) {
                    DropdownSelector("Portée", allowedScopes, scope, {
                        when (it) {
                            "national" -> "Nationale"
                            "regional" -> "Régionale"
                            else -> "Établissement"
                        }
                    }, { scope = it })
                    if (scope == "regional" && regions.isNotEmpty()) {
                        DropdownSelector("Région", regions, selectedRegion, { it.name }, { selectedRegion = it })
                    }
                    if (scope == "school" && schools.isNotEmpty()) {
                        DropdownSelector("Établissement", schools, selectedSchool, { it.name }, { selectedSchool = it })
                    }
                } else {
                    Text(
                        text = "Portée : ${existingAnnouncement?.scope ?: scope}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                DropdownSelector("Priorité", listOf("normale", "importante", "urgente"), priority, { it.replaceFirstChar { c -> c.uppercase() } }, { priority = it })
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titre de l'annonce *") },
                    modifier = Modifier.fillMaxWidth().testTag("announcement_title_input")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Contenu *") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("announcement_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val regId = if (scope == "regional") (selectedRegion?.id ?: profile.regionId) else (selectedSchool?.regionId ?: profile.regionId)
                    val schId = if (scope == "school") (selectedSchool?.id ?: profile.schoolId) else ""
                    val audience = if (isEdit) emptyList() else buildList {
                        managedUsers.forEach { u ->
                            if (scope == "national" || (scope == "school" && u.schoolId == schId) || (scope == "regional" && u.regionId == regId)) {
                                add(u.userId)
                            }
                        }
                        classes.forEach { c ->
                            if (scope == "national" || c.schoolId == schId || c.regionId == regId) {
                                addAll(c.authorizedUids)
                            }
                        }
                        students.forEach { s ->
                            if (scope == "national" || s.schoolId == schId || s.regionId == regId) {
                                addAll(s.authorizedUids)
                            }
                        }
                    }.distinct()
                    onSubmit(scope, regId, schId, title, content, priority, audience)
                },
                enabled = title.trim().length >= 2 && content.trim().length >= 2,
                modifier = Modifier.testTag("save_announcement_button")
            ) { Text(if (isEdit) "Enregistrer" else "Publier") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
