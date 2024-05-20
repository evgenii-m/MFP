package ru.push.musicfeed.platform.external.source.yandex

import mu.KotlinLogging
import okhttp3.internal.EMPTY_REQUEST
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalCreateMusicPackException
import ru.push.musicfeed.platform.application.ExternalSourceException
import ru.push.musicfeed.platform.application.ExternalSourceNotSupportedException
import ru.push.musicfeed.platform.application.InvalidExternalSourceUrlException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.YandexProperties
import ru.push.musicfeed.platform.application.dto.CollectionInfoDto
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import ru.push.musicfeed.platform.external.source.MusicPackExternalSourceClient
import ru.push.musicfeed.platform.util.correctZoneOffset
import ru.push.musicfeed.platform.util.trimToPage
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.push.musicfeed.platform.external.http.HttpClientProvider

@Component
class YandexMusicPackExternalSourceClient(
    applicationProperties: ApplicationProperties,
    private val yandexProperties: YandexProperties = applicationProperties.yandex,
    private val clock: Clock = Clock.systemDefaultZone()
) : MusicPackExternalSourceClient(
    supportedMusicCollectionType = MusicCollectionType.YANDEX,
    sourceUrlPattern = "^http.*music\\.yandex\\.ru/users/(.+)\$",
    authorizationTokenPrefix = "OAuth",
    responsesRecorderProperties = applicationProperties.externalSourceResponsesRecorder,
    httpClientTimeoutSec = yandexProperties.httpClientTimeoutSec,
) {
    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }

    final val logger = KotlinLogging.logger {}

    override fun extractCollectionExternalIdFromUrl(externalSourceUrl: String): String {
        val regex = Regex(sourceUrlPattern)
        return regex.find(externalSourceUrl)
            ?.let { it.groups.last()?.value }
            ?: throw InvalidExternalSourceUrlException(externalSourceUrl)
    }

    override fun obtainAll(token: String, collectionId: String): List<MusicPackDto> {
        val collectionIdParts = collectionId.split('/')
        if (collectionIdParts.size != 2) {
            throw ExternalSourceException(collectionExternalId = collectionId)
        }

        val user = collectionIdParts[0].lowercase(Locale.getDefault())
        val resource = collectionIdParts[1].lowercase(Locale.getDefault())
        return when (resource) {
            CollectionResource.PLAYLISTS.getName() -> {
                obtainUserPlaylistsInfo(token, user)
                    .convertToMusicPack(user)
            }
            CollectionResource.ALBUMS.getName() -> {
                obtainUserLikesAlbumsInfo(token, user)
                    .convertToMusicPack()
            }
            else -> throw ExternalSourceNotSupportedException()
        }
    }

    override fun obtainAllAddedAfter(
        token: String,
        collectionId: String,
        dateTime: LocalDateTime
    ): List<MusicPackDto> {
        return obtainAll(token, collectionId)
            .filter { !it.addedAt.isBefore(dateTime) }
    }

    override fun obtainPage(token: String, collectionId: String, page: Int, size: Int): Page<MusicPackDto> {
        val all = obtainAll(token, collectionId)
        return PageImpl(
            all.trimToPage(page, size),
            PageRequest.of(page, size),
            all.size.toLong()
        )
    }

    override fun obtainCount(token: String, collectionId: String): Int {
        return obtainCollectionInfo(token, collectionId)?.itemsCount ?: 0
    }

    override fun createMusicPack(token: String, collectionId: String, musicPackDto: MusicPackDto): MusicPackDto {
        val collectionIdParts = collectionId.split('/')
        if (collectionIdParts.size != 2) {
            throw ExternalSourceException(collectionExternalId = collectionId)
        }

        val user = collectionIdParts[0].lowercase(Locale.getDefault())
        val resource = collectionIdParts[1].lowercase(Locale.getDefault())
        val resourceId = musicPackDto.externalId!!.toLong()
        val methodUrl = when (resource) {
            CollectionResource.PLAYLISTS.getName() ->
                "${yandexProperties.apiUrl}/users/$user/likes/playlists/add-multiple?playlist-ids=$resourceId"
            CollectionResource.ALBUMS.getName() ->
                "${yandexProperties.apiUrl}/users/$user/likes/albums/add-multiple?album-ids=$resourceId"
            else -> throw ExternalSourceNotSupportedException()
        }
        val createResult = makePostRequest(methodUrl, EMPTY_REQUEST, HttpClientProvider.CONTENT_TYPE_JSON, token)
            .transformToObject<OperationResultResponse>()
            ?.result
        if (createResult != "ok")
            throw ExternalCreateMusicPackException(user, resourceId)

        return musicPackDto
    }

    override fun removeMusicPack(token: String, musicPackId: String, collectionId: String): Boolean {
        val collectionIdParts = collectionId.split('/')
        if (collectionIdParts.size != 2) {
            throw ExternalSourceException(collectionExternalId = collectionId)
        }

        val user = collectionIdParts[0].lowercase(Locale.getDefault())
        val resource = collectionIdParts[1].lowercase(Locale.getDefault())
        val requestParamName = when (resource) {
            CollectionResource.PLAYLISTS.getName() -> "playlist-ids"
            CollectionResource.ALBUMS.getName() -> "album-ids"
            else -> throw ExternalSourceNotSupportedException()
        }
        val params = "$requestParamName=$musicPackId"
        val result = makePostRequest(
            "${yandexProperties.apiUrl}/users/$user/likes/$resource/remove?$params",
            EMPTY_REQUEST,
            HttpClientProvider.CONTENT_TYPE_JSON,
            token
        ).transformToObject<OperationResultResponse>()

        return result?.result == "ok"
    }

    override fun obtainCollectionInfo(token: String, collectionId: String): CollectionInfoDto? {
        val collectionIdParts = collectionId.split('/')
        if (collectionIdParts.size != 2) {
            throw ExternalSourceException(collectionExternalId = collectionId)
        }

        val user = collectionIdParts[0].lowercase(Locale.getDefault())
        val resource = collectionIdParts[1].lowercase(Locale.getDefault())
        val itemsCount = when (resource) {
            CollectionResource.PLAYLISTS.getName() -> {
                // TODO add user likes playlists
                obtainUserPlaylistsInfo(token, user)
//                    .plus(obtainUserLikesPlaylistsInfo(token, user))
//                    .distinct()
                    .size
            }
            CollectionResource.ALBUMS.getName() -> {
                obtainUserLikesAlbumsInfo(token, user).size
            }
            else -> throw ExternalSourceNotSupportedException()
        }
        return formCollectionInfo(collectionId, resource, itemsCount)
    }


    private fun obtainUserPlaylistsInfo(token: String, user: String): Array<PlaylistResult> {
        return makeGetRequest("${yandexProperties.apiUrl}/users/$user/playlists/list", token)
            .transformToObject<PlaylistsListResponse>()
            ?.result
            ?: emptyArray()
    }

    private fun obtainUserLikesPlaylistsInfo(token: String, user: String): Array<PlaylistResult> {
        return makeGetRequest("${yandexProperties.apiUrl}/users/$user/likes/playlists", token)
            .transformToObject<PlaylistsListResponse>()
            ?.result
            ?: emptyArray()
    }

    private fun obtainUserLikesAlbumsInfo(token: String, user: String): Array<UserAlbumsItem> {
        return makeGetRequest("${yandexProperties.apiUrl}/users/$user/likes/albums?rich=true", token)
            .transformToObject<UserAlbumsListResponse>()
            ?.result
            ?: emptyArray()
    }


    internal fun Array<PlaylistResult>.convertToMusicPack(user: String) =
        map { it.convertToMusicPack(user) }

    internal fun PlaylistResult.convertToMusicPack(user: String, addedAt: LocalDateTime? = null) =
        MusicPackDto(
            externalId = kind.toString(),
            title = title,
            description = description,
            addedAt = addedAt ?: created.correctZoneOffset(),
            updatedAt = addedAt ?: modified.correctZoneOffset(),
            tags = tags.map { it },
            pageUrl = "${yandexProperties.webUrl}/users/$user/playlists/$kind",
        )

    internal fun Array<AlbumResult>.convertToMusicPack(
        albumIdToAddedAtMap: Map<Long, LocalDateTime>
    ): List<MusicPackDto> {
        val now = LocalDateTime.now(clock)
        return map {
            it.convertToMusicPack(albumIdToAddedAtMap[it.id]?.correctZoneOffset() ?: now)
        }
    }

    internal fun AlbumResult.convertToMusicPack(addedAt: LocalDateTime) =
        MusicPackDto(
            externalId = id.toString(),
            title = formMusicPackTitle(),
            description = year?.let { "Release year: $it" },
            addedAt = addedAt,
            updatedAt = addedAt,
            tags = if (genre != null) listOf(genre) else emptyList(),
            pageUrl = "${yandexProperties.webUrl}/album/$id",
        )

    internal fun Array<UserAlbumsItem>.convertToMusicPack(): List<MusicPackDto> =
        map { it.convertToMusicPack() }

    internal fun UserAlbumsItem.convertToMusicPack() =
        album.convertToMusicPack(timestamp.correctZoneOffset())


    private fun formCollectionInfo(collectionId: String, resource: String, itemsCount: Int) =
        CollectionInfoDto(
            externalId = collectionId,
            title = "Yandex.Music $resource",
            type = MusicCollectionType.YANDEX,
            itemsCount = itemsCount
        )
}