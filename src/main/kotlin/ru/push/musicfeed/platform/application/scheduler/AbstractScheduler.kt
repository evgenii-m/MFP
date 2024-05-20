package ru.push.musicfeed.platform.application.scheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.coroutines.CoroutineContext

abstract class AbstractScheduler(
    private val schedulerName: String,
    private val enabled: Boolean,
    private val intervalMs: Long,
    private val initialDelayMs: Long = 0
) : CoroutineScope {

    private val logger = KotlinLogging.logger {}

    private val job: Job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job


    abstract fun schedulerLogic()

    fun cancel() {
        job.cancel()
    }

    fun schedule() {
        if (enabled) {
            launch {
                delay(initialDelayMs)
                logger.debug { "Start scheduler - $schedulerName" }

                while (isActive) {
                    logger.debug { "Scheduler logic begin - $schedulerName" }
                    schedulerLogic()
                    logger.debug { "Scheduler logic end - $schedulerName" }
                    delay(intervalMs)
                }

                logger.debug { "Finish scheduler - $schedulerName" }
            }
        }
    }
}