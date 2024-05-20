package ru.push.musicfeed.platform.application.dto

import ru.push.musicfeed.platform.data.model.music.MusicSourceType
import java.time.LocalDateTime
import kotlin.time.Duration
import org.springframework.data.domain.Page

data class MusicPackDto(
    val id: Long? = null,
    val externalId: String? = null,
    val collectionId: Long? = null,
    val title: String,
    val description: String? = null,
    val addedAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null,
    val tags: List<String>,
    val coverUrl: String? = null,
    val pageUrl: String? = null,
    val removed: Boolean? = false,
    val editable: Boolean? = null
)

data class MusicArtistDto(
    val externalId: Long? = null,
    val name: String,
    val source: MusicSourceDto?,
)

data class MusicAlbumDto(
    val externalId: Long? = null,
    val title: String,
    val year: Int? = null,
    val releaseDate: LocalDateTime? = null,
    val artists: List<MusicArtistDto>,
    val source: MusicSourceDto?,
)

data class MusicTrackDto(
    val id: Long? = null,
    val externalId: Long? = null,
    val title: String,
    val position: Int? = null,
    val albumPosition: Int? = null,
    val duration: Duration? = null,
    val addedAt: LocalDateTime? = null,
    val album: MusicAlbumDto? = null,
    val artists: List<MusicArtistDto>? = null,
    val source: MusicSourceDto,
)

data class MusicSourceDto(
    val type: MusicSourceType,
    val url: String? = null,
    val localFileId: Long? = null,
) {
    companion object {
        fun buildNotDefined() = MusicSourceDto(type = MusicSourceType.NOT_DEFINED)
    }
}

data class MusicTrackExternalIdDto(
    val albumId: Long? = null,
    val trackId: Long
)

data class MusicPackWithContentDto(
    val musicPack: MusicPackDto,
    val artists: List<MusicArtistDto> = emptyList(),
    val albums: List<MusicAlbumDto> = emptyList(),
    val tracks: List<MusicTrackDto> = emptyList(),
) {
    fun copyWith(musicPack: MusicPackDto) = MusicPackWithContentDto(musicPack, artists, albums, tracks)
}

data class AddTrackToMusicPackResultDto(
    val added: Boolean,
    val musicPack: MusicPackDto? = null,
    val artists: List<MusicArtistDto> = emptyList(),
    val albums: List<MusicAlbumDto> = emptyList(),
    val tracks: List<MusicTrackDto> = emptyList(),
)

data class MusicPackTrackListDto(
    val musicPack: MusicPackDto,
    val contentPage: Page<MusicTrackDto>
)

data class MusicTrackListDto(
    val content: List<MusicTrackDto>
)