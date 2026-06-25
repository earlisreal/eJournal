package io.earlisreal.ejournal.ui.viewmodel

import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.parser.ParseResult
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.testutil.FakePortfolioSettingsRepository
import io.earlisreal.ejournal.testutil.FakeTransactionRepository
import io.earlisreal.ejournal.testutil.tx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    // A parser that yields fixed transactions, one of which the repository will treat as a duplicate.
    private fun fixedParser(transactions: List<Transaction>) = object : TransactionParser {
        override val brokerName = "fixed"
        override val supportedExtensions = listOf("csv")
        override fun detect(content: ByteArray) = true
        override fun parse(content: ByteArray, portfolioId: Long) = ParseResult(transactions)
    }

    @Test
    fun importReportsInsertedCountExcludingDuplicates() = runTest {
        val dupId = "tzcsv:dup#0"
        val repo = FakeTransactionRepository(duplicateExternalIds = setOf(dupId))
        val parser = fixedParser(
            listOf(tx(externalId = "a"), tx(externalId = dupId), tx(externalId = "b")),
        )
        val vm = ImportViewModel(repo, listOf(parser), FakePortfolioSettingsRepository())

        vm.parseFiles(listOf(ByteArray(1)), portfolioId = 1L)
        vm.state.first { it.parsedTransactions.size == 3 }

        vm.import(onSuccess = {})
        val success = vm.state.first { it.status is ImportStatus.Success }.status as ImportStatus.Success

        assertEquals(2, success.count) // 3 parsed, 1 was a duplicate skip
        assertEquals(2, repo.inserted.size)
    }
}
