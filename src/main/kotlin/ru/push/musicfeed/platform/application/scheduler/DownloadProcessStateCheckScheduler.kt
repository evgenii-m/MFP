package ru.push.musicfeed.platform.application.scheduler

import java.time.Clock
import java.time.LocalDateTime
import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.DownloadProcessStateCheckProperties
import ru.push.musicfeed.platform.data.model.download.ACTIVE_DOWNLOAD_STATUSES
import ru.push.musicfeed.platform.data.model.download.DownloadProcessInfo
import ru.push.musicfeed.platform.data.model.download.DownloadStatus
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.IN_PROGRESS
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.REQUESTED
import ru.push.musicfeed.platform.data.repo.DownloadProcessInfoRepository
import ru.push.musicfeed.platform.util.TransactionHelper

@Component
class DownloadProcessStateCheckScheduler(
    applicationProperties: ApplicationProperties,
    private val schedulerProperties: DownloadProcessStateCheckProperties = applicationProperties.schedulers.downloadProcessStateCheck,
    schedulerName: String = "DownloadProcessStateCheck",
    private val downloadProcessInfoRepository: DownloadProcessInfoRepository,
    private val transactionHelper: TransactionHelper,
    private val clock: Clock = Clock.systemDefaultZone()
) : AbstractScheduler(schedulerName, schedulerProperties.enabled, schedulerProperties.intervalMs, schedulerProperties.initialDelayMs) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = KotlinLogging.logger(javaClass.enclosingClass.canonicalName)
    }

    override fun schedulerLogic() {
        try {
            transactionHelper.withTransaction { checkDownloadProcessesHang() }
        } catch (ex: Exception) {
            logger.error(ex) { "Scheduler logic throws exception: ${ex.message}" }
        }
    }

    private fun checkDownloadProcessesHang() {
        val activeDownloadProcess = downloadProcessInfoRepository.findByStatusIn(ACTIVE_DOWNLOAD_STATUSES)
        val now = LocalDateTime.now(clock)
        val hangDownloadProcesses = mutableSetOf<DownloadProcessInfo>()

        activeDownloadProcess.filterByStatusAndMakeFailWhenProcessIsHang(
            status = REQUESTED,
            hangDateTime = now.minusMinutes(schedulerProperties.requestedHangTimeIntervalMinutes),
            nowDateTime = now
        ).also {
            hangDownloadProcesses.addAll(it)
        }

        activeDownloadProcess.filterByStatusAndMakeFailWhenProcessIsHang(
            status = IN_PROGRESS,
            hangDateTime = now.minusMinutes(schedulerProperties.inProgressHangTimeIntervalMinutes),
            nowDateTime = now
        ).also {
            hangDownloadProcesses.addAll(it)
        }

        if (hangDownloadProcesses.isNotEmpty()) {
            logger.info { "Detected hang download processes, count = ${hangDownloadProcesses.size}, ids = [${hangDownloadProcesses.idsString()}]" }
            downloadProcessInfoRepository.saveAll(hangDownloadProcesses)
        }
    }

    private fun List<DownloadProcessInfo>.filterByStatusAndMakeFailWhenProcessIsHang(
        status: DownloadStatus,
        hangDateTime: LocalDateTime,
        nowDateTime: LocalDateTime
    ): List<DownloadProcessInfo> {
        return this.filter { it.status == status }
            .filter { it.updatedAt?.isAfter(hangDateTime)?.not() ?: true }
            .onEach {
                it.status = DownloadStatus.FAIL
                it.errorDescription = "Download process is hang"
                it.updatedAt = nowDateTime
            }.toList()
    }

    private fun Collection<DownloadProcessInfo>.idsString(): String = this.map { it.id }.joinToString { it.toString() }
}