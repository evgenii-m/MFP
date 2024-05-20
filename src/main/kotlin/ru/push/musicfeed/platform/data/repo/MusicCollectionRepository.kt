package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.music.MusicCollection
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType

@Repository
interface MusicCollectionRepository : JpaRepository<MusicCollection, Long> {

    fun existsByExternalIdAndRemovedFalse(@Param("externalId") externalId: String): Boolean

    fun findByExternalIdAndRemovedFalse(@Param("externalId") externalId: String): MusicCollection?

    @Query(value = """
        select c.id from MusicCollection c 
            join c.userCollections uc
        where uc.userId = :userId
            and c.removed = false
            and c.type = :type
    """)
    fun findActualIdsByUserIdAndType(
        @Param("userId") userId: Long,
        @Param("type") type: MusicCollectionType
    ): List<Long>

    @Query(value = """
        select c.id from MusicCollection c 
            join c.userCollections uc
        where uc.userId = :userId
            and c.removed = false
    """)
    fun findActualIdsByUserId(
        @Param("userId") userId: Long,
    ): List<Long>
}