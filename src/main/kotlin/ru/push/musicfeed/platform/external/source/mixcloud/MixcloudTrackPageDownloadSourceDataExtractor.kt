package ru.push.musicfeed.platform.external.source.mixcloud

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalSourceParseException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.MixcloudProperties
import ru.push.musicfeed.platform.external.source.DataExtractor

@Component
class MixcloudTrackPageDownloadSourceDataExtractor(
    applicationProperties: ApplicationProperties,
    private val mixcloudProperties: MixcloudProperties = applicationProperties.mixcloud,
    private val mixcloudHelper: MixcloudHelper,
) : DataExtractor<DownloadSourceInfo>(
    sourceUrlPattern = mixcloudProperties.trackUrlPattern!!
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
        val document = Jsoup.connect(sourceUrl)
            .timeout(mixcloudProperties.timeoutSec.toInt() * 1000)
            .headers(mixcloudHelper.mixcloudSpecificHeaders)
            .get()
        val dataContext = document.getElementById("app-context")?.data()
            ?: throw ExternalSourceParseException(sourceUrl)
        val dataJsonNode = mapper.readTree(dataContext)
        return mixcloudHelper.extractDownloadSourceInfo(dataJsonNode, sourceUrl.extractSlug())
            ?: throw ExternalSourceParseException(sourceUrl)
    }
}