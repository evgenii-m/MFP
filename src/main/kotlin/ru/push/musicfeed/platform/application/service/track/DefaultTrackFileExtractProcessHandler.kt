package ru.push.musicfeed.platform.application.service.track

import java.time.Clock
import java.time.LocalDateTime
import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.data.model.TrackLocalFile
import ru.push.musicfeed.platform.data.repo.TrackLocalFileRepository

@Component
class DefaultTrackFileExtractProcessHandler(
    private val trackLocalFileRepository: TrackLocalFileRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : TrackFileExtractProcessHandler {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val log = KotlinLogging.logger(javaClass.enclosingClass.canonicalName)
    }

    override fun completeSuccess(sourceTrackLocalFileId: Long, targetTrackTitle: String, targetFilePath: String, trackDurationSec: Long) {
        val now = LocalDateTime.now(clock)
        trackLocalFileRepository.save(TrackLocalFile(
            filePath = targetFilePath,
            trackTitle = targetTrackTitle,
            trackDurationSec = trackDurationSec,
            sourceTrackFileId = sourceTrackLocalFileId,
            addedAt = now
        ))
    }

    override fun fail(sourceTrackLocalFileId: Long, targetTrackTitle: String, targetFilePath: String, ex: Throwable) {
        log.error { "Track file extracting failed: sourceTrackLocalFileId = $sourceTrackLocalFileId, targetTrackTitle = $targetTrackTitle, " +
                "targetFilePath = $targetFilePath, ex = $ex" }
    }
}