package ru.push.musicfeed.platform.external.http

import okhttp3.RequestBody
import okhttp3.Response

interface HttpClientProvider {
    companion object {
        const val CONTENT_TYPE_JSON = "application/json"
    }

    fun makeGetRequest(methodUrl: String, token: String? = null): Response

    fun makeDeleteRequest(methodUrl: String, token: String? = null): Response

    fun <T> makePostRequestJsonBody(methodUrl: String, body: T, token: String? = null): Response

    fun makePostRequest(
        methodUrl: String,
        requestBody: RequestBody,
        contentType: String,
        token: String? = null,
    ): Response
}