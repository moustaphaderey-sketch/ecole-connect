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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.AuditLog
import com.example.data.model.NotificationItem
import com.example.data.model.PlatformSettings
import com.example.data.model.Region
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.UserPrivateContact
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.DropdownSelector
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoleBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.TabChipsRow

@Composable
fun NotificationsAndAuditScreen(
    profile: UserProfile,
    contact: UserPrivateContact?,
    settings: PlatformSettings?,
    notifications: List<NotificationItem>,
    auditLogs: List<AuditLog>,
    accessRequests: List<AccessRequest> = emptyList(),
    regions: List<Region> = emptyList(),
    schools: List<School> = emptyList(),
    classes: List<SchoolClass> = emptyList(),
    subjects: List<Subject> = emptyList(),
    students: List<Student> = emptyList(),
    onMarkNotificationRead: (String) -> Unit,
    onApproveAccessRequest: (AccessRequest, UserRole, String, School?, List<String>, List<String>, List<String>, String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onRequestMoreInfoAccessRequest: (AccessRequest, String) -> Unit = { _, _ -> },
    onRejectAccessRequest: (AccessRequest, String) -> Unit = { _, _ -> },
    onOpenApprovals: () -> Unit = {},
    onOpenAdminTab: (String) -> Unit = {},
    onSavePreferences: (String, String, String, String) -> Unit
) {
    val role = profile.roleEnum()
    val showAuditTab = role == UserRole.ADMIN_NATIONAL || role == UserRole.DIRECTEUR
    val tabs = remember(showAuditTab) {
        if (showAuditTab) listOf("Notifications", "Journal d'audit", "Profil & Confidentialité")
        else listOf("Notifications", "Profil & Confidentialité")
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var approveRequestTarget by remember { mutableStateOf<AccessRequest?>(null) }
    var requestInfoTarget by remember { mutableStateOf<AccessRequest?>(null) }
    var rejectRequestTarget by remember { mutableStateOf<AccessRequest?>(null) }
    val safeIndex = selectedTabIndex.coerceIn(0, tabs.lastIndex)
    val activeTab = tabs[safeIndex]
    val pendingRequests = remember(role, accessRequests) {
        val actionableStatuses = setOf("pending", "info_requested")
        when (role) {
            UserRole.ADMIN_NATIONAL -> accessRequests.filter { it.status in actionableStatuses }
            UserRole.DIRECTEUR -> accessRequests.filter {
                it.status in actionableStatuses &&
                    it.requestedRole in listOf(UserRole.PARENT.key, UserRole.ENSEIGNANT.key, UserRole.ELEVE.key)
            }
            else -> emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            SectionHeader(
                title = "Notifications, Profil & Sécurité",
                subtitle = "Alertes en temps réel, préférences SMS/WhatsApp et traçabilité",
                icon = Icons.Filled.Notifications
            )
        }

        item {
            TabChipsRow(
                tabs = tabs,
                selectedIndex = safeIndex,
                onSelectTab = { selectedTabIndex = it }
            )
        }

        when (activeTab) {
            "Notifications" -> {
                if (pendingRequests.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Inscriptions en attente (${pendingRequests.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(onClick = onOpenApprovals) {
                                Text("Gérer tout")
                            }
                        }
                    }
                    items(pendingRequests, key = { "notif_req_${it.userId}" }) { req ->
                        val authLabel = if (req.requestedRole in listOf(UserRole.PARENT.key, UserRole.ENSEIGNANT.key, UserRole.ELEVE.key)) {
                            "Autorité : Direction d'école"
                        } else {
                            "Autorité : Administration nationale"
                        }
                        AccessRequestValidationCard(
                            request = req,
                            authorityLabel = authLabel,
                            onApprove = { approveRequestTarget = req },
                            onRequestMoreInfo = { requestInfoTarget = req },
                            onReject = { rejectRequestTarget = req }
                        )
                    }
                }

                if (notifications.isEmpty() && pendingRequests.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.Notifications,
                            title = "Aucune notification pour le moment.",
                            subtitle = "Les alertes d'absence, retard, nouvelle note, leçon, devoir et bulletin apparaîtront ici."
                        )
                    }
                } else {
                    items(notifications, key = { it.id }) { item ->
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
                                    Surface(
                                        color = if (item.read) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = item.type.uppercase() + if (!item.read) " • NON LU" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = "Canal : ${item.channelPreference.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(item.body, style = MaterialTheme.typography.bodyMedium)
                                if (!item.read) {
                                    OutlinedButton(
                                        onClick = { onMarkNotificationRead(item.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.DoneAll, contentDescription = null)
                                        Text("  Marquer comme lue")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Journal d'audit" -> {
                if (auditLogs.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.History,
                            title = "Aucune action journalisée.",
                            subtitle = "Les créations, affectations et modifications sensibles sont enregistrées de manière immuable."
                        )
                    }
                } else {
                    items(auditLogs, key = { it.id }) { log ->
                        ElevatedCard {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(log.action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    Text(log.actorRole, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                Text(log.details, style = MaterialTheme.typography.bodyMedium)
                                Text("Par ${log.actorName} (${log.targetType})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            else -> {
                item {
                    ProfileAndPrivacyCard(
                        profile = profile,
                        contact = contact,
                        settings = settings,
                        onSavePreferences = onSavePreferences
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
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
            onNavigateToTab = onOpenAdminTab,
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
}

@Composable
private fun ProfileAndPrivacyCard(
    profile: UserProfile,
    contact: UserPrivateContact?,
    settings: PlatformSettings?,
    onSavePreferences: (String, String, String, String) -> Unit
) {
    var fullName by remember(profile.fullName) { mutableStateOf(profile.fullName) }
    var channel by remember(profile.preferredChannel) { mutableStateOf(profile.preferredChannel.ifBlank { "app" }) }
    var phone by remember(contact?.phone) { mutableStateOf(contact?.phone ?: "") }
    var whatsapp by remember(contact?.whatsappNumber) { mutableStateOf(contact?.whatsappNumber ?: "") }

    ElevatedCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Mon compte sécurisé", style = MaterialTheme.typography.titleLarge)
                RoleBadge(profile.roleEnum())
            }
            if (!contact?.email.isNullOrBlank()) {
                Text("Adresse vérifiée : ${contact?.email}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Nom complet") },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownSelector(
                label = "Canal de notification prioritaire",
                options = listOf("app", "sms", "whatsapp"),
                selectedOption = channel,
                optionLabel = {
                    when (it) {
                        "sms" -> "Application + SMS (passerelle : ${if (settings?.smsGatewayEnabled == true) "active" else "non activée"})"
                        "whatsapp" -> "Application + WhatsApp (passerelle : ${if (settings?.whatsappGatewayEnabled == true) "active" else "non activée"})"
                        else -> "Notifications dans l'application"
                    }
                },
                onOptionSelected = { channel = it }
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Téléphone") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = whatsapp,
                onValueChange = { whatsapp = it },
                label = { Text("Numéro WhatsApp") },
                modifier = Modifier.fillMaxWidth()
            )
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small) {
                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Consentement de protection des données des mineurs accepté. Les coordonnées personnelles sont isolées dans votre coffre privé.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Button(
                onClick = { onSavePreferences(fullName, channel, phone, whatsapp) },
                enabled = fullName.trim().length >= 2,
                modifier = Modifier.fillMaxWidth().testTag("save_profile_preferences_button")
            ) {
                Text("Enregistrer mes préférences")
            }
        }
    }
}
