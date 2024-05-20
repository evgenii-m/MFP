package ru.push.musicfeed.platform.data.repo

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.music.MusicPack

@Repository
interface MusicPackRepository : JpaRepository<MusicPack, Long> {

    @Query(
        value = """
            select distinct mp.id, mp.addedAt  from MusicPack mp
                join mp.collection c
                join c.userCollections ucs
                join ucs.user u
                left join mp.tags tags 
            where tags.value in (:tags) 
                and u.id = :userId
                and c.removed = false
                and mp.removed = false
            order by mp.addedAt desc
        """
    )
    fun findActualIdsByUserIdAndTags(
        @Param("userId") userId: Long,
        @Param("tags") tags: List<String>,
        pageable: Pageable
    ): Page<Array<Any>>


    @Query(
        value = """
            select distinct mp.id, mp.addedAt from MusicPack mp
                join mp.collection c
                join c.userCollections ucs
            where ucs.userId = :userId
                and c.removed = false
                and mp.removed = false
            order by mp.addedAt desc
        """
    )
    fun findActualIdsByUserId(
        @Param("userId") userId: Long,
        pageable: Pageable
    ): Page<Array<Any>>


    @Query(
        value = """
            select distinct mp.id, mp.addedAt from MusicPack mp
                join mp.collection c
                left join mp.tags tags 
            where c.id = :collectionId 
                and tags.value in (:tags)
                and c.removed = false
                and mp.removed = false
            order by mp.addedAt desc
        """
    )
    fun findActualIdsByCollectionIdAndTags(
        @Param("collectionId") collectionId: Long,
        @Param("tags") tags: List<String>,
        pageable: Pageable
    ): Page<Array<Any>>


    @Query(
        value = """
            select mp.id from MusicPack mp 
                join mp.collection c
            where c.id = :collectionId
                and c.removed = false
                and mp.removed = false
            order by mp.addedAt desc
        """
    )
    fun findActualIdsByCollectionId(
        @Param("collectionId") collectionId: Long,
        pageable: Pageable
    ): Page<Long>


    @Query(nativeQuery = true, value = """
        select mp.id from application.music_pack mp 
        where mp.collection_id in (?1)
            and mp.removed = false
            and (
                lower(mp.title) ~ ?2
                or (mp.description is not null and lower(mp.description) ~ ?2)
            )
        order by mp.added_at desc
        limit ?3
        offset ?4
    """)
    fun findActualIdsByCollectionIdAndSearchRequest(
        collectionIds: Collection<Long>,
        searchRequestRegexPattern: String,
        limit: Int,
        offset: Int
    ): List<Long>

    @Query(nativeQuery = true, value = """
        select count(mp.id) from application.music_pack mp 
        where mp.collection_id in (?1)
            and mp.removed = false
            and (
                lower(mp.title) ~ ?2
                or (mp.description is not null and lower(mp.description) ~ ?2)
            )
    """)
    fun countActualIdsByCollectionIdAndSearchRequest(collectionIds: Collection<Long>, searchRequestRegexPattern: String): Long

    @Query(
        value = """
            select mp.externalId from MusicPack mp 
                join mp.collection c
            where c.id = :collectionId
                and mp.externalId in (:externalIds)
                and c.removed = false
                and mp.removed = false
        """
    )
    fun findActualExternalIdsByCollectionIdAndExternalIdIn(
        @Param("collectionId") collectionId: Long,
        @Param("externalIds") externalIds: Set<String>
    ): Set<String>


    @Query(
        value = """
            select distinct mp from MusicPack mp
                left join fetch mp.tags tags 
            where mp.id in (:ids)
            order by mp.addedAt desc            
        """
    )
    fun fetchWithTagsByIds(@Param("ids") ids: List<Long>): List<MusicPack>


    @Query(
        value = """
            select distinct mp from MusicPack mp
                left join fetch mp.tags tags 
                join mp.collection.userCollections uc 
            where mp.id = :id
                and uc.userId = :userId
        """
    )
    fun fetchWithTagsByIdAndUserId(@Param("id") id: Long, @Param("userId") userId: Long): MusicPack?

    @Query(
        value = """
            select distinct mp from MusicPack mp
                left join fetch mp.tags tags 
                join fetch mp.collection c
                join c.userCollections uc 
                left join fetch mp.musicArtists mp_ar
                left join fetch mp_ar.musicArtist mar
                left join fetch mp.musicAlbums mp_al
                left join fetch mp_al.musicAlbum mal
                left join fetch mal.artists mal_ar
                left join fetch mp.musicTracks mp_tr
                left join fetch mp_tr.musicTrack mtr
                left join fetch mtr.album mtr_al
                left join fetch mtr_al.artists mtr_al_ar
                left join fetch mtr.artists mtr_ar
                left join fetch mar.sources mar_s
                left join fetch mal.sources mal_s
                left join fetch mtr.sources mtr_s
                left join fetch mal_ar.sources mal_ar_s
                left join fetch mtr_al.sources mtr_al_s
                left join fetch mtr_al_ar.sources mtr_al_ar
                left join fetch mtr_ar.sources mtr_ar_s
            where mp.id = :id
                and uc.userId = :userId
        """
    )
    fun fetchWithContentByIdAndUserId(@Param("id") id: Long, @Param("userId") userId: Long): MusicPack?


    @Query(
        value = """
            select distinct mp from MusicPack mp
                join fetch mp.collection c
                join fetch c.userCollections uc 
            where mp.id = :id
                and uc.userId = :userId
                and c.removed = false
                and mp.removed = false
        """
    )
    fun findActualByIdAndUserId(@Param("id") id: Long, @Param("userId") userId: Long): MusicPack?

    @Query(
        value = """
            select distinct mp from MusicPack mp
                join fetch mp.collection c
                join fetch c.userCollections uc 
            where c.id in (:collectionIds)
                and c.removed = false
                and mp.removed = false
        """
    )
    fun findActualByCollectionIds(@Param("collectionIds") collectionIds: List<Long>): List<MusicPack>

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            update MusicPack mp 
            set
                mp.removed = true
            where mp.collectionId = :collectionId
        """
    )
    fun setRemovedTrueByCollectionId(@Param("collectionId") collectionId: Long)


    @Query(
        value = """
            select count(mp.id) from MusicPack mp
            where mp.collectionId = :collectionId
                and mp.removed = false
        """
    )
    fun countByCollectionId(@Param("collectionId") collectionId: Long): Long


    @Query(
        value = """
            select mp.collectionId, count(mp.id) from MusicPack mp
            where mp.collectionId in (:collectionIds)
                and mp.removed = false
            group by mp.collectionId
        """
    )
    fun countByCollectionIds(@Param("collectionIds") collectionIds: List<Long>): Array<Array<Any>>


    @Query(
        value = """
            select count(distinct mp.id) from MusicPack mp
                join mp.collection c
                join c.userCollections ucs
                join ucs.user u 
            where u.id = :userId 
                and c.removed = false
                and mp.removed = false
        """
    )
    fun countByUserId(@Param("userId") userId: Long): Long


    fun existsByCollectionIdAndPageUrlAndRemovedFalse(
        @Param("collectionId") collectionId: Long,
        @Param("pageUrl") pageUrl: String
    ): Boolean


    @Query(
        value = """
            select distinct mp.pageUrl from MusicPack mp
                join mp.collection.userCollections uc 
            where mp.id = :id
                and uc.userId = :userId
        """
    )
    fun findPageUrlByIdAndUserId(@Param("id") id: Long, @Param("userId") userId: Long): List<String>
}