package ru.push.musicfeed.platform.external.source.ytdlp

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import ru.push.musicfeed.platform.application.ExternalSourceParseException
import ru.push.musicfeed.platform.application.ExternalSourceSearchException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.CachingConfig
import ru.push.musicfeed.platform.application.config.YtDlpProperties
import ru.push.musicfeed.platform.application.service.download.DownloadProcessHandler
import ru.push.musicfeed.platform.util.executeWithTimeout
import ru.push.musicfeed.platform.util.toDuration

@Service
class YtDlpService(
    applicationProperties: ApplicationProperties,
    private val ytDlpProperties: YtDlpProperties = applicationProperties.ytDlp,
    private val processHandler: DownloadProcessHandler,
) {
    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val log = KotlinLogging.logger(javaClass.enclosingClass.canonicalName)

        private val printInfoArguments = arrayOf(
            "--print", "artist",
            "--print", "title",
            "--print", "webpage_url",
            "--print", "duration_string",
        )
    }

    private val ytdlpScope = object : CoroutineScope {
        override val coroutineContext = Dispatchers.IO
    }

    fun mixcloudStreamDownloadByUrlAsMp3(trackUrl: String, outputFilePath: String, processInfoId: Long) {
        downloadByUrlAsMp3(trackUrl, outputFilePath, processInfoId) { processOutput ->
            val totalParts = processOutput.waitForDownloadStart(processInfoId)
            processOutput.waitForDownloadProgress(processInfoId)
            processOutput.waitForDownloadComplete(processInfoId, totalParts)
        }
    }

    fun defaultDownloadByUrlAsMp3(trackUrl: String, outputFilePath: String, processInfoId: Long) {
        downloadByUrlAsMp3(trackUrl, outputFilePath, processInfoId) { processOutput ->
            val totalParts = processOutput.waitForDownloadProgressPercents(processInfoId)
            processOutput.waitForDownloadComplete(processInfoId, totalParts)
        }
    }

    fun downloadByUrlAsMp3(
        trackUrl: String,
        outputFilePath: String,
        processInfoId: Long,
        outputProcessingAction: suspend (BufferedReader) -> Unit,
    ) {
        try {
            val process = ProcessBuilder().command(
                ytDlpProperties.execPath,
                "--encoding", ytDlpProperties.encoding,
                "--verbose",
                "--extract-audio",
                "--audio-format", "mp3",
                "--audio-quality", "0",
                "--output", outputFilePath,
                trackUrl
            ).start()
            executeWithTimeout(
                scope = ytdlpScope,
                timeoutSec = ytDlpProperties.downloadTimeoutSec,
                mainAction = {
                    log.trace { "Download track via yt-dlp started, processInfoId = $processInfoId, PID = ${process.pid()}" }
                    val processOutput = process.inputStream.createBufferedReader()
                    try {
                        outputProcessingAction(processOutput)
                    } catch (ex: DownloadAlreadyCompletedException) {
                        processHandler.completeSuccess(processInfoId)
                        log.trace { "Track already downloaded via yt-dlp, processInfoId = $processInfoId, PID = ${process.pid()}" }
                        process.destroy()
                    }
                },
                completeAction = {
                    process.waitFor(ytDlpProperties.downloadTimeoutSec, TimeUnit.SECONDS)
                    log.trace { "Download track via yt-dlp finished, processInfoId = $processInfoId, PID = ${process.pid()}" }
                },
                timeoutAction = {
                    processHandler.fail(processInfoId, IllegalStateException("Download process time out"))
                    log.warn { "Download track via yt-dlp time out, processInfoId = $processInfoId, PID = ${process.pid()}" }
                }
            )
        } catch (e: Throwable) {
            log.error(e) { "Yt-Dlp download or convert exception, processInfoId = $processInfoId: $e" }
            processHandler.fail(processInfoId, e)
        }
    }

    @Cacheable(value = [CachingConfig.YT_DLP_DEFAULT_SEARCH_TRACKS], key = "{#size, #searchText}")
    fun defaultSearchTracks(searchText: String, size: Int): TrackSearchResult {
        try {
            val process = ProcessBuilder().command(
                ytDlpProperties.execPath,
                "--encoding", ytDlpProperties.encoding,
                "ytsearch$size:\\\"${searchText}\\\"",
                "--skip-download",
                *printInfoArguments
            ).start()
            val resultItems: MutableList<TrackInfo> = ArrayList(size)
            executeWithTimeout(
                scope = ytdlpScope,
                timeoutSec = ytDlpProperties.searchTimeoutSec,
                mainAction = {
                    log.trace { "Search track via yt-dlp started, PID = ${process.pid()}" }
                    val processOutput = process.inputStream.createBufferedReader()
                    val outputLines = processOutput.waitForAllOutputLines(size)
                    resultItems.addAll(outputLines.parseToTrackInfoList())
                },
                completeAction = {
                    process.waitFor(ytDlpProperties.searchTimeoutSec, TimeUnit.SECONDS)
                    log.trace { "Search track via yt-dlp finished, PID = ${process.pid()}" }
                },
                timeoutAction = {
                    log.warn { "Search track via yt-dlp time out, PID = ${process.pid()}" }
                    throw ExternalSourceSearchException(searchText)
                }
            )
            return TrackSearchResult(resultItems)
        } catch (e: Throwable) {
            log.error(e) { "Yt-Dlp search exception: $e" }
            throw ExternalSourceSearchException(searchText)
        }
    }

    private fun List<String>.parseToTrackInfoList(): List<TrackInfo> {
        return this.chunked(4).filter { it.size == 4 }
            .map {
                val rawArtists = it[0]; val rawTitle = it[1]; val rawUrl = it[2]; val rawDuration = it[3]
                val title: String
                val artistNames: List<String>
                val titleParts = rawTitle.split(" - ", limit = 2).map { it.trim() }
                if (titleParts.size == 2 && (rawArtists.isBlank() || rawArtists.contentEquals("NA", true))) {
                    artistNames = listOf(titleParts[0])
                    title = titleParts[1]
                } else {
                    artistNames = rawArtists.split(";").filter { it.isNotBlank() }
                    title = if (titleParts.size == 2 && titleParts[0].contentEquals(rawArtists))
                        titleParts[1]
                    else
                        rawTitle.trim()
                }
                TrackInfo(
                    title = title,
                    artistNames = artistNames,
                    url = rawUrl.trim(),
                    duration = rawDuration.takeIf { it.isNotBlank() }?.toDuration() ?: Duration.ZERO,
                )
            }
    }

    @Cacheable(value = [CachingConfig.YT_DLP_OBTAIN_TRACK_INFO_BY_URL], key = "#trackUrl")
    fun obtainTrackInfoByUrl(trackUrl: String): TrackSearchResult {
        try {
            val process = ProcessBuilder().command(
                ytDlpProperties.execPath,
                "--encoding", ytDlpProperties.encoding,
                "--skip-download",
                *printInfoArguments,
                trackUrl
            ).start()

            val resultItems: MutableList<TrackInfo> = ArrayList()
            executeWithTimeout(
                scope = ytdlpScope,
                timeoutSec = ytDlpProperties.searchTimeoutSec,
                mainAction = {
                    log.trace { "Obtain track info via yt-dlp started, PID = ${process.pid()}" }
                    val processOutput = process.inputStream.createBufferedReader()
                    val outputLines = processOutput.waitForAllOutputLines()
                    resultItems.addAll(outputLines.parseToTrackInfoList())
                },
                completeAction = {
                    process.waitFor(ytDlpProperties.searchTimeoutSec, TimeUnit.SECONDS)
                    log.trace { "Obtain track info via yt-dlp finished, PID = ${process.pid()}" }
                },
                timeoutAction = {
                    log.warn { "Obtain track info via yt-dlp time out, PID = ${process.pid()}" }
//                    process.destroy()
                }
            )
            return TrackSearchResult(resultItems)
        } catch (e: Throwable) {
            log.error(e) { "Yt-Dlp obtain track info exception: $e" }
            throw ExternalSourceParseException(sourceUrl = trackUrl)
        }
    }

    private fun InputStream.createBufferedReader() = BufferedReader(InputStreamReader(this, ytDlpProperties.encoding))


    private suspend fun BufferedReader.waitForDownloadStart(processInfoId: Long): Int {
        var result = 0
        do {
            val outputLine: String? = this.readLineWithLog()
            outputLine?.let { checkAlreadyCompleted(it) }
            val totalPartsNumber = outputLine?.let { line ->
                Regex(".*Total fragments:\\s*(\\d+).*").find(line)?.groupValues
                    ?.let { it[it.size - 1].toInt() }
            }?.also {
                processHandler.start(processInfoId, it)
                result = it
            }
        } while (outputLine != null && totalPartsNumber == null)
        return result
    }

    private fun checkAlreadyCompleted(outputLine: String) {
        val completedOutputPatterns = listOf(
            ".*has already been downloaded.*",
            ".*file is already in target format.*",
        )
        completedOutputPatterns.forEach {
            if (Regex(it).matches(outputLine))
                throw DownloadAlreadyCompletedException()
        }
    }

    private suspend fun BufferedReader.waitForDownloadProgress(processInfoId: Long) {
        do {
            val outputLine: String? = this.readLineWithLog()
            val progressInfo = outputLine?.let { line ->
                Regex(".*\\(frag (\\d+)/(\\d+)\\).*").find(line)?.groupValues
                    ?.let {
                        DownloadProgressInfo(
                            totalParts = it[it.size - 1].toInt(),
                            downloadedParts = it[it.size - 2].toInt()
                        )
                    }
            }?.also {
                processHandler.progress(processInfoId, it.downloadedParts)
            }
        } while (outputLine != null && !isFinished(progressInfo))
    }

    private suspend fun BufferedReader.waitForDownloadComplete(processInfoId: Long, totalParts: Int) {
        do {
            val outputLine: String? = this.readLineWithLog()
            val extractAudioComplete = outputLine?.let { Regex(".*Deleting original file.*").matches(it) }
                ?: false
            val alreadyInTargetFormat = outputLine?.let { Regex(".*file is already in target format.*").matches(it) }
                ?: false
            if (extractAudioComplete || alreadyInTargetFormat) {
                processHandler.completeSuccess(processInfoId, totalParts)
            }
        } while (outputLine != null && !extractAudioComplete && !alreadyInTargetFormat)
    }

    private suspend fun BufferedReader.waitForDownloadProgressPercents(processInfoId: Long): Int {
        val totalParts = 100
        processHandler.start(processInfoId, totalParts)
        do {
            val outputLine: String? = this.readLineWithLog()
            outputLine?.let { checkAlreadyCompleted(it) }
            val progressInfo = outputLine?.let { line ->
                Regex(".*\\[download\\]\\s+(\\d+).\\d%.*").find(line)?.groupValues
                    ?.let { DownloadProgressInfo(totalParts = totalParts, downloadedParts = it[1].toInt()) }
            }?.also {
                processHandler.progress(processInfoId, it.downloadedParts)
            }
        } while (outputLine != null && !isFinished(progressInfo))
        return totalParts
    }

    private fun BufferedReader.waitForAllOutputLines(initialSize: Int? = 1): List<String> {
        val outputLines: MutableList<String> = ArrayList(initialSize!! * 3)
        do {
            val outputLine: String? = this.readLineWithLog()
                ?.also { outputLines.add(it) }
        } while (outputLine != null)
        return outputLines
    }

    private fun BufferedReader.readLineWithLog(): String? {
        return this.readLine()?.also {
            if (ytDlpProperties.logEnable && it.isNotBlank()) {
                log.info { it }
            }
        }
    }

    private data class DownloadProgressInfo(
        val totalParts: Int,
        val downloadedParts: Int = 0
    )

    private fun isFinished(info: DownloadProgressInfo?): Boolean = info?.let { it.downloadedParts >= it.totalParts } ?: false

    private class DownloadAlreadyCompletedException() : Throwable()
}