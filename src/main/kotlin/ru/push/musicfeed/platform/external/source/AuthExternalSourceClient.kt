package ru.push.musicfeed.platform.external.source

import ru.push.musicfeed.platform.application.config.ExternalSourceResponsesRecorderProperties
import ru.push.musicfeed.platform.application.dto.UserTokenDto

abstract class AuthExternalSourceClient(
    sourceUrlPattern: String,
    authorizationTokenPrefix: String,
    responsesRecorderProperties: ExternalSourceResponsesRecorderProperties,
    httpClientTimeoutSec: Long,
) : AbstractExternalSourceClient(
    sourceUrlPattern,
    authorizationTokenPrefix,
    responsesRecorderProperties,
    httpClientTimeoutSec,
) {

    abstract fun obtainAuthorizationPageUrl(): String

    abstract fun obtainUserTokenByAuthCode(authCode: String): UserTokenDto
}