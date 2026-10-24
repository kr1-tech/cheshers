package app.cheshers

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cheshers.ui.OnboardingScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun skipCompletesOnboarding() {
        var completed = false
        compose.setContent { MaterialTheme { OnboardingScreen(onDone = { completed = true }) } }
        compose.onNodeWithText("Welcome to Cheshers").assertIsDisplayed()
        compose.onNodeWithText("Skip").performClick()
        compose.runOnIdle { assertTrue(completed) }
    }

    @Test
    fun nextAdvancesTutorial() {
        compose.setContent { MaterialTheme { OnboardingScreen(onDone = {}) } }
        compose.onNodeWithText("Next").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Your game, your way").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your game, your way").assertIsDisplayed()
    }
}
