package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.FileExternalInfo
import ru.push.musicfeed.platform.data.model.FileExternalType

@Repository
interface FileExternalInfoRepository : JpaRepository<FileExternalInfo, Long> {

    fun findTop1ByTrackLocalFileIdAndTypeOrderByIdDesc(trackLocalFileId: Long, type: FileExternalType): FileExternalInfo?

    fun deleteByTrackLocalFileId(trackLocalFileId: Long)

//    @Query("""
//        delete from FileExternalInfo fei
//        where fei.trackLocalFileId in (
//            select tlf.id from TrackLocalFile tlf
//            where tlf.downloadProcessId = :downloadProcessId
//        )
//    """)
//    fun deleteAllByDownloadProcessId(@Param("downloadProcessId") downloadProcessId: Long)
}