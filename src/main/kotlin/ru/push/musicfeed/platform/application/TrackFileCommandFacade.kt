package ru.push.musicfeed.platform.application

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.dto.TrackFileInfoDto
import ru.push.musicfeed.platform.application.service.ActionEventService
import ru.push.musicfeed.platform.application.service.MusicPackService
import ru.push.musicfeed.platform.application.service.UserService
import ru.push.musicfeed.platform.application.service.track.TrackLocalFileService
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.data.model.FileExternalType
import ru.push.musicfeed.platform.util.LogFunction
import ru.push.musicfeed.platform.util.toDuration

@Component
class TrackFileCommandFacade(
    private val trackLocalFileService: TrackLocalFileService,
    private val userService: UserService,
    private val musicPackService: MusicPackService,
    private val actionEventService: ActionEventService,
) {

    private val logger = KotlinLogging.logger {}

    @LogFunction(displayResult = false)
    fun saveTelegramFileId(trackLocalFileId: Long, telegramFileId: String) {
        trackLocalFileService.storeFileExternalId(trackLocalFileId, telegramFileId, FileExternalType.TELEGRAM)
    }

    @LogFunction(displayResult = false)
    fun extractPartFromTrackFile(
        userExternalId: Long,
        downloadProcessId: Long? = null,
        trackLocalFileId: Long? = null,
        musicPackId: Long? = null,
        targetTrackTitle: String? = null,
        startTimestamp: String,
        endTimestamp: String
    ): TrackFileInfoDto {
        val user = userService.findUserByExternalId(userExternalId)

        val start = startTimestamp.toDuration()
        val end = endTimestamp.toDuration()
        if (end < start)
            throw IllegalArgumentException("End timestamp must be after start")
        val duration = end.minus(start)

        val trackFileInfo = trackLocalFileId?.let {
            trackLocalFileService.extractPartFromTrackFileByLocalFileId(it, targetTrackTitle, start, duration)
        } ?: musicPackId?.let {
            trackLocalFileService.extractPartFromTrackFileByMusicPackId(it, targetTrackTitle, start, duration)
        } ?: downloadProcessId?.let {
            trackLocalFileService.extractPartFromTrackFileByDownloadProcessId(it, targetTrackTitle, start, duration)
        } ?: throw IllegalArgumentException("Required downloadProcessId or trackLocalFileId")

        val userId = user.id!!
        if (musicPackId != null) {
            musicPackService.addTrackToMusicPackByFileInfo(userId, musicPackId, trackFileInfo)
        }

        actionEventService.registerActionEvent(
            type = ActionEventType.TRACK_EXTRACTED,
            userId = userId,
        )

        return trackFileInfo
    }

    @LogFunction(displayResult = false)
    fun storeTrackFileFromExternalSource(
        userExternalId: Long,
        fileExternalId: String,
        fileExternalType: FileExternalType,
        fileExternalUrl: String,
        trackArtistName: String?,
        trackTitle: String?,
        trackDurationSec: Int?,
        musicPackId: Long?,
    ): TrackFileInfoDto {
        val user = userService.findUserByExternalId(userExternalId)

        val trackFileInfo = trackLocalFileService.storeTrackFileFromExternalSource(
            fileSourceUrl = fileExternalUrl,
            trackArtistName = trackArtistName,
            trackTitle = trackTitle,
            trackDurationSec = trackDurationSec
        )
        trackLocalFileService.storeFileExternalId(
            trackLocalFileId = trackFileInfo.trackLocalFileId,
            fileExternalId = fileExternalId,
            type = fileExternalType
        )
        trackFileInfo.fileExternalId = fileExternalId

        val userId = user.id!!
        if (musicPackId != null) {
            musicPackService.addTrackToMusicPackByFileInfo(
                userId = userId,
                musicPackId = musicPackId,
                fileInfo = trackFileInfo
            )
        }

        val replacedMessageId = actionEventService.getLastActionEven(userId)?.messageId
        actionEventService.registerActionEvent(
            type = ActionEventType.ADDED_MUSIC_TRACK_TO_MUSIC_PACK,
            userId = userId,
            musicPackId = musicPackId,
            messageId = replacedMessageId
        )
        return trackFileInfo
    }


}