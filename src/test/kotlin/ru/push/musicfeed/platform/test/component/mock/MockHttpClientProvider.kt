package ru.push.musicfeed.platform.test.component.mock

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.test.context.ActiveProfiles
import ru.push.musicfeed.platform.external.http.HttpClientProvider
import ru.push.musicfeed.platform.external.http.HttpClientProviderFactory
import ru.push.musicfeed.platform.test.component.readFileFromResources

@Component
@ActiveProfiles(profiles = ["component-test", "mockhttp-test"])
@Primary
class MockHttpClientProviderFactory : HttpClientProviderFactory {

    override fun construct(httpClientTimeoutSec: Long, authorizationTokenPrefix: String?) = MockHttpClientProvider()
}

@Component
@ActiveProfiles(profiles = ["component-test", "mockhttp-test"])
@Primary
class MockHttpClientProvider : HttpClientProvider {

    private val getMethodMockData: Map<String, String> = mapOf(
        ".*/collection/24412771" to
                "external/json/raindrops_collection_24412771.json",
        ".*/raindrops/24412771\\?perpage=10&page=0&sort=-created" to
                "external/json/raindrops_list_24412771.json"
    )

    private fun String.readJson() = readFileFromResources(this).toResponseBody("application/json".toMediaType())

    override fun makeGetRequest(methodUrl: String, token: String?): Response {
        for (entry in getMethodMockData.entries) {
            if (Regex(entry.key).matches(methodUrl)) {
                return baseResponseBuilder()
                    .request(Request.Builder().url(methodUrl).get().build())
                    .body(entry.value.readJson())
                    .build()
            }
        }
        return Response.Builder().code(HttpStatus.NOT_FOUND.value()).build()
    }

    override fun makeDeleteRequest(methodUrl: String, token: String?): Response {
        return baseResponseBuilder()
            .request(Request.Builder().url(methodUrl).delete().build())
            .build()
    }

    override fun <T> makePostRequestJsonBody(methodUrl: String, body: T, token: String?): Response {
        return baseResponseBuilder()
            .request(Request.Builder().url(methodUrl).post("{}".toRequestBody("application/json".toMediaType())).build())
            .build()
    }

    override fun makePostRequest(
        methodUrl: String,
        requestBody: RequestBody,
        contentType: String,
        token: String?
    ): Response {
        return baseResponseBuilder()
            .request(Request.Builder().url(methodUrl).post(requestBody).build())
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun baseResponseBuilder() = Response.Builder()
        .protocol(Protocol.HTTP_1_1)
        .message("")
        .code(HttpStatus.OK.value())
}