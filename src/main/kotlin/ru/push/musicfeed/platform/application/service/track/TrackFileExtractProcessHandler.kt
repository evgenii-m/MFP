package ru.push.musicfeed.platform.application.service.track

interface TrackFileExtractProcessHandler {

    fun completeSuccess(sourceTrackLocalFileId: Long, targetTrackTitle: String, targetFilePath: String, trackDurationSec: Long)

    fun fail(sourceTrackLocalFileId: Long, targetTrackTitle: String, targetFilePath: String, ex: Throwable)
}