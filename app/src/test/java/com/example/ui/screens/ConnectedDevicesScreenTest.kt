package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.ConnectedDeviceSession
import com.example.data.model.QrPairingRequest
import com.example.ui.theme.EcoleConnecteeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ConnectedDevicesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun connectDeviceFlow_opensScannerAndTriggersManualInspect() {
        var inspectedCode: String? = null

        composeRule.setContent {
            EcoleConnecteeTheme {
                ConnectedDevicesScreen(
                    devices = emptyList(),
                    pendingInspection = null,
                    isInspecting = false,
                    isBusy = false,
                    onInspectQrCode = { inspectedCode = it },
                    onDismissInspection = {},
                    onConfirmPairing = {},
                    onRejectPairing = {},
                    onRevokeDevice = {},
                    onRevokeAllDevices = {}
                )
            }
        }

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Aucun ordinateur associé"))
        composeRule.onNodeWithText("Aucun ordinateur associé").assertIsDisplayed()
        composeRule.onNodeWithTag("connect_device_button").performScrollTo().performClick()

        composeRule.onNodeWithTag("manual_qr_code_input").performTextReplacement("req_djib_123456")
        composeRule.onNodeWithTag("manual_qr_submit_button").performClick()

        assertEquals("req_djib_123456", inspectedCode)
    }

    @Test
    fun pairingConfirmationDialog_displaysComputerMetadataAndConfirmsOrRejects() {
        var confirmedRequest: QrPairingRequest? = null
        var rejectedRequest: QrPairingRequest? = null
        val pendingReq = QrPairingRequest(
            id = "req_999",
            requestId = "req_999",
            browserName = "Chrome",
            osName = "Windows 11",
            userAgentLabel = "Chrome 131 sur Windows 11",
            status = "pending"
        )

        composeRule.setContent {
            EcoleConnecteeTheme {
                ConnectedDevicesScreen(
                    devices = emptyList(),
                    pendingInspection = pendingReq,
                    isInspecting = false,
                    isBusy = false,
                    onInspectQrCode = {},
                    onDismissInspection = {},
                    onConfirmPairing = { confirmedRequest = it },
                    onRejectPairing = { rejectedRequest = it },
                    onRevokeDevice = {},
                    onRevokeAllDevices = {}
                )
            }
        }

        composeRule.onNodeWithText("Autoriser cet ordinateur ?").assertIsDisplayed()
        composeRule.onNodeWithText("Navigateur : Chrome").assertIsDisplayed()
        composeRule.onNodeWithText("Système : Windows 11").assertIsDisplayed()

        composeRule.onNodeWithTag("reject_qr_pairing_button").performClick()
        assertEquals("req_999", rejectedRequest?.requestId)

        composeRule.onNodeWithTag("confirm_qr_pairing_button").performClick()
        assertEquals("req_999", confirmedRequest?.requestId)
    }

    @Test
    fun activeSessions_displayMetadataAndSupportSingleAndBulkDisconnect() {
        var revokedSingle: ConnectedDeviceSession? = null
        var revokedAllCalled = false
        val session = ConnectedDeviceSession(
            id = "sess_1",
            sessionId = "sess_1",
            userId = "user_1",
            userRole = "directeur",
            browserName = "Firefox",
            osName = "macOS",
            userAgentLabel = "Firefox sur macOS",
            revoked = false
        )

        composeRule.setContent {
            EcoleConnecteeTheme {
                ConnectedDevicesScreen(
                    devices = listOf(session),
                    pendingInspection = null,
                    isInspecting = false,
                    isBusy = false,
                    onInspectQrCode = {},
                    onDismissInspection = {},
                    onConfirmPairing = {},
                    onRejectPairing = {},
                    onRevokeDevice = { revokedSingle = it },
                    onRevokeAllDevices = { revokedAllCalled = true }
                )
            }
        }

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Firefox • macOS"))
        composeRule.onNodeWithText("Firefox • macOS").assertIsDisplayed()
        composeRule.onNodeWithText("1 session(s) ordinateur active(s)").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("disconnect_device_sess_1").performScrollTo().performClick()
        assertEquals("sess_1", revokedSingle?.sessionId)

        composeRule.onNodeWithTag("revoke_all_devices_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_revoke_all_button").performClick()
        assertTrue(revokedAllCalled)
    }

    @Test
    fun webPortalLink_isDisplayedAndCopyableToClipboard() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val testUrl = "https://gen-lang-client-0442156732.web.app"

        composeRule.setContent {
            EcoleConnecteeTheme {
                ConnectedDevicesScreen(
                    devices = emptyList(),
                    pendingInspection = null,
                    isInspecting = false,
                    isBusy = false,
                    onInspectQrCode = {},
                    onDismissInspection = {},
                    onConfirmPairing = {},
                    onRejectPairing = {},
                    onRevokeDevice = {},
                    onRevokeAllDevices = {},
                    webPortalUrl = testUrl
                )
            }
        }

        composeRule.onNodeWithTag("web_portal_url_text").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(testUrl).assertIsDisplayed()
        composeRule.onNodeWithTag("copy_web_link_button").performScrollTo().performClick()

        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals(testUrl, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        composeRule.onNodeWithTag("web_link_copied_feedback").performScrollTo().assertIsDisplayed()
    }
}
