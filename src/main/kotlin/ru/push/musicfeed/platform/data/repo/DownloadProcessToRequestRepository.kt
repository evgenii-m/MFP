package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.download.DownloadProcessToRequest

@Repository
interface DownloadProcessToRequestRepository : JpaRepository<DownloadProcessToRequest, Long> {
    @Query(
        value = """
            select nextval('download_process_request_id_sequence')
        """,
        nativeQuery = true
    )
    fun getRequestIdNextVal(): Long

    @Query(value = """
        select dptr from DownloadProcessToRequest dptr 
            left join fetch dptr.processInfo
        where dptr.requestId = :requestId
    """)
    fun findByRequestId(@Param("requestId") requestId: Long): List<DownloadProcessToRequest>

    fun existsByRequestIdAndProcessId(requestId: Long, processId: Long): Boolean
}