package ru.push.musicfeed.platform.application.dto

import ru.push.musicfeed.platform.data.model.music.MusicCollectionType

data class CollectionInfoDto(
    val id: Long? = null,
    val externalId: String? = null,
    val title: String? = null,
    val channelName: String? = null,
    val type: MusicCollectionType,
    val selected: Boolean = false,
    val itemsCount: Int,
)
