package ru.push.musicfeed.platform.external.source

import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalSourceParseException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.dto.MusicArtistDto
import ru.push.musicfeed.platform.application.dto.MusicSourceDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.application.dto.MusicTrackListDto
import ru.push.musicfeed.platform.data.model.music.MusicSourceType
import ru.push.musicfeed.platform.external.source.ytdlp.YtDlpService

@Component
class DefaultMusicTrackDataExtractor(
    private val applicationProperties: ApplicationProperties,
    private val ytDlpService: YtDlpService,
) : MusicTrackDataExtractor(
    sourceUrlPattern = "^(https?://)?.+"
) {

    override fun priority(): Int = 99

    override fun extractData(sourceUrl: String): MusicTrackListDto {
        return ytDlpService.obtainTrackInfoByUrl(sourceUrl).items
            .mapIndexed { idx, trackInfo ->
                MusicTrackDto(
                    title = trackInfo.title,
                    duration = trackInfo.duration,
                    position = idx,
                    artists = trackInfo.artistNames.map { MusicArtistDto(name = it, source = null) },
                    source = MusicSourceDto(type = MusicSourceType.COMMON_EXTERNAL_LINK, url = trackInfo.url),
                )
            }.takeIf { it.isNotEmpty() }
            ?.let { MusicTrackListDto(it) }
            ?: throw ExternalSourceParseException(sourceUrl)
    }

}