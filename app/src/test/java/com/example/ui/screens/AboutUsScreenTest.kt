package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.ui.theme.EcoleConnecteeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutUsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aboutUsScreen_displaysLogoMissionValuesAndFounderAttribution() {
        composeRule.setContent {
            EcoleConnecteeTheme {
                AboutUsScreen()
            }
        }

        composeRule.onNodeWithTag("about_us_screen").assertIsDisplayed()
        composeRule.onNodeWithText("À PROPOS DE NOUS").assertIsDisplayed()
        composeRule.onNodeWithText("ÉCOLE CONNECTÉE").assertIsDisplayed()
        composeRule.onNodeWithText("Apprendre • Suivre • Réussir ensemble").assertIsDisplayed()
        composeRule.onNodeWithText("Notre mission").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Notre vision").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Nos objectifs").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Nos valeurs").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("À qui s'adresse la plateforme ?").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Notre engagement").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Fondateur et auteur du projet").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Auteur et initiateur du projet : Moustapha Derey").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Projet : ÉCOLE CONNECTÉE").performScrollTo().assertIsDisplayed()
    }
}
