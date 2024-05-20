package ru.push.musicfeed.platform.data.repo

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.SEARCH_RESULT_STR_SEPARATOR
import ru.push.musicfeed.platform.data.TrackDataSearchResultProj
import ru.push.musicfeed.platform.data.model.music.MusicPackTrack
import ru.push.musicfeed.platform.data.model.music.MusicTrack

@Repository
interface MusicTrackRepository : JpaRepository<MusicTrack, Long> {

    // TODO think about optimization with filter by albumTitle
    @Query(value = """
        select distinct mtr from MusicTrack mtr 
            left join fetch mtr.album mal
            left join fetch mtr.artists mar
            left join fetch mtr.sources src
        where mtr.title in (:titles)
    """)
    fun findByTitles(@Param("titles") titles: Set<String>): Set<MusicTrack>

    @Query(nativeQuery = true, value = """
        select mt.id as trackId, 
            mt.title as trackTitle,
            coalesce(string_agg(ma.name, '${SEARCH_RESULT_STR_SEPARATOR}'), '') as artistsNames
        from application.music_track mt
            left join application.music_track_artist mta on mt.id = mta.music_track_id
            join application.music_artist ma on mta.music_artist_id = ma.id
        where lower(mt.title) ~ ?1
        group by mt.id
    """)
    fun findByTitleRegexMatch(titleRegexPattern: String): Set<TrackDataSearchResultProj>

    @Query(
        value = """
            select distinct mp_tr from MusicPack mp
                join mp.collection c
                join c.userCollections uc 
                join mp.musicTracks mp_tr
                join mp_tr.musicTrack mtr
            where mp.id = :musicPackId
                and uc.userId = :userId
            order by mp_tr.position 
        """
    )
    fun findMusicPackTrackPage(@Param("musicPackId") musicPackId: Long, @Param("userId") userId: Long, pageable: Pageable): Page<MusicPackTrack>

    @Query(
        value = """
            select distinct mp_tr from MusicPack mp
                join mp.musicTracks mp_tr
                join fetch mp_tr.musicTrack mtr
                left join fetch mtr.album mtr_al
                left join fetch mtr_al.artists mtr_al_ar
                left join fetch mtr.artists mtr_ar
                left join fetch mtr.sources mtr_s
            where mp.id = :musicPackId 
                and mtr.id in (:trackIds)
        """
    )
    fun fetchMusicPackTrackList(@Param("musicPackId") musicPackId: Long, @Param("trackIds") trackIds: Collection<Long>): List<MusicPackTrack>

    @Query(
        value = """
            select distinct mp_tr from MusicPack mp
                join mp.musicTracks mp_tr
                join fetch mp_tr.musicTrack mtr
                left join fetch mtr.album mtr_al
                left join fetch mtr_al.artists mtr_al_ar
                left join fetch mtr.artists mtr_ar
                left join fetch mtr.sources mtr_s
            where mp.id = :musicPackId
        """
    )
    fun fetchAllMusicPackTrackList(@Param("musicPackId") musicPackId: Long): List<MusicPackTrack>

    @Query(value = """
        select distinct mtr from MusicTrack mtr 
            left join fetch mtr.album mal
            left join fetch mtr.artists mar
            left join fetch mtr.sources src
        where mtr.id = :id
    """)
    fun fetchById(@Param("id") id: Long): MusicTrack?

    @Query(value = """
        select distinct mtr from MusicTrack mtr 
            left join fetch mtr.album mal
            left join fetch mtr.artists mar
            left join fetch mtr.sources src
        where mtr.id in (:trackIds)
    """)
    fun fetchByIdIn(@Param("trackIds") trackIds: List<Long>): List<MusicTrack>

    @Query(value = """
        select distinct mtr from MusicTrack mtr 
            left join fetch mtr.album mal
            left join fetch mtr.artists mar
            join fetch mtr.sources src
        where src.localFileId = :localFileId
    """)
    fun fetchByLocalFileId(@Param("localFileId") localFileId: Long): List<MusicTrack>
}