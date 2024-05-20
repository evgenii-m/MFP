package ru.push.musicfeed.platform.external.source.yandex

import okhttp3.internal.EMPTY_REQUEST
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalChangeMusicPackContentException
import ru.push.musicfeed.platform.application.ExternalSourceException
import ru.push.musicfeed.platform.application.ExternalSourceNotSupportedException
import ru.push.musicfeed.platform.application.ExternalSourceSearchException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.YandexProperties
import ru.push.musicfeed.platform.application.dto.MusicAlbumDto
import ru.push.musicfeed.platform.application.dto.MusicArtistDto
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.application.dto.MusicPackWithContentDto
import ru.push.musicfeed.platform.application.dto.MusicSourceDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.data.model.music.MusicSourceType
import ru.push.musicfeed.platform.data.model.music.MusicTrack
import ru.push.musicfeed.platform.external.source.MusicPackContentExternalSourceClient
import ru.push.musicfeed.platform.util.correctZoneOffset
import java.time.Clock
import java.time.LocalDateTime
import java.util.Locale
import ru.push.musicfeed.platform.external.http.HttpClientProvider
import ru.push.musicfeed.platform.external.source.yandex.parser.YandexMusicTrackExternalIdDataExtractor

@Component
class YandexMusicPackContentExternalSourceClient(
    applicationProperties: ApplicationProperties,
    private val musicTrackExternalIdParser: YandexMusicTrackExternalIdDataExtractor,
    private val yandexProperties: YandexProperties = applicationProperties.yandex,
    private val clock: Clock = Clock.systemDefaultZone()
) : MusicPackContentExternalSourceClient(
    sourceUrlPattern = "^http.*music\\.yandex\\.ru/(.+)\$",
    authorizationTokenPrefix = "OAuth",
    responsesRecorderProperties = applicationProperties.externalSourceResponsesRecorder,
    httpClientTimeoutSec = yandexProperties.httpClientTimeoutSec,
) {
    companion object {
    }

    override fun fetchMusicPacksContent(
        token: String,
        collectionId: String,
        musicPacks: List<MusicPackDto>
    ): List<MusicPackWithContentDto> {
        val (user, resource) = extractUserAndResource(collectionId)
        return when (resource) {
            CollectionResource.PLAYLISTS.getName() -> {
                val playlistIds = musicPacks.mapTo(hashSetOf()) { it.externalId!!.toLong() }
                obtainUserPlaylistsWithContent(token, user, playlistIds)
                    .convertToMusicPackWithContent(user)
            }
            CollectionResource.ALBUMS.getName() -> {
                val albumIdToAddedAtMap = musicPacks.associate { it.externalId!!.toLong() to it.addedAt }
                obtainAlbumsWithContent(albumIdToAddedAtMap.keys)
                    .convertToMusicPackWithContent(albumIdToAddedAtMap)
            }
            else -> throw ExternalSourceNotSupportedException()
        }
    }

    override fun fetchMusicPackContent(
        token: String,
        collectionId: String,
        musicPack: MusicPackDto
    ): MusicPackWithContentDto? {
        return fetchMusicPacksContent(token, collectionId, listOf(musicPack)).firstOrNull()
    }

    override fun fetchAlbumMusicPackWithContent(albumId: Long): MusicPackWithContentDto? {
        return obtainAlbumWithContent(albumId)
            ?.convertToMusicPackWithContent(LocalDateTime.now(clock))
    }

    override fun fetchPlaylistMusicPackWithContent(
        token: String,
        user: String,
        playlistId: Long
    ): MusicPackWithContentDto? {
        return obtainUserPlaylistWithContent(token, user, playlistId)
            ?.convertToMusicPackWithContent(user)
    }

    override fun fetchTrack(trackId: Long): MusicTrackDto? {
        return makeGetRequest(
            "${yandexProperties.apiUrl}/tracks/$trackId"
        ).transformToObject<TrackInfoResult>()
            ?.result
            ?.firstOrNull()
            ?.convertToMusicTrack()
    }

    override fun addTrackToMusicPack(
        token: String,
        collectionId: String,
        musicPackId: String,
        trackId: Long,
        albumId: Long?
    ): MusicPackDto {
        val (user, resource) = extractUserAndResource(collectionId)
        if (resource != CollectionResource.PLAYLISTS.getName())
            throw ExternalSourceException(collectionId, musicPackId)

        val userPlaylist = obtainUserPlaylistWithContent(token, user, musicPackId.toLong())
            ?: throw ExternalSourceException(collectionId, musicPackId)

        val trackAlbumId = "\"id\":${trackId}${
            albumId?.let { ",\"albumId\":$it" } ?: ""
        }"
        val newTrackPosition = userPlaylist.tracks?.size
        val diff = """
            [{
                "op": "insert",
                "at": $newTrackPosition,
                "tracks": [{$trackAlbumId}]
            }]
        """.trimIndent()
        val revision = userPlaylist.revision

        return userPlaylistChange(token, user, musicPackId.toLong(), trackId, revision, diff)
    }

    override fun removeTrackFromMusicPack(
        token: String,
        collectionId: String,
        musicPackId: String,
        musicTrack: MusicTrack
    ): MusicPackDto {
        val (user, resource) = extractUserAndResource(collectionId)
        if (resource != CollectionResource.PLAYLISTS.getName())
            throw ExternalSourceException(collectionId, musicPackId)

        val userPlaylist = obtainUserPlaylistWithContent(token, user, musicPackId.toLong())
            ?: throw ExternalSourceException(collectionId, musicPackId)

        val trackSourceUrl = musicTrack.findSource(MusicSourceType.YANDEX_MUSIC)?.externalSourceUrl
            ?: throw ExternalSourceException(collectionId, musicPackId)
        val trackId = musicTrackExternalIdParser.extractData(trackSourceUrl).trackId

        val trackPosition = userPlaylist.tracks?.find { it.id == trackId }?.originalIndex
            ?: throw ExternalChangeMusicPackContentException(user, musicPackId.toLong(), trackId)
        val diff = """
            [{
                "op": "delete",
                "from": $trackPosition,
                "to": ${trackPosition + 1}
            }]
        """.trimIndent()
        val revision = userPlaylist.revision

        return userPlaylistChange(token, user, musicPackId.toLong(), trackId, revision, diff)
    }

    fun searchTrack(searchText: String): List<MusicTrackDto> {
        return makeGetRequest(
            "${yandexProperties.apiUrl}/search?type=track&nocorrect=false&page=0&text=$searchText"
        )
            .transformToObject<SearchTrackResponse>()
            ?.result?.tracks?.results
            ?.convertToMusicTrack()
            ?: throw ExternalSourceSearchException(searchText)
    }

    private fun obtainUserPlaylistWithContent(token: String, user: String, playlistId: Long): PlaylistResult? {
        return obtainUserPlaylistsWithContent(token, user, setOf(playlistId))
            .firstOrNull()
    }

    private fun obtainUserPlaylistsWithContent(
        token: String,
        user: String,
        playlistIds: Set<Long>
    ): Array<PlaylistResult> {
        val playlistIdsParamValue = playlistIds.joinToString(",")
        return makeGetRequest(
            "${yandexProperties.apiUrl}/users/$user/playlists?kinds=$playlistIdsParamValue&rich-tracks=true",
            token
        )
            .transformToObject<PlaylistsListResponse>()
            ?.result
            ?: emptyArray()
    }

    private fun obtainAlbumsWithContent(albumIds: Set<Long>): Array<AlbumResult> {
        return albumIds.mapNotNull { obtainAlbumWithContent(it) }
            .toTypedArray()
    }

    private fun obtainAlbumWithContent(albumId: Long): AlbumResult? {
        return makeGetRequest("${yandexProperties.apiUrl}/albums/$albumId/with-tracks")
            .transformToObject<AlbumResponse?>()
            ?.result
    }

    private fun userPlaylistChange(
        token: String,
        user: String,
        playlistId: Long,
        trackExternalId: Long,
        revision: Int,
        diff: String
    ): MusicPackDto {
        val params = "kind=$playlistId&revision=$revision&diff=$diff"
        val result = makePostRequest(
            "${yandexProperties.apiUrl}/users/$user/playlists/$playlistId/change?$params",
            EMPTY_REQUEST,
            HttpClientProvider.CONTENT_TYPE_JSON,
            token
        ).transformToObject<PlaylistResponse>()
            ?.result
            ?: throw ExternalChangeMusicPackContentException(user, playlistId, trackExternalId)

        return result.convertToMusicPackWithContent(user).musicPack
    }


    private data class UserResource(val user: String, val resource: String)

    private fun extractUserAndResource(collectionId: String): UserResource {
        val collectionIdParts = collectionId.split('/')
        if (collectionIdParts.size != 2) {
            throw ExternalSourceException(collectionExternalId = collectionId)
        }

        val user = collectionIdParts[0].lowercase(Locale.getDefault())
        val resource = collectionIdParts[1].lowercase(Locale.getDefault())
        return UserResource(user, resource)
    }


    internal fun Array<PlaylistResult>.convertToMusicPackWithContent(user: String) =
        map { it.convertToMusicPackWithContent(user) }

    internal fun PlaylistResult.convertToMusicPackWithContent(user: String, addedAt: LocalDateTime? = null) =
        MusicPackWithContentDto(
            musicPack = MusicPackDto(
                externalId = kind.toString(),
                title = title,
                description = description,
                addedAt = addedAt ?: created.correctZoneOffset(),
                updatedAt = addedAt ?: modified.correctZoneOffset(),
                tags = tags.map { it },
                pageUrl = "${yandexProperties.webUrl}/users/$user/playlists/$kind",
            ),
            tracks = tracks?.convertToMusicTrack() ?: emptyList()
        )

    internal fun Array<AlbumResult>.convertToMusicPackWithContent(
        albumIdToAddedAtMap: Map<Long, LocalDateTime>
    ): List<MusicPackWithContentDto> {
        val now = LocalDateTime.now(clock)
        return map {
            it.convertToMusicPackWithContent(albumIdToAddedAtMap[it.id]?.correctZoneOffset() ?: now)
        }
    }

    internal fun AlbumResult.convertToMusicPackWithContent(addedAt: LocalDateTime) =
        MusicPackWithContentDto(
            musicPack = MusicPackDto(
                externalId = id.toString(),
                title = formMusicPackTitle(),
                description = year?.let { "Release year: $it" },
                addedAt = addedAt,
                updatedAt = addedAt,
                tags = if (genre != null) listOf(genre) else emptyList(),
                pageUrl = "${yandexProperties.webUrl}/album/$id",
            ),
            albums = listOf(
                convertToMusicAlbum()
            )
        )

    internal fun AlbumResult.convertToMusicAlbum() =
        MusicAlbumDto(
            externalId = id,
            title = title,
            year = year,
            releaseDate = releaseDate,
            artists = artists.map { it.convertToMusicArtist() },
            source = formYandexMusicSource("${yandexProperties.webUrl}/album/$id")
        )

    internal fun Array<ArtistInfo>.convertToMusicArtist() = map { it.convertToMusicArtist() }

    internal fun ArtistInfo.convertToMusicArtist() =
        MusicArtistDto(
            externalId = id,
            name = name,
            source = formYandexMusicSource("${yandexProperties.webUrl}/artist/$id")
        )

    internal fun Array<PlaylistTrack>.convertToMusicTrack() = map { it.convertToMusicTrack() }

    internal fun PlaylistTrack.convertToMusicTrack(): MusicTrackDto {
        val album = track.albums.firstOrNull()
        return MusicTrackDto(
            externalId = track.id.toLong(),
            title = track.title,
            position = originalIndex,
            albumPosition = album?.trackPosition?.index ?: 0,
            addedAt = timestamp?.correctZoneOffset(),
            album = album?.convertToMusicAlbum(),
            artists = track.artists.convertToMusicArtist(),
            source = formYandexMusicSource(
                if (album != null)
                    "${yandexProperties.webUrl}/album/${album.id}/track/$id"
                else
                    "${yandexProperties.webUrl}/track/$id"
            )
        )
    }

    internal fun Array<TrackInfo>.convertToMusicTrack() = map { it.convertToMusicTrack() }

    internal fun TrackInfo.convertToMusicTrack(): MusicTrackDto {
        val album = albums.firstOrNull()
        return MusicTrackDto(
            externalId = id.toLong(),
            title = title,
            album = album?.convertToMusicAlbum(),
            albumPosition = album?.trackPosition?.index ?: 0,
            artists = artists.convertToMusicArtist(),
            source = formYandexMusicSource(
                if (album != null)
                    "${yandexProperties.webUrl}/album/${album.id}/track/$id"
                else
                    "${yandexProperties.webUrl}/track/$id"
            )
        )
    }

    internal fun formYandexMusicSource(url: String) = MusicSourceDto(type = MusicSourceType.YANDEX_MUSIC, url = url)
}