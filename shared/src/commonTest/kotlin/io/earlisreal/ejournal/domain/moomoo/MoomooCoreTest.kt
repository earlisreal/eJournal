package io.earlisreal.ejournal.domain.moomoo

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class MoomooCoreTest {
    @Test
    fun accountEligibilityRequiresActiveRealNormalAndUsAuthorization() {
        fun account(
            environment: MoomooAccountEnvironment = MoomooAccountEnvironment.REAL,
            role: MoomooAccountRole = MoomooAccountRole.NORMAL,
            markets: Set<MoomooMarket> = setOf(MoomooMarket.US),
            active: Boolean = true,
        ) = MoomooAccount("1", "Account", "Firm", environment, role, markets, active)

        val eligible = listOf(
            account(),
            account(environment = MoomooAccountEnvironment.SIMULATE),
            account(role = MoomooAccountRole.MASTER),
            account(role = MoomooAccountRole.IPO),
            account(markets = setOf(MoomooMarket.OTHER)),
            account(active = false),
        ).eligibleForUsStocks()

        assertEquals(1, eligible.size)
    }

    @Test
    fun windowsAreOldestFirstAndNeverExceedNinetyDays() {
        assertEquals(
            listOf(
                MoomooWindow(LocalDate(2018, 1, 1), LocalDate(2018, 3, 31)),
                MoomooWindow(LocalDate(2018, 4, 1), LocalDate(2018, 6, 29)),
                MoomooWindow(LocalDate(2018, 6, 30), LocalDate(2018, 7, 1)),
            ),
            moomooWindows(LocalDate(2018, 1, 1), LocalDate(2018, 7, 1)),
        )
    }

    @Test
    fun initialSyncStartsAtOneYearAndClampsOldCheckpoints() {
        val today = LocalDate(2026, 8, 9)

        assertEquals(LocalDate(2025, 8, 9), moomooSyncStart(today, null))
        assertEquals(LocalDate(2025, 8, 9), moomooSyncStart(today, LocalDate(2018, 1, 1)))
        assertEquals(LocalDate(2026, 8, 6), moomooSyncStart(today, today))
    }

    @Test
    fun rateLimiterSpacesRequestsByThreeSeconds() = runTest {
        var clock = 0L
        val waits = mutableListOf<Long>()
        val limiter = MoomooRateLimiter(
            nowMillis = { clock },
            sleep = { millis -> waits += millis; clock += millis },
        )

        repeat(11) { limiter.awaitPermit() }

        assertEquals(List(10) { 3_000L }, waits)
        assertEquals(30_000L, clock)
    }
}
