package ru.push.musicfeed.platform.external.source.raindrops

import com.google.common.collect.Lists
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalSourceException
import ru.push.musicfeed.platform.application.InvalidExternalSourceUrlException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.RaindropsProperties
import ru.push.musicfeed.platform.application.dto.CollectionInfoDto
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import ru.push.musicfeed.platform.external.source.MusicPackExternalSourceClient
import ru.push.musicfeed.platform.util.correctZoneOffset
import java.time.LocalDateTime

@Component
class RaindropsMusicPackExternalSourceClient(
    applicationProperties: ApplicationProperties,
    private val raindropsProperties: RaindropsProperties = applicationProperties.raindrops,
) : MusicPackExternalSourceClient(
    supportedMusicCollectionType = MusicCollectionType.RAINDROPS,
    sourceUrlPattern = "^http.*raindrop\\.io.*/(\\d+)/*\$",
    authorizationTokenPrefix = "Bearer",
    responsesRecorderProperties = applicationProperties.externalSourceResponsesRecorder,
    httpClientTimeoutSec = raindropsProperties.httpClientTimeoutSec,
) {
    companion object {
        const val MAX_PAGE_SIZE = 50
    }

    override fun extractCollectionExternalIdFromUrl(externalSourceUrl: String): String {
        val regex = Regex(sourceUrlPattern)
        return regex.find(externalSourceUrl)
            ?.let { it.groups.last()?.value?.takeIf { it.toLongOrNull() != null } }
            ?: throw InvalidExternalSourceUrlException(externalSourceUrl)
    }

    override fun obtainAll(token: String, collectionId: String): List<MusicPackDto> {
        val pageSize = MAX_PAGE_SIZE
        var totalCount = obtainCount(token, collectionId)
        val raindrops = Lists.newArrayListWithCapacity<Raindrop>(totalCount)

        var idx = 0
        while (idx < totalCount) {
            val raindropList = obtainRaindropList(token, idx / pageSize, pageSize, collectionId.toLong())
                ?: break

            raindrops.addAll(raindropList.items)

            idx += pageSize
            totalCount = obtainCount(token, collectionId)
        }

        return raindrops.convert()
    }

    override fun createMusicPack(token: String, collectionId: String, musicPackDto: MusicPackDto): MusicPackDto {
        val request = CreateRaindropRequest(
            title = musicPackDto.title,
            link = musicPackDto.pageUrl!!,
            collectionId = collectionId.toLong(),
            excerpt = musicPackDto.description,
            cover = musicPackDto.coverUrl,
            tags = musicPackDto.tags
        )
        val result = makePostRequestJsonBody("${raindropsProperties.apiUrl}/raindrop/", request, token)
            .transformToObject<RaindropResult?>()
            ?.takeIf { it.result }
            ?: throw ExternalSourceException(collectionId)
        return result.item.convert()
    }

    override fun removeMusicPack(token: String, musicPackId: String, collectionId: String): Boolean {
        val musicPackIdLong = musicPackId.toLong()
        val result = makeDeleteRequest("${raindropsProperties.apiUrl}/raindrop/$musicPackIdLong", token)
            .transformToObject<BooleanResult>()
            ?: throw ExternalSourceException(musicPackExternalId = musicPackId)
        return result.result
    }

    override fun obtainAllAddedAfter(token: String, collectionId: String, dateTime: LocalDateTime): List<MusicPackDto> {
        val pageSize = 10
        var totalCount = obtainCount(token, collectionId)
        val raindrops = Lists.newArrayListWithCapacity<Raindrop>(pageSize)

        var idx = 0
        while (idx < totalCount) {
            val raindropList = obtainRaindropList(token, idx / pageSize, pageSize, collectionId.toLong())
                ?: break

            val newRaindrops = raindropList.items.filter { !it.created.correctZoneOffset().isBefore(dateTime) }
            raindrops.addAll(newRaindrops)

            if (newRaindrops.size != raindropList.items.size) {
                break
            }

            idx += pageSize
            totalCount = obtainCount(token, collectionId)
        }

        return raindrops.convert()
    }

    override fun obtainPage(token: String, collectionId: String, page: Int, size: Int): Page<MusicPackDto> {
        if (size > MAX_PAGE_SIZE)
            throw IllegalArgumentException("Page size too big.")

        val raindropList = obtainRaindropList(token, page, size, collectionId.toLong())
        return PageImpl(
            raindropList?.convert() ?: emptyList(),
            PageRequest.of(page, size),
            raindropList?.count?.toLong() ?: 0
        )
    }

    override fun obtainCount(token: String, collectionId: String): Int {
        val collectionIdLong = collectionId.toLong()
        return makeGetRequest(
            "${raindropsProperties.apiUrl}/collection/$collectionIdLong",
            token
        )
            .transformToObject<CollectionResult?>()
            .takeIf { it?.result == true }
            ?.item
            ?.count
            ?: 0
    }

    override fun obtainCollectionInfo(token: String, collectionId: String): CollectionInfoDto? {
        val collectionIdLong = collectionId.toLong()
        return makeGetRequest(
            "${raindropsProperties.apiUrl}/collection/$collectionIdLong",
            token
        )
            .transformToObject<CollectionResult?>()
            ?.takeIf { it.result && it.item?.id == collectionIdLong }
            ?.let { it.item!! }
            ?.let {
                CollectionInfoDto(
                    externalId = it.id.toString(),
                    title = it.title,
                    type = MusicCollectionType.RAINDROPS,
                    itemsCount = it.count ?: 0
                )
            }
    }

    private fun obtainRaindropList(token: String, page: Int, size: Int, collectionId: Long): RaindropList? {
        return makeGetRequest(
            "${raindropsProperties.apiUrl}/raindrops/$collectionId?perpage=$size&page=$page&sort=-created",
            token
        )
            .transformToObject<RaindropList?>()
            ?.takeIf { it.result && it.items.isNotEmpty() }
    }


    private fun RaindropList.convert(): List<MusicPackDto> = items.map { it.convert() }

    private fun List<Raindrop>.convert(): List<MusicPackDto> = this.map { it.convert() }

    private fun Raindrop.convert(): MusicPackDto =
        MusicPackDto(
            externalId = this.id.toString(),
            title = this.title,
            description = this.excerpt,
            coverUrl = this.cover,
            pageUrl = this.link,
            tags = this.tags.toList(),
            addedAt = this.created.correctZoneOffset(),
            updatedAt = this.lastUpdate.correctZoneOffset(),
            removed = this.removed,
        )
}