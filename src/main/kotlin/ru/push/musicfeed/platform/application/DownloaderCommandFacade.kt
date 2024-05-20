package ru.push.musicfeed.platform.application

import mu.KotlinLogging
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoList
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.application.dto.MusicTrackListDto
import ru.push.musicfeed.platform.application.dto.TrackFileInfoDto
import ru.push.musicfeed.platform.application.service.ActionEventService
import ru.push.musicfeed.platform.application.service.MusicPackService
import ru.push.musicfeed.platform.application.service.download.DownloaderService
import ru.push.musicfeed.platform.application.service.UserService
import ru.push.musicfeed.platform.application.service.track.TrackLocalFileService
import ru.push.musicfeed.platform.application.service.track.TrackSearchService
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.SUCCESS
import ru.push.musicfeed.platform.data.model.music.MusicSourceType
import ru.push.musicfeed.platform.util.LogFunction

@Component
class DownloaderCommandFacade(
    private val userService: UserService,
    private val actionEventService: ActionEventService,
    private val downloaderService: DownloaderService,
    private val trackLocalFileService: TrackLocalFileService,
    private val musicPackService: MusicPackService,
    private val trackSearchService: TrackSearchService,
) {

    private val logger = KotlinLogging.logger {}

    @LogFunction(displayResult = true)
    fun requestSingleTrackDownload(userExternalId: Long, trackUrl: String): DownloadProcessInfoList {
        val user = userService.findUserByExternalId(userExternalId)
        val userId = user.id!!
        val processInfoList = downloaderService.requestSingleTrackDownloadByUrl(userId = userId, trackUrl = trackUrl)
        actionEventService.registerActionEvent(type = ActionEventType.DOWNLOAD_STARTED, userId = userId)
        return processInfoList
    }

    @LogFunction(displayResult = true)
    fun requestTrackListDownloadForMusicPack(userExternalId: Long, musicPackId: Long): DownloadProcessInfoList {
        val user = userService.findUserByExternalId(userExternalId)
        val userId = user.id!!
        val musicPackWithContent = musicPackService.fetchMusicPackWithContent(musicPackId, userId)
        val musicTracksData = musicPackWithContent.tracks.takeIf { it.isNotEmpty() }
            ?: musicPackWithContent.musicPack.pageUrl
                ?.let { musicPackService.musicPackFormDefaultTracklist(userId, musicPackId) }
                ?.takeIf { it.isNotEmpty() }
            ?: throw MusicPackNoContentToDownloadException(userId, musicPackId)
        return requestTrackListDownloadForMusicTracks(userId, musicTracksData)
    }

    @LogFunction
    fun requestSingleTrackDownloadForMusicTrack(userExternalId: Long, musicTrackId: Long): DownloadProcessInfoDto {
        val user = userService.findUserByExternalId(userExternalId)
        val userId = user.id!!
        val trackData = musicPackService.fetchMusicTrackData(musicTrackId = musicTrackId, userId = userId)
        val processInfoList = downloaderService.requestSingleTrackDownload(userId = userId, trackData = trackData)
        actionEventService.registerActionEvent(type = ActionEventType.DOWNLOAD_STARTED, userId = userId)
        return processInfoList.dtoList.first()
    }

    @LogFunction(displayResult = true)
    fun requestTrackListDownload(userExternalId: Long, trackUrls: List<String>): DownloadProcessInfoList {
        val user = userService.findUserByExternalId(userExternalId)
        val userId = user.id!!
        val processInfoList = downloaderService.requestTrackListDownloadByUrls(userId, trackUrls)
        actionEventService.registerActionEvent(type = ActionEventType.DOWNLOAD_STARTED, userId = userId)
        return processInfoList
    }

    @LogFunction
    fun requestTrackListDownloadForMusicPackTrackListPage(userExternalId: Long, musicPackId: Long, page: Int, size: Int): DownloadProcessInfoList {
        val user = userService.findUserByExternalId(userExternalId)
        val userId = user.id!!
        val musicPackTracks = musicPackService.fetchMusicPackTrackList(musicPackId, userId, page, size)

        val musicTracksData = musicPackTracks.contentPage.content
        return requestTrackListDownloadForMusicTracks(userId, musicTracksData)
    }

    private fun requestTrackListDownloadForMusicTracks(userId: Long, musicTracks: List<MusicTrackDto>): DownloadProcessInfoList {
        val musicPackTracksWithExternalLink = musicTracks.filter {
            it.source.type == MusicSourceType.COMMON_EXTERNAL_LINK && it.source.url != null
        }
        val trackIdsToSourceUrl = musicPackTracksWithExternalLink.associateBy({ it.id!! }, { it.source.url!! })
        val startedProcessInfoList = downloaderService.requestTrackListDownloadByTrackIds(userId, trackIdsToSourceUrl)
        val successProcessInfos = startedProcessInfoList.dtoList.content.filter { it.status == SUCCESS }
        if (successProcessInfos.isNotEmpty()) {
            musicPackTracksWithExternalLink.forEach { musicPackTrack ->
                successProcessInfos.firstOrNull { it.sourceUrl == musicPackTrack.source.url!! }
                    ?.let { trackLocalFileService.obtainDownloadedTrackFileInfo(it.downloadProcessId) }
                    ?.let { musicPackService.storeTrackLocalFileInfoSource(musicPackTrack.id!!, it.trackLocalFileId) }
            }
        }

        val musicPackTracksWithLocalFile = musicTracks.filter { it.source.localFileId != null }
        val downloadedTrackIds = musicPackTracksWithLocalFile.map { it.id!! }
        val trackIdsToDownloadProcessInfo = downloaderService.obtainDownloadProcessInfoByTrackIds(downloadedTrackIds)
        val downloadedProcessIds = trackIdsToDownloadProcessInfo.values.map { it.downloadProcessId }
        val requestId = startedProcessInfoList.requestId!!
        downloaderService.associateDownloadedProcessInfoWithRequest(downloadedProcessIds, requestId)

        actionEventService.registerActionEvent(type = ActionEventType.DOWNLOAD_STARTED, userId = userId)
        val dtoList: MutableList<DownloadProcessInfoDto> = mutableListOf()
        dtoList.addAll(startedProcessInfoList.dtoList.content)
        dtoList.addAll(trackIdsToDownloadProcessInfo.values)
        return DownloadProcessInfoList(
            requestId = requestId,
            dtoList = PageImpl(dtoList)
        )
    }

    @LogFunction(displayResult = true)
    fun searchTrackForDownload(searchText: String): MusicTrackListDto {
        val trackList = trackSearchService.searchTrack(searchText)
        if (trackList.isEmpty())
            throw ExternalSourceSearchNotFoundException(searchText = searchText)
        return MusicTrackListDto(content = trackList)
    }

    @LogFunction(displayResult = true)
    fun retryDownloadProcess(userExternalId: Long, downloadProcessId: Long): DownloadProcessInfoDto {
        val user = userService.findUserByExternalId(userExternalId)
        val userId = user.id!!
        actionEventService.registerActionEvent(type = ActionEventType.REQUEST_DOWNLOAD_RETRY, userId = userId)
        return downloaderService.requestRetryDownload(userId, downloadProcessId)
    }

    @LogFunction(displayResult = false)
    fun stopDownloadProcess(userExternalId: Long, downloadProcessId: Long): DownloadProcessInfoDto {
        // todo: implement logic
        throw DownloadProcessCannotBeStoppedException(downloadProcessId)
    }

    @LogFunction(displayResult = true)
    fun getDownloadProcessInfo(downloadProcessId: Long): DownloadProcessInfoDto {
        return downloaderService.getDownloadProcessInfo(downloadProcessId)
    }

    @LogFunction(displayResult = false)
    fun getDownloadProcessInfoList(downloadRequestId: Long): DownloadProcessInfoList {
        return downloaderService.getDownloadProcessInfoList(downloadRequestId)
    }

    @LogFunction(displayResult = false)
    fun getDownloadProcessInfoList(userExternalId: Long, page: Int, size: Int): DownloadProcessInfoList {
        val user = userService.findUserByExternalId(userExternalId)
        val userId = user.id!!
        return downloaderService.getDownloadProcessInfoList(userId, page, size)
    }

    @LogFunction
    fun getDownloadedTrackFileInfo(downloadProcessId: Long): TrackFileInfoDto {
        return trackLocalFileService.obtainDownloadedTrackFileInfo(downloadProcessId)
    }

    @LogFunction
    fun getDownloadedTrackFileInfoForMusicTrack(musicTrackId: Long): TrackFileInfoDto? {
        return musicPackService.getTrackLocalFileIdByTrackId(musicTrackId)
            ?.let { trackLocalFileService.obtainLocalTrackFileInfo(it, musicTrackId) }
    }

    @LogFunction(displayResult = false)
    fun removeDownloadsItem(userExternalId: Long, downloadProcessId: Long, page: Int, size: Int): DownloadProcessInfoList {
        val user = userService.findUserByExternalId(userExternalId)
        val userId = user.id!!
        downloaderService.removeUserDownloadsItem(userId, downloadProcessId)
        return downloaderService.getDownloadProcessInfoList(userId, page, size)
    }
}