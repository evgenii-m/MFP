package ru.push.musicfeed.platform.external.source.yandex

import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import ru.push.musicfeed.platform.external.source.TrackSearchProvider

@Component
class YandexMusicTrackSearchProvider(
    private val externalSourceClient: YandexMusicPackContentExternalSourceClient,
) : TrackSearchProvider {

    override val supportedMusicCollectionType: MusicCollectionType
        get() = MusicCollectionType.YANDEX

    override fun searchTrack(searchText: String): List<MusicTrackDto> {
        return externalSourceClient.searchTrack(searchText)
    }
}