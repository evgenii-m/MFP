package ru.push.musicfeed.platform.external.source.yandex

import java.util.Locale

enum class CollectionResource {
    PLAYLISTS,
    ALBUMS;

    fun getName() = name.lowercase(Locale.getDefault())
}