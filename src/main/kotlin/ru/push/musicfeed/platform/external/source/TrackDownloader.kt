package ru.push.musicfeed.platform.external.source

import mu.KotlinLogging
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto

abstract class TrackDownloader(
    val urlPattern: String,
) {
    private val logger = KotlinLogging.logger {}

    open fun priority(): Int = 0

    fun isApplicableUrl(downloadUrl: String): Boolean {
        logger.debug { "downloadUrl = $downloadUrl, urlPattern = $urlPattern, downloaderClass = ${this.javaClass.canonicalName}" }
        return Regex(urlPattern).matches(downloadUrl)
    }

    fun requestDownload(userId: Long, requestId: Long, downloadUrl: String): List<DownloadProcessInfoDto> {
        return requestDownload(userId, requestId, downloadUrl, downloadUrl)
    }

    abstract fun requestDownload(userId: Long, requestId: Long, musicTrackId: Long): DownloadProcessInfoDto

    abstract fun requestDownload(
        userId: Long,
        requestId: Long,
        downloadUrl: String,
        sourceUrl: String
    ): List<DownloadProcessInfoDto>

    abstract fun requestRetryDownload(processId: Long, downloadUrl: String): DownloadProcessInfoDto
}