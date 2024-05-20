package ru.push.musicfeed.platform.external.source.yandex

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import ru.push.musicfeed.platform.data.model.music.MusicPack
import ru.push.musicfeed.platform.util.cut
import java.time.LocalDateTime

data class TokenResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("expires_in") val expiresIn: Long,
)

data class AccountStatusResponse(
    val result: AccountStatusResult,
)

data class AccountStatusResult(
    val account: ResultAccount
)

data class ResultAccount(
    val login: String,
)

data class OperationResultResponse(
    val result: String
)


data class PlaylistsListResponse(
    val result: Array<PlaylistResult>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaylistsListResponse) return false

        if (!result.contentEquals(other.result)) return false

        return true
    }

    override fun hashCode(): Int {
        return result.contentHashCode()
    }
}

data class PlaylistResponse(
    val result: PlaylistResult,
)

data class PlaylistResult(
    val kind: Long,
    val revision: Int,
    val visibility: String? = null,
    val available: Boolean,
    val title: String,
    val description: String? = null,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    val created: LocalDateTime,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    val modified: LocalDateTime,
    val trackCount: Long,
    val tags: Array<String>,
    val tracks: Array<PlaylistTrack>? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaylistResult) return false

        if (kind != other.kind) return false
        if (available != other.available) return false
        if (title != other.title) return false
        if (trackCount != other.trackCount) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + available.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + trackCount.hashCode()
        return result
    }
}

data class UserAlbumsListResponse(
    val result: Array<UserAlbumsItem>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserAlbumsListResponse) return false

        if (!result.contentEquals(other.result)) return false

        return true
    }

    override fun hashCode(): Int {
        return result.contentHashCode()
    }
}

data class UserAlbumsItem(
    val album: AlbumResult,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    val timestamp: LocalDateTime,
)

data class AlbumsInfoResponse(
    val result: Array<AlbumResult>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AlbumsInfoResponse) return false

        if (!result.contentEquals(other.result)) return false

        return true
    }

    override fun hashCode(): Int {
        return result.contentHashCode()
    }
}

data class AlbumResponse(
    val result: AlbumResult,
)

data class ArtistInfo(
    val id: Long,
    val name: String
)

data class AlbumResult(
    val id: Long,
    val title: String,
    val artists: Array<ArtistInfo>,
    val genre: String? = null,
    val trackCount: Int,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    val releaseDate: LocalDateTime? = null,
    val year: Int? = null,
    val volumes: Array<Array<TrackInfo>>? = null,
    val trackPosition: AlbumTrackPosition? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AlbumResult) return false

        if (id != other.id) return false
        if (title != other.title) return false
        if (trackCount != other.trackCount) return false
        if (releaseDate != other.releaseDate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + trackCount
        result = 31 * result + releaseDate.hashCode()
        return result
    }

    fun formMusicPackTitle(): String {
        val artistsTitleMaxLength = MusicPack.TITLE_MAX_LENGTH / 2
        val artistsTitle = artists.joinToString(", ") { it.name }
            .cut(artistsTitleMaxLength)
        return if (artistsTitle.isNotBlank()) "$artistsTitle - $title" else title
            .cut(MusicPack.TITLE_MAX_LENGTH)
    }
}

data class AlbumTrackPosition(
    val volume: Int,
    val index: Int
)

data class PlaylistTrack(
    val id: Long,
    val track: TrackInfo,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    val timestamp: LocalDateTime? = null,
    val originalIndex: Int?= null,
)

data class TrackInfo(
    val id: String,
    val title: String,
    val durationMs: Long,
    val available: Boolean,
    val artists: Array<ArtistInfo>,
    val albums: Array<AlbumResult>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackInfo) return false

        if (id != other.id) return false
        if (title != other.title) return false
        if (durationMs != other.durationMs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + durationMs.hashCode()
        return result
    }
}

data class TrackInfoResult(
    val result: Array<TrackInfo>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackInfoResult) return false

        if (!result.contentEquals(other.result)) return false

        return true
    }

    override fun hashCode(): Int {
        return result.contentHashCode()
    }
}

data class SearchTrackResponse(
    val result: SearchResult
)

data class SearchResult(
    val page: Int,
    val tracks: SearchResultTracks? = null
)

data class SearchResultTracks(
    val total: Int,
    val results: Array<TrackInfo>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SearchResultTracks) return false

        if (total != other.total) return false
        if (!results.contentEquals(other.results)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = total
        result = 31 * result + results.contentHashCode()
        return result
    }
}