package ru.push.musicfeed.platform.external.http

import org.springframework.stereotype.Component

@Component
class DefaultHttpClientProviderFactory : HttpClientProviderFactory {

    override fun construct(httpClientTimeoutSec: Long, authorizationTokenPrefix: String?): HttpClientProvider {
        return DefaultHttpClientProvider.construct(httpClientTimeoutSec, authorizationTokenPrefix)
    }
}