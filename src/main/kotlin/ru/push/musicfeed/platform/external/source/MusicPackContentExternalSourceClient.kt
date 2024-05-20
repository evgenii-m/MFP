package ru.push.musicfeed.platform.external.source

import ru.push.musicfeed.platform.application.config.ExternalSourceResponsesRecorderProperties
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.application.dto.MusicPackWithContentDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.data.model.music.MusicTrack

abstract class MusicPackContentExternalSourceClient(
    sourceUrlPattern: String,
    authorizationTokenPrefix: String,
    responsesRecorderProperties: ExternalSourceResponsesRecorderProperties,
    httpClientTimeoutSec: Long,
) : AbstractExternalSourceClient(
    sourceUrlPattern,
    authorizationTokenPrefix,
    responsesRecorderProperties,
    httpClientTimeoutSec,
) {

    abstract fun fetchMusicPacksContent(
        token: String,
        collectionId: String,
        musicPacks: List<MusicPackDto>
    ): List<MusicPackWithContentDto>

    abstract fun fetchMusicPackContent(
        token: String,
        collectionId: String,
        musicPack: MusicPackDto
    ): MusicPackWithContentDto?

    abstract fun fetchAlbumMusicPackWithContent(
        albumId: Long
    ): MusicPackWithContentDto?

    abstract fun fetchPlaylistMusicPackWithContent(
        token: String,
        user: String,
        playlistId: Long
    ): MusicPackWithContentDto?

    abstract fun fetchTrack(trackId: Long): MusicTrackDto?

    abstract fun addTrackToMusicPack(
        token: String,
        collectionId: String,
        musicPackId: String,
        trackId: Long,
        albumId: Long?
    ): MusicPackDto

    abstract fun removeTrackFromMusicPack(
        token: String,
        collectionId: String,
        musicPackId: String,
        musicTrack: MusicTrack
    ): MusicPackDto
}