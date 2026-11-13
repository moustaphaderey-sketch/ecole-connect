package com.example.ui.screens.auth

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.ui.theme.EcoleConnecteeTheme
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

        composeRule.onNodeWithTag("signup_fullname_input").performScrollTo().performTextInput("Moussa Ibrahim")
        composeRule.onNodeWithTag("signup_consent_checkbox").performScrollTo().performClick()
        composeRule.onNodeWithTag("role_signup_google_button").performScrollTo().assertIsEnabled()

        // Navigate back to role chooser and verify Parent screen
        composeRule.onNodeWithTag("signup_back_button").performClick()
        composeRule.onNodeWithTag("select_role_parent").performClick()
        composeRule.onNodeWithText("Inscription Parent").assertExists()

        // Navigate back to role chooser and verify Admin screen
        composeRule.onNodeWithTag("signup_back_button").performClick()
        composeRule.onNodeWithTag("select_role_admin").performClick()
        composeRule.onNodeWithText("Inscription Admin").assertExists()
    }
}
