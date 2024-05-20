package ru.push.musicfeed.platform.application.scheduler

import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.SchedulerBaseProperties

@Component
class CacheTotalCleaner(
    applicationProperties: ApplicationProperties,
    private val schedulerProperties: SchedulerBaseProperties = applicationProperties.schedulers.cacheTotalCleaner,
    schedulerName: String = "CacheTotalCleaner",
    schedulerEnabled: Boolean = schedulerProperties.enabled,
    intervalMs: Long = schedulerProperties.intervalMs,
    initialDelayMs: Long = schedulerProperties.initialDelayMs,
    private val cacheManager: CacheManager
) : AbstractScheduler(schedulerName, schedulerEnabled, intervalMs, initialDelayMs) {

    override fun schedulerLogic() {
        cacheManager.cacheNames.forEach { cacheName -> cacheManager.getCache(cacheName)?.clear() }
    }
}