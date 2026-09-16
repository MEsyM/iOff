package cz.ioff.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IOffEndToEndTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun focusSessionAndPrimaryNavigationWorkEndToEnd() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Today’s One Thing").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("What makes today count?").performTextInput("Finish production UI")
        compose.onNodeWithText("▶  Start Focus").performClick()
        compose.onNodeWithText("Finish one thing.").assertIsDisplayed()
        compose.onNodeWithText("▶  Begin Focus").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Deep Work").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Deep Work").assertIsDisplayed()

        compose.onNodeWithContentDescription("End").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Protected.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Protected.").assertIsDisplayed()

        compose.onNodeWithText("Save session  ✓").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Finish one thing.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Finish one thing.").assertIsDisplayed()

        compose.onNodeWithTag("tab_ideas").performClick()
        compose.onNodeWithText("Idea Parking").assertIsDisplayed()
        compose.onNodeWithTag("tab_progress").performClick()
        compose.onNodeWithText("Your Attention Insights", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("tab_more").performClick()
        compose.onNodeWithText("Daily Shutdown").assertIsDisplayed()

        compose.onNodeWithText("Blocked Apps").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Choose which apps iOff blocks", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Choose which apps iOff blocks", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Daily Shutdown").assertIsDisplayed()

        val preferences = compose.activity.getSharedPreferences("ioff", 0)
        assertEquals(1, preferences.getInt(MetricKeys.experiment(1, "sessions"), 0))
    }
}
