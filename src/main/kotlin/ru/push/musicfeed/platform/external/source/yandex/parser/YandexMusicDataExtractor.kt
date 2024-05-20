package ru.push.musicfeed.platform.external.source.yandex.parser

import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalSourceParseException
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.application.dto.MusicTrackListDto
import ru.push.musicfeed.platform.external.source.MusicTrackDataExtractor
import ru.push.musicfeed.platform.external.source.yandex.YandexMusicPackContentExternalSourceClient

@Component
class YandexMusicDataExtractor(
    val musicPackContentSource: YandexMusicPackContentExternalSourceClient
) : MusicTrackDataExtractor(
    sourceUrlPattern = "^http.*music\\.yandex\\.ru/.*track/(\\d+)/?\$"
) {

    override fun extractData(sourceUrl: String): MusicTrackListDto {
        val regex = Regex(sourceUrlPattern)
        val matchResult = regex.find(sourceUrl)
        if (matchResult == null || matchResult.groupValues.size < 2)
            throw ExternalSourceParseException(sourceUrl)

        val trackId = matchResult.groupValues[1].toLong()

        return musicPackContentSource.fetchTrack(trackId)
            ?.let { MusicTrackListDto(listOf(it)) }
            ?: throw ExternalSourceParseException(sourceUrl)
    }
}