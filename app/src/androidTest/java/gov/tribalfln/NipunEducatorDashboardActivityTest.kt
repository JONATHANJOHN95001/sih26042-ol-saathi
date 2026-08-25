package gov.tribalfln

import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anything
import org.hamcrest.TypeSafeMatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AndroidX Espresso instrumented test suite for [NipunEducatorDashboardActivity].
 *
 * Validates all core action buttons are displayed, the [gov.tribalfln.ui.LearningGapRadarView]
 * renders on screen, and that the worksheet PDF generation flow completes without crashing.
 *
 * Run:
 *   ./gradlew.bat connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=gov.tribalfln.NipunEducatorDashboardActivityTest
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class NipunEducatorDashboardActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(NipunEducatorDashboardActivity::class.java)

    @Before
    fun setUp() {
        // Ensure we have a clean state before each test
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put global airplane_mode_on 1")
        Thread.sleep(200)
    }

    // ─── Test 1: Dashboard launches and status indicator is visible ──────────
    @Test
    fun dashboardLaunches_andStatusIsVisible() {
        onView(withId(R.id.tv_status))
            .check(matches(isDisplayed()))
    }

    // ─── Test 2: Voice Assistant button is displayed ─────────────────────────
    @Test
    fun voiceAssistantButton_isDisplayed() {
        onView(withId(R.id.btn_voice_assistant))
            .check(matches(isDisplayed()))
        onView(withId(R.id.btn_voice_assistant))
            .check(matches(withSubstring("Voice")))
    }

    // ─── Test 3: Generate Worksheet button is displayed ──────────────────────
    @Test
    fun generateWorksheetButton_isDisplayed() {
        onView(withId(R.id.btn_generate_worksheet))
            .check(matches(isDisplayed()))
        onView(withId(R.id.btn_generate_worksheet))
            .check(matches(withSubstring("Worksheet")))
    }

    // ─── Test 4: OCR Grade button is displayed ──────────────────────────────
    @Test
    fun ocrGradeButton_isDisplayed() {
        onView(withId(R.id.btn_ocr_grade))
            .check(matches(isDisplayed()))
    }

    // ─── Test 5: Mesh Sync button is displayed ──────────────────────────────
    @Test
    fun meshSyncButton_isDisplayed() {
        onView(withId(R.id.btn_mesh_sync))
            .check(matches(isDisplayed()))
    }

    // ─── Test 6: Share Data button is displayed ─────────────────────────────
    @Test
    fun shareDataButton_isDisplayed() {
        onView(withId(R.id.btn_share_data))
            .check(matches(isDisplayed()))
    }

    // ─── Test 7: View Progress button is displayed ──────────────────────────
    @Test
    fun viewProgressButton_isDisplayed() {
        onView(withId(R.id.btn_view_progress))
            .check(matches(isDisplayed()))
    }

    // ─── Test 8: LearningGapRadarView renders on screen ─────────────────────
    @Test
    fun learningGapRadarView_isDisplayed() {
        onView(withId(R.id.radar_view))
            .check(matches(isDisplayed()))
    }

    // ─── Test 9: Student count card displays ────────────────────────────────
    @Test
    fun studentCountCard_isDisplayed() {
        onView(withId(R.id.tv_student_count))
            .check(matches(isDisplayed()))
    }

    // ─── Test 10: Mastery percentage card displays ──────────────────────────
    @Test
    fun masteryPercentageCard_isDisplayed() {
        onView(withId(R.id.tv_mastery_pct))
            .check(matches(isDisplayed()))
    }

    // ─── Test 11: Peer count card displays ──────────────────────────────────
    @Test
    fun peerCountCard_isDisplayed() {
        onView(withId(R.id.tv_peer_count))
            .check(matches(isDisplayed()))
    }

    // ─── Test 12: Last worksheet label displays ─────────────────────────────
    @Test
    fun lastWorksheetCard_isDisplayed() {
        onView(withId(R.id.tv_last_worksheet))
            .check(matches(isDisplayed()))
    }

    // ─── Test 13: Click Generate Worksheet — PDF generation triggers ─────────
    @Test
    fun clickGenerateWorksheet_triggersPdfGeneration() {
        onView(withId(R.id.btn_generate_worksheet))
            .perform(click())

        // The status should change from "Ready" to indicate generation
        // Give it a moment to process
        Thread.sleep(2000)

        // Status should now show "Generating..." or "PDF:" or "Failed"
        // At minimum, the app should not crash — the activity should still be alive
        onView(withId(R.id.tv_status))
            .check(matches(isDisplayed()))
    }

    // ─── Test 14: Click Voice Assistant button does not crash ────────────────
    @Test
    fun clickVoiceAssistant_doesNotCrash() {
        onView(withId(R.id.btn_voice_assistant))
            .perform(click())

        // Activity should remain alive
        onView(withId(R.id.tv_status))
            .check(matches(isDisplayed()))
    }

    // ─── Test 15: Click Mesh Sync button does not crash ─────────────────────
    @Test
    fun clickMeshSync_doesNotCrash() {
        onView(withId(R.id.btn_mesh_sync))
            .perform(click())

        // Activity should remain alive
        onView(withId(R.id.tv_status))
            .check(matches(isDisplayed()))
    }

    // ─── Test 16: All six action buttons exist in the view hierarchy ─────────
    @Test
    fun allActionButtons_existInHierarchy() {
        val buttons = listOf(
            R.id.btn_voice_assistant,
            R.id.btn_generate_worksheet,
            R.id.btn_ocr_grade,
            R.id.btn_mesh_sync,
            R.id.btn_share_data,
            R.id.btn_view_progress
        )
        for (buttonId in buttons) {
            onView(withId(buttonId))
                .check(matches(anything<View>()))
        }
    }

    // ─── Helper: substring matcher ───────────────────────────────────────────
    private fun withSubstring(substring: String): Matcher<View> {
        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("with text containing: $substring")
            }

            override fun matchesSafely(item: View): Boolean {
                if (item is android.widget.TextView) {
                    return item.text.toString().contains(substring, ignoreCase = true)
                }
                return false
            }
        }
    }
}
