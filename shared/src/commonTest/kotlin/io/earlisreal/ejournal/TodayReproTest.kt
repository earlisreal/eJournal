package io.earlisreal.ejournal

import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression guard for the kotlinx-datetime 0.6.x→0.7.x skew: material3 forces datetime 0.7.x at
 * runtime, which removed `kotlinx.datetime.Clock` (moved to `kotlin.time.Clock`). This runs on the
 * resolved runtime, so it fails with NoClassDefFoundError if the Clock reference regresses.
 */
class TodayReproTest {
    @Test
    fun canGetToday() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        assertTrue(today.year > 2000)
    }
}
