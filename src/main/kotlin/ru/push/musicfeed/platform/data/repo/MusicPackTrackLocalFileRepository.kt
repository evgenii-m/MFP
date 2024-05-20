package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.music.MusicPackTrackLocalFile

@Repository
interface MusicPackTrackLocalFileRepository : JpaRepository<MusicPackTrackLocalFile, Long> {

    @Query(value = """
        select mptlf from MusicPackTrackLocalFile mptlf 
            join fetch mptlf.trackLocalFile tlf
        where mptlf.musicPackId = :musicPackId
        order by mptlf.id desc
    """)
    fun findByMusicPackId(@Param("musicPackId") musicPackId: Long): List<MusicPackTrackLocalFile>

    fun deleteAllByTrackLocalFileIdIn(trackFileIds: List<Long>)
}