package ru.push.musicfeed.platform.external.source

import ru.push.musicfeed.platform.application.dto.MusicPackWithContentDto
import ru.push.musicfeed.platform.application.dto.MusicTrackExternalIdDto
import java.time.Clock
import ru.push.musicfeed.platform.application.dto.MusicTrackListDto

typealias MusicPackDataExtractor = DataExtractor<MusicPackWithContentDto>
typealias MusicTrackDataExtractor = DataExtractor<MusicTrackListDto>
typealias MusicTrackExternalIdDataExtractor = DataExtractor<MusicTrackExternalIdDto>

abstract class DataExtractor<T>(
    internal val sourceUrlPattern: String
) {
    internal val clock: Clock = Clock.systemDefaultZone()

    fun isSupportedSource(sourceUrl: String): Boolean {
        val regex = Regex(sourceUrlPattern)
        return regex.matches(sourceUrl)
    }

    open fun priority(): Int = 0

    abstract fun extractData(sourceUrl: String): T
}