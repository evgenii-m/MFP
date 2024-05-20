package ru.push.musicfeed.platform.application.dto

import ru.push.musicfeed.platform.data.model.ActionEventType

data class ActionEventDto(
    val type: ActionEventType,
    val collectionId: Long? = null,
    val musicPackId: Long? = null,
    val messageId: Long? = null,
    val eventDataId: Long? = null,
)
