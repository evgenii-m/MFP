package ru.push.musicfeed.platform.application.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.application.CollectionSourceAbsentException
import ru.push.musicfeed.platform.application.ExternalSourceNotSupportedException
import ru.push.musicfeed.platform.application.ExternalTokenNotFoundException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.RaindropsProperties
import ru.push.musicfeed.platform.application.dto.CollectionInfoDto
import ru.push.musicfeed.platform.application.dto.MusicPackWithContentDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.application.dto.UserTokenDto
import ru.push.musicfeed.platform.data.model.music.MusicCollection
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import ru.push.musicfeed.platform.data.model.music.MusicTrack
import ru.push.musicfeed.platform.data.model.TokenType
import ru.push.musicfeed.platform.data.model.UserToken
import ru.push.musicfeed.platform.data.repo.UserTokenRepository
import ru.push.musicfeed.platform.external.source.MusicPackExternalSourceClient
import ru.push.musicfeed.platform.external.source.MusicPackContentExternalSourceClient
import ru.push.musicfeed.platform.external.source.MusicPackDataExtractor
import ru.push.musicfeed.platform.external.source.MusicTrackExternalIdDataExtractor
import ru.push.musicfeed.platform.external.source.MusicTrackDataExtractor
import ru.push.musicfeed.platform.util.LogFunction
import java.time.LocalDateTime
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType.YANDEX
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.YANDEX_MUSIC

@Service
class ExternalSourceService(
    private val musicPackDataExtractors: List<MusicPackDataExtractor>,
    private val musicTrackDataExtractors: List<MusicTrackDataExtractor>,
    private val musicTrackExternalIdDataExtractors: List<MusicTrackExternalIdDataExtractor>,
    private val userTokenRepository: UserTokenRepository,
    private val musicPackContentExternalSourceClient: MusicPackContentExternalSourceClient,
    musicPackExternalSourceClients: List<MusicPackExternalSourceClient>,
    applicationProperties: ApplicationProperties,
    private val raindropsProperties: RaindropsProperties = applicationProperties.raindrops,
) {

    private val externalMusicPackSourceClientsMap = musicPackExternalSourceClients.associateBy { it.supportedMusicCollectionType }


    fun obtainCollectionInfo(userId: Long, collection: MusicCollection): CollectionInfoDto {
        val collectionType = collection.type
        val externalMusicPackSourceClient = getExternalMusicPackSourceClient(collectionType)
        val token = getToken(userId, collectionType)
        val collectionExternalId = collection.externalId!!
        return externalMusicPackSourceClient.obtainCollectionInfo(token, collectionExternalId)
            ?: throw CollectionSourceAbsentException(collectionExternalId)
    }

    fun obtainCollectionInfoBySourceUrl(userId: Long, externalSourceUrl: String): CollectionInfoDto {
        val externalMusicPackSourceClient = getExternalMusicPackSourceClient(externalSourceUrl)
        val token = getToken(userId, externalMusicPackSourceClient.supportedMusicCollectionType)
        val collectionExternalId = externalMusicPackSourceClient.extractCollectionExternalIdFromUrl(externalSourceUrl)
        return externalMusicPackSourceClient.obtainCollectionInfo(token, collectionExternalId)
            ?: throw CollectionSourceAbsentException(collectionExternalId)
    }

    fun obtainAll(userId: Long, collection: MusicCollection): List<MusicPackWithContentDto> {
        val collectionType = collection.type
        val externalMusicPackSourceClient = getExternalMusicPackSourceClient(collectionType)
        val token = getToken(userId, collectionType)
        val collectionExternalId = collection.externalId!!

        val musicPacks = externalMusicPackSourceClient.obtainAll(token, collectionExternalId)
        return if (collectionType == MusicCollectionType.YANDEX)
            musicPackContentExternalSourceClient.fetchMusicPacksContent(token, collectionExternalId, musicPacks)
        else
            musicPacks.map { MusicPackWithContentDto(it) }
    }

    fun obtainAllAddedAfter(
        userId: Long,
        collection: MusicCollection,
        dateTime: LocalDateTime
    ): List<MusicPackWithContentDto> {
        val collectionType = collection.type
        val externalMusicPackSourceClient = getExternalMusicPackSourceClient(collectionType)
        val token = getToken(userId, collectionType)
        val collectionExternalId = collection.externalId!!

        val musicPacks = externalMusicPackSourceClient.obtainAllAddedAfter(token, collectionExternalId, dateTime)
        return if (collectionType == MusicCollectionType.YANDEX)
            musicPackContentExternalSourceClient.fetchMusicPacksContent(token, collectionExternalId, musicPacks)
        else
            musicPacks.map { MusicPackWithContentDto(it) }
    }

    @LogFunction
    fun createMusicPackBySourceUrl(userId: Long, collection: MusicCollection, sourceUrl: String): MusicPackWithContentDto {
        val collectionExternalId = collection.externalId
        val musicPackWithContent = extractMusicPackData(sourceUrl)

        return if (collectionExternalId != null && collection.isSynchronized) {
            val collectionType = collection.type
            val externalMusicPackSourceClient = getExternalMusicPackSourceClient(collectionType)
            val token = getToken(userId, collectionType)
            val createdMusicPack = externalMusicPackSourceClient.createMusicPack(token, collection.externalId!!, musicPackWithContent.musicPack)
            return musicPackWithContent.copyWith(createdMusicPack)
        } else musicPackWithContent
    }

    @LogFunction
    fun removeMusicPack(userId: Long, collection: MusicCollection, musicPackId: String): Boolean {
        if (collection.externalId != null && collection.isSynchronized) {
            val collectionType = collection.type
            val externalMusicPackSourceClient = getExternalMusicPackSourceClient(collectionType)
            val token = getToken(userId, collectionType)
            return externalMusicPackSourceClient.removeMusicPack(token, musicPackId, collection.externalId!!)
        }
        return true
    }

    @LogFunction
    fun extractTracksDataAndAddTracksToMusicPack(
        userId: Long,
        collection: MusicCollection,
        musicPackId: String?,
        sourceUrl: String
    ): List<MusicTrackDto> {
        val musicTracks = extractMusicTrackData(sourceUrl)
        val collectionExternalId = collection.externalId
        if (musicPackId != null && collectionExternalId != null && collection.isSynchronized) {
            val collectionType = collection.type
            if (collectionType == YANDEX) {
                val token = getToken(userId, collectionType)
                // todo убрать костыль
                musicTracks.content.filter { it.source.type == YANDEX_MUSIC }
                    .forEach {
                        musicPackContentExternalSourceClient.addTrackToMusicPack(
                            token, collectionExternalId, musicPackId, it.externalId!!, it.album?.externalId
                        )
                    }
            }
        }
        return musicTracks.content
    }

    @LogFunction
    fun addTrackToMusicPackBySourceUrl(
        userId: Long,
        collection: MusicCollection,
        musicPackId: String?,
        sourceUrl: String
    ) {
        val collectionExternalId = collection.externalId
        if (musicPackId != null && collectionExternalId != null && collection.isSynchronized) {
            val collectionType = collection.type
            if (collectionType == YANDEX) {
                val token = getToken(userId, collectionType)
                val (albumId, trackId) = extractMusicTrackExternalId(sourceUrl)
                musicPackContentExternalSourceClient.addTrackToMusicPack(token, collectionExternalId, musicPackId, trackId, albumId)
            }
        }
    }

    @LogFunction
    fun removeTrackFromMusicPack(
        userId: Long,
        collection: MusicCollection,
        musicPackId: String?,
        musicTrack: MusicTrack
    ): Boolean {
        val collectionType = collection.type
        val collectionExternalId = collection.externalId
        if (musicPackId != null && collectionExternalId != null && collection.isSynchronized) {
            if (collectionType == YANDEX) {
                val token = getToken(userId, collectionType)
                musicPackContentExternalSourceClient.removeTrackFromMusicPack(token, collectionExternalId, musicPackId, musicTrack)
                return true
            }
        }
        return false
    }

    @Transactional
    fun saveUserToken(userId: Long, tokenType: TokenType, userTokenDto: UserTokenDto) {
        val userToken = userTokenRepository.findByUserIdAndType(userId, tokenType).firstOrNull()
            ?.apply {
                accountName = userTokenDto.accountName
                value = userTokenDto.value
                expirationDate = userTokenDto.expirationDate
            }
            ?: UserToken(
                tokenType,
                userId,
                userTokenDto.accountName,
                userTokenDto.value,
                userTokenDto.expirationDate
            )
        userTokenRepository.save(userToken)
    }


    private fun getToken(userId: Long, musicCollectionType: MusicCollectionType): String {
        return if (musicCollectionType == MusicCollectionType.RAINDROPS)
            raindropsProperties.token
        else
            userTokenRepository.findByUserIdAndType(userId, musicCollectionType.toTokenType())
                .firstOrNull()
                ?.value
                ?: throw ExternalTokenNotFoundException(userId, musicCollectionType)
    }

    private fun getExternalMusicPackSourceClient(externalSourceUrl: String) =
        externalMusicPackSourceClientsMap.values.find { it.isSupportedSourceUrl(externalSourceUrl) }
            ?: throw ExternalSourceNotSupportedException(url = externalSourceUrl)

    private fun getExternalMusicPackSourceClient(musicCollectionType: MusicCollectionType) =
        externalMusicPackSourceClientsMap[musicCollectionType]
            ?: throw ExternalSourceNotSupportedException(musicCollectionType = musicCollectionType)

    private fun extractMusicPackData(sourceUrl: String) =
        getMusicPackDataExtractor(sourceUrl).extractData(sourceUrl)

    private fun getMusicPackDataExtractor(sourceUrl: String) =
        musicPackDataExtractors.sortedBy { it.priority() }
            .firstOrNull { it.isSupportedSource(sourceUrl) }
            ?: throw ExternalSourceNotSupportedException(sourceUrl)

    private fun extractMusicTrackData(sourceUrl: String) =
        getMusicTrackDataExtractor(sourceUrl).extractData(sourceUrl)

    private fun getMusicTrackDataExtractor(sourceUrl: String) =
        musicTrackDataExtractors.firstOrNull { it.isSupportedSource(sourceUrl) }
            ?: throw ExternalSourceNotSupportedException(sourceUrl)

    private fun extractMusicTrackExternalId(sourceUrl: String) =
        getMusicTrackExternalIdDataExtractor(sourceUrl).extractData(sourceUrl)

    private fun getMusicTrackExternalIdDataExtractor(sourceUrl: String) =
        musicTrackExternalIdDataExtractors.firstOrNull { it.isSupportedSource(sourceUrl) }
            ?: throw ExternalSourceNotSupportedException(sourceUrl)
}