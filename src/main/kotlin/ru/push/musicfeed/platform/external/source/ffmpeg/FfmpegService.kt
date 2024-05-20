package ru.push.musicfeed.platform.external.source.ffmpeg

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.DurationUnit.SECONDS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.push.musicfeed.platform.application.TrackFileAlreadyExistsException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.FfmpegProperties
import ru.push.musicfeed.platform.application.service.track.TrackFileExtractProcessHandler
import ru.push.musicfeed.platform.util.executeWithTimeout

@Service
class FfmpegService(
    applicationProperties: ApplicationProperties,
    private val ffmpegProperties: FfmpegProperties = applicationProperties.ffmpeg,
    private val trackFileExtractProcessHandler: TrackFileExtractProcessHandler,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val log = KotlinLogging.logger(javaClass.enclosingClass.canonicalName)
    }

    private val ffmpegScope = object : CoroutineScope {
        override val coroutineContext = Dispatchers.IO
    }

    private fun Duration.convertToTimestamp(): String = this.toComponents { hours, minutes, seconds, _ ->
        "${withLeadZero(hours)}:${withLeadZero(minutes)}:${withLeadZero(seconds)}"
    }

    private fun withLeadZero(o: Any): String = "%02d".format(o)


    fun cutTrackFile(
        sourceTrackLocalFileId: Long,
        sourceFilePath: String,
        targetFilePath: String,
        targetTrackTitle: String,
        start: Duration,
        duration: Duration,
    ) {
        if (File(targetFilePath).exists()) {
            throw TrackFileAlreadyExistsException(targetFilePath)
        }

        try {
            val process = ProcessBuilder().command(
                ffmpegProperties.execPath,
                "-i", "\"$sourceFilePath\"",
                "-ss", start.convertToTimestamp(),
                "-t", duration.convertToTimestamp(),
                "-c", "copy", "\"$targetFilePath\"",
            ).start()

            executeWithTimeout(
                scope = ffmpegScope,
                timeoutSec = ffmpegProperties.timeoutSec,
                mainAction = {
                    log.trace { "FFmpeg mp3 file cut started, sourceTrackLocalFileId = $sourceTrackLocalFileId, PID = ${process.pid()}" }
                    if (ffmpegProperties.logEnable) {
                        printOutputToLog(process.inputStream)
                        printOutputToLog(process.errorStream)
                    }
                },
                completeAction = {
                    process.waitFor(ffmpegProperties.timeoutSec, TimeUnit.SECONDS)
                    trackFileExtractProcessHandler.completeSuccess(
                        sourceTrackLocalFileId = sourceTrackLocalFileId,
                        targetTrackTitle = targetTrackTitle,
                        targetFilePath = targetFilePath,
                        trackDurationSec = duration.toLong(SECONDS)
                    )
                    log.trace { "FFmpeg mp3 file cut finish successfully, sourceTrackLocalFileId = $sourceTrackLocalFileId, PID = ${process.pid()}" }
                },
                timeoutAction = {
                    trackFileExtractProcessHandler.fail(
                        sourceTrackLocalFileId = sourceTrackLocalFileId,
                        targetTrackTitle = targetTrackTitle,
                        targetFilePath = targetFilePath,
                        ex = IllegalStateException("Download process time out")
                    )
                    log.warn { "FFmpeg mp3 file cut operation time out, sourceTrackLocalFileId = $sourceTrackLocalFileId, PID = ${process.pid()}" }
                }
            )
        } catch (e: Throwable) {
            log.error(e) { "FFmpeg mp3 file cut operation exception, sourceTrackLocalFileId = $sourceTrackLocalFileId, exception: $e" }
            trackFileExtractProcessHandler.fail(sourceTrackLocalFileId, targetTrackTitle, targetFilePath, e)
        }
    }

    private fun InputStream.createBufferedReader() = BufferedReader(InputStreamReader(this, ffmpegProperties.encoding))

    private fun printOutputToLog(processStream: InputStream) {
        val processOutput = processStream.createBufferedReader()
        do {
            val outputLine: String? = processOutput.readLine()
                ?.takeUnless { it.contentEquals("null") }
                ?.also { log.info { it } }
        } while (outputLine != null)
    }

}