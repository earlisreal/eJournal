package io.earlisreal.ejournal.domain.update

import io.earlisreal.ejournal.data.repository.FilterPrefs
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi

private class UpdateSettings(
    var automatic: Boolean = true,
    var lastCheck: Long? = null,
) : SettingsRepository {
    override fun getThemeMode() = io.earlisreal.ejournal.ui.theme.ThemeMode.SYSTEM
    override fun setThemeMode(mode: io.earlisreal.ejournal.ui.theme.ThemeMode) = Unit
    override fun getFilterPrefs(): FilterPrefs? = null
    override fun setFilterPrefs(prefs: FilterPrefs) = Unit
    override fun getAutomaticUpdateChecksEnabled() = automatic
    override fun setAutomaticUpdateChecksEnabled(enabled: Boolean) { automatic = enabled }
    override fun getLastUpdateCheckEpochMillis() = lastCheck
    override fun setLastUpdateCheckEpochMillis(epochMillis: Long) { lastCheck = epochMillis }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GithubUpdateManagerTest {
    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun manager(
        body: String,
        settings: UpdateSettings = UpdateSettings(),
        requestCapture: (HttpRequestData) -> Unit = {},
        scope: kotlinx.coroutines.CoroutineScope,
    ): GithubUpdateManager {
        val client = HttpClient(MockEngine { request ->
            requestCapture(request)
            json(body)
        })
        return GithubUpdateManager(
            client = client,
            settingsRepository = settings,
            identity = BuildIdentity("1.0.0", officialRelease = true, distribution = "msi"),
            scope = scope,
        )
    }

    @Test
    fun `release tags are strict and versions compare numerically`() {
        assertEquals("1.2.3", GithubUpdateManager.parseReleaseVersion("v1.2.3"))
        assertNull(GithubUpdateManager.parseReleaseVersion("1.2.3"))
        assertNull(GithubUpdateManager.parseReleaseVersion("v1.2.3-beta"))
        assertEquals(1, GithubUpdateManager.compareVersions("1.10.0", "1.9.9"))
    }

    @Test
    fun `manual check reports a newer stable release and uses the fixed user agent`() = runTest {
        var request: HttpRequestData? = null
        val manager = manager(
            body = """{"draft":false,"prerelease":false,"tag_name":"v1.2.0","html_url":"https://github.com/earlisreal/eJournal/releases/tag/v1.2.0","body":"notes"}""",
            requestCapture = { request = it },
            scope = this,
        )

        manager.checkNow(manual = true)

        val result = assertIs<UpdateResult.Available>(manager.state.value.result)
        assertEquals("1.2.0", result.update.version)
        assertEquals("eJournal/1.0.0", request?.headers?.get("User-Agent"))
    }

    @Test
    fun `malformed release metadata is a failed check`() = runTest {
        val manager = manager(body = """{"draft":false,"prerelease":false,"tag_name":"latest"}""", scope = this)

        manager.checkNow(manual = true)

        assertIs<UpdateResult.Failed>(manager.state.value.result)
    }

    @Test
    fun `automatic failures are non manual`() = runTest {
        val manager = manager(body = "not-json", scope = this)

        manager.checkNow(manual = false)

        assertEquals(false, assertIs<UpdateResult.Failed>(manager.state.value.result).manual)
    }

    @Test
    fun `automatic checks honor the twenty four hour throttle while manual checks bypass it`() = runTest {
        val settings = UpdateSettings(lastCheck = System.currentTimeMillis())
        var requests = 0
        val manager = manager(
            body = """{"draft":false,"prerelease":false,"tag_name":"v1.2.0","html_url":"https://github.com/earlisreal/eJournal/releases/tag/v1.2.0"}""",
            settings = settings,
            requestCapture = { requests++ },
            scope = this,
        )

        manager.requestAutomaticCheck()
        advanceUntilIdle()
        assertEquals(0, requests)

        manager.checkNow(manual = true)
        assertEquals(1, requests)
    }

    @Test
    fun `available update can be dismissed without forgetting the result`() = runTest {
        val manager = manager(
            body = """{"draft":false,"prerelease":false,"tag_name":"v1.2.0","html_url":"https://github.com/earlisreal/eJournal/releases/tag/v1.2.0"}""",
            scope = this,
        )
        manager.checkNow(manual = true)
        manager.dismissCurrent()

        assertEquals(true, assertIs<UpdateResult.Available>(manager.state.value.result).dismissed)
    }

    @Test
    fun `development builds cannot request updates`() = runTest {
        val manager = GithubUpdateManager(
            client = HttpClient(MockEngine { error("request should not run") }),
            settingsRepository = UpdateSettings(),
            identity = BuildIdentity("development", officialRelease = false, distribution = "development"),
            scope = this,
        )

        manager.requestManualCheck()

        assertIs<UpdateResult.Failed>(manager.state.value.result)
    }
}
