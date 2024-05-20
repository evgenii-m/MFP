package ru.push.musicfeed.platform.util

import org.apache.commons.validator.routines.UrlValidator
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.toDuration
import liquibase.repackaged.org.apache.commons.lang3.time.DurationFormatUtils

fun <E, S> List<Map<E, S>>.mergeMaps(): Map<E, S> =
    fold(mutableMapOf()) { acc, map ->
        acc.putAll(map)
        acc
    }

fun String.cut(limit: Int): String {
    return if (length > limit)
        substring(0, limit - 3) + "..."
    else
        this
}

fun <T> List<T>.cut(limit: Int): List<T> {
    return if (size > limit)
        subList(0, limit)
    else
        this
}

fun LocalDateTime.correctZoneOffset(): LocalDateTime =
    this.toInstant(ZoneOffset.UTC).atZone(ZoneId.systemDefault()).toLocalDateTime()

fun <T> uniteToSet(vararg lists: List<T>): Set<T> {
    return listOf(*lists).flatten().toHashSet()
}

fun String.isUrl() = UrlValidator().isValid(this)

fun String.splitBySpaces() = this.split(Regex("[\\n\\s]")).map { it.trim() }.filter { it.isNotBlank() }

fun String.normalizeSearchText() = this.split(Regex("[\\n\\s]")).map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")

fun String.toDuration(): Duration {
    return Regex("(\\d?\\d):?(\\d?\\d)?:?(\\d\\d)?").find(this)
        ?.takeIf { it.groupValues.isNotEmpty() && it.groupValues.size >= 3 }
        ?.let {
            val seconds: Long; val minutes: Long; val hours: Long
            if (it.groupValues[3].isNotBlank()) {
                seconds = it.groupValues[3].toLong()
                minutes = it.groupValues[2].toLong()
                hours = it.groupValues[1].toLong()
            } else if (it.groupValues[2].isNotBlank()) {
                seconds = it.groupValues[2].toLong()
                minutes = it.groupValues[1].toLong()
                hours = 0
            } else {
                seconds = it.groupValues[1].toLong()
                minutes = 0
                hours = 0
            }
            val totalSeconds = seconds + (minutes * 60) + (hours * 60 * 60)
            totalSeconds.toDuration(SECONDS)
        }
        ?: throw IllegalArgumentException("Invalid timestamp format: $this")
}

fun Duration.formatted(): String {
    val format = if (this.inWholeHours > 0) "HH:mm:ss" else "mm:ss"
    return DurationFormatUtils.formatDuration(this.inWholeMilliseconds, format)
}