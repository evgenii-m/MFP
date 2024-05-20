package ru.push.musicfeed.platform.application.service.music

import kotlin.time.DurationUnit.SECONDS
import kotlin.time.toDuration
import ru.push.musicfeed.platform.application.dto.MusicAlbumDto
import ru.push.musicfeed.platform.application.dto.MusicArtistDto
import ru.push.musicfeed.platform.application.dto.MusicSourceDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.data.model.music.MusicAlbum
import ru.push.musicfeed.platform.data.model.music.MusicAlbumSource
import ru.push.musicfeed.platform.data.model.music.MusicArtist
import ru.push.musicfeed.platform.data.model.music.MusicArtistSource
import ru.push.musicfeed.platform.data.model.music.MusicPackTrack
import ru.push.musicfeed.platform.data.model.music.MusicSourceType
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.COMMON_EXTERNAL_LINK
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.TRACK_LOCAL_FILE
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.YANDEX_MUSIC
import ru.push.musicfeed.platform.data.model.music.MusicTrack
import ru.push.musicfeed.platform.data.model.music.MusicTrackSource


@JvmName("toDtoMusicArtist")
fun List<MusicArtist>.toDto(): List<MusicArtistDto> = map { it.toDto() }

fun MusicArtist.toDto(): MusicArtistDto =
    MusicArtistDto(
        name = name,
        source = sources.findPrimaryOrFirst()
    )

fun MusicAlbum.toDto(): MusicAlbumDto =
    MusicAlbumDto(
        title = title,
        year = year,
        releaseDate = releaseDate,
        artists = artists.toList().sortedBy { it.name }.toDto(),
        source = sources.findPrimaryOrFirst()
    )

@JvmName("toDtoMusicTrack")
fun List<MusicPackTrack>.toDto(): List<MusicTrackDto> = map { it.toDto() }

fun MusicPackTrack.toDto() = MusicTrackDto(
    id = this.musicTrack.id,
    title = this.musicTrack.title,
    position = this.position,
    albumPosition = this.musicTrack.albumPosition,
    duration = this.musicTrack.durationSec?.toDuration(SECONDS),
    addedAt = this.addedAt,
    album = this.musicTrack.album?.toDto(),
    artists = this.musicTrack.artists.toList().sortedBy { artist -> artist.id }.toDto(),
    source = this.musicTrack.sources.findPrimaryOrFirst()
)


@JvmName("toDtoMusicTrackFromTrack")
fun List<MusicTrack>.toDto(): List<MusicTrackDto> = map { it.toDto() }

fun MusicTrack.toDto() = MusicTrackDto(
    id = this.id,
    title = this.title,
    albumPosition = this.albumPosition,
    duration = this.durationSec?.toDuration(SECONDS),
    album = this.album?.toDto(),
    artists = this.artists.toList().sortedBy { artist -> artist.id }.toDto(),
    source = this.sources.findPrimaryOrFirst()
)

private val PRIMARY_MUSIC_SOURCE_TYPE = TRACK_LOCAL_FILE

fun Set<MusicArtistSource>.findPrimaryOrFirst(): MusicSourceDto {
    val musicSource = this.find { it.sourceType == PRIMARY_MUSIC_SOURCE_TYPE }
        ?: this.minByOrNull { it.id!! }
    return musicSource?.let { MusicSourceDto(type = it.sourceType, url = it.externalSourceUrl) }
        ?: MusicSourceDto.buildNotDefined()
}

@JvmName("findPrimaryOrFirstMusicAlbumSource")
fun Set<MusicAlbumSource>.findPrimaryOrFirst(): MusicSourceDto {
    val musicSource = this.find { it.sourceType == PRIMARY_MUSIC_SOURCE_TYPE }
        ?: this.minByOrNull { it.id!! }
    return musicSource?.let { MusicSourceDto(type = it.sourceType, url = it.externalSourceUrl) }
        ?: MusicSourceDto.buildNotDefined()
}

@JvmName("findPrimaryOrFirstMusicTrackSource")
fun Set<MusicTrackSource>.findPrimaryOrFirst(): MusicSourceDto {
    val musicSource = this.find { it.sourceType == PRIMARY_MUSIC_SOURCE_TYPE } ?: this.minByOrNull { it.id!! }
    return musicSource?.let { MusicSourceDto(type = it.sourceType, url = it.externalSourceUrl, localFileId = musicSource.localFileId) }
        ?: MusicSourceDto.buildNotDefined()
}

@JvmName("findOrFirstMusicTrackSource")
fun Set<MusicTrackSource>.findOrFirst(type: MusicSourceType): MusicSourceDto {
    val musicSource = this.find { it.sourceType == type } ?: this.minByOrNull { it.id!! }
    return musicSource?.let { MusicSourceDto(type = it.sourceType, url = it.externalSourceUrl, localFileId = musicSource.localFileId) }
        ?: MusicSourceDto.buildNotDefined()
}

fun MusicSourceDto.toTrackSource(): MusicTrackSource {
    return when (this.type) {
        TRACK_LOCAL_FILE -> MusicTrackSource(sourceType = this.type, localFileId = this.localFileId)
        YANDEX_MUSIC -> MusicTrackSource(sourceType = this.type, externalSourceUrl = this.url)
        COMMON_EXTERNAL_LINK -> MusicTrackSource(sourceType = this.type, externalSourceUrl = this.url)
        else -> throw IllegalStateException("Music source type ${this.type} not supported for tracks")
    }
}

fun MusicSourceDto.toAlbumSource(): MusicAlbumSource {
    return when (this.type) {
        YANDEX_MUSIC -> MusicAlbumSource(sourceType = this.type, externalSourceUrl = this.url)
        else -> throw IllegalStateException("Music source type TRACK_LOCAL_FILE not supported for albums")
    }
}

fun MusicSourceDto.toArtistSource(): MusicArtistSource {
    return when (this.type) {
        YANDEX_MUSIC -> MusicArtistSource(sourceType = this.type, externalSourceUrl = this.url)
        else -> throw IllegalStateException("Music source type TRACK_LOCAL_FILE not supported for artists")
    }
}