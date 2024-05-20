package ru.push.musicfeed.platform.data.model.download

import ru.push.musicfeed.platform.data.model.download.DownloadStatus.FAIL
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.IN_PROGRESS
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.REQUESTED
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.SUCCESS

enum class DownloadStatus {
    REQUESTED,
    IN_PROGRESS,
    SUCCESS,
    FAIL
}

val RETRYABLE_DOWNLOAD_STATUSES: List<DownloadStatus> = listOf(FAIL, REQUESTED)
val ACTIVE_DOWNLOAD_STATUSES: List<DownloadStatus> = listOf(REQUESTED, IN_PROGRESS)
val INACTIVE_DOWNLOAD_STATUSES: List<DownloadStatus> = listOf(FAIL, SUCCESS)