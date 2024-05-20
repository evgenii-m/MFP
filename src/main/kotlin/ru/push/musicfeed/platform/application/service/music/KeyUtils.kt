package ru.push.musicfeed.platform.application.service.music

import ru.push.musicfeed.platform.application.dto.MusicAlbumDto
import ru.push.musicfeed.platform.application.dto.MusicArtistDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.data.model.music.MusicAlbum
import ru.push.musicfeed.platform.data.model.music.MusicArtist
import ru.push.musicfeed.platform.data.model.music.MusicTrack


fun MusicAlbum.formKey() = formAlbumKey(title, artists.map { it.name })

fun MusicAlbumDto.formKey() = formAlbumKey(title, artists.map { it.name })

fun formAlbumKey(title: String, artistNames: List<String>?): String {
    if (artistNames == null || artistNames.isEmpty()) {
        return title
    }
    val artistsKeyPart = artistNames.sorted().joinToString("_") { it }
    return "${title}_${artistsKeyPart}"
}

fun MusicArtist.formKey(): String {
    return sources.mapNotNull { it.externalSourceUrl }
        .takeIf { it.isNotEmpty() }
        ?.let { formArtistKey(name, it) }
        ?: name
}

fun MusicArtistDto.formKey(): String {
    return source?.url?.let { formArtistKey(name, listOf(it)) }
        ?: name
}

fun formArtistKey(name: String, sourcesKey: List<String>): String {
    val sourcesUrlKeyPart = sourcesKey.sorted().joinToString("_") { it }
    return "${name}_${sourcesUrlKeyPart}"
}

fun MusicTrack.formKey() = formTrackKey(title, album?.title ?: " ", artists.map { it.name })
fun MusicTrackDto.formKey() = formTrackKey(title, album?.title ?: " ", artists?.map { it.name })

fun formTrackKey(trackTitle: String, albumTitle: String, artistNames: List<String>?): String {
    return "${trackTitle}_${formAlbumKey(albumTitle, artistNames)}"
}