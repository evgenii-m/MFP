package ru.push.musicfeed.platform.external.source.soundcloud

import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.InvalidExternalSourceUrlException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.external.source.AbstractExternalSourceClient

@Component
class SoundcloudApiProvider(
    applicationProperties: ApplicationProperties,
) : AbstractExternalSourceClient(
    sourceUrlPattern = "^http.*soundcloud\\.com/.+?client_id=.+",
    responsesRecorderProperties = applicationProperties.externalSourceResponsesRecorder,
    httpClientTimeoutSec = applicationProperties.soundcloud.timeoutSec,
) {

    fun obtainStreamUrl(hlsRequestUrl: String): String {
        return makeGetRequest(hlsRequestUrl)
            .transformToObject<UrlResponse>()
            ?.url
            ?: throw InvalidExternalSourceUrlException(hlsRequestUrl)
    }
}