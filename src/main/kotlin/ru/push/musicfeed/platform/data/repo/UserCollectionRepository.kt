package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.UserCollection
import ru.push.musicfeed.platform.data.model.UserCollectionId

@Repository
interface UserCollectionRepository : JpaRepository<UserCollection, UserCollectionId> {

    @Query(value = """
        select distinct uc from UserCollection uc
            join fetch uc.collection c
            join fetch uc.user u
        where uc.userId = :userId
            and c.removed = false 
            and (uc.isOwner = true or c.isPrivate = false)
        order by uc.collectionId
    """)
    fun findActualByUserId(@Param("userId") userId: Long): List<UserCollection>


    @Query(value = """
        select uc from UserCollection uc
            join fetch uc.collection c
            join fetch uc.user u
        where uc.userId = :userId
            and uc.collectionId = :collectionId
            and c.removed = false
            and (uc.isOwner = true or c.isPrivate = false)
    """)
    fun findActualByUserIdAndCollectionId(
        @Param("userId") userId: Long,
        @Param("collectionId") collectionId: Long
    ): UserCollection?

    @Query(value = """
        select uc from UserCollection uc
            join fetch uc.collection c
            join fetch uc.user u
        where uc.userId = :userId
            and uc.collectionId = :collectionId
            and c.removed = false
            and (uc.isOwner = true or c.isPrivate = false)
    """)
    fun findByUserIdAndMusicPackId(
        @Param("userId") userId: Long,
        @Param("collectionId") collectionId: Long
    ): UserCollection?


    @Query(value = """
        select distinct uc from UserCollection uc
            join fetch uc.collection c
            join fetch uc.user u
        where c.removed = false
            and c.isSynchronized = true
            and uc.isOwner = true
    """)
    fun findActualByCollectionIsOwnerTrueAndIsSynchronizedTrue(): List<UserCollection>


    @Modifying(clearAutomatically = true)
    @Query(value = """
        update UserCollection uc 
        set
            uc.selected = true
        where uc.userId = :userId
            and uc.collectionId = :collectionId
    """)
    fun setSelectedTrueByUserIdAndCollectionId(
        @Param("userId") userId: Long,
        @Param("collectionId") collectionId: Long
    )
}