package ru.push.musicfeed.platform.external.source.soundcloud

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.toDuration
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalSourceParseException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.SoundcloudProperties
import ru.push.musicfeed.platform.external.source.DataExtractor

@Component
class SoundcloudTrackPageDownloadSourceDataExtractor(
    applicationProperties: ApplicationProperties,
    private val soundcloudProperties: SoundcloudProperties = applicationProperties.soundcloud
) : DataExtractor<DownloadSourceInfo>(
    sourceUrlPattern = soundcloudProperties.trackUrlPattern!!
) {

    companion object {
        private const val UNKNOWN = "unknown"
    }

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .apply {
            findAndRegisterModules()
        }

    override fun extractData(sourceUrl: String): DownloadSourceInfo {
        val document = Jsoup.connect(sourceUrl).timeout(soundcloudProperties.timeoutSec.toInt() * 1000).get()
        val pageDataJson = document.obtainPageDataJson(sourceUrl)
        val dataJsonNode = mapper.readTree(pageDataJson)

        val trackUrn = dataJsonNode.findPath("trackUrn").asText()
        val trackDataJsonNode = dataJsonNode.findPath("tracks")?.findValue(trackUrn)?.findPath("data")
            ?: throw ExternalSourceParseException(sourceUrl)

        val artistName = trackDataJsonNode.findPath("artist")?.textValue()
        val trackTitle = trackDataJsonNode.path("title")?.textValue() ?: UNKNOWN
        val mpegHlsTranscoding = trackDataJsonNode.findMpegHlsTranscoding()
            ?: throw ExternalSourceParseException(sourceUrl)
        val mpegHlsUrl = mpegHlsTranscoding.path("url").asText()
        val durationMillis = mpegHlsTranscoding.path("duration").asLong(0)
        val clientId = dataJsonNode.findPath("clientId").textValue()

        return DownloadSourceInfo(
            hlsRequestUrl = "$mpegHlsUrl?client_id=$clientId",
            artistName = artistName,
            trackName = trackTitle,
            duration = durationMillis.toDuration(MILLISECONDS)
        )
    }

    private fun Document.obtainPageDataJson(sourceUrl: String): String {
        return this.getElementById("__NEXT_DATA__")
            ?.takeIf { it.attr("type").equals("application/json") }
            ?.data()
            ?: throw ExternalSourceParseException(sourceUrl)
    }

    private fun JsonNode.findMpegHlsTranscoding(): JsonNode? {
        return this.findPath("transcodings")?.find { transconigNode ->
            transconigNode.findPath("protocol").asText().equals("hls")
                    && transconigNode.findPath("mime_type").asText().equals("audio/mpeg")
        }
    }
}