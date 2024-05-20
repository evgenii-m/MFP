package ru.push.musicfeed.platform.application.service.download

import kotlin.time.Duration
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto

interface DownloadProcessHandler {
    fun initialize(
        userId: Long,
        sourceUrl: String,
        filePath: String,
        trackTitle: String?,
        trackDuration: Duration?
    ): DownloadProcessInfoDto

    fun initializeFromTrackData(
        userId: Long,
        musicTrackId: Long
    ): DownloadProcessInfoDto

    fun retry(
        id: Long,
        filePath: String? = null,
        trackTitle: String? = null,
        trackDuration: Duration? = null
    ): DownloadProcessInfoDto

    fun start(id: Long, totalParts: Int)

    fun progress(id: Long, downloadedParts: Int)

    fun completeSuccess(id: Long, downloadedParts: Int? = null)

    fun fail(id: Long, ex: Throwable)
}