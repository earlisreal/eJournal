package io.earlisreal.ejournal.domain.update

import kotlinx.coroutines.flow.StateFlow

data class BuildIdentity(
    val version: String,
    val officialRelease: Boolean,
    val distribution: String,
)

data class AvailableUpdate(
    val version: String,
    val releaseUrl: String,
    val releaseNotes: String,
)

sealed class UpdateResult {
    data object Idle : UpdateResult()
    data object Checking : UpdateResult()
    data object Current : UpdateResult()
    data class Available(val update: AvailableUpdate, val dismissed: Boolean = false) : UpdateResult()
    data class Failed(val message: String, val manual: Boolean) : UpdateResult()
}

data class UpdateState(
    val result: UpdateResult = UpdateResult.Idle,
    val lastCheckedEpochMillis: Long? = null,
)

interface UpdateManager {
    val identity: BuildIdentity
    val state: StateFlow<UpdateState>

    fun requestAutomaticCheck()
    fun requestManualCheck()
    fun dismissCurrent()
}
