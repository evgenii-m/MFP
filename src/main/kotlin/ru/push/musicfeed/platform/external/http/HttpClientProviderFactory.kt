package ru.push.musicfeed.platform.external.http

interface HttpClientProviderFactory {
    fun construct(httpClientTimeoutSec: Long, authorizationTokenPrefix: String?): HttpClientProvider
}