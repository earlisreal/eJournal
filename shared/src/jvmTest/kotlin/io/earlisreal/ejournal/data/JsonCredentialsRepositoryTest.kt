package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.AlpacaCredentials
import io.earlisreal.ejournal.data.repository.TradeZeroCredentials
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
    fun `set then get round-trips credentials`() {
        val (repo, _) = newRepo()
        repo.setAlpacaCredentials(AlpacaCredentials(keyId = "PKTEST123", secretKey = "secret456"))
        assertEquals(AlpacaCredentials("PKTEST123", "secret456"), repo.getAlpacaCredentials())
    }

    @Test
    fun `get returns null when file is missing`() {
        val (repo, file) = newRepo()
        assertTrue(!file.exists())
        assertNull(repo.getAlpacaCredentials())
    }

    @Test
    fun `get returns null on malformed json`() {
        val (repo, file) = newRepo()
        file.writeText("{not json!!")
        assertNull(repo.getAlpacaCredentials())
    }

    @Test
    fun `get returns null when fields are blank`() {
        val (repo, file) = newRepo()
        file.writeText("""{"alpaca":{"keyId":"","secretKey":""}}""")
        assertNull(repo.getAlpacaCredentials())
    }

    @Test
    fun `get returns null when alpaca section is absent`() {
        val (repo, file) = newRepo()
        file.writeText("""{"someOtherProvider":{"apiKey":"x"}}""")
        assertNull(repo.getAlpacaCredentials())
    }

    @Test
    fun `set preserves unknown sections from other providers`() {
        val (repo, file) = newRepo()
        file.writeText("""{"other":{"apiKey":"keep-me"}}""")
        repo.setAlpacaCredentials(AlpacaCredentials("id", "secret"))
        assertTrue(file.readText().contains("keep-me"))
        assertEquals(AlpacaCredentials("id", "secret"), repo.getAlpacaCredentials())
    }

    @Test
    fun `set writes file with owner-only permissions`() {
        val (repo, file) = newRepo()
        repo.setAlpacaCredentials(AlpacaCredentials("id", "secret"))
        val perms = Files.getPosixFilePermissions(file)
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            perms,
        )
    }

    @Test
    fun `set creates the directory if missing`() {
        val parent = Files.createTempDirectory("ejournal-test")
        val dir = parent.resolve("nested")
        val repo = JsonCredentialsRepository(dir)
        repo.setAlpacaCredentials(AlpacaCredentials("id", "secret"))
        assertEquals(AlpacaCredentials("id", "secret"), repo.getAlpacaCredentials())
    }

    // ── Trade Zero ──────────────────────────────────────────────────────────────

    @Test
    fun `set then get round-trips tradeZero credentials`() {
        val (repo, _) = newRepo()
        repo.setTradeZeroCredentials(TradeZeroCredentials(keyId = "tzk_123", secretKey = "tzsec_abc"))
        assertEquals(TradeZeroCredentials("tzk_123", "tzsec_abc"), repo.getTradeZeroCredentials())
    }

    @Test
    fun `get returns null when tradeZero section is absent`() {
        val (repo, file) = newRepo()
        file.writeText("""{"alpaca":{"keyId":"x","secretKey":"y"}}""")
        assertNull(repo.getTradeZeroCredentials())
    }

    @Test
    fun `get returns null when tradeZero fields are blank`() {
        val (repo, file) = newRepo()
        file.writeText("""{"tradeZero":{"keyId":"","secretKey":""}}""")
        assertNull(repo.getTradeZeroCredentials())
    }

    @Test
    fun `set tradeZero preserves existing alpaca section`() {
        val (repo, _) = newRepo()
        repo.setAlpacaCredentials(AlpacaCredentials("ak", "sk"))
        repo.setTradeZeroCredentials(TradeZeroCredentials("tzk", "tzs"))
        assertEquals(AlpacaCredentials("ak", "sk"), repo.getAlpacaCredentials())
        assertEquals(TradeZeroCredentials("tzk", "tzs"), repo.getTradeZeroCredentials())
    }
}
