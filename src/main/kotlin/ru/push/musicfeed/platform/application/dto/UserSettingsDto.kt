package ru.push.musicfeed.platform.application.dto

data class UserSettingsDto(
    val userCollections: List<UserCollectionSettingsDto>?,
    val yandexMusicAccountName: String? = null,
    val isSystemAdminUser: Boolean
)

data class UserCollectionSettingsDto(
    val collectionId: Long,
    val collectionExternalId: String? = null,
    val title: String?,
    val selected: Boolean,
)
