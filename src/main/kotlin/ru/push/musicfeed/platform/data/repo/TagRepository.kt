package ru.push.musicfeed.platform.data.repo

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.music.Tag

@Repository
interface TagRepository : JpaRepository<Tag, Long> {

    @Query(value = """
        select distinct t from Tag t
            join t.musicPacks mp
            join mp.collection c
            join c.userCollections uc
        where uc.userId = :userId
    """)
    fun findByUserId(@Param("userId") userId: Long, pageable: Pageable): Page<Tag>

    fun findByValueIn(@Param("values") values: List<String>): Set<Tag>
}