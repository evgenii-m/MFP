package ru.push.musicfeed.platform.external.source

import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType

interface TrackSearchProvider {

    val supportedMusicCollectionType: MusicCollectionType

    fun searchTrack(searchText: String): List<MusicTrackDto>
}