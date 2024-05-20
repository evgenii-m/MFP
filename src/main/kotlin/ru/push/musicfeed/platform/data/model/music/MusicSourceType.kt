package ru.push.musicfeed.platform.data.model.music

import ru.push.musicfeed.platform.data.model.music.MusicSourceType.YANDEX_MUSIC

enum class MusicSourceType {
    NOT_DEFINED,
    YANDEX_MUSIC,
    COMMON_EXTERNAL_LINK,
    TRACK_LOCAL_FILE
}

val NOT_EDITABLE_MUSIC_SOURCE_TYPES = setOf(YANDEX_MUSIC)