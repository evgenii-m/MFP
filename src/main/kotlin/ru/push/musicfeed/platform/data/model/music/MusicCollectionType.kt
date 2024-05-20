package ru.push.musicfeed.platform.data.model.music

import ru.push.musicfeed.platform.application.ExternalTokenNotProvidedException
import ru.push.musicfeed.platform.data.model.TokenType

enum class MusicCollectionType {
    LOCAL,
    RAINDROPS,
    YANDEX;

    fun toTokenType(): TokenType {
        return when (this) {
            RAINDROPS -> TokenType.RAINDROPS
            YANDEX -> TokenType.YANDEX
            else -> throw ExternalTokenNotProvidedException(this)
        }
    }

    fun toMusicSourceType(): MusicSourceType {
        // todo add logic when multiple music source support was implemented
        return when (this) {
            YANDEX -> MusicSourceType.YANDEX_MUSIC
            else -> MusicSourceType.COMMON_EXTERNAL_LINK
        }
    }
}