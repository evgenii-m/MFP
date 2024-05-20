package ru.push.musicfeed.platform.application.service.download

import mu.KotlinLogging
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.application.DownloadSourceNotSupportedException
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoList
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.application.dto.toDto
import ru.push.musicfeed.platform.data.model.download.DownloadProcessInfo
import ru.push.musicfeed.platform.data.model.download.DownloadProcessToMusicPack
import ru.push.musicfeed.platform.data.model.download.DownloadProcessToMusicTrack
import ru.push.musicfeed.platform.data.model.download.DownloadProcessToRequest
import ru.push.musicfeed.platform.data.model.download.RETRYABLE_DOWNLOAD_STATUSES
import ru.push.musicfeed.platform.data.model.download.UserToDownloadProcess
import ru.push.musicfeed.platform.data.repo.DownloadProcessInfoRepository
import ru.push.musicfeed.platform.data.repo.DownloadProcessToMusicPackRepository
import ru.push.musicfeed.platform.data.repo.DownloadProcessToMusicTrackRepository
import ru.push.musicfeed.platform.data.repo.DownloadProcessToRequestRepository
import ru.push.musicfeed.platform.data.repo.UserToDownloadProcessRepository
import ru.push.musicfeed.platform.external.source.TrackDownloader
import ru.push.musicfeed.platform.util.TransactionHelper

@Service
class DownloaderService(
    private val trackDownloaders: List<TrackDownloader>,
    private val downloadProcessInfoRepository: DownloadProcessInfoRepository,
    private val downloadProcessToRequestRepository: DownloadProcessToRequestRepository,
    private val downloadProcessToMusicPackRepository: DownloadProcessToMusicPackRepository,
    private val downloadProcessToMusicTrackRepository: DownloadProcessToMusicTrackRepository,
    private val userToDownloadProcessRepository: UserToDownloadProcessRepository,
    private val transactionHelper: TransactionHelper
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val logger = KotlinLogging.logger(javaClass.enclosingClass.canonicalName)

    }


    private fun getTrackDownloader(trackUrl: String): TrackDownloader {
        return trackDownloaders.sortedBy { it.priority() }
            .firstOrNull { it.isApplicableUrl(trackUrl) }
            ?: throw DownloadSourceNotSupportedException(trackUrl)
    }

    @Transactional
    fun requestSingleTrackDownloadByUrl(
        userId: Long,
        trackUrl: String,
        musicPackId: Long? = null,
    ): DownloadProcessInfoList {
        return requestTrackListDownloadByUrls(userId, listOf(trackUrl), musicPackId)
    }

    @Transactional
    fun requestTrackListDownloadByUrls(
        userId: Long,
        trackUrls: List<String>,
        musicPackId: Long? = null
    ): DownloadProcessInfoList {
        val requestId = downloadProcessToRequestRepository.getRequestIdNextVal()
        val dtoList = trackUrls
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .flatMap { trackUrl ->
                transactionHelper.withTransaction {
                    val processInfoDtos = reuseDownloadProcessIfAccessible(userId, trackUrl).takeIf { it.isNotEmpty() }
                        ?: getTrackDownloader(trackUrl).requestDownload(userId, requestId, trackUrl)
                    processInfoDtos.forEach {
                        storeDownloadProcessRelations(
                            processId = it.downloadProcessId,
                            requestId = requestId,
                            musicPackId = musicPackId,
                            musicTrackId = null
                        )
                    }
                    processInfoDtos
                }
            }.sortedByProcessId()
            .toList()
        return DownloadProcessInfoList(requestId, PageImpl(dtoList))
    }

    @Transactional
    fun requestTrackListDownloadByTrackIds(
        userId: Long,
        trackIdsToSourceUrl: Map<Long, String>,
        musicPackId: Long? = null
    ): DownloadProcessInfoList {
        val requestId = downloadProcessToRequestRepository.getRequestIdNextVal()
        val dtoList = trackIdsToSourceUrl
            .filter { it.value.isNotBlank() }
            .flatMap { entry ->
                val trackId = entry.key
                val trackUrl = entry.value.trim()
                transactionHelper.withTransaction {
                    val processInfoDtos = reuseDownloadProcessIfAccessible(userId, trackUrl).takeIf { it.isNotEmpty() }
                        ?: listOf(getTrackDownloader(trackUrl).requestDownload(userId, requestId, trackId))
                    processInfoDtos.forEach {
                        storeDownloadProcessRelations(
                            processId = it.downloadProcessId,
                            requestId = requestId,
                            musicPackId = musicPackId,
                            musicTrackId = trackId
                        )
                    }
                    processInfoDtos
                }
            }.sortedByProcessId()
            .toList()
        return DownloadProcessInfoList(requestId, PageImpl(dtoList))
    }

    @Transactional
    fun requestRetryDownload(userId: Long, downloadProcessId: Long): DownloadProcessInfoDto {
        val downloadProcessInfo = downloadProcessInfoRepository.findById(downloadProcessId).orElseThrow()
        storeUserToDownloadProcessRelation(userId, downloadProcessInfo)
        return requestRetryDownload(downloadProcessInfo)
    }

    private fun requestRetryDownload(downloadProcessInfo: DownloadProcessInfo): DownloadProcessInfoDto {
        return getTrackDownloader(downloadProcessInfo.sourceUrl)
            .requestRetryDownload(downloadProcessInfo.id!!, downloadProcessInfo.sourceUrl)
    }

    private fun reuseDownloadProcessIfAccessible(userId: Long, trackUrl: String): List<DownloadProcessInfoDto> {
        val processInfoEntities = downloadProcessInfoRepository.findBySourceUrl(trackUrl)
        return processInfoEntities.map { infoEntity ->
            val processId = infoEntity.id!!
            logger.debug { "Found accessible download process info, processId = $processId, status = ${infoEntity.status}" }
            if (RETRYABLE_DOWNLOAD_STATUSES.contains(infoEntity.status)) {
                requestRetryDownload(userId, processId)
            } else {
                storeUserToDownloadProcessRelation(userId, infoEntity)
                infoEntity.toDto()
            }
        }
    }

    private fun storeUserToDownloadProcessRelation(userId: Long, downloadProcessInfo: DownloadProcessInfo) {
        val needSetUserRel = downloadProcessInfo.usersRel.none { it.userId == userId }
        if (needSetUserRel) {
            userToDownloadProcessRepository.save(
                UserToDownloadProcess(
                    userId = userId,
                    processId = downloadProcessInfo.id!!,
                    isOwner = false
                )
            )
        }
    }

    @Transactional
    fun associateDownloadedProcessInfoWithRequest(processIds: List<Long>, requestId: Long) {
        downloadProcessToRequestRepository.saveAll(
            processIds.map { DownloadProcessToRequest(processId = it, requestId = requestId) }
        )
    }

    private fun storeDownloadProcessRelations(processId: Long, requestId: Long, musicPackId: Long?, musicTrackId: Long?) {
        if (!downloadProcessToRequestRepository.existsByRequestIdAndProcessId(requestId, processId)) {
            downloadProcessToRequestRepository.save(
                DownloadProcessToRequest(
                    processId = processId,
                    requestId = requestId
                )
            )
        }
        if (musicPackId != null && !downloadProcessToMusicPackRepository.existsByMusicPackIdAndProcessId(musicPackId, processId)) {
            downloadProcessToMusicPackRepository.save(
                DownloadProcessToMusicPack(
                    processId = processId,
                    musicPackId = musicPackId
                )
            )
        }
        if (musicTrackId != null && !downloadProcessToMusicTrackRepository.existsByMusicTrackIdAndProcessId(musicTrackId, processId)) {
            downloadProcessToMusicTrackRepository.save(
                DownloadProcessToMusicTrack(
                    processId = processId,
                    musicTrackId = musicTrackId
                )
            )
        }
    }

    @Transactional
    fun requestSingleTrackDownload(userId: Long, trackData: MusicTrackDto): DownloadProcessInfoList {
        val trackId = trackData.id!!
        val trackUrl = trackData.source.url ?: throw DownloadSourceNotSupportedException(trackId = trackId)
        val requestId = downloadProcessToRequestRepository.getRequestIdNextVal()
        val processInfoDto = reuseDownloadProcessIfAccessible(userId, trackId)
            ?: getTrackDownloader(trackUrl).requestDownload(userId, requestId, trackId)
        storeDownloadProcessRelations(
            processId = processInfoDto.downloadProcessId,
            requestId = requestId,
            musicPackId = null,
            musicTrackId = trackId
        )
        return DownloadProcessInfoList(requestId, PageImpl(listOf(processInfoDto)))
    }


    private fun reuseDownloadProcessIfAccessible(userId: Long, musicTrackId: Long): DownloadProcessInfoDto? {
        val infoEntity = downloadProcessToMusicTrackRepository.findTop1ByMusicTrackId(musicTrackId)?.processInfo
            ?: return null

        val processId = infoEntity.id!!
        logger.debug { "Found accessible download process info, processId = $processId, status = ${infoEntity.status}" }
        return if (RETRYABLE_DOWNLOAD_STATUSES.contains(infoEntity.status)) {
            requestRetryDownload(userId, processId)
        } else {
            storeUserToDownloadProcessRelation(userId, infoEntity)
            infoEntity.toDto()
        }
    }


    fun getDownloadProcessInfo(downloadProcessId: Long): DownloadProcessInfoDto {
        return downloadProcessInfoRepository.findById(downloadProcessId)
            .orElseThrow()
            .toDto()
    }

    fun getDownloadProcessInfoList(downloadRequestId: Long): DownloadProcessInfoList {
        val entitiesList = downloadProcessToRequestRepository.findByRequestId(downloadRequestId)
            .mapNotNull { it.processInfo }
        val dtoList = entitiesList
            .distinctBy { it.id }
            .map { it.toDto() }
            .sortedByProcessId()
            .toList()
        return DownloadProcessInfoList(downloadRequestId, PageImpl(dtoList))
    }

    fun getDownloadProcessInfoList(userId: Long, page: Int, size: Int): DownloadProcessInfoList {
        val pageable = PageRequest.of(page, size)
        val entitiesPage = downloadProcessInfoRepository.findByUserIdOrderByAddedAtDesc(userId, pageable)
        val dtoList = entitiesPage
            .distinctBy { it.id }
            .map { it.toDto() }
            .sortedByDownloadedAtDesc()
            .toList()
        return DownloadProcessInfoList(dtoList = PageImpl(dtoList, pageable, entitiesPage.totalElements))
    }

    private fun Iterable<DownloadProcessInfoDto>.sortedByProcessId() = this.sortedBy { it.downloadProcessId }

    private fun Iterable<DownloadProcessInfoDto>.sortedByDownloadedAtDesc() = this.sortedByDescending { it.addedAt }

    @Transactional
    fun removeUserDownloadsItem(userId: Long, downloadProcessId: Long) {
        userToDownloadProcessRepository.deleteByUserIdAndProcessId(userId, downloadProcessId)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun removeDataAssociatedWithTracks(trackIds: List<Long>) {
        logger.info("Start removeDataAssociatedWithTracks for trackIds: [${trackIds.joinToString { it.toString() }}]")
        val processInfos = downloadProcessToMusicTrackRepository.findByMusicTrackIdIn(trackIds)
            .mapNotNull { it.processInfo }
        logger.info("Found processInfos associated with removed tracks, processIds: [${processInfos.joinToString { it.id!!.toString() }}]")
        downloadProcessInfoRepository.deleteAll(processInfos)
        logger.info("End removeDataAssociatedWithTracks")
    }

    @Transactional(readOnly = true)
    fun obtainDownloadProcessInfoByTrackIds(trackIds: List<Long>): Map<Long, DownloadProcessInfoDto> {
        return downloadProcessToMusicTrackRepository.findByMusicTrackIdIn(trackIds)
            .associateBy( { it.musicTrackId }, { it.processInfo!!.toDto() } )
    }

}