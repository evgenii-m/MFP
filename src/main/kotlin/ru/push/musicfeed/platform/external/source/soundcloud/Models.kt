package ru.push.musicfeed.platform.external.source.soundcloud

import kotlin.time.Duration

data class UrlResponse(
    val url: String
)

data class DownloadSourceInfo(
    val hlsRequestUrl: String,
    val artistName: String? = null,
    val trackName: String,
    val trackId: String? = null,
    val albumTitle: String? = null,
    val duration: Duration? = null,
)