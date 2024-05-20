package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.TrackLocalFile

@Repository
interface TrackLocalFileRepository : JpaRepository<TrackLocalFile, Long> {

    fun findTop1ByDownloadProcessIdOrderByAddedAtDesc(downloadProcessId: Long): TrackLocalFile?

    @Query("""
        select tlf from TrackLocalFile tlf
            join tlf.musicPackRels mpr 
            left join fetch tlf.downloadProcess dp 
            left join fetch tlf.externalInfos
        where mpr.musicPackId = :musicPackId
        order by tlf.addedAt desc
    """)
    fun findByMusicPackId(@Param("musicPackId") musicPackId: Long): List<TrackLocalFile>

    fun findTop1BySourceTrackFileIdOrderByIdDesc(sourceTrackFileId: Long): TrackLocalFile?

    fun findAllByDownloadProcessId(downloadProcessId: Long): List<TrackLocalFile>

    @Query("""
        select tlf from TrackLocalFile tlf
            left join fetch tlf.downloadProcess dp 
            left join fetch tlf.externalInfos
        where tlf.id in (:ids)
        order by tlf.addedAt desc
    """)
    fun fetchByIdIn(@Param("ids") ids: List<Long>): List<TrackLocalFile>
}