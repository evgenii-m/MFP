package ru.push.musicfeed.platform.external.source.ytdlp

import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.DownloadSourceNotSupportedException
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.service.download.DownloadProcessHandler
import ru.push.musicfeed.platform.application.service.track.TrackDataHelper
import ru.push.musicfeed.platform.coroutines.DownloaderScope
import ru.push.musicfeed.platform.external.source.TrackDownloader

@Component
class YtDlpCommonDownloader(
    private val processHandler: DownloadProcessHandler,
    private val ytDlpService: YtDlpService,
    private val trackLocalFileHelper: TrackDataHelper,
) : TrackDownloader(
    urlPattern = "^(https?://)?.+"
) {
    private val downloaderScope = DownloaderScope

    override fun priority(): Int = 99

    override fun requestDownload(userId: Long, requestId: Long, downloadUrl: String, sourceUrl: String): List<DownloadProcessInfoDto> {
        val trackInfos = ytDlpService.obtainTrackInfoByUrl(downloadUrl).items.takeIf { it.isNotEmpty() }
            ?: throw DownloadSourceNotSupportedException(sourceUrl)
        return trackInfos.map { trackInfo ->
            val trackTitle = trackLocalFileHelper.formTrackTitle(trackInfo.artistNames, trackInfo.title)
            val outputFilePath = trackLocalFileHelper.formOutputFilePath()
            val downloadProcessInfo = processHandler.initialize(userId, sourceUrl, outputFilePath, trackTitle, trackInfo.duration)
            downloaderScope.launch {
                ytDlpService.defaultDownloadByUrlAsMp3(
                    trackUrl = trackInfo.url,
                    outputFilePath = downloadProcessInfo.filePath,
                    processInfoId = downloadProcessInfo.downloadProcessId
                )
            }
            downloadProcessInfo
        }
    }

    override fun requestDownload(userId: Long, requestId: Long, musicTrackId: Long): DownloadProcessInfoDto {
        val downloadProcessInfo = processHandler.initializeFromTrackData(userId, musicTrackId)
        downloaderScope.launch {
            ytDlpService.defaultDownloadByUrlAsMp3(
                trackUrl = downloadProcessInfo.sourceUrl,
                outputFilePath = downloadProcessInfo.filePath,
                processInfoId = downloadProcessInfo.downloadProcessId
            )
        }
        return downloadProcessInfo
    }

    override fun requestRetryDownload(processId: Long, downloadUrl: String): DownloadProcessInfoDto {
        val downloadProcessInfo = processHandler.retry(processId)
        val trackInfos = ytDlpService.obtainTrackInfoByUrl(downloadUrl).items
        val requestedTrack = trackInfos.firstOrNull {
            downloadProcessInfo.trackTitle == trackLocalFileHelper.formTrackTitle(it.artistNames, it.title)
                    && it.duration == downloadProcessInfo.duration
        }
            ?: trackInfos.firstOrNull()
            ?: throw DownloadSourceNotSupportedException(downloadUrl)
        downloaderScope.launch {
            ytDlpService.defaultDownloadByUrlAsMp3(
                trackUrl = requestedTrack.url,
                outputFilePath = downloadProcessInfo.filePath,
                processInfoId = downloadProcessInfo.downloadProcessId
            )
        }
        return downloadProcessInfo
    }
}