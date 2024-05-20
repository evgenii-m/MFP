package ru.push.musicfeed.platform.application.service.track

import org.springframework.stereotype.Service
import ru.push.musicfeed.platform.application.ExternalTrackSearchNotSupportedException
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.application.service.music.MusicEntitiesDao
import ru.push.musicfeed.platform.application.service.music.MusicEntitiesSearchHelper
import ru.push.musicfeed.platform.application.service.music.formKey
import ru.push.musicfeed.platform.application.service.music.toDto
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import ru.push.musicfeed.platform.external.source.TrackSearchProvider
import ru.push.musicfeed.platform.external.source.ytdlp.YtDlpTrackSearchProvider
import ru.push.musicfeed.platform.util.normalizeSearchText

@Service
class TrackSearchService(
    trackSearchProviders: List<TrackSearchProvider>,
    private val defaultTrackSearchProvider: YtDlpTrackSearchProvider,
    private val musicEntitiesDao: MusicEntitiesDao,
    private val musicEntitiesSearchHelper: MusicEntitiesSearchHelper,
) {

    private val trackSearchProvidersMap = trackSearchProviders.associateBy { it.supportedMusicCollectionType }

    fun searchTrack(musicCollectionType: MusicCollectionType, searchText: String): List<MusicTrackDto> {
        return getTrackSearchProvider(musicCollectionType).searchTrackAndStoreMusicEntities(searchText)
    }

    fun searchTrack(searchText: String): List<MusicTrackDto> {
        return defaultTrackSearchProvider.searchTrackAndStoreMusicEntities(searchText.normalizeSearchText())
    }

    private fun TrackSearchProvider.searchTrackAndStoreMusicEntities(searchText: String): List<MusicTrackDto> {
        val entitiesSearchResult = musicEntitiesSearchHelper.searchTrack(searchText)
        val entitiesSearchedTracks = entitiesSearchResult.tracks
        if (entitiesSearchResult.hasFullMatch) {
            return entitiesSearchedTracks
        }
        val searchedTracks = this.searchTrack(searchText)
        if (searchedTracks.isEmpty())
            return entitiesSearchedTracks
        val musicTracksMap = musicEntitiesDao.checkAndSaveMusicTracks(searchedTracks).associateBy { it.formKey() }
        val newSearchedTracks = searchedTracks.mapNotNull { musicTracksMap[it.formKey()] }.toDto()
        return listOf(newSearchedTracks, entitiesSearchedTracks).flatten().distinctBy { it.id }
    }

    private fun getTrackSearchProvider(musicCollectionType: MusicCollectionType) = trackSearchProvidersMap[musicCollectionType]
        ?: throw ExternalTrackSearchNotSupportedException(musicCollectionType = musicCollectionType)

}