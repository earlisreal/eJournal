package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.AlpacaBrokerCredentials
import io.earlisreal.ejournal.data.repository.AlpacaMarketDataCredentials
import io.earlisreal.ejournal.data.repository.TradeZeroBrokerCredentials
import io.earlisreal.ejournal.domain.alpaca.AlpacaEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonCredentialsRepositoryTest {

    private fun newRepo(dir: Path = Files.createTempDirectory("ejournal-test")): Pair<JsonCredentialsRepository, Path> =
        JsonCredentialsRepository(dir) to dir.resolve("credentials.json")

    @Test
    fun `global Alpaca market-data credentials round trip`() {
        val (repo, _) = newRepo()
        repo.setAlpacaMarketDataCredentials(AlpacaMarketDataCredentials("PKTEST123", "secret456"))
        assertEquals(AlpacaMarketDataCredentials("PKTEST123", "secret456"), repo.getAlpacaMarketDataCredentials())
    }

    @Test
    fun `old global environment property is ignored`() {
        val (repo, file) = newRepo()
        file.writeText("""{"alpaca":{"keyId":"id","secretKey":"secret","environment":"LIVE"}}""")
        assertEquals(AlpacaMarketDataCredentials("id", "secret"), repo.getAlpacaMarketDataCredentials())
    }

    @Test
    fun `malformed or incomplete global JSON returns null`() {
        val (repo, file) = newRepo()
        file.writeText("{not json!!")
        assertNull(repo.getAlpacaMarketDataCredentials())
        file.writeText("""{"alpaca":{"keyId":"","secretKey":""}}""")
        assertNull(repo.getAlpacaMarketDataCredentials())
    }

    @Test
    fun `portfolio credentials round trip independently`() {
        val (repo, _) = newRepo()
        val alpaca = AlpacaBrokerCredentials("a-key", "a-secret", AlpacaEnvironment.LIVE)
        val tradeZero = TradeZeroBrokerCredentials("t-key", "t-secret")
        repo.setPortfolioBrokerCredentials("ref-a", alpaca)
        repo.setPortfolioBrokerCredentials("ref-b", tradeZero)

        assertEquals(alpaca, repo.getPortfolioBrokerCredentials("ref-a"))
        assertEquals(tradeZero, repo.getPortfolioBrokerCredentials("ref-b"))
    }

    @Test
    fun `portfolio changes do not affect global or other portfolios`() {
        val (repo, _) = newRepo()
        val global = AlpacaMarketDataCredentials("global", "global-secret")
        val first = AlpacaBrokerCredentials("one", "one-secret", AlpacaEnvironment.PAPER)
        val second = TradeZeroBrokerCredentials("two", "two-secret")
        repo.setAlpacaMarketDataCredentials(global)
        repo.setPortfolioBrokerCredentials("one", first)
        repo.setPortfolioBrokerCredentials("two", second)

        repo.setPortfolioBrokerCredentials("one", AlpacaBrokerCredentials("changed", "changed-secret", AlpacaEnvironment.LIVE))
        assertEquals(global, repo.getAlpacaMarketDataCredentials())
        assertEquals(second, repo.getPortfolioBrokerCredentials("two"))

        repo.setAlpacaMarketDataCredentials(AlpacaMarketDataCredentials("new-global", "new-secret"))
        assertEquals(AlpacaBrokerCredentials("changed", "changed-secret", AlpacaEnvironment.LIVE), repo.getPortfolioBrokerCredentials("one"))
    }

    @Test
    fun `deleting one portfolio credential preserves the rest`() {
        val (repo, file) = newRepo()
        repo.setPortfolioBrokerCredentials("one", TradeZeroBrokerCredentials("one", "secret"))
        repo.setPortfolioBrokerCredentials("two", TradeZeroBrokerCredentials("two", "secret"))
        repo.deletePortfolioBrokerCredentials("one")

        assertNull(repo.getPortfolioBrokerCredentials("one"))
        assertEquals(TradeZeroBrokerCredentials("two", "secret"), repo.getPortfolioBrokerCredentials("two"))
        assertTrue(file.readText().contains("two"))
    }

    @Test
    fun `unknown sections survive writes`() {
        val (repo, file) = newRepo()
        file.writeText("""{"other":{"apiKey":"keep-me"},"tradeZero":{"keyId":"legacy"}}""")
        repo.setAlpacaMarketDataCredentials(AlpacaMarketDataCredentials("id", "secret"))
        repo.setPortfolioBrokerCredentials("ref", TradeZeroBrokerCredentials("tz", "secret"))
        val contents = file.readText()
        assertTrue(contents.contains("keep-me"))
        assertTrue(contents.contains("legacy"))
    }

    @Test
    fun `writes owner-only permissions on POSIX filesystems`() {
        val (repo, file) = newRepo()
        repo.setAlpacaMarketDataCredentials(AlpacaMarketDataCredentials("id", "secret"))
        runCatching { Files.getPosixFilePermissions(file) }
            .getOrNull()
            ?.let { perms ->
                assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), perms)
            }
    }

    @Test
    fun `creates missing directory and reads missing file safely`() {
        val parent = Files.createTempDirectory("ejournal-test")
        val dir = parent.resolve("nested")
        val repo = JsonCredentialsRepository(dir)
        assertNull(repo.getAlpacaMarketDataCredentials())
        repo.setAlpacaMarketDataCredentials(AlpacaMarketDataCredentials("id", "secret"))
        assertTrue(dir.resolve("credentials.json").exists())
    }
}
