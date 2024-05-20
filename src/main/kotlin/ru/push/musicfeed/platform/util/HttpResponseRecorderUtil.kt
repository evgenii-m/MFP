package ru.push.musicfeed.platform.util

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import mu.KotlinLogging
import okhttp3.Response

private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH-mm-SSSSSSS")
private val CLOCK: Clock = Clock.systemDefaultZone()
private val logger = KotlinLogging.logger {}

fun Response.recordResponse(responsesFolderPath: String): Response {
    try {
        val purifiedUrl = request.url.toString()
            .replace(Regex("[^a-zA-Z0-9\\.\\-]"), "_")
            .cut(200)
        val formattedNowTime = LocalDateTime.now(CLOCK).format(DATE_TIME_FORMATTER)
        val filePath = Paths.get("$responsesFolderPath${File.separator}$purifiedUrl $formattedNowTime")
        val bufferedWriter = Files.newBufferedWriter(
            filePath,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
        )
        bufferedWriter.use {
            bufferedWriter.write(peekBody(Long.MAX_VALUE).string())
        }
    } catch (ex: Exception) {
        logger.error(ex) { "Exception occurred while response recording" }
    }
    return this
}