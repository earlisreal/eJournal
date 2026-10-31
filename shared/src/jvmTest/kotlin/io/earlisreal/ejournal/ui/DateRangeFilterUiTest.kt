package io.earlisreal.ejournal.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.ui.components.DateRangeFilter
import io.earlisreal.ejournal.ui.components.monthName
import io.earlisreal.ejournal.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class DateRangeFilterUiTest {
    @BeforeTest fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @AfterTest fun cleanup() = Dispatchers.resetMain()

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }
    private fun ImageComposeScene.nodes() = semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }
    private fun SemanticsNode.text() = config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    private fun SemanticsNode.click() {
        val action = config.getOrNull(SemanticsActions.OnClick)?.action
        if (action != null) assertTrue(action()) else requireNotNull(parent).click()
    }

    @Test
    fun presetsApplyOnlyOnConfirmationAndCustomDatesStayEditable() = runTest {
        var preset by mutableStateOf(DateRangePreset.THIS_MONTH)
        var customRange by mutableStateOf<DateRange?>(null)
        var enabled by mutableStateOf(true)
        val changes = mutableListOf<Pair<DateRangePreset, DateRange?>>()
        val scene = ImageComposeScene(width = 1000, height = 900, coroutineContext = coroutineContext) {
            AppTheme(darkTheme = true) {
                Surface {
                    DateRangeFilter(preset, customRange, { nextPreset, nextRange ->
                        changes += nextPreset to nextRange
                        preset = nextPreset
                        customRange = nextRange
                    }, enabled = enabled)
                }
            }
        }
        var frameTime = 0L
        fun frame() { repeat(4) { runCurrent(); frameTime += 100_000_000; scene.render(frameTime) } }
        fun open() {
            scene.nodes().single { it.config.getOrNull(SemanticsActions.OnClick)?.label == "Choose date range" }.click()
            frame()
        }
        fun click(label: String) { scene.nodes().single { it.text() == label }.click(); frame() }
        fun selected(label: String) = scene.nodes().single { it.text() == label }
            .config.getOrNull(SemanticsProperties.Selected) == true
        try {
            frame()
            assertFalse(scene.nodes().any { it.text() == "Week" })
            open()
            for (label in listOf("Week", "Month", "Quarter", "YTD", "Last Year", "All Time")) {
                assertTrue(scene.nodes().any { it.text() == label }, label)
            }
            assertTrue(selected("Month"))
            click("Last Year")
            assertTrue(selected("Last Year"))
            assertTrue(changes.isEmpty())
            click("Cancel")
            assertTrue(changes.isEmpty())
            open()
            assertTrue(selected("Month"))
            click("Last Year")
            click("Apply")
            assertEquals(DateRangePreset.LAST_YEAR to null, changes.last())
            open()
            assertTrue(selected("Last Year"))
            click("All Time")
            click("Apply")
            assertEquals(DateRangePreset.ALL_TIME to null, changes.last())

            preset = DateRangePreset.CUSTOM
            customRange = DateRange(LocalDate(2024, 1, 5), LocalDate(2024, 2, 6))
            frame()
            assertTrue(scene.nodes().any { it.text().contains("2024-01-05 – 2024-02-06") })
            open()
            click("Apply")
            assertEquals(DateRangePreset.CUSTOM to customRange, changes.last())

            preset = DateRangePreset.THIS_MONTH
            customRange = null
            frame()
            open()
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val month = monthName(today.month.number)
            scene.nodes().single { it.text().endsWith("$month 1, ${today.year}") }.click()
            frame()
            assertFalse(selected("Month"))
            assertTrue(SemanticsProperties.Disabled in scene.nodes().single { it.text() == "Apply" }.config)
            scene.nodes().single { it.text().endsWith("$month 2, ${today.year}") }.click()
            frame()
            click("Apply")
            assertEquals(
                DateRangePreset.CUSTOM to DateRange(LocalDate(today.year, today.month, 1), LocalDate(today.year, today.month, 2)),
                changes.last(),
            )

            open()
            enabled = false
            frame()
            assertFalse(scene.nodes().any { it.text() == "Apply" })
        } finally {
            scene.close()
        }
    }
}
