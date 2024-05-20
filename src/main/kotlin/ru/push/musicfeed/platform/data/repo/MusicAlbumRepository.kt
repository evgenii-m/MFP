package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.music.MusicAlbum

@Repository
interface MusicAlbumRepository : JpaRepository<MusicAlbum, Long> {

    @Query(value = """
        select distinct mal from MusicAlbum mal 
            join fetch mal.artists mar
            left join fetch mal.sources src
        where mal.title in (:titles)
    """)
    fun findByTitles(@Param("titles") titles: Set<String>): Set<MusicAlbum>
}