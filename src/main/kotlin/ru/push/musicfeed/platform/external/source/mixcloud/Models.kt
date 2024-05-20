package ru.push.musicfeed.platform.external.source.mixcloud

import kotlin.time.Duration

data class DownloadSourceInfo(
    val artistName: String? = null,
    val trackName: String,
    val albumTitle: String? = null,
    val duration: Duration? = null,
)