package ru.push.musicfeed.platform.external.source

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import okhttp3.RequestBody
import okhttp3.Response
import ru.push.musicfeed.platform.application.config.ExternalSourceResponsesRecorderProperties
import java.time.Clock
import javax.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import ru.push.musicfeed.platform.external.http.HttpClientProvider
import ru.push.musicfeed.platform.external.http.HttpClientProviderFactory
import ru.push.musicfeed.platform.util.recordResponse

abstract class AbstractExternalSourceClient(
    val sourceUrlPattern: String,
    private val authorizationTokenPrefix: String? = null,
    private val responsesRecorderProperties: ExternalSourceResponsesRecorderProperties,
    private val httpClientTimeoutSec: Long,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    private val logger = KotlinLogging.logger {}
    @Autowired
    private lateinit var httpClientProviderFactory: HttpClientProviderFactory
    private lateinit var httpClientProvider: HttpClientProvider

    @PostConstruct
    fun setUp() {
        this.httpClientProvider = httpClientProviderFactory.construct(httpClientTimeoutSec, authorizationTokenPrefix)
    }

    fun setHttpClientProvider(provider: HttpClientProvider) {
        this.httpClientProvider = provider
    }

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .apply {
            findAndRegisterModules()
        }

    fun isSupportedSourceUrl(sourceUrl: String): Boolean {
        val regex = Regex(sourceUrlPattern)
        return regex.matches(sourceUrl)
    }

    fun makeGetRequest(methodUrl: String, token: String? = null) =
        httpClientProvider.makeGetRequest(methodUrl, token).recordResponse()

    fun makeDeleteRequest(methodUrl: String, token: String? = null) =
        httpClientProvider.makeDeleteRequest(methodUrl, token).recordResponse()

    fun <T> makePostRequestJsonBody(methodUrl: String, body: T, token: String? = null) =
        httpClientProvider.makePostRequestJsonBody(methodUrl, body, token).recordResponse()

    fun makePostRequest(
        methodUrl: String,
        requestBody: RequestBody,
        contentType: String,
        token: String? = null,
    ): Response {
        return httpClientProvider.makePostRequest(methodUrl, requestBody, contentType, token).recordResponse()
    }

    private fun Response.recordResponse() = also {
        if (responsesRecorderProperties.enabled) {
            it.recordResponse(responsesRecorderProperties.responsesFolderPath)
        }
    }

    internal inline fun <reified T> Response.transformToObject(): T? =
        body?.string()?.let {
            try {
                return mapper.readValue(it)
            } catch (ex: Exception) {
                logger.error { "Response parse error: $ex" }
            }
            return null
        }
}