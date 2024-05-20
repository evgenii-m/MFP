package ru.push.musicfeed.platform.application.service.download

import java.time.Clock
import java.time.LocalDateTime
import kotlin.time.Duration
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.application.ExternalSourceNotSupportedException
import ru.push.musicfeed.platform.application.MusicTrackNotFoundException
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.dto.toDto
import ru.push.musicfeed.platform.application.service.music.MusicEntitiesDao
import ru.push.musicfeed.platform.application.service.track.TrackDataHelper
import ru.push.musicfeed.platform.data.model.download.DownloadProcessInfo
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.FAIL
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.IN_PROGRESS
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.REQUESTED
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.SUCCESS
import ru.push.musicfeed.platform.data.model.download.INACTIVE_DOWNLOAD_STATUSES
import ru.push.musicfeed.platform.data.model.TrackLocalFile
import ru.push.musicfeed.platform.data.model.download.UserToDownloadProcess
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.COMMON_EXTERNAL_LINK
import ru.push.musicfeed.platform.data.repo.DownloadProcessInfoRepository
import ru.push.musicfeed.platform.data.repo.DownloadProcessToMusicPackRepository
import ru.push.musicfeed.platform.data.repo.DownloadProcessToMusicTrackRepository
import ru.push.musicfeed.platform.data.repo.TrackLocalFileRepository
import ru.push.musicfeed.platform.data.repo.UserToDownloadProcessRepository
import ru.push.musicfeed.platform.util.cut

@Component
class DefaultDownloadProcessHandler(
    private val downloadProcessInfoRepository: DownloadProcessInfoRepository,
    private val userToDownloadProcessRepository: UserToDownloadProcessRepository,
    private val trackLocalFileRepository: TrackLocalFileRepository,
    private val downloadProcessToMusicPackRepository: DownloadProcessToMusicPackRepository,
    private val downloadProcessToMusicTrackRepository: DownloadProcessToMusicTrackRepository,
    private val musicEntitiesDao: MusicEntitiesDao,
    private val trackLocalFileHelper: TrackDataHelper,
    private val clock: Clock = Clock.systemDefaultZone()
) : DownloadProcessHandler {

    @Transactional(propagation = REQUIRES_NEW)
    override fun initialize(
        userId: Long,
        sourceUrl: String,
        filePath: String,
        trackTitle: String?,
        trackDuration: Duration?
    ): DownloadProcessInfoDto {
        return createNewDownloadProcess(
            userId = userId,
            sourceUrl = sourceUrl,
            filePath = filePath,
            trackTitle = trackTitle,
            durationSec = trackDuration?.inWholeSeconds
        ).toDto()
    }

    @Transactional(propagation = REQUIRES_NEW)
    override fun initializeFromTrackData(userId: Long, musicTrackId: Long): DownloadProcessInfoDto {
        val trackData = musicEntitiesDao.fetchTrackById(musicTrackId)
            ?: throw MusicTrackNotFoundException(userId, musicTrackId)
        val sourceUrl = trackData.findSource(COMMON_EXTERNAL_LINK)?.externalSourceUrl
            ?: throw ExternalSourceNotSupportedException()
        val trackTitle = "${trackData.artists.joinToString { it.name }.cut(100)} - ${trackData.title}"
        val filePath = trackLocalFileHelper.formOutputFilePath()

        return createNewDownloadProcess(
            userId = userId,
            sourceUrl = sourceUrl,
            filePath = filePath,
            trackTitle = trackTitle,
            durationSec = trackData.durationSec
        ).toDto()
    }

    private fun createNewDownloadProcess(
        userId: Long,
        sourceUrl: String,
        filePath: String,
        trackTitle: String?,
        durationSec: Long?
    ): DownloadProcessInfo {
        val now = LocalDateTime.now(clock)
        val downloadProcessInfo = DownloadProcessInfo(
            status = REQUESTED,
            sourceUrl = sourceUrl,
            filePath = filePath,
            trackTitle = trackTitle,
            trackDurationSec = durationSec ?: 0,
            totalParts = 100,
            addedAt = now,
            updatedAt = now,
        )
        val savedEntity = downloadProcessInfoRepository.save(downloadProcessInfo)
        val userToDownloadProcess = UserToDownloadProcess(
            processId = savedEntity.id!!,
            userId = userId,
            isOwner = true
        )
        userToDownloadProcessRepository.save(userToDownloadProcess)
        return savedEntity
    }

    @Transactional(propagation = REQUIRES_NEW)
    override fun retry(id: Long, filePath: String?, trackTitle: String?, trackDuration: Duration?): DownloadProcessInfoDto {
        val downloadProcessInfo = downloadProcessInfoRepository.findById(id).orElseThrow()
        downloadProcessInfo.apply {
            this.status = IN_PROGRESS
            this.downloadedParts = 0
            this.errorDescription = null
        }
        filePath?.let { downloadProcessInfo.filePath = it }
        trackTitle?.let { downloadProcessInfo.trackTitle = it }
        trackDuration?.let { downloadProcessInfo.trackDurationSec = it.inWholeSeconds }
        downloadProcessInfoRepository.save(downloadProcessInfo)

        val trackLocalFiles = trackLocalFileRepository.findAllByDownloadProcessId(id)
        trackLocalFileRepository.deleteAll(trackLocalFiles)

        return downloadProcessInfo.toDto()
    }

    @Transactional(propagation = REQUIRES_NEW)
    override fun start(id: Long, totalParts: Int) {
        val now = LocalDateTime.now(clock)
        downloadProcessInfoRepository.setStatusAndTotalPartsById(id, IN_PROGRESS, totalParts, now)
    }

    @Transactional(propagation = REQUIRES_NEW)
    override fun progress(id: Long, downloadedParts: Int) {
        val downloadProcessInfo = downloadProcessInfoRepository.findById(id).orElseThrow()
        if (!INACTIVE_DOWNLOAD_STATUSES.contains(downloadProcessInfo.status) && downloadProcessInfo.downloadedParts != downloadedParts) {
            val now = LocalDateTime.now(clock)
            downloadProcessInfoRepository.setStatusAndDownloadedPartsById(id, IN_PROGRESS, downloadedParts, now)
        }
    }

    @Transactional(propagation = REQUIRES_NEW)
    override fun completeSuccess(id: Long, downloadedParts: Int?) {
        val now = LocalDateTime.now(clock)
        val downloadProcessInfo = downloadProcessInfoRepository.findById(id).orElseThrow()
        val finalDownloadedParts = downloadedParts ?: downloadProcessInfo.totalParts
        val trackLocalFile = trackLocalFileRepository.save(
            TrackLocalFile(
                filePath = downloadProcessInfo.filePath,
                trackTitle = downloadProcessInfo.trackTitle ?: downloadProcessInfo.sourceUrl,
                trackDurationSec = downloadProcessInfo.trackDurationSec,
                downloadProcessId = id,
                addedAt = now
            )
        )
        downloadProcessInfoRepository.setStatusAndDownloadedPartsById(id, SUCCESS, finalDownloadedParts, now)

        val localFileId = trackLocalFile.id!!
        downloadProcessToMusicPackRepository.findByProcessId(id)
            .forEach { musicEntitiesDao.storeMusicPackLocalFileInfo(it.musicPackId, localFileId) }
        downloadProcessToMusicTrackRepository.findByProcessId(id)
            .forEach { musicEntitiesDao.appendLocalFileSourceToMusicTrack(it.musicTrackId, localFileId) }
    }

    @Transactional(propagation = REQUIRES_NEW)
    override fun fail(id: Long, ex: Throwable) {
        val now = LocalDateTime.now(clock)
        val errorDescription = ex.message ?: ex.toString()
        downloadProcessInfoRepository.setStatusAndErrorDescriptionById(id, FAIL, errorDescription, now)
    }
}