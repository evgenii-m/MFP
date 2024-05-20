package ru.push.musicfeed.platform.data.repo

import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.download.DownloadProcessInfo
import ru.push.musicfeed.platform.data.model.download.DownloadStatus

@Repository
interface DownloadProcessInfoRepository : JpaRepository<DownloadProcessInfo, Long> {

    fun findBySourceUrl(sourceUrl: String): List<DownloadProcessInfo>

    fun findByIdAndStatus(id: Long, status: DownloadStatus): DownloadProcessInfo?

    fun findByStatusIn(status: List<DownloadStatus>): List<DownloadProcessInfo>

    @Query(value = """
        select distinct dpi from DownloadProcessInfo dpi 
            left join dpi.usersRel ur
        where ur.userId = :userId
        order by dpi.addedAt desc
    """)
    fun findByUserIdOrderByAddedAtDesc(@Param("userId") userId: Long, pageable: Pageable): Page<DownloadProcessInfo>

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            update DownloadProcessInfo dpi 
            set
                dpi.status = :status,
                dpi.totalParts = :totalParts,
                dpi.updatedAt = :updatedAt
            where dpi.id = :id
        """
    )
    fun setStatusAndTotalPartsById(
        @Param("id") id: Long,
        @Param("status") status: DownloadStatus,
        @Param("totalParts") totalParts: Int,
        @Param("updatedAt") updatedAt: LocalDateTime
    )

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            update DownloadProcessInfo dpi 
            set
                dpi.status = :status,
                dpi.downloadedParts = :downloadedParts,
                dpi.updatedAt = :updatedAt
            where dpi.id = :id
        """
    )
    fun setStatusAndDownloadedPartsById(
        @Param("id") id: Long,
        @Param("status") status: DownloadStatus,
        @Param("downloadedParts") downloadedParts: Int,
        @Param("updatedAt") updatedAt: LocalDateTime
    )

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            update DownloadProcessInfo dpi 
            set
                dpi.status = :status,
                dpi.errorDescription = :errorDescription,
                dpi.updatedAt = :updatedAt
            where dpi.id = :id
        """
    )
    fun setStatusAndErrorDescriptionById(
        @Param("id") id: Long,
        @Param("status") status: DownloadStatus,
        @Param("errorDescription") errorDescription: String,
        @Param("updatedAt") updatedAt: LocalDateTime
    )

}