package ru.push.musicfeed.platform.external.source.yandex.parser

import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalSourceParseException
import ru.push.musicfeed.platform.application.dto.MusicTrackExternalIdDto
import ru.push.musicfeed.platform.external.source.MusicTrackExternalIdDataExtractor

@Component
class YandexMusicTrackExternalIdDataExtractor(
) : MusicTrackExternalIdDataExtractor(
    sourceUrlPattern = "^http.*music\\.yandex\\.ru/(album/(\\d+)/)?track/(\\d+)/?\$"
) {
    override fun extractData(sourceUrl: String): MusicTrackExternalIdDto {
        val regex = Regex(sourceUrlPattern)
        val matchResult = regex.find(sourceUrl)
        if (matchResult == null || matchResult.groupValues.size < 3)
            throw ExternalSourceParseException(sourceUrl)

        val groupValues = matchResult.groupValues
        val trackId = groupValues[groupValues.size - 1].toLong()
        val albumId = groupValues[groupValues.size - 2].takeIf { it.isNotBlank() }?.toLong()

        return MusicTrackExternalIdDto(albumId, trackId)
    }
}