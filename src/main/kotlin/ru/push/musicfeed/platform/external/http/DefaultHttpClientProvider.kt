package ru.push.musicfeed.platform.external.http

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.common.net.HttpHeaders
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class DefaultHttpClientProvider(
    httpClientTimeoutSec: Long,
    private val authorizationTokenPrefix: String,
) : HttpClientProvider {

    companion object {
        fun construct(httpClientTimeoutSec: Long, authorizationTokenPrefix: String? = null): HttpClientProvider {
            return DefaultHttpClientProvider(httpClientTimeoutSec, authorizationTokenPrefix ?: "")
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(httpClientTimeoutSec, TimeUnit.SECONDS)
        .readTimeout(httpClientTimeoutSec, TimeUnit.SECONDS)
        .writeTimeout(httpClientTimeoutSec, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .proxy(Proxy.NO_PROXY)
        .build()

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .apply {
            findAndRegisterModules()
        }

    override fun makeGetRequest(methodUrl: String, token: String?): Response {
        val requestBuilder = Request.Builder()
            .url(methodUrl)
            .get()
            .appendTokenIfNotNull(token)
        return httpClient.newCall(requestBuilder.build()).execute()
    }

    override fun makeDeleteRequest(methodUrl: String, token: String?): Response {
        val requestBuilder = Request.Builder()
            .url(methodUrl)
            .delete()
            .appendTokenIfNotNull(token)
        return httpClient.newCall(requestBuilder.build()).execute()
    }

    override fun <T> makePostRequestJsonBody(methodUrl: String, body: T, token: String?): Response {
        val requestBody = mapper.writeValueAsString(body).toRequestBody(HttpClientProvider.CONTENT_TYPE_JSON.toMediaType())
        val requestBuilder = Request.Builder()
            .url(methodUrl)
            .post(requestBody)
            .appendTokenIfNotNull(token)
        return httpClient.newCall(requestBuilder.build()).execute()
    }

    override fun makePostRequest(
        methodUrl: String,
        requestBody: RequestBody,
        contentType: String,
        token: String?,
    ): Response {
        val requestBuilder = Request.Builder()
            .url(methodUrl)
            .post(requestBody)
            .appendTokenIfNotNull(token)
            .appendHeaderIfNotNull(HttpHeaders.CONTENT_TYPE, contentType)
        return httpClient.newCall(requestBuilder.build()).execute()
    }

    private fun Request.Builder.appendHeaderIfNotNull(name: String, value: String?): Request.Builder {
        if (value != null) {
            header(name, value)
        }
        return this
    }

    private fun Request.Builder.appendTokenIfNotNull(token: String?): Request.Builder {
        if (token != null) {
            header(HttpHeaders.AUTHORIZATION, "$authorizationTokenPrefix $token")
        }
        return this
    }
}