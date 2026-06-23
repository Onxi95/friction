package dev.pawelsowa.focusgate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class FocusGateAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsDashboard() {
        composeRule.onNodeWithText("FocusGate").assertIsDisplayed()
        composeRule.onNodeWithText("Start VPN").assertIsDisplayed()
    }

}
