package ru.push.musicfeed.platform.application.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.application.service.download.DownloaderService
import ru.push.musicfeed.platform.application.service.track.TrackLocalFileService
import ru.push.musicfeed.platform.data.model.music.MusicTrack
import ru.push.musicfeed.platform.data.repo.MusicPackTrackRepository
import ru.push.musicfeed.platform.data.repo.MusicTrackRepository

@Service
class DataManagerService(
    private val musicTrackRepository: MusicTrackRepository,
    private val musicPackTrackRepository: MusicPackTrackRepository,
    private val downloaderService: DownloaderService,
    private val trackLocalFileService: TrackLocalFileService,
) {

    @Transactional
    fun removeTrackData(trackIds: List<Long>) {
        val musicTracks = musicTrackRepository.fetchByIdIn(trackIds)
        removeTracksData(musicTracks)
    }

    @Transactional
    fun removeMusicPackTracksData(musicPackId: Long) {
        musicPackTrackRepository.deleteByMusicPackId(musicPackId)
        val musicTracks = musicTrackRepository.fetchAllMusicPackTrackList(musicPackId).map { it.musicTrack }
        removeTracksData(musicTracks)
    }

    private fun removeTracksData(musicTracks: List<MusicTrack>) {
        val foundTrackIds = musicTracks.map { it.id!! }.distinct()
        musicPackTrackRepository.deleteByMusicTrackIdIn(foundTrackIds)
        musicTrackRepository.deleteAll(musicTracks)
        val trackLocalFileIds = musicTracks.flatMap { it.sources }.mapNotNull { it.localFileId }
        trackLocalFileService.deleteTrackFilesAndDataByIds(trackLocalFileIds)
        downloaderService.removeDataAssociatedWithTracks(foundTrackIds)
    }

}