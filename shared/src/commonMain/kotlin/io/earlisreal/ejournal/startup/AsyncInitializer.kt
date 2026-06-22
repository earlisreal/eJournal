package io.earlisreal.ejournal.startup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.cancellation.CancellationException

/** Result of asynchronous startup initialization. */
sealed interface InitState<out T> {
    data object Loading : InitState<Nothing>
    data class Ready<T>(val value: T) : InitState<T>
    data class Failed(val message: String) : InitState<Nothing>
}

/**
 * Runs [build] off the UI thread and publishes the outcome as [state]. UI-agnostic and generic so
 * the state machine can be tested without constructing real app dependencies.
 */
class AsyncInitializer<T : Any>(private val build: suspend () -> T) {
    private val _state = MutableStateFlow<InitState<T>>(InitState.Loading)
    val state: StateFlow<InitState<T>> = _state.asStateFlow()

    suspend fun run() {
        _state.value = InitState.Loading
        _state.value = try {
            InitState.Ready(build())
        } catch (c: CancellationException) {
            throw c // never swallow cancellation
        } catch (t: Throwable) {
            InitState.Failed(t.message ?: t::class.simpleName ?: "Initialization failed")
        }
    }
}
