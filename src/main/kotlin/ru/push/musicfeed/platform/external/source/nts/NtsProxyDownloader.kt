package ru.push.musicfeed.platform.external.source.nts

import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalSourceNotSupportedException
import ru.push.musicfeed.platform.application.MusicTrackNotFoundException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.NtsProperties
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.service.music.MusicEntitiesDao
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.COMMON_EXTERNAL_LINK
import ru.push.musicfeed.platform.external.source.TrackDownloader
import ru.push.musicfeed.platform.external.source.mixcloud.MixcloudDownloader
import ru.push.musicfeed.platform.external.source.soundcloud.SoundcloudDownloader
import ru.push.musicfeed.platform.external.source.ytdlp.YtDlpCommonDownloader

@Component
class NtsProxyDownloader(
    applicationProperties: ApplicationProperties,
    ntsProperties: NtsProperties = applicationProperties.nts,
    private val ntsStreamLinkExtractor: NtsStreamLinkExtractor,
    private val soundcloudDownloader: SoundcloudDownloader,
    private val mixcloudDownloader: MixcloudDownloader,
    private val defaultDownloader: YtDlpCommonDownloader,
    private val musicEntitiesDao: MusicEntitiesDao,
) : TrackDownloader(
    urlPattern = ntsProperties.trackUrlPattern
) {

    override fun requestDownload(userId: Long, requestId: Long, downloadUrl: String, sourceUrl: String): List<DownloadProcessInfoDto> {
        val targetUrl = ntsStreamLinkExtractor.parse(downloadUrl)
        return getTrackDownloader(targetUrl).requestDownload(
            userId = userId, requestId = requestId, downloadUrl = targetUrl, sourceUrl = downloadUrl
        )
    }

    override fun requestDownload(userId: Long, requestId: Long, musicTrackId: Long): DownloadProcessInfoDto {
        val trackData = musicEntitiesDao.fetchTrackById(musicTrackId)
            ?: throw MusicTrackNotFoundException(userId, musicTrackId)
        val sourceUrl = trackData.findSource(COMMON_EXTERNAL_LINK)?.externalSourceUrl
            ?: throw ExternalSourceNotSupportedException()
        val targetUrl = ntsStreamLinkExtractor.parse(sourceUrl)
        return getTrackDownloader(targetUrl).requestDownload(
            userId = userId, requestId = requestId, downloadUrl = targetUrl, sourceUrl = sourceUrl
        ).first()
    }

    override fun requestRetryDownload(processId: Long, downloadUrl: String): DownloadProcessInfoDto {
        val targetUrl = ntsStreamLinkExtractor.parse(downloadUrl)
        return getTrackDownloader(targetUrl).requestRetryDownload(processId, targetUrl)
    }

    private fun getTrackDownloader(trackUrl: String): TrackDownloader {
        return if (soundcloudDownloader.isApplicableUrl(trackUrl))
            soundcloudDownloader
        else if (mixcloudDownloader.isApplicableUrl(trackUrl))
            mixcloudDownloader
        else defaultDownloader
    }
}