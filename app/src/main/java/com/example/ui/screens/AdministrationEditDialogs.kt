package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
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

@Composable
internal fun EditRegionDialog(
    region: Region,
    onDismiss: () -> Unit,
    onSubmit: (name: String, code: String) -> Unit
) {
    var name by remember(region.id) { mutableStateOf(region.name) }
    var code by remember(region.id) { mutableStateOf(region.code) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la région") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de la région *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_region_name_input")
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Code *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_region_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name, code) },
                enabled = name.trim().length >= 2 && code.trim().isNotBlank(),
                modifier = Modifier.testTag("save_edit_region_button")
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
internal fun EditSchoolDialog(
    school: School,
    regions: List<Region>,
    onDismiss: () -> Unit,
    onSubmit: (
        region: Region?,
        name: String,
        code: String,
        schoolType: String,
        address: String,
        sector: String,
        description: String,
        isActive: Boolean
    ) -> Unit
) {
    var selectedRegion by remember(school.id, regions) {
        mutableStateOf(regions.firstOrNull { it.id == school.regionId } ?: regions.firstOrNull())
    }
    var name by remember(school.id) { mutableStateOf(school.name) }
    var code by remember(school.id) { mutableStateOf(school.code) }
    var schoolType by remember(school.id) { mutableStateOf(school.schoolType.ifBlank { "primaire" }) }
    var address by remember(school.id) { mutableStateOf(school.address) }
    var sector by remember(school.id) { mutableStateOf(school.sector) }
    var description by remember(school.id) { mutableStateOf(school.description) }
    var isActive by remember(school.id) { mutableStateOf(school.isActive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier l'établissement") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (regions.isNotEmpty()) {
                    DropdownSelector(
                        label = "Région",
                        options = regions,
                        selectedOption = selectedRegion,
                        optionLabel = { "${it.name} (${it.code})" },
                        onOptionSelected = { selectedRegion = it }
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de l'établissement *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_school_name_input")
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Code officiel *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_school_code_input")
                )
                DropdownSelector(
                    label = "Type d'établissement",
                    options = listOf("primaire", "college", "lycee"),
                    selectedOption = schoolType,
                    optionLabel = {
                        when (it) {
                            "college" -> "Collège"
                            "lycee" -> "Lycée"
                            else -> "Primaire"
                        }
                    },
                    onOptionSelected = { schoolType = it }
                )
                OutlinedTextField(
                    value = sector,
                    onValueChange = { sector = it },
                    label = { Text("Secteur / District") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse / Quartier") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Établissement actif", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        modifier = Modifier.testTag("edit_school_active_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedRegion, name, code, schoolType, address, sector, description, isActive) },
                enabled = name.trim().length >= 2 && code.trim().isNotBlank(),
                modifier = Modifier.testTag("save_edit_school_button")
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
internal fun EditClassDialog(
    schoolClass: SchoolClass,
    onDismiss: () -> Unit,
    onSubmit: (name: String, level: String, academicYear: String) -> Unit
) {
    var name by remember(schoolClass.id) { mutableStateOf(schoolClass.name) }
    var level by remember(schoolClass.id) { mutableStateOf(schoolClass.level) }
    var academicYear by remember(schoolClass.id) { mutableStateOf(schoolClass.academicYear) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la classe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Établissement : ${schoolClass.schoolName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de la classe *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_class_name_input")
                )
                OutlinedTextField(
                    value = level,
                    onValueChange = { level = it },
                    label = { Text("Niveau *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_class_level_input")
                )
                OutlinedTextField(
                    value = academicYear,
                    onValueChange = { academicYear = it },
                    label = { Text("Année scolaire *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name, level, academicYear) },
                enabled = name.trim().isNotBlank() && level.trim().isNotBlank() && academicYear.trim().length >= 4,
                modifier = Modifier.testTag("save_edit_class_button")
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
internal fun EditSubjectDialog(
    subject: Subject,
    managedUsers: List<UserProfile>,
    onDismiss: () -> Unit,
    onSubmit: (name: String, code: String, coefficient: Double, teacherUid: String, teacherName: String) -> Unit
) {
    val teachers = remember(managedUsers, subject.schoolId) {
        managedUsers.filter {
            it.roleEnum() == UserRole.ENSEIGNANT &&
                (it.schoolId.isBlank() || subject.schoolId.isBlank() || it.schoolId == subject.schoolId)
        }
    }
    var selectedTeacher by remember(subject.id, teachers) {
        mutableStateOf(teachers.firstOrNull { it.userId == subject.teacherUid })
    }
    var name by remember(subject.id) { mutableStateOf(subject.name) }
    var code by remember(subject.id) { mutableStateOf(subject.code) }
    var coeffText by remember(subject.id) { mutableStateOf(subject.coefficient.toString()) }
    var unassignTeacher by remember(subject.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la matière") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Classe : ${subject.className}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de la matière *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_subject_name_input")
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Code") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coeffText,
                    onValueChange = { coeffText = it },
                    label = { Text("Coefficient *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_subject_coeff_input")
                )
                if (teachers.isNotEmpty()) {
                    DropdownSelector(
                        label = "Enseignant affecté",
                        options = teachers,
                        selectedOption = selectedTeacher,
                        optionLabel = { it.fullName },
                        onOptionSelected = {
                            selectedTeacher = it
                            unassignTeacher = false
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Retirer l'enseignant affecté", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = unassignTeacher, onCheckedChange = { unassignTeacher = it })
                    }
                }
            }
        },
        confirmButton = {
            val coeff = coeffText.replace(",", ".").toDoubleOrNull()
            Button(
                onClick = {
                    val c = (coeff ?: 1.0).coerceIn(0.5, 20.0)
                    val tUid = if (unassignTeacher) "" else (selectedTeacher?.userId ?: subject.teacherUid)
                    val tName = if (unassignTeacher) "" else (selectedTeacher?.fullName ?: subject.teacherName)
                    onSubmit(name, code, c, tUid, tName)
                },
                enabled = name.trim().length >= 2 && coeff != null && coeff in 0.5..20.0,
                modifier = Modifier.testTag("save_edit_subject_button")
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
internal fun EditScheduleDialog(
    entry: ScheduleEntry,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSubmit: (subject: Subject?, dayOfWeek: String, startTime: String, endTime: String, room: String) -> Unit
) {
    val classSubjects = remember(subjects, entry.classId) {
        subjects.filter { it.classId == entry.classId }
    }
    var selectedSubject by remember(entry.id, classSubjects) {
        mutableStateOf(classSubjects.firstOrNull { it.id == entry.subjectId } ?: classSubjects.firstOrNull())
    }
    var day by remember(entry.id) { mutableStateOf(entry.dayOfWeek.ifBlank { "lundi" }) }
    var startTime by remember(entry.id) { mutableStateOf(entry.startTime) }
    var endTime by remember(entry.id) { mutableStateOf(entry.endTime) }
    var room by remember(entry.id) { mutableStateOf(entry.room) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier le créneau (${entry.className})") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (classSubjects.isNotEmpty()) {
                    DropdownSelector(
                        label = "Matière",
                        options = classSubjects,
                        selectedOption = selectedSubject,
                        optionLabel = { it.name },
                        onOptionSelected = { selectedSubject = it }
                    )
                }
                DropdownSelector(
                    label = "Jour",
                    options = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi"),
                    selectedOption = day,
                    optionLabel = { it.replaceFirstChar { c -> c.uppercase() } },
                    onOptionSelected = { day = it }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Début *") },
                        modifier = Modifier.weight(1f).testTag("edit_schedule_start_input")
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("Fin *") },
                        modifier = Modifier.weight(1f).testTag("edit_schedule_end_input")
                    )
                }
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Salle") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_schedule_room_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedSubject, day, startTime, endTime, room) },
                enabled = startTime.trim().length >= 4 && endTime.trim().length >= 4,
                modifier = Modifier.testTag("save_edit_schedule_button")
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
internal fun EditStudentDialog(
    student: Student,
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onSubmit: (
        targetClass: SchoolClass?,
        firstName: String,
        lastName: String,
        matricule: String,
        dateOfBirth: String,
        gender: String,
        gradesVisibleToStudent: Boolean,
        isActive: Boolean
    ) -> Unit
) {
    val schoolClasses = remember(classes, student.schoolId) {
        classes.filter { it.schoolId == student.schoolId }
    }
    var selectedClass by remember(student.id, schoolClasses) {
        mutableStateOf(schoolClasses.firstOrNull { it.id == student.classId } ?: schoolClasses.firstOrNull())
    }
    var firstName by remember(student.id) { mutableStateOf(student.firstName) }
    var lastName by remember(student.id) { mutableStateOf(student.lastName) }
    var matricule by remember(student.id) { mutableStateOf(student.matricule) }
    var dob by remember(student.id) { mutableStateOf(student.dateOfBirth) }
    var gender by remember(student.id) { mutableStateOf(student.gender.ifBlank { "M" }) }
    var gradesVisible by remember(student.id) { mutableStateOf(student.gradesVisibleToStudent) }
    var isActive by remember(student.id) { mutableStateOf(student.isActive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier la fiche élève") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (schoolClasses.isNotEmpty()) {
                    DropdownSelector(
                        label = "Classe",
                        options = schoolClasses,
                        selectedOption = selectedClass,
                        optionLabel = { "${it.name} (${it.level})" },
                        onOptionSelected = { selectedClass = it }
                    )
                }
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Prénom *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_student_firstname_input")
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Nom *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_student_lastname_input")
                )
                OutlinedTextField(
                    value = matricule,
                    onValueChange = { matricule = it },
                    label = { Text("Matricule *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_student_matricule_input")
                )
                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("Date de naissance (AAAA-MM-JJ)") },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelector(
                    label = "Genre",
                    options = listOf("M", "F"),
                    selectedOption = gender,
                    optionLabel = { if (it == "M") "Masculin" else "Féminin" },
                    onOptionSelected = { gender = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Autoriser l'élève à voir ses notes", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = gradesVisible, onCheckedChange = { gradesVisible = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Élève actif", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(selectedClass, firstName, lastName, matricule, dob, gender, gradesVisible, isActive)
                },
                enabled = firstName.trim().isNotBlank() && lastName.trim().isNotBlank() && matricule.trim().isNotBlank(),
                modifier = Modifier.testTag("save_edit_student_button")
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
internal fun EditInvitationDialog(
    invitation: RoleInvitation,
    onDismiss: () -> Unit,
    onSubmit: (fullName: String, phone: String, status: String) -> Unit
) {
    var fullName by remember(invitation.id) { mutableStateOf(invitation.fullName) }
    var phone by remember(invitation.id) { mutableStateOf(invitation.phone) }
    var status by remember(invitation.id) {
        mutableStateOf(if (invitation.status == "revoked") "revoked" else "pending")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier l'invitation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${invitation.email} • ${UserRole.fromKey(invitation.role).labelFr}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nom complet *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_invitation_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Téléphone") },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownSelector(
                    label = "Statut de l'invitation",
                    options = listOf("pending", "revoked"),
                    selectedOption = status,
                    optionLabel = { if (it == "pending") "En attente (active)" else "Révoquée" },
                    onOptionSelected = { status = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(fullName, phone, status) },
                enabled = fullName.trim().length >= 2,
                modifier = Modifier.testTag("save_edit_invitation_button")
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
internal fun EditManagedUserDialog(
    actorRole: UserRole,
    targetUser: UserProfile,
    schools: List<School>,
    onDismiss: () -> Unit,
    onSubmit: (fullName: String, role: UserRole?, school: School?, isActive: Boolean) -> Unit
) {
    val isNationalAdmin = actorRole == UserRole.ADMIN_NATIONAL
    var fullName by remember(targetUser.userId) { mutableStateOf(targetUser.fullName) }
    var selectedRole by remember(targetUser.userId) { mutableStateOf(targetUser.roleEnum()) }
    var selectedSchool by remember(targetUser.userId, schools) {
        mutableStateOf(schools.firstOrNull { it.id == targetUser.schoolId })
    }
    var isActive by remember(targetUser.userId) { mutableStateOf(targetUser.isActive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier le compte utilisateur") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nom complet *") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_user_name_input")
                )
                if (isNationalAdmin) {
                    DropdownSelector(
                        label = "Rôle",
                        options = UserRole.entries,
                        selectedOption = selectedRole,
                        optionLabel = { it.labelFr },
                        onOptionSelected = { selectedRole = it }
                    )
                    if (schools.isNotEmpty() && selectedRole != UserRole.ADMIN_NATIONAL && selectedRole != UserRole.ADMIN_REGIONAL) {
                        DropdownSelector(
                            label = "Établissement de rattachement",
                            options = schools,
                            selectedOption = selectedSchool,
                            optionLabel = { it.name },
                            onOptionSelected = { selectedSchool = it }
                        )
                    }
                } else {
                    Text(
                        text = "Rôle : ${targetUser.roleEnum().labelFr} • ${targetUser.schoolName.ifBlank { "Établissement" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Compte actif", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it },
                        modifier = Modifier.testTag("edit_user_active_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        fullName,
                        if (isNationalAdmin) selectedRole else null,
                        if (isNationalAdmin && selectedRole != UserRole.ADMIN_NATIONAL && selectedRole != UserRole.ADMIN_REGIONAL) selectedSchool else null,
                        isActive
                    )
                },
                enabled = fullName.trim().length >= 2,
                modifier = Modifier.testTag("save_edit_user_button")
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
