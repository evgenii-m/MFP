package ru.push.musicfeed.platform.application.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.data.model.ActionEvent
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.data.repo.ActionEventRepository
import java.time.Clock
import java.time.LocalDateTime

@Service
class ActionEventService(
    private val actionEventRepository: ActionEventRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    private val logger = KotlinLogging.logger {}

    @Transactional
    fun registerActionEvent(
        type: ActionEventType,
        userId: Long,
        messageId: Long? = null,
        collectionId: Long? = null,
        musicPackId: Long? = null,
        processId: Long? = null,
        musicTrackId: Long? = null,
    ): ActionEvent {
        listOfNotNull(processId, musicTrackId)
            .takeIf { it.size > 1 }
            ?.let { obtainedIds ->
                throw IllegalArgumentException("Expected only one of arguments [processId, musicTrackId], " +
                        "but obtained more then one: [${obtainedIds.joinToString { it.toString() }}]"
                )
            }

        val eventTime = LocalDateTime.now(clock)
        val eventDataId = when (type) {
            ActionEventType.REQUEST_EXTRACT_TRACK -> {
                processId
            }
            ActionEventType.REQUEST_EDIT_MUSIC_TRACK_DATA,
            ActionEventType.REQUEST_SELECT_TRACK_NEW_POSITION_FOR_CHANGE -> {
                musicTrackId
            }
            else -> null
        }

        val registeredEvent = actionEventRepository.save(
            ActionEvent(
                userId = userId,
                messageId = messageId,
                collectionId = collectionId,
                musicPackId = musicPackId,
                type = type,
                eventTime = eventTime,
                eventDataId = eventDataId
            )
        )
        logger.debug { "Registered new event: $registeredEvent" }
        return registeredEvent
    }

    fun getLastActionEven(userId: Long): ActionEvent? {
        return actionEventRepository.findTop1ByUserIdOrderByEventTimeDesc(userId).firstOrNull()
    }
}