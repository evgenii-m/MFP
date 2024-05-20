package ru.push.musicfeed.platform.external.source.mixcloud

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalSourceParseException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.MixcloudProperties
import ru.push.musicfeed.platform.application.dto.MusicArtistDto
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.application.dto.MusicPackWithContentDto
import ru.push.musicfeed.platform.application.dto.MusicSourceDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.COMMON_EXTERNAL_LINK
import ru.push.musicfeed.platform.external.source.MusicPackDataExtractor

@Component
class MixcloudTrackPageMusicPackDataExtractor(
    applicationProperties: ApplicationProperties,
    private val mixcloudProperties: MixcloudProperties = applicationProperties.mixcloud,
    private val mixcloudHelper: MixcloudHelper
) : MusicPackDataExtractor(
    sourceUrlPattern = mixcloudProperties.trackUrlPattern!!
) {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .apply {
            findAndRegisterModules()
        }

    override fun extractData(sourceUrl: String): MusicPackWithContentDto {
        val doc = Jsoup.connect(sourceUrl)
            .timeout(mixcloudProperties.timeoutSec.toInt() * 1000)
            .headers(mixcloudHelper.mixcloudSpecificHeaders)
            .get()
        val title = doc.title()

        val dataContext = doc.getElementById("app-context")?.data()
            ?: throw ExternalSourceParseException(sourceUrl)
        val dataJsonNode = mapper.readTree(dataContext)

        val trackData = mixcloudHelper.extractDownloadSourceInfo(dataJsonNode, sourceUrl.extractSlug())
            ?.let {
                MusicTrackDto(
                    title = it.trackName,
                    position = 0,
                    duration = it.duration,
                    artists = it.artistName?.let { listOf(MusicArtistDto(name = it, source = null)) } ?: emptyList(),
                    source = MusicSourceDto(type = COMMON_EXTERNAL_LINK, url = sourceUrl)
                )
            }

        return MusicPackWithContentDto(
            musicPack = MusicPackDto(
                title = title,
                description = null,
                tags = mutableListOf(),
                addedAt = LocalDateTime.now(clock),
                coverUrl = null,
                pageUrl = sourceUrl,
                editable = true
            ),
            tracks = trackData?.let { listOf(it) } ?: emptyList()
        )
    }
}