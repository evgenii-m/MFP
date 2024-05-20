package ru.push.musicfeed.platform.external.source.yandex

import okhttp3.FormBody
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ExternalSourceException
import ru.push.musicfeed.platform.application.ObtainUserTokenException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.YandexProperties
import ru.push.musicfeed.platform.application.dto.UserTokenDto
import ru.push.musicfeed.platform.external.source.AuthExternalSourceClient
import java.time.Clock
import java.time.LocalDateTime

@Component
class YandexMusicAuthExternalSourceClient(
    applicationProperties: ApplicationProperties,
    private val yandexProperties: YandexProperties = applicationProperties.yandex,
    private val clock: Clock = Clock.systemDefaultZone()
) : AuthExternalSourceClient(
    sourceUrlPattern = "^http.*music\\.yandex\\.ru/(.+)\$",
    authorizationTokenPrefix = "OAuth",
    responsesRecorderProperties = applicationProperties.externalSourceResponsesRecorder,
    httpClientTimeoutSec = yandexProperties.httpClientTimeoutSec,
) {

    override fun obtainAuthorizationPageUrl(): String = yandexProperties.oAuthClientUrl

    override fun obtainUserTokenByAuthCode(authCode: String): UserTokenDto {
        val requestBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", authCode)
            .add("client_id", yandexProperties.clientId)
            .add("client_secret", yandexProperties.clientSecret)
            .build()

        val tokenRequestDateTime = LocalDateTime.now(clock)
        val result = makePostRequest(
            methodUrl = yandexProperties.oAuthTokenUrl,
            requestBody = requestBody,
            contentType = "application/x-www-form-urlencoded"
        )
            .transformToObject<TokenResponse?>()
            ?: throw ObtainUserTokenException()

        val token = result.accessToken
        val statusResult = makeGetRequest("${yandexProperties.apiUrl}/account/status", token)
            .transformToObject<AccountStatusResponse?>()
            ?: throw ExternalSourceException()
        val accountName = statusResult.result.account.login

        return UserTokenDto(token, tokenRequestDateTime.plusSeconds(result.expiresIn), accountName)
    }

}