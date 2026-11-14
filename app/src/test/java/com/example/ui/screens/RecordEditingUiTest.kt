package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.Announcement
import com.example.data.model.AssessmentGrade
import com.example.data.model.AttendanceRecord
import com.example.data.model.DifficultyAlert
import com.example.data.model.LessonEntry
import com.example.data.model.Region
import com.example.data.model.ReportCard
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.theme.EcoleConnecteeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecordEditingUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val adminProfile = UserProfile(
        userId = "adm_1",
        fullName = "Inspecteur Djibouti",
        role = UserRole.ADMIN_NATIONAL.key,
        isActive = true,
        consentAccepted = true
    )

    private val teacherProfile = UserProfile(
        userId = "prof_1",
        fullName = "Amina Farah",
        role = UserRole.ENSEIGNANT.key,
        schoolId = "sch_1",
        tenantId = "sch_1",
        isActive = true,
        consentAccepted = true
    )

    private val region = Region(id = "reg_1", name = "Djibouti-Ville", code = "DJ01")
    private val school = School(
        id = "sch_1",
        tenantId = "sch_1",
        regionId = "reg_1",
        regionName = "Djibouti-Ville",
        name = "Collège de Boulaos",
        code = "CB01",
        schoolType = "college",
        isActive = true
    )
    private val schoolClass = SchoolClass(
        id = "cls_1",
        schoolId = "sch_1",
        tenantId = "sch_1",
        schoolName = "Collège de Boulaos",
        name = "6ème A",
        level = "6ème",
        academicYear = "2026-2027"
    )
    private val subject = Subject(
        id = "subj_1",
        schoolId = "sch_1",
        classId = "cls_1",
        className = "6ème A",
        name = "Mathématiques",
        code = "MATH",
        coefficient = 4.0,
        teacherUid = "prof_1",
        teacherName = "Amina Farah"
    )
    private val student = Student(
        id = "stu_1",
        schoolId = "sch_1",
        schoolName = "Collège de Boulaos",
        classId = "cls_1",
        className = "6ème A",
        firstName = "Ali",
        lastName = "Hassan",
        matricule = "DJ-001",
        isActive = true
    )

    @Test
    fun administrationScreen_editRegionDialog_updatesRegion() {
        var updatedName: String? = null
        var updatedCode: String? = null

        composeRule.setContent {
            EcoleConnecteeTheme {
                AdministrationScreen(
                    profile = adminProfile,
                    regions = listOf(region),
                    schools = listOf(school),
                    classes = listOf(schoolClass),
                    subjects = listOf(subject),
                    schedules = emptyList(),
                    students = listOf(student),
                    invitations = emptyList(),
                    managedUsers = listOf(adminProfile, teacherProfile),
                    onCreateRegion = { _, _ -> },
                    onCreateSchool = { _, _, _, _, _ -> },
                    onCreateClass = { _, _, _, _ -> },
                    onCreateSubject = { _, _, _, _, _, _, _ -> },
                    onCreateScheduleEntry = { _, _, _, _, _, _, _ -> },
                    onEnrollStudent = { _, _, _, _, _, _, _, _, _, _ -> },
                    onLinkParent = { _, _ -> },
                    onCreateInvitation = { _, _, _, _, _, _, _, _ -> },
                    onSetUserActive = { _, _ -> },
                    onAssignDirector = { _, _ -> },
                    onUpdateRegion = { _, newName, newCode ->
                        updatedName = newName
                        updatedCode = newCode
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_region_reg_1").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Modifier la région").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_region_name_input").performTextReplacement("Djibouti Centre")
        composeRule.onNodeWithTag("edit_region_code_input").performTextReplacement("DJC")
        composeRule.onNodeWithTag("save_edit_region_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Djibouti Centre", updatedName)
        assertEquals("DJC", updatedCode)
    }

    @Test
    fun cahierTexteScreen_editLessonDialog_invokesCallback() {
        val lesson = LessonEntry(
            id = "les_1",
            schoolId = "sch_1",
            classId = "cls_1",
            className = "6ème A",
            subjectId = "subj_1",
            subjectName = "Mathématiques",
            teacherUid = "prof_1",
            teacherName = "Amina Farah",
            lessonDate = "2026-11-10",
            chapter = "Fractions",
            title = "Titre initial",
            summary = "Résumé initial de cours"
        )
        var savedTitle: String? = null

        composeRule.setContent {
            EcoleConnecteeTheme {
                CahierTexteScreen(
                    profile = teacherProfile,
                    schools = listOf(school),
                    classes = listOf(schoolClass),
                    subjects = listOf(subject),
                    students = listOf(student),
                    lessons = listOf(lesson),
                    selectedChildId = null,
                    onSelectChild = {},
                    onPublishLesson = { _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    onUpdateLesson = { _, _, _, newTitle, _, _, _, _, _, _ ->
                        savedTitle = newTitle
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("edit_lesson_les_1"))
        composeRule.onNodeWithTag("edit_lesson_les_1").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Modifier la leçon").assertIsDisplayed()
        composeRule.onNodeWithTag("lesson_title_input").performTextReplacement("Fractions révisées")
        composeRule.onNodeWithTag("save_lesson_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Fractions révisées", savedTitle)
    }

    @Test
    fun gradesScreen_editAssessmentDialog_invokesCallback() {
        val grade = AssessmentGrade(
            id = "grd_1",
            schoolId = "sch_1",
            classId = "cls_1",
            className = "6ème A",
            subjectId = "subj_1",
            subjectName = "Mathématiques",
            studentId = "stu_1",
            studentName = "Ali Hassan",
            teacherUid = "prof_1",
            title = "Devoir 1",
            assessmentType = "devoir",
            score = 14.0,
            maxScore = 20.0,
            coefficient = 2.0,
            period = "Trimestre 1",
            date = "2026-11-10"
        )
        var savedScore: Double? = null
        var savedTitle: String? = null

        composeRule.setContent {
            EcoleConnecteeTheme {
                GradesAndBulletinsScreen(
                    profile = teacherProfile,
                    schools = listOf(school),
                    students = listOf(student),
                    subjects = listOf(subject),
                    assessments = listOf(grade),
                    attendance = emptyList(),
                    reportCards = emptyList<ReportCard>(),
                    selectedChildId = null,
                    onSelectChild = {},
                    onRecordAssessment = { _, _, _, _, _, _, _, _, _, _ -> },
                    onGenerateReportCard = { _, _, _, _, _, _, _, _, _, _, _ -> },
                    onUpdateAssessment = { _, newTitle, _, newScore, _, _, _, _, _ ->
                        savedTitle = newTitle
                        savedScore = newScore
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("edit_grade_grd_1"))
        composeRule.onNodeWithTag("edit_grade_grd_1").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("grade_title_input").performTextReplacement("Devoir 1 corrigé")
        composeRule.onNodeWithTag("grade_score_input").performTextReplacement("17.5")
        composeRule.onNodeWithTag("save_grade_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Devoir 1 corrigé", savedTitle)
        assertNotNull(savedScore)
        assertEquals(17.5, savedScore!!, 0.01)
    }

    @Test
    fun attendanceScreen_editAttendanceButton_opensDialogAndUpdates() {
        val record = AttendanceRecord(
            id = "att_1",
            schoolId = "sch_1",
            classId = "cls_1",
            className = "6ème A",
            studentId = "stu_1",
            studentName = "Ali Hassan",
            teacherUid = "prof_1",
            date = "2026-11-10",
            status = "absent",
            minutesLate = 0,
            justified = false,
            reason = "Motif initial"
        )
        var savedReason: String? = null

        composeRule.setContent {
            EcoleConnecteeTheme {
                AttendanceAndAlertsScreen(
                    profile = teacherProfile,
                    students = listOf(student),
                    subjects = listOf(subject),
                    attendance = listOf(record),
                    difficulties = emptyList<DifficultyAlert>(),
                    selectedChildId = null,
                    onSelectChild = {},
                    onRecordAttendance = { _, _, _, _, _, _ -> },
                    onReportDifficulty = { _, _, _, _, _, _ -> },
                    onResolveDifficulty = { _, _ -> },
                    onUpdateAttendance = { _, _, _, _, _, newReason ->
                        savedReason = newReason
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("edit_attendance_att_1").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Modifier présence / absence").assertIsDisplayed()
        composeRule.onNodeWithTag("save_attendance_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Motif initial", savedReason)
    }

    @Test
    fun communicationScreen_editAnnouncementDialog_invokesCallback() {
        val announcement = Announcement(
            id = "ann_1",
            scope = "school",
            schoolId = "sch_1",
            authorUid = "adm_1",
            authorName = "Inspecteur Djibouti",
            title = "Réunion parents",
            content = "Rendez-vous samedi matin.",
            priority = "normale"
        )
        var savedTitle: String? = null
        var savedContent: String? = null

        composeRule.setContent {
            EcoleConnecteeTheme {
                CommunicationScreen(
                    profile = adminProfile,
                    regions = listOf(region),
                    schools = listOf(school),
                    classes = listOf(schoolClass),
                    subjects = listOf(subject),
                    lessons = emptyList(),
                    students = listOf(student),
                    managedUsers = listOf(adminProfile, teacherProfile),
                    messages = emptyList(),
                    announcements = listOf(announcement),
                    onSendMessage = { _, _, _, _, _, _, _ -> },
                    onMarkMessageRead = {},
                    onPublishAnnouncement = { _, _, _, _, _, _, _ -> },
                    onUpdateAnnouncement = { _, newTitle, newContent, _ ->
                        savedTitle = newTitle
                        savedContent = newContent
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Annonces générales").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("edit_announcement_ann_1").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("announcement_title_input").performTextReplacement("Réunion reportée")
        composeRule.onNodeWithTag("announcement_content_input").performTextReplacement("Reportée à dimanche 9h.")
        composeRule.onNodeWithTag("save_announcement_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Réunion reportée", savedTitle)
        assertEquals("Reportée à dimanche 9h.", savedContent)
        assertTrue(savedTitle!!.isNotBlank())
    }
}
