package io.earlisreal.ejournal.domain.update

import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.regex.Pattern
import kotlin.time.Clock

class GithubUpdateManager(
    private val client: HttpClient,
    private val settingsRepository: SettingsRepository,
    override val identity: BuildIdentity,
    private val scope: CoroutineScope,
) : UpdateManager {
    private val _state = MutableStateFlow(UpdateState(lastCheckedEpochMillis = settingsRepository.getLastUpdateCheckEpochMillis()))
    override val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var activeCheck: Job? = null

    override fun requestAutomaticCheck() {
        if (!identity.officialRelease || !settingsRepository.getAutomaticUpdateChecksEnabled()) return
        val last = settingsRepository.getLastUpdateCheckEpochMillis()
        if (last != null && now() - last < CHECK_INTERVAL_MS) return
        startCheck(manual = false)
    }

    override fun requestManualCheck() {
        if (!identity.officialRelease) {
            _state.value = UpdateState(UpdateResult.Failed("Update checks are unavailable in development builds", manual = true), _state.value.lastCheckedEpochMillis)
            return
        }
        startCheck(manual = true)
    }

    override fun dismissCurrent() {
        val current = _state.value.result
        if (current is UpdateResult.Available) _state.value = _state.value.copy(result = current.copy(dismissed = true))
    }

    private fun startCheck(manual: Boolean) {
        if (activeCheck?.isActive == true) return
        activeCheck = scope.launch { checkNow(manual) }
    }

    internal suspend fun checkNow(manual: Boolean) {
        _state.value = _state.value.copy(result = UpdateResult.Checking)
        val checkedAt = now()
        val result = try {
            fetchLatest().let { release ->
                if (compareVersions(release.version, identity.version) <= 0) UpdateResult.Current
                else UpdateResult.Available(release)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            UpdateResult.Failed(error.message ?: "Could not check for updates", manual)
        }
        settingsRepository.setLastUpdateCheckEpochMillis(checkedAt)
        _state.value = UpdateState(result, checkedAt)
    }

    private suspend fun fetchLatest(): AvailableUpdate {
        val body = client.get(RELEASES_URL) {
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            header("User-Agent", "eJournal/${identity.version}")
        }.bodyAsText()
        val json = Json.parseToJsonElement(body).jsonObject
        check(json["draft"]?.jsonPrimitive?.booleanOrNull == false) { "GitHub release metadata is draft or missing" }
        check(json["prerelease"]?.jsonPrimitive?.booleanOrNull == false) { "GitHub release metadata is prerelease or missing" }
        val tag = checkNotNull(json["tag_name"]?.jsonPrimitive?.contentOrNull) { "GitHub release tag is missing" }
        val version = checkNotNull(parseReleaseVersion(tag)) { "GitHub release tag is not vMAJOR.MINOR.PATCH" }
        val url = checkNotNull(json["html_url"]?.jsonPrimitive?.contentOrNull) { "GitHub release URL is missing" }
        return AvailableUpdate(
            version = version,
            releaseUrl = url,
            releaseNotes = json["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    companion object {
        private const val RELEASES_URL = "https://api.github.com/repos/earlisreal/eJournal/releases/latest"
        private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
        private val RELEASE_PATTERN = Pattern.compile("^v(\\d+)\\.(\\d+)\\.(\\d+)$")

        internal fun parseReleaseVersion(tag: String): String? {
            val matcher = RELEASE_PATTERN.matcher(tag)
            return if (matcher.matches()) "${matcher.group(1)}.${matcher.group(2)}.${matcher.group(3)}" else null
        }

        internal fun compareVersions(left: String, right: String): Int {
            val a = left.split('.').mapNotNull(String::toIntOrNull)
            val b = right.split('.').mapNotNull(String::toIntOrNull)
            if (a.size != 3 || b.size != 3) return 0
            return (0..2).firstNotNullOfOrNull { index ->
                a[index].compareTo(b[index]).takeIf { it != 0 }
            } ?: 0
        }

        private fun now(): Long = Clock.System.now().toEpochMilliseconds()
    }
}
