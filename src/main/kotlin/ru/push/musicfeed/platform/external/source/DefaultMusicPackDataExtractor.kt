package ru.push.musicfeed.platform.external.source

import java.time.LocalDateTime
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.application.dto.MusicPackWithContentDto

@Component
class DefaultMusicPackDataExtractor(
    private val applicationProperties: ApplicationProperties,
    private val defaultMusicTrackDataExtractor: DefaultMusicTrackDataExtractor,
) : MusicPackDataExtractor(
    sourceUrlPattern = "^(https?://)?.+"
) {

    override fun priority(): Int = 99

    override fun extractData(sourceUrl: String): MusicPackWithContentDto {
        val doc = Jsoup.connect(sourceUrl)
            .timeout(applicationProperties.defaultHttpClientTimeoutSec * 1000)
            .get()
        val title = doc.title()

        val trackList = try {
            defaultMusicTrackDataExtractor.extractData(sourceUrl)
        } catch (ex: Throwable) {
            null
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
            tracks = trackList?.content ?: emptyList()
        )
    }
}