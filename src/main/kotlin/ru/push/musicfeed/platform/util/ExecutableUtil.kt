package ru.push.musicfeed.platform.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun executeWithTimeout(
    scope: CoroutineScope,
    timeoutSec: Long,
    mainAction: suspend CoroutineScope.() -> Unit,
    completeAction: () -> Unit,
    timeoutAction: () -> Unit
) {
    runBlocking {
        val job = launch(scope.coroutineContext) {
            mainAction()
        }
        var timeSecLeft = timeoutSec
        do {
            delay(1000)
            timeSecLeft--
        } while (job.isActive && timeSecLeft >= 0)

        if (job.isActive) {
            timeoutAction()
            job.cancel()
        } else if (job.isCompleted) {
            completeAction()
        }
    }
}