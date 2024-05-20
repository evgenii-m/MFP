package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.music.MusicArtist

@Repository
interface MusicArtistRepository : JpaRepository<MusicArtist, Long> {

    @Query(value = """
        select distinct mar from MusicArtist mar 
            left join fetch mar.sources src
        where mar.name in (:names)
    """)
    fun findByNames(@Param("names") names: Set<String>): Set<MusicArtist>
}