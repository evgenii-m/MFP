package ru.push.musicfeed.platform.application.dto

import org.springframework.data.domain.Page

data class CollectionWithContentDto(
    val collectionInfo: CollectionInfoDto,
    val contentPage: Page<MusicPackDto>,
)
