package ru.push.musicfeed.platform.application.service.track

import java.io.File
import java.util.*
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.data.repo.MusicTrackRepository

@Component
class TrackDataHelper(
    private val applicationProperties: ApplicationProperties,
    private val musicTrackRepository: MusicTrackRepository,
) {
    private val pathSeparator = applicationProperties.storagePathSeparator

    companion object {
        private const val UUID_GEN_ATTEMPTS_COUNT = 10
    }

    fun formTrackTitle(artistName: String?, trackName: String): String {
        return if (artistName == null) trackName else
            "$artistName - $trackName"
    }

    fun formTrackTitle(artistNames: List<String>, trackName: String): String {
        return formTrackPerformerString(artistNames)?.let { "$it - $trackName" } ?: trackName
    }

    fun obtainTrackPerformerAndTitle(trackLocalFileId: Long): Pair<String?, String?> {
        val trackData = musicTrackRepository.fetchByLocalFileId(trackLocalFileId).maxByOrNull { it.id!! }
        return Pair(
            trackData?.artists?.map { it.name }?.let { formTrackPerformerString(it) },
            trackData?.title
        )
    }

    private fun formTrackPerformerString(artistNames: Collection<String>): String? {
        return artistNames.takeIf { it.isNotEmpty() }?.joinToString("; ") { it }
    }

    fun formOutputFilePath(attemptsLeft: Int = UUID_GEN_ATTEMPTS_COUNT): String {
        if (attemptsLeft <= 0)
            throw IllegalStateException("Occurred UUID generations max attempts count")
        val trackFileName = "${applicationProperties.storageFolder.forceEndSeparator()}${UUID.randomUUID()}.mp3"
        return trackFileName.takeIf { File(it).exists().not() }
            ?: formOutputFilePath(attemptsLeft - 1)
    }

    @Deprecated("Should to use ru.push.musicfeed.platform.application.service.track.TrackDataHelper.formOutputFilePath(int)")
    fun formOutputFilePath(trackTitle: String): String {
        val trackFileName = removeFileNameForbiddenChars("${trackTitle}.mp3")
        return "${applicationProperties.storageFolder.forceEndSeparator()}$trackFileName"
    }

    @Deprecated("Should to use ru.push.musicfeed.platform.application.service.track.TrackDataHelper.formOutputFilePath(int)")
    fun formOutputFilePath(artistName: String?, trackName: String): String {
        val trackTitle = formTrackTitle(artistName, trackName)
        val trackFileName = removeFileNameForbiddenChars("${trackTitle}.mp3")
        return "${applicationProperties.storageFolder.forceEndSeparator()}$trackFileName"
    }

    fun removeFileNameForbiddenChars(fileName: String): String {
        var result = fileName
        applicationProperties.fileNameForbiddenChars.toCharArray()
            .forEach { forbiddenChar ->
                result = result.replace(forbiddenChar, ' ', false)
            }
        return result
    }

    private fun String.forceEndSeparator() = if (endsWith(pathSeparator)) this else this + pathSeparator
}