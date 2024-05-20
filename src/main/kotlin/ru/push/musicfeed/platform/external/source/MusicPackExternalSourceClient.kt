package ru.push.musicfeed.platform.external.source

import org.springframework.data.domain.Page
import ru.push.musicfeed.platform.application.config.ExternalSourceResponsesRecorderProperties
import ru.push.musicfeed.platform.application.dto.CollectionInfoDto
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import java.time.LocalDateTime

abstract class MusicPackExternalSourceClient(
    val supportedMusicCollectionType: MusicCollectionType,
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

    abstract fun extractCollectionExternalIdFromUrl(externalSourceUrl: String): String

    abstract fun obtainAll(token: String, collectionId: String): List<MusicPackDto>

    abstract fun obtainAllAddedAfter(token: String, collectionId: String, dateTime: LocalDateTime): List<MusicPackDto>

    abstract fun obtainPage(token: String, collectionId: String, page: Int, size: Int): Page<MusicPackDto>

    abstract fun obtainCount(token: String, collectionId: String): Int

    abstract fun createMusicPack(token: String, collectionId: String, musicPackDto: MusicPackDto): MusicPackDto

    abstract fun removeMusicPack(token: String, musicPackId: String, collectionId: String): Boolean

    abstract fun obtainCollectionInfo(token: String, collectionId: String): CollectionInfoDto?

}