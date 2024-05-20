package ru.push.musicfeed.platform.application.dto

import kotlin.time.Duration

data class TrackFileInfoDto(
    val trackLocalFileId: Long,
    val performer: String? = null,
    val trackTitle: String,
    val duration: Duration,
    val filePath: String,
    var fileExternalId: String? = null,
)

data class MusicPackTracksFileInfo(
    val musicPack: MusicPackDto,
    val trackListFileInfo: List<TrackFileInfoDto>
)