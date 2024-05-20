package ru.push.musicfeed.platform.external.source.yandex.parser

import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalSourceParseException
import ru.push.musicfeed.platform.application.dto.MusicPackWithContentDto
import ru.push.musicfeed.platform.external.source.MusicPackDataExtractor
import ru.push.musicfeed.platform.external.source.yandex.YandexMusicPackContentExternalSourceClient

@Component
class YandexAlbumMusicPackDataExtractor(
    val musicPackContentSource: YandexMusicPackContentExternalSourceClient,
) : MusicPackDataExtractor(
    sourceUrlPattern = "^http.*music\\.yandex\\.ru/album/(\\d+)/?\$"
) {

    override fun extractData(sourceUrl: String): MusicPackWithContentDto {
        val regex = Regex(sourceUrlPattern)
        val matchResult = regex.find(sourceUrl)
        if (matchResult == null || matchResult.groupValues.size < 2)
            throw ExternalSourceParseException(sourceUrl)

        val albumId = matchResult.groupValues[1].toLong()

        return musicPackContentSource.fetchAlbumMusicPackWithContent(albumId)
            ?: throw ExternalSourceParseException(sourceUrl)
    }
}