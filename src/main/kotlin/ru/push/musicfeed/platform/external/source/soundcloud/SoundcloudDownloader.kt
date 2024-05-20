package ru.push.musicfeed.platform.external.source.soundcloud

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalSourceParseException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.SoundcloudProperties
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.service.download.DownloadProcessHandler
import ru.push.musicfeed.platform.application.service.track.TrackDataHelper
import ru.push.musicfeed.platform.external.source.TrackDownloader
import ru.push.musicfeed.platform.external.source.hls.HLSDownloader
import ru.push.musicfeed.platform.external.source.ytdlp.YtDlpService

@Component
class SoundcloudDownloader(
    private val applicationProperties: ApplicationProperties,
    private val soundcloudProperties: SoundcloudProperties = applicationProperties.soundcloud,
    private val trackLocalFileHelper: TrackDataHelper,
    private val soundcloudTrackPageDownloadSourceDataExtractor: SoundcloudTrackPageDownloadSourceDataExtractor,
    private val soundcloudApiProvider: SoundcloudApiProvider,
    private val processHandler: DownloadProcessHandler,
    private val hlsDownloader: HLSDownloader,
    private val ytDlpService: YtDlpService,
) : TrackDownloader(
    urlPattern = soundcloudProperties.trackUrlPattern!!
) {
    private val downloaderScope = object : CoroutineScope {
        override val coroutineContext = Dispatchers.IO
    }

    override fun requestDownload(userId: Long, requestId: Long, downloadUrl: String, sourceUrl: String): List<DownloadProcessInfoDto> {
        val transformedTrackUrl = downloadUrl.transformToMobileUrl()
        val downloadSourceInfo = soundcloudTrackPageDownloadSourceDataExtractor.extractData(transformedTrackUrl)
        val streamUrl = soundcloudApiProvider.obtainStreamUrl(downloadSourceInfo.hlsRequestUrl)
        val trackTitle = trackLocalFileHelper.formTrackTitle(downloadSourceInfo.artistName, downloadSourceInfo.trackName)
        val outputFilePath = trackLocalFileHelper.formOutputFilePath()
        val downloadProcessInfo = processHandler.initialize(userId, sourceUrl, outputFilePath, trackTitle, downloadSourceInfo.duration)
        downloaderScope.launch {
            hlsDownloader.downloadWithCatch(downloadUrl, streamUrl, outputFilePath, downloadProcessInfo.downloadProcessId)
        }
        return listOf(downloadProcessInfo)
    }

    override fun requestDownload(userId: Long, requestId: Long, musicTrackId: Long): DownloadProcessInfoDto {
        val downloadProcessInfo = processHandler.initializeFromTrackData(userId, musicTrackId)
        val downloadUrl = downloadProcessInfo.sourceUrl
        val transformedTrackUrl = downloadUrl.transformToMobileUrl()
        val downloadSourceInfo = soundcloudTrackPageDownloadSourceDataExtractor.extractData(transformedTrackUrl)
        val streamUrl = soundcloudApiProvider.obtainStreamUrl(downloadSourceInfo.hlsRequestUrl)
        downloaderScope.launch {
            hlsDownloader.downloadWithCatch(downloadUrl, streamUrl, downloadProcessInfo.filePath, downloadProcessInfo.downloadProcessId)
        }
        return downloadProcessInfo
    }

    override fun requestRetryDownload(processId: Long, downloadUrl: String): DownloadProcessInfoDto {
        val transformedTrackUrl = downloadUrl.transformToMobileUrl()
        val downloadSourceInfo = soundcloudTrackPageDownloadSourceDataExtractor.extractData(transformedTrackUrl)
        val streamUrl = soundcloudApiProvider.obtainStreamUrl(downloadSourceInfo.hlsRequestUrl)
        val trackTitle = trackLocalFileHelper.formTrackTitle(downloadSourceInfo.artistName, downloadSourceInfo.trackName)
        val outputFilePath = trackLocalFileHelper.formOutputFilePath()
        val downloadProcessInfo = processHandler.retry(processId, outputFilePath, trackTitle, downloadSourceInfo.duration)
        downloaderScope.launch {
            hlsDownloader.downloadWithCatch(downloadUrl, streamUrl, outputFilePath, processId)
        }
        return downloadProcessInfo
    }

    private fun String.transformToMobileUrl(): String {
        val matchResult = Regex(urlPattern).find(this)
        if (matchResult == null || matchResult.groups.size < 2)
            throw ExternalSourceParseException(this)
        val groupValues = matchResult.groupValues
        val trackUri = groupValues[groupValues.size - 1].takeIf { it.isNotBlank() }
            ?: throw ExternalSourceParseException(this)
        return "${soundcloudProperties.soundcloudMobileBaseUrl}/$trackUri"
    }

    private suspend fun HLSDownloader.downloadWithCatch(downloadUrl: String, hlsUrl: String, outputFilePath: String, processInfoId: Long) {
        try {
            this.download(hlsUrl, outputFilePath, processInfoId)
        } catch (ex: IllegalArgumentException) {
            ytDlpService.mixcloudStreamDownloadByUrlAsMp3(downloadUrl, outputFilePath, processInfoId)
        }
    }
}