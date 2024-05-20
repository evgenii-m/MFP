package ru.push.musicfeed.platform.application

import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.dto.ActionEventDto
import ru.push.musicfeed.platform.application.service.ActionEventService
import ru.push.musicfeed.platform.application.service.DataManagerService
import ru.push.musicfeed.platform.application.service.UserService
import ru.push.musicfeed.platform.data.model.ActionEvent
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.util.LogFunction

@Component
class ServiceCommandFacade(
    private val userService: UserService,
    private val actionEventService: ActionEventService,
    private val dataManagerService: DataManagerService,
) {

    @LogFunction
    fun registerActionEvent(
        type: ActionEventType,
        userExternalId: Long,
        messageId: Long? = null,
        collectionId: Long? = null,
        musicPackId: Long? = null,
        processId: Long? = null,
        musicTrackId: Long? = null,
    ) {
        val userId = userService.findUserByExternalId(userExternalId).id!!
        actionEventService.registerActionEvent(
            type = type,
            userId = userId,
            messageId = messageId,
            collectionId = collectionId,
            musicPackId = musicPackId,
            processId = processId,
            musicTrackId = musicTrackId,
        )
    }

    @LogFunction
    fun getLastActionEvent(userExternalId: Long): ActionEventDto? {
        val userId = userService.findUserByExternalId(userExternalId).id!!
        return actionEventService.getLastActionEven(userId)?.toDto()
    }

    @LogFunction
    fun removeTrackData(userExternalId: Long, trackIds: List<Long>) {
        if (!userService.isSystemAdminUser(userExternalId))
            throw UserHasNotPermissionsException(userExternalId)
        val user = userService.findUserByExternalId(userExternalId)
        dataManagerService.removeTrackData(trackIds)
        actionEventService.registerActionEvent(
            type = ActionEventType.TRACK_DATA_DELETED,
            userId = user.id!!,
        )
    }

    @LogFunction
    fun removeMusicPackTracksData(userExternalId: Long, musicPackId: Long) {
        if (!userService.isSystemAdminUser(userExternalId))
            throw UserHasNotPermissionsException(userExternalId)
        val user = userService.findUserByExternalId(userExternalId)
        dataManagerService.removeMusicPackTracksData(musicPackId)
        actionEventService.registerActionEvent(
            type = ActionEventType.TRACK_DATA_DELETED,
            userId = user.id!!,
        )
    }

    fun ActionEvent.toDto() = ActionEventDto(
        type = type,
        collectionId = collectionId,
        musicPackId = musicPackId,
        messageId = messageId,
        eventDataId = eventDataId
    )
}