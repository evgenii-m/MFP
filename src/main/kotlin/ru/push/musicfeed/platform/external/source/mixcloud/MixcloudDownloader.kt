package ru.push.musicfeed.platform.external.source.mixcloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.MixcloudProperties
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.service.download.DownloadProcessHandler
import ru.push.musicfeed.platform.application.service.track.TrackDataHelper
import ru.push.musicfeed.platform.external.source.TrackDownloader
import ru.push.musicfeed.platform.external.source.ytdlp.YtDlpService

@Component
class MixcloudDownloader(
    applicationProperties: ApplicationProperties,
    mixcloudProperties: MixcloudProperties = applicationProperties.mixcloud,
    private val mixcloudTrackPageDownloadSourceDataExtractor: MixcloudTrackPageDownloadSourceDataExtractor,
    private val trackLocalFileHelper: TrackDataHelper,
    private val processHandler: DownloadProcessHandler,
    private val ytDlpService: YtDlpService,
) : TrackDownloader(
    urlPattern = mixcloudProperties.trackUrlPattern!!
) {
    private val downloaderScope = object : CoroutineScope {
        override val coroutineContext = Dispatchers.IO
    }

    override fun requestDownload(userId: Long, requestId: Long, downloadUrl: String, sourceUrl: String): List<DownloadProcessInfoDto> {
        val downloadSourceInfo = mixcloudTrackPageDownloadSourceDataExtractor.extractData(downloadUrl)
        val trackTitle = trackLocalFileHelper.formTrackTitle(downloadSourceInfo.artistName, downloadSourceInfo.trackName)
        val outputFilePath = trackLocalFileHelper.formOutputFilePath()
        val downloadProcessInfo = processHandler.initialize(userId, sourceUrl, outputFilePath, trackTitle, downloadSourceInfo.duration)
        downloaderScope.launch {
            ytDlpService.mixcloudStreamDownloadByUrlAsMp3(
                trackUrl = downloadUrl,
                outputFilePath = outputFilePath,
                processInfoId = downloadProcessInfo.downloadProcessId
            )
        }
        return listOf(downloadProcessInfo)
    }

    override fun requestDownload(userId: Long, requestId: Long, musicTrackId: Long): DownloadProcessInfoDto {
        val downloadProcessInfo = processHandler.initializeFromTrackData(userId, musicTrackId)
        downloaderScope.launch {
            ytDlpService.mixcloudStreamDownloadByUrlAsMp3(
                trackUrl = downloadProcessInfo.sourceUrl,
                outputFilePath = downloadProcessInfo.filePath,
                processInfoId = downloadProcessInfo.downloadProcessId
            )
        }
        return downloadProcessInfo
    }

    override fun requestRetryDownload(processId: Long, downloadUrl: String): DownloadProcessInfoDto {
        val downloadSourceInfo = mixcloudTrackPageDownloadSourceDataExtractor.extractData(downloadUrl)
        val trackTitle = trackLocalFileHelper.formTrackTitle(downloadSourceInfo.artistName, downloadSourceInfo.trackName)
        val outputFilePath = trackLocalFileHelper.formOutputFilePath()
        val downloadProcessInfo = processHandler.retry(processId, outputFilePath, trackTitle, downloadSourceInfo.duration)
        downloaderScope.launch {
            ytDlpService.mixcloudStreamDownloadByUrlAsMp3(
                trackUrl = downloadUrl,
                outputFilePath = outputFilePath,
                processInfoId = processId
            )
        }
        return downloadProcessInfo
    }
}