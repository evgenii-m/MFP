package ru.push.musicfeed.platform.application.service.track

import java.io.File
import java.lang.Exception
import java.net.URI
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.toDuration
import mu.KotlinLogging
import okio.buffer
import okio.sink
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.application.TrackLocalFileIsNotAccessibleException
import ru.push.musicfeed.platform.application.TrackLocalFileNotFoundException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.dto.TrackFileInfoDto
import ru.push.musicfeed.platform.application.service.download.DownloadProcessHandler
import ru.push.musicfeed.platform.data.model.FileExternalInfo
import ru.push.musicfeed.platform.data.model.FileExternalType
import ru.push.musicfeed.platform.data.model.TrackLocalFile
import ru.push.musicfeed.platform.data.model.download.DownloadStatus
import ru.push.musicfeed.platform.data.repo.DownloadProcessInfoRepository
import ru.push.musicfeed.platform.data.repo.FileExternalInfoRepository
import ru.push.musicfeed.platform.data.repo.TrackLocalFileRepository
import ru.push.musicfeed.platform.external.source.ffmpeg.FfmpegService
import ru.push.musicfeed.platform.util.formatted

@Service
class TrackLocalFileService(
    private val applicationProperties: ApplicationProperties,
    private val ffmpegService: FfmpegService,
    private val trackLocalFileRepository: TrackLocalFileRepository,
    private val trackLocalFileHelper: TrackDataHelper,
    private val fileExternalInfoRepository: FileExternalInfoRepository,
    private val downloadProcessInfoRepository: DownloadProcessInfoRepository,
    private val downloadProcessHandler: DownloadProcessHandler,
    private val clock: Clock = Clock.systemDefaultZone()
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = KotlinLogging.logger(javaClass.enclosingClass.canonicalName)

        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH-mm-SS")
    }


    @Transactional(noRollbackFor = [TrackLocalFileNotFoundException::class])
    fun obtainDownloadedTrackFileInfo(downloadProcessId: Long): TrackFileInfoDto {
        val trackLocalFile = trackLocalFileRepository.findTop1ByDownloadProcessIdOrderByAddedAtDesc(downloadProcessId)
            ?: backwardCompatibilityFetch(downloadProcessId)
            ?: throw TrackLocalFileIsNotAccessibleException(downloadProcessId = downloadProcessId)
        val filePath = trackLocalFile.filePath
        val fileExternalInfo = trackLocalFile.externalInfos.firstOrNull { it.type == FileExternalType.TELEGRAM }
        if (!File(filePath).exists()) {
            throw TrackLocalFileNotFoundException(downloadProcessId = downloadProcessId, filePath = filePath)
                .also { downloadProcessHandler.fail(downloadProcessId, it) }
        }

        val trackPerformerAndTitle = trackLocalFileHelper.obtainTrackPerformerAndTitle(trackLocalFile.id!!)
        return TrackFileInfoDto(
            trackLocalFileId = trackLocalFile.id!!,
            performer = trackPerformerAndTitle.first,
            trackTitle = trackPerformerAndTitle.second ?: trackLocalFile.trackTitle,
            duration = trackLocalFile.trackDurationSec.toDuration(SECONDS),
            filePath = trackLocalFile.filePath,
            fileExternalId = fileExternalInfo?.externalId
        )
    }

    // todo: remove after release
    fun backwardCompatibilityFetch(downloadProcessId: Long): TrackLocalFile? {
        return downloadProcessInfoRepository.findByIdAndStatus(downloadProcessId, DownloadStatus.SUCCESS)
            ?.let { downloadProcessInfo ->
                val now = LocalDateTime.now(clock)
                trackLocalFileRepository.save(
                    TrackLocalFile(
                        filePath = downloadProcessInfo.filePath,
                        trackTitle = downloadProcessInfo.trackTitle ?: downloadProcessInfo.sourceUrl,
                        trackDurationSec = downloadProcessInfo.trackDurationSec,
                        downloadProcessId = downloadProcessInfo.id,
                        addedAt = now
                    )
                )
            }
    }

    @Transactional(readOnly = true)
    fun obtainLocalTrackFileInfo(trackLocalFileId: Long, musicTrackId: Long? = null): TrackFileInfoDto {
        val trackLocalFile = trackLocalFileRepository.findById(trackLocalFileId)
            .orElseThrow { throw TrackLocalFileIsNotAccessibleException(trackLocalFileId = trackLocalFileId) }
        val filePath = trackLocalFile.filePath
        val fileExternalInfo = trackLocalFile.externalInfos.firstOrNull { it.type == FileExternalType.TELEGRAM }
        if (!File(filePath).exists()) {
            throw TrackLocalFileNotFoundException(trackLocalFileId = trackLocalFileId, musicTrackId = musicTrackId, filePath = filePath)
        }

        val trackPerformerAndTitle = trackLocalFileHelper.obtainTrackPerformerAndTitle(trackLocalFile.id!!)
        return TrackFileInfoDto(
            trackLocalFileId = trackLocalFile.id!!,
            performer = trackPerformerAndTitle.first,
            trackTitle = trackPerformerAndTitle.second ?: trackLocalFile.trackTitle,
            duration = trackLocalFile.trackDurationSec.toDuration(SECONDS),
            filePath = trackLocalFile.filePath,
            fileExternalId = fileExternalInfo?.externalId
        )
    }

    @Transactional
    fun obtainLocalTrackFileInfoList(trackLocalFileIds: List<Long>): List<TrackFileInfoDto> {
        val trackLocalFileList = trackLocalFileRepository.fetchByIdIn(trackLocalFileIds)
        return trackLocalFileList
            .filter {
                if (!File(it.filePath).exists()) {
                    it.downloadProcess?.id?.let { processId ->
                        downloadProcessHandler.fail(processId, TrackLocalFileNotFoundException(processId, filePath = it.filePath))
                    }
                    false
                } else {
                    true
                }
            }
            .map { trackLocalFile ->
                val fileExternalInfo = trackLocalFile.externalInfos.firstOrNull { it.type == FileExternalType.TELEGRAM }
                val trackPerformerAndTitle = trackLocalFileHelper.obtainTrackPerformerAndTitle(trackLocalFile.id!!)
                TrackFileInfoDto(
                    trackLocalFileId = trackLocalFile.id!!,
                    performer = trackPerformerAndTitle.first,
                    trackTitle = trackPerformerAndTitle.second ?: trackLocalFile.trackTitle,
                    duration = trackLocalFile.trackDurationSec.toDuration(SECONDS),
                    filePath = trackLocalFile.filePath,
                    fileExternalId = fileExternalInfo?.externalId
                )
            }
    }

    @Transactional
    fun obtainLocalTrackFileInfoByMusicPackId(musicPackId: Long): TrackFileInfoDto? {
        val trackLocalFile = trackLocalFileRepository.findByMusicPackId(musicPackId).firstOrNull()
            ?: return null
        val filePath = trackLocalFile.filePath
        val fileExternalInfo = trackLocalFile.externalInfos.firstOrNull { it.type == FileExternalType.TELEGRAM }
        if (!File(filePath).exists()) {
            trackLocalFile.downloadProcess?.id?.let { processId ->
                downloadProcessHandler.fail(processId, TrackLocalFileNotFoundException(processId, filePath = filePath))
            }
            return null
        }

        val trackPerformerAndTitle = trackLocalFileHelper.obtainTrackPerformerAndTitle(trackLocalFile.id!!)
        return TrackFileInfoDto(
            trackLocalFileId = trackLocalFile.id!!,
            performer = trackPerformerAndTitle.first,
            trackTitle = trackPerformerAndTitle.second ?: trackLocalFile.trackTitle,
            duration = trackLocalFile.trackDurationSec.toDuration(SECONDS),
            filePath = trackLocalFile.filePath,
            fileExternalId = fileExternalInfo?.externalId
        )
    }

    @Transactional
    fun storeFileExternalId(trackLocalFileId: Long, fileExternalId: String, type: FileExternalType) {
        val trackLocalFile = trackLocalFileRepository.findById(trackLocalFileId)
            .orElseThrow { TrackLocalFileIsNotAccessibleException(trackLocalFileId = trackLocalFileId) }
        fileExternalInfoRepository.save(
            FileExternalInfo(
                trackLocalFileId = trackLocalFile.id!!,
                externalId = fileExternalId,
                type = type
            )
        )
    }

    @Transactional(noRollbackFor = [TrackLocalFileNotFoundException::class])
    fun extractPartFromTrackFileByDownloadProcessId(
        sourceDownloadProcessId: Long,
        targetTrackTitle: String?,
        start: Duration,
        duration: Duration,
    ) = extractPartFromTrackFile(obtainDownloadedTrackFileInfo(sourceDownloadProcessId), targetTrackTitle, start, duration)

    fun extractPartFromTrackFileByLocalFileId(
        sourceTrackLocalFileId: Long,
        targetTrackTitle: String?,
        start: Duration,
        duration: Duration,
    ) = extractPartFromTrackFile(obtainLocalTrackFileInfo(sourceTrackLocalFileId), targetTrackTitle, start, duration)

    fun extractPartFromTrackFileByMusicPackId(
        musicPackId: Long,
        targetTrackTitle: String?,
        start: Duration,
        duration: Duration,
    ): TrackFileInfoDto {
        return obtainLocalTrackFileInfoByMusicPackId(musicPackId)
            ?.let { extractPartFromTrackFile(it, targetTrackTitle, start, duration) }
            ?: throw TrackLocalFileIsNotAccessibleException(musicPackId = musicPackId)
    }


    private fun extractPartFromTrackFile(
        sourceTrackFileInfo: TrackFileInfoDto,
        targetTrackTitle: String?,
        start: Duration,
        duration: Duration,
    ): TrackFileInfoDto {
        val sourceLocalFileId = sourceTrackFileInfo.trackLocalFileId
        val end = start.plus(duration)
        if (sourceTrackFileInfo.duration < end)
            throw IllegalArgumentException("Requested timestamps exceed the track duration, sourceLocalFileId=$sourceLocalFileId")

        val finalTitle = targetTrackTitle ?: "${sourceTrackFileInfo.trackTitle} (${start.formatted()} - ${end.formatted()})"
        val targetTrackFilePath = trackLocalFileHelper.formOutputFilePath()
        ffmpegService.cutTrackFile(
            sourceTrackLocalFileId = sourceLocalFileId,
            sourceFilePath = sourceTrackFileInfo.filePath,
            targetFilePath = targetTrackFilePath,
            targetTrackTitle = finalTitle,
            start = start,
            duration = duration
        )
        return trackLocalFileRepository.findTop1BySourceTrackFileIdOrderByIdDesc(sourceLocalFileId)?.toDto()
            ?: throw TrackLocalFileNotFoundException(filePath = targetTrackFilePath)
    }

    private fun TrackLocalFile.toDto(performer: String? = null): TrackFileInfoDto {
        return TrackFileInfoDto(
            trackLocalFileId = this.id!!,
            performer = performer ?: trackLocalFileHelper.obtainTrackPerformerAndTitle(this.id!!).first,
            trackTitle = this.trackTitle,
            duration = this.trackDurationSec.toDuration(SECONDS),
            filePath = this.filePath,
        )
    }

    @Transactional
    fun storeTrackFileFromExternalSource(
        fileSourceUrl: String,
        trackArtistName: String?,
        trackTitle: String?,
        trackDurationSec: Int?,
    ): TrackFileInfoDto {
        val targetTrackTitle = trackTitle?.let { trackLocalFileHelper.formTrackTitle(trackArtistName, it) }
            ?: generateDefaultTrackTitle()
        val outputFilePath = trackLocalFileHelper.formOutputFilePath()
        val output = File(outputFilePath)
        output.parentFile.mkdirs()
        output.createNewFile()

        val bufferedSink = output.sink().buffer()
        URI(fileSourceUrl).toURL().openStream().use { inputStream ->
            val buffer = ByteArray(1024)
            var bytesCount: Int
            while (inputStream.read(buffer).also { bytesCount = it } > 0) {
                bufferedSink.write(buffer, 0, bytesCount)
                bufferedSink.flush()
            }
        }
        bufferedSink.close()

        val now = LocalDateTime.now(clock)
        val trackLocalFile = trackLocalFileRepository.save(
            TrackLocalFile(
                filePath = outputFilePath,
                trackTitle = targetTrackTitle,
                trackDurationSec = trackDurationSec?.toLong() ?: 0,
                addedAt = now
            )
        )
        return trackLocalFile.toDto(performer = trackArtistName)
    }

    fun generateDefaultTrackTitle(): String {
        val now = LocalDateTime.now(clock)
        return "unknown ${now.format(DATE_TIME_FORMATTER)}"
    }

    @Transactional(propagation = REQUIRES_NEW)
    fun deleteTrackFilesAndDataByIds(trackLocalFileIds: List<Long>) {
        val trackLocalFiles = trackLocalFileRepository.findAllById(trackLocalFileIds)
        trackLocalFiles.forEach {
            val filePath = it.filePath
            try {
                val file = File(filePath)
                if (file.exists()) {
                    file.delete()
                    logger.info { "Deleted file: $filePath" }
                } else {
                    logger.info { "File not found, deleting skipped: $filePath" }
                }
                fileExternalInfoRepository.deleteByTrackLocalFileId(it.id!!)
                trackLocalFileRepository.delete(it)
            } catch (ex: Exception) {
                logger.warn(ex) { "Delete file exception, skipped: filePath = '$filePath', ex = $ex" }
            }
        }
    }
}