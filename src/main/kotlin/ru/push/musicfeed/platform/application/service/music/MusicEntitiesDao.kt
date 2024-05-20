package ru.push.musicfeed.platform.application.service.music

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.application.dto.MusicAlbumDto
import ru.push.musicfeed.platform.application.dto.MusicArtistDto
import ru.push.musicfeed.platform.application.dto.MusicPackWithContentDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.data.model.music.MusicAlbum
import ru.push.musicfeed.platform.data.model.music.MusicAlbumSource
import ru.push.musicfeed.platform.data.model.music.MusicArtist
import ru.push.musicfeed.platform.data.model.music.MusicArtistSource
import ru.push.musicfeed.platform.data.model.music.MusicPack
import ru.push.musicfeed.platform.data.model.music.MusicTrack
import ru.push.musicfeed.platform.data.model.music.MusicTrackSource
import ru.push.musicfeed.platform.data.repo.MusicAlbumRepository
import ru.push.musicfeed.platform.data.repo.MusicArtistRepository
import ru.push.musicfeed.platform.data.repo.MusicTrackRepository
import ru.push.musicfeed.platform.util.uniteToSet
import java.time.Clock
import java.time.LocalDateTime
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.toDuration
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import ru.push.musicfeed.platform.application.MusicTrackNotFoundException
import ru.push.musicfeed.platform.application.dto.MusicSourceDto
import ru.push.musicfeed.platform.data.model.music.MusicPackTrack
import ru.push.musicfeed.platform.data.model.music.MusicPackTrackLocalFile
import ru.push.musicfeed.platform.data.model.music.MusicSourceType
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.TRACK_LOCAL_FILE
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.YANDEX_MUSIC
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.COMMON_EXTERNAL_LINK
import ru.push.musicfeed.platform.data.repo.MusicPackTrackLocalFileRepository

class MusicPackEntitiesWrapper(
    val musicPackWithContentDto: MusicPackWithContentDto,
    val musicArtists: Set<MusicArtist> = setOf(),
    val musicAlbums: Set<MusicAlbum> = setOf(),
    val musicTracks: Set<MusicTrack> = setOf()
)

@Service
class MusicEntitiesDao(
    private val musicArtistRepository: MusicArtistRepository,
    private val musicAlbumRepository: MusicAlbumRepository,
    private val musicTrackRepository: MusicTrackRepository,
    private val musicPackTrackLocalFileRepository: MusicPackTrackLocalFileRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    final val logger = KotlinLogging.logger {}

    fun fetchTrackById(trackId: Long): MusicTrack? {
        return musicTrackRepository.fetchById(trackId)
    }

    fun fetchTrackListPageForMusicPack(musicPackId: Long, userId: Long, page: Int, size: Int): Page<MusicPackTrack> {
        val pageable = PageRequest.of(page, size)
        val musicPackTrackPage = musicTrackRepository.findMusicPackTrackPage(musicPackId, userId, pageable)
        if (musicPackTrackPage.isEmpty)
            return PageImpl(listOf(), pageable, musicPackTrackPage.totalElements)
        val trackIdToPosition = musicPackTrackPage.content.associate { it.musicTrackId to it.position }
        val trackList = musicTrackRepository.fetchMusicPackTrackList(musicPackId, trackIdToPosition.keys)
            .sortedBy { trackIdToPosition[it.id!!] }
        return PageImpl(trackList, pageable, musicPackTrackPage.totalElements)
    }

    fun fetchAllMusicPackTracks(musicPackId: Long): List<MusicPackTrack> {
        return musicTrackRepository.fetchAllMusicPackTrackList(musicPackId).sortedBy { it.position }
    }

    @Transactional
    fun checkAndSaveMusicEntities(musicPacksDto: List<MusicPackWithContentDto>): List<MusicPackEntitiesWrapper> {
        if (musicPacksDto.isEmpty())
            return listOf()

        val artists = uniteToSet(
            musicPacksDto.flatMap { it.artists },
            musicPacksDto.flatMap { it.albums.flatMap { it.artists } },
            musicPacksDto.flatMap { it.tracks.mapNotNull { it.artists }.flatten() },
            musicPacksDto.flatMap { it.tracks.mapNotNull { it.album }.flatMap { it.artists } }
        )
        val artistEntitiesMap = checkAndSaveMusicArtists(artists)

        val albums = uniteToSet(
            musicPacksDto.flatMap { it.albums },
            musicPacksDto.flatMap { it.tracks.mapNotNull { it.album } }
        )
        val albumEntitiesMap = checkAndSaveMusicAlbums(albums, artistEntitiesMap)

        val tracks = musicPacksDto.flatMap { it.tracks }.toHashSet()
        val trackEntitiesMap = checkAndSaveMusicTracks(tracks, artistEntitiesMap, albumEntitiesMap)

        return musicPacksDto.map {
            formMusicPackEntitiesWrapper(it, artistEntitiesMap, albumEntitiesMap, trackEntitiesMap)
        }
    }

    @Transactional
    fun checkAndSaveMusicEntities(musicPackDto: MusicPackWithContentDto): MusicPackEntitiesWrapper {
        val artists = uniteToSet(
            musicPackDto.artists,
            musicPackDto.albums.flatMap { it.artists },
            musicPackDto.tracks.mapNotNull { it.artists }.flatten(),
            musicPackDto.tracks.mapNotNull { it.album }.flatMap { it.artists }
        )
        val artistEntitiesMap = checkAndSaveMusicArtists(artists)

        val albums = uniteToSet(
            musicPackDto.albums,
            musicPackDto.tracks.mapNotNull { it.album }
        )
        val albumEntitiesMap = checkAndSaveMusicAlbums(albums, artistEntitiesMap)

        val tracks = musicPackDto.tracks.toHashSet()
        val trackEntitiesMap = checkAndSaveMusicTracks(tracks, artistEntitiesMap, albumEntitiesMap)

        return formMusicPackEntitiesWrapper(musicPackDto, artistEntitiesMap, albumEntitiesMap, trackEntitiesMap)
    }

    @Transactional
    fun checkAndSaveMusicTrack(musicTrackDto: MusicTrackDto): MusicTrack {
        return checkAndSaveMusicTracks(listOf(musicTrackDto)).first()
    }

    @Transactional
    fun checkAndSaveMusicTracks(musicTracksDto: List<MusicTrackDto>): Set<MusicTrack> {
        val artists = uniteToSet(
            musicTracksDto.mapNotNull { it.artists }.flatten(),
            musicTracksDto.mapNotNull { it.album }.flatMap { it.artists }
        )
        val artistEntitiesMap = checkAndSaveMusicArtists(artists)

        val albums = musicTracksDto.mapNotNullTo(hashSetOf()) { it.album }
        val albumEntitiesMap = checkAndSaveMusicAlbums(albums, artistEntitiesMap)

        val tracks = musicTracksDto.toHashSet()
        val trackEntitiesMap = checkAndSaveMusicTracks(tracks, artistEntitiesMap, albumEntitiesMap)

        return trackEntitiesMap.mapTo(hashSetOf()) { it.value }
    }

    @Transactional
    fun checkAndDeleteMusicEntities(musicPack: MusicPack) {
        // TODO add implementation for remove orphan music entities
    }

    @Transactional
    fun checkAndDeleteMusicTrack(musicTrack: MusicTrack) {
        // TODO add implementation for remove orphan music entities
    }

    private fun checkAndSaveMusicArtists(artists: Set<MusicArtistDto>): Map<String, MusicArtist> {
        if (artists.isEmpty())
            return emptyMap()

        val artistsNames = artists.mapTo(mutableSetOf()) { it.name }
        val foundArtists = musicArtistRepository.findByNames(artistsNames)
        val foundArtistsMap = foundArtists.associateBy { it.formKey() }
        val now = LocalDateTime.now(clock)
        val existedArtists = mutableSetOf<MusicArtist>()
        val newArtists = artists
//            .distinctBy { it.source }
            .filter {
                val foundArtist = foundArtistsMap[it.formKey()]
                if (foundArtist != null) {
                    existedArtists.add(foundArtist)
                    return@filter false
                }
                return@filter true
            }
            .map {
                MusicArtist(
                    name = it.name,
                    sources = it.source?.let { mutableSetOf(it.toArtistSource()) } ?: mutableSetOf(),
                    createdAt = now
                )
            }

        val artistEntities = musicArtistRepository.saveAll(newArtists)
        artistEntities.addAll(existedArtists)
        return artistEntities.associateBy { it.formKey() }
    }

    private fun checkAndSaveMusicAlbums(
        albums: Set<MusicAlbumDto>,
        artistEntitiesMap: Map<String, MusicArtist>
    ): Map<String, MusicAlbum> {
        if (albums.isEmpty())
            return emptyMap()

        val albumNames = albums.mapTo(mutableSetOf()) { it.title }
        val foundAlbums = musicAlbumRepository.findByTitles(albumNames)
        val foundAlbumsMap = foundAlbums.associateBy { album ->
            formAlbumKey(album.title, album.artists.map { it.name })
        }
        val now = LocalDateTime.now(clock)
        val existedAlbums = mutableSetOf<MusicAlbum>()
        val newAlbums = albums
//            .distinctBy { it.source }
            .filter {
                val foundAlbum = foundAlbumsMap[it.formKey()]
                if (foundAlbum != null) {
                    existedAlbums.add(foundAlbum)
                    return@filter false
                }
                return@filter true
            }
            .map {
                MusicAlbum(
                    title = it.title,
                    year = it.year,
                    releaseDate = it.releaseDate,
                    artists = it.artists.mapNotNullTo(mutableSetOf()) { artistEntitiesMap[it.formKey()] },
                    sources = it.source?.let { mutableSetOf(it.toAlbumSource()) } ?: mutableSetOf(),
                    createdAt = now
                )
            }

        val albumEntities = musicAlbumRepository.saveAll(newAlbums)
        albumEntities.addAll(existedAlbums)
        return albumEntities.associateBy { it.formKey() }
    }

    private fun checkAndSaveMusicTracks(
        tracks: Set<MusicTrackDto>,
        artistEntitiesMap: Map<String, MusicArtist>,
        albumEntitiesMap: Map<String, MusicAlbum>
    ): Map<String, MusicTrack> {
        if (tracks.isEmpty())
            return emptyMap()

        val trackNames = tracks.mapTo(mutableSetOf()) { it.title }
        val foundTracks = musicTrackRepository.findByTitles(trackNames)
        val foundTracksMap = foundTracks.associateBy { track ->
            if (track.album == null) logger.info { "Track with empty album: $track" }
            track.formKey()
        }
        val now = LocalDateTime.now(clock)
        val existedTracks = mutableSetOf<MusicTrack>()
        val newTracks = tracks
            .distinctBy { it.source }
            .filter { track ->
                val foundTrack = foundTracksMap[track.formKey()]
                if (foundTrack != null) {
                    existedTracks.add(foundTrack)
                    return@filter false
                }
                return@filter true
            }
            .map {
                MusicTrack(
                    title = it.title,
                    album = it.album?.let { albumEntitiesMap[it.formKey()] },
                    artists = it.artists?.mapNotNullTo(mutableSetOf()) { artistEntitiesMap[it.formKey()] }
                        ?: mutableSetOf(),
                    sources = mutableSetOf(it.source.toTrackSource()),
                    createdAt = now,
                    albumPosition = it.albumPosition,
                    durationSec = it.duration?.inWholeSeconds
                )
            }

        val trackEntities = musicTrackRepository.saveAll(newTracks)
        trackEntities.addAll(existedTracks)
        return trackEntities.associateBy { it.formKey() }
    }

    fun formMusicPackEntitiesWrapper(
        musicPackWithContent: MusicPackWithContentDto,
        artistEntitiesMap: Map<String, MusicArtist>,
        albumEntitiesMap: Map<String, MusicAlbum>,
        trackEntitiesMap: Map<String, MusicTrack>
    ): MusicPackEntitiesWrapper {
        return MusicPackEntitiesWrapper(
            musicPackWithContentDto = musicPackWithContent,
            musicArtists = musicPackWithContent.artists
                .mapNotNullTo(mutableSetOf()) { artistEntitiesMap[it.formKey()] },
            musicAlbums = musicPackWithContent.albums
                .mapNotNullTo(mutableSetOf()) { albumEntitiesMap[it.formKey()] },
            musicTracks = musicPackWithContent.tracks
                .mapNotNullTo(mutableSetOf()) { trackEntitiesMap[it.formKey()] }
        )
    }

    @Transactional
    fun storeMusicPackLocalFileInfo(id: Long, trackLocalFileId: Long) {
        musicPackTrackLocalFileRepository.save(
            MusicPackTrackLocalFile(
                musicPackId = id,
                trackLocalFileId = trackLocalFileId
            )
        )
    }

    @Transactional
    fun appendLocalFileSourceToMusicTrack(musicTrackId: Long, localFileId: Long) {
        val musicTrack = fetchTrackById(musicTrackId) ?: throw MusicTrackNotFoundException(musicTrackId = musicTrackId)
        val trackLocalFileSource = musicTrack.sources.firstOrNull { it.sourceType == TRACK_LOCAL_FILE }
        if (trackLocalFileSource != null) {
            logger.warn { "Music track source local file conflict, will overrides for track with new localFileId = '$localFileId', " +
                    "track data: ${musicTrack.id!!}, ${musicTrack.sources}" }
            trackLocalFileSource.localFileId = localFileId
        } else {
            val newSource = MusicTrackSource(
                sourceType = TRACK_LOCAL_FILE,
                localFileId = localFileId
            )
            musicTrack.sources.add(newSource)
        }
        musicTrackRepository.save(musicTrack)
    }

}

