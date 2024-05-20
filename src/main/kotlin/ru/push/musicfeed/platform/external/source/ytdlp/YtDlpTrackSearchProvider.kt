package ru.push.musicfeed.platform.external.source.ytdlp

import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.dto.MusicArtistDto
import ru.push.musicfeed.platform.application.dto.MusicSourceDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.COMMON_EXTERNAL_LINK
import ru.push.musicfeed.platform.external.source.TrackSearchProvider

@Component
class YtDlpTrackSearchProvider(
    private val ytDlpService: YtDlpService,
) : TrackSearchProvider {

    companion object {
        private const val DEFAULT_SEARCH_SIZE = 5
    }

    override val supportedMusicCollectionType: MusicCollectionType
        get() = MusicCollectionType.LOCAL

    override fun searchTrack(searchText: String): List<MusicTrackDto> {
        return ytDlpService.defaultSearchTracks(searchText, DEFAULT_SEARCH_SIZE).items
            .map {
                MusicTrackDto(
                    externalId = null,
                    title = it.title,
                    duration = it.duration,
                    artists = it.artistNames.map { MusicArtistDto(name = it, source = null) },
                    source = MusicSourceDto(type = COMMON_EXTERNAL_LINK, url = it.url)
                )
            }
    }

}