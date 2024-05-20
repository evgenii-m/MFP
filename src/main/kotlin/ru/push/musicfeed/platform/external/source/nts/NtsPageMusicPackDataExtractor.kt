package ru.push.musicfeed.platform.external.source.nts

import org.apache.logging.log4j.util.Strings
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.application.dto.MusicPackWithContentDto
import ru.push.musicfeed.platform.external.source.MusicPackDataExtractor
import java.time.LocalDateTime
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.NtsProperties
import ru.push.musicfeed.platform.application.dto.MusicSourceDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.COMMON_EXTERNAL_LINK

@Component
class NtsPageMusicPackDataExtractor(
    private val applicationProperties: ApplicationProperties,
    private val ntsProperties: NtsProperties = applicationProperties.nts,
) : MusicPackDataExtractor(
    sourceUrlPattern = ntsProperties.trackUrlPattern
) {

    override fun extractData(sourceUrl: String): MusicPackWithContentDto {
        val doc = Jsoup.connect(sourceUrl)
            .timeout(ntsProperties.timeoutSec * 1000)
            .get()
        val bioElem = doc.getElementsByClass("bio").first()!!
        val title = bioElem.getElementsByClass("episode__btn").first()
            ?.attr("data-episode-name")
            ?.trim()
            ?: Strings.EMPTY
        val description = bioElem.getElementsByClass("description").first()
            ?.getElementsByTag("h3")?.first()
            ?.text()
            ?.trim()
        val tags = bioElem.getElementsByClass("episode-genres").first()
            ?.getElementsByTag("a")
            ?.map { it.text().lowercase() }
            ?.toList()
            ?: emptyList()
        val coverUrl = doc.head().getElementsByTag("meta")
            .find { it.attr("property") == "og:image" }
            ?.attr("content")

        val trackData = MusicTrackDto(
            title = title,
            position = 0,
            source = MusicSourceDto(type = COMMON_EXTERNAL_LINK, url = sourceUrl)
        )

        return MusicPackWithContentDto(
            musicPack = MusicPackDto(
                title = title,
                description = description,
                tags = tags,
                addedAt = LocalDateTime.now(clock),
                coverUrl = coverUrl,
                pageUrl = sourceUrl,
                editable = true
            ),
            tracks = listOf(trackData)
        )
    }
}