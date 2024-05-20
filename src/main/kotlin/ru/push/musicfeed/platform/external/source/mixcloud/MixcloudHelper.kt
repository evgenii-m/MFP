package ru.push.musicfeed.platform.external.source.mixcloud

import com.fasterxml.jackson.databind.JsonNode
import kotlin.time.Duration
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.toDuration
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.MixcloudProperties
import ru.push.musicfeed.platform.external.source.mixcloud.MixcloudTrackPageDownloadSourceDataExtractor.Companion

@Component
class MixcloudHelper(
    applicationProperties: ApplicationProperties,
    private val mixcloudProperties: MixcloudProperties = applicationProperties.mixcloud,
) {
    final val UNKNOWN = "unknown"
    final val mixcloudSpecificHeaders = mapOf(
        "User-Agent" to mixcloudProperties.userAgent,
        "Host" to "www.mixcloud.com",
        "Accept" to "*/*",
        "Accept-Encoding" to "identity",
        "Connection" to "keep-alive"
    )

    fun isMatchUrl(sourceUrl: String): Boolean {
        val regex = Regex(mixcloudProperties.trackUrlPattern!!)
        return regex.matches(sourceUrl)
    }

    internal fun extractDownloadSourceInfo(dataJsonNode: JsonNode, trackSlug: String): DownloadSourceInfo? {
        val initialRelayDataNode = dataJsonNode.findPath("initialRelayData")
        val trackInfo = initialRelayDataNode?.firstOrNull { it.hasTypeNameCloudcast() || it.hasSlug(trackSlug) }
            ?: return null
        val authorInfo = initialRelayDataNode.firstOrNull { it.hasTypeNameUser() }

        return DownloadSourceInfo(
            artistName = authorInfo?.findPath("username")?.asText() ?: UNKNOWN,
            trackName = trackInfo.findPath("name")!!.asText(),
            duration = trackInfo.findPath("audioLength")?.asLong()?.toDuration(SECONDS)
                ?: Duration.ZERO
        )
    }

    private fun JsonNode.hasTypeNameCloudcast(): Boolean = this.findPath("__typename")?.textValue()?.equals("Cloudcast")  ?: false

    private fun JsonNode.hasTypeNameUser(): Boolean = this.findPath("__typename")?.textValue()?.equals("User")  ?: false

    private fun JsonNode.hasSlug(expectedSlug: String): Boolean = this.findPath("slug")?.textValue()?.equals(expectedSlug)  ?: false
}

fun String.extractSlug() = this.substringAfterLast("/")