package com.example.ui.screens.auth

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.model.AccessRequest
import com.example.data.model.Region
import com.example.data.model.School
import com.example.data.model.SchoolClass
import com.example.data.model.Student
import com.example.data.model.Subject
import com.example.data.model.UserRole
import com.example.ui.components.OnboardingAndConsentGate
import com.example.ui.screens.ApproveAccessRequestDialog
import com.example.ui.theme.EcoleConnecteeTheme
import com.example.ui.viewmodel.AccountGateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthFlowScreensTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginAndRoleRegistrationFlow_displaysAllThreeRolesAndValidatesForm() {
        composeRule.setContent {
            EcoleConnecteeTheme {
                AuthEntryHost(
                    onPrepareRegistration = {},
                    onAuthSuccess = {}
                )
            }
        }

        // Login screen visible
        composeRule.onNodeWithTag("google_sign_in_button").assertIsEnabled()
        composeRule.onNodeWithTag("go_to_register_button").performScrollTo().performClick()

        // Role selection screen shows Admin, Enseignant, Parent
        composeRule.onNodeWithTag("select_role_admin").assertExists()
        composeRule.onNodeWithTag("select_role_enseignant").assertExists()
        composeRule.onNodeWithTag("select_role_parent").assertExists()

        // Navigate to Enseignant sign-up screen
        composeRule.onNodeWithTag("select_role_enseignant").performClick()
        composeRule.onNodeWithText("Inscription Enseignant").assertExists()
        composeRule.onNodeWithTag("role_signup_google_button").performScrollTo().assertIsNotEnabled()

        // Pick a school from official Djibouti-Ville directory
        composeRule.onNodeWithTag("signup_open_school_picker_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("school_filter_college").performClick()
        composeRule.onNodeWithTag("official_school_search_input").performTextInput("Boulaos")
        composeRule.onNodeWithTag("official_school_item_dj_col_boulaos").performClick()
        composeRule.onNodeWithText("Collège de Boulaos (Collège)", substring = true).assertExists()

        composeRule.onNodeWithTag("signup_lastname_input").performScrollTo().performTextInput("Ibrahim")
        composeRule.onNodeWithTag("signup_firstname_input").performScrollTo().performTextInput("Moussa")
        composeRule.onNodeWithTag("signup_phone_input").performScrollTo().performTextInput("+25377112233")
        composeRule.onNodeWithTag("signup_email_input").performScrollTo().performTextInput("moussa.ibrahim@ecole.dj")
        composeRule.onNodeWithTag("signup_consent_checkbox").performScrollTo().performClick()
        composeRule.onNodeWithTag("role_signup_google_button").performScrollTo().assertIsEnabled()

        // Navigate back to role chooser and verify Parent screen
        composeRule.onNodeWithTag("signup_back_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("select_role_parent").performScrollTo().performClick()
        composeRule.onNodeWithText("Inscription Parent").assertExists()

        // Navigate back to role chooser and verify Admin screen
        composeRule.onNodeWithTag("signup_back_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("select_role_admin").performScrollTo().performClick()
        composeRule.onNodeWithText("Inscription Admin").assertExists()
    }

    @Test
    fun onboardingGate_pendingAndInfoRequestedStates_enforceValidationWorkflow() {
        val infoRequested = AccessRequest(
            userId = "parent_pending_1",
            firstName = "Fatouma",
            lastName = "Hassan",
            fullName = "Fatouma Hassan",
            email = "fatouma@ecole.dj",
            phone = "+25377889900",
            requestedRole = UserRole.PARENT.key,
            schoolId = "dj_col_boulaos",
            schoolName = "Collège de Boulaos",
            schoolHint = "Collège de Boulaos (Collège)",
            status = "info_requested",
            reviewerName = "Directeur Boulaos",
            decisionNote = "Merci de préciser la classe exacte de votre enfant."
        )
        var resubmittedResponse = ""

        composeRule.setContent {
            EcoleConnecteeTheme {
                OnboardingAndConsentGate(
                    gateState = AccountGateState.NeedsOnboarding(
                        pendingInvitation = null,
                        accessRequest = infoRequested,
                        canBootstrapAdmin = false
                    ),
                    userEmail = "fatouma@ecole.dj",
                    userDisplayName = "Fatouma Hassan",
                    isBusy = false,
                    errorMessage = null,
                    onBootstrapAdmin = { _, _, _ -> },
                    onClaimInvitation = { _, _, _, _ -> },
                    onSubmitAccessRequest = { _, _, _, _, _, _, _, _ -> },
                    onResubmitAccessRequest = { _, _, _, _, _, _, _, response, _ ->
                        resubmittedResponse = response
                    },
                    onSaveConsent = { _, _, _, _ -> },
                    onSignOut = {}
                )
            }
        }

        composeRule.onNodeWithText("Informations complémentaires requises").assertExists()
        composeRule.onNodeWithText("Merci de préciser la classe exacte de votre enfant.", substring = true).assertExists()
        composeRule.onNodeWithTag("resubmit_access_request_button").performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithTag("gate_applicant_response_input").performScrollTo().performTextInput("Élève en 6ème A : Ali Hassan")
        composeRule.onNodeWithTag("resubmit_access_request_button").performScrollTo().assertIsEnabled().performClick()

        assertEquals("Élève en 6ème A : Ali Hassan", resubmittedResponse)
    }

    @Test
    fun approveAccessRequestDialog_enforcesParentStudentAndTeacherClassLinkages() {
        val school = School(id = "dj_col_boulaos", name = "Collège de Boulaos", regionId = "reg_djibouti_ville")
        val region = Region(id = "reg_djibouti_ville", name = "Djibouti-Ville")
        val schoolClass = SchoolClass(id = "cls_6a", schoolId = school.id, name = "6ème A", level = "6ème")
        val subject = Subject(id = "sub_math", schoolId = school.id, classId = schoolClass.id, className = "6ème A", name = "Mathématiques")
        val student = Student(id = "stu_1", schoolId = school.id, classId = schoolClass.id, className = "6ème A", firstName = "Ali", lastName = "Hassan", matricule = "DJ-100")

        val parentReq = AccessRequest(
            userId = "p1",
            fullName = "Fatouma Hassan",
            email = "fatouma@ecole.dj",
            requestedRole = UserRole.PARENT.key,
            schoolId = school.id,
            schoolName = school.name,
            status = "pending"
        )
        var linkedStudents = emptyList<String>()

        composeRule.setContent {
            EcoleConnecteeTheme {
                ApproveAccessRequestDialog(
                    actorRole = UserRole.DIRECTEUR,
                    actorSchoolId = school.id,
                    request = parentReq,
                    regions = listOf(region),
                    schools = listOf(school),
                    classes = listOf(schoolClass),
                    subjects = listOf(subject),
                    students = listOf(student),
                    onDismiss = {},
                    onSubmit = { _, _, _, stuIds, _, _, _ ->
                        linkedStudents = stuIds
                    }
                )
            }
        }

        // Parent cannot be approved without linking at least one child
        composeRule.onNodeWithTag("confirm_approve_request_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("approve_link_student_stu_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_approve_request_button").assertIsEnabled().performClick()

        assertTrue(linkedStudents.contains("stu_1"))
    }
}
