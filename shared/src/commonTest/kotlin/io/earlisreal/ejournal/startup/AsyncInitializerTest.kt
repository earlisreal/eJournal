package io.earlisreal.ejournal.startup

import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class AsyncInitializerTest {

    @Test
    fun startsInLoading() {
        val init = AsyncInitializer { "x" }
        assertEquals(InitState.Loading, init.state.value)
    }

    @Test
    fun emitsReadyWithBuiltValue() = runTest {
        val init = AsyncInitializer { "ready-value" }
        init.run()
        assertEquals(InitState.Ready("ready-value"), init.state.value)
    }

    @Test
    fun emitsFailedWithMessageOnException() = runTest {
        val init = AsyncInitializer<String> { throw IllegalStateException("db is locked") }
        init.run()
        assertEquals(InitState.Failed("db is locked"), init.state.value)
    }

    @Test
    fun rethrowsCancellationExceptionInsteadOfFailing() = runTest {
        val init = AsyncInitializer<String> { throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { init.run() }
    }

    @Test
    fun rerunResetsToLoadingThenReady() = runTest {
        var attempt = 0
        val init = AsyncInitializer { "attempt-${++attempt}" }
        init.run()
        init.run()
        assertEquals(InitState.Ready("attempt-2"), init.state.value)
    }
}
