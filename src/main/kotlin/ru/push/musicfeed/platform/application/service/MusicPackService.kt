package ru.push.musicfeed.platform.application.service

import mu.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.application.MusicCollectionAccessDeniedException
import ru.push.musicfeed.platform.application.MusicPackAlreadyExistException
import ru.push.musicfeed.platform.application.MusicPackNotEditableException
import ru.push.musicfeed.platform.application.MusicPackNotFoundException
import ru.push.musicfeed.platform.application.dto.MusicAlbumDto
import ru.push.musicfeed.platform.application.dto.MusicArtistDto
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.application.dto.MusicPackWithContentDto
import ru.push.musicfeed.platform.application.dto.AddTrackToMusicPackResultDto
import ru.push.musicfeed.platform.application.dto.MusicSourceDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.application.dto.TagDto
import ru.push.musicfeed.platform.data.model.music.MusicCollection
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import ru.push.musicfeed.platform.data.repo.MusicPackRepository
import ru.push.musicfeed.platform.data.repo.TagRepository
import ru.push.musicfeed.platform.data.model.music.MusicPack
import ru.push.musicfeed.platform.data.model.music.MusicPackAlbum
import ru.push.musicfeed.platform.data.model.music.MusicPackArtist
import ru.push.musicfeed.platform.data.model.music.MusicPackTrack
import ru.push.musicfeed.platform.data.model.music.MusicTrack
import ru.push.musicfeed.platform.data.model.music.Tag
import ru.push.musicfeed.platform.data.model.UserCollection
import ru.push.musicfeed.platform.data.repo.MusicCollectionRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import ru.push.musicfeed.platform.application.ExternalSourceParseException
import ru.push.musicfeed.platform.application.InvalidMusicPackCreateException
import ru.push.musicfeed.platform.application.MusicTrackNotEditableException
import ru.push.musicfeed.platform.application.MusicTrackNotFoundException
import ru.push.musicfeed.platform.application.dto.MusicPackTrackListDto
import ru.push.musicfeed.platform.application.dto.TrackFileInfoDto
import ru.push.musicfeed.platform.application.service.music.MusicEntitiesDao
import ru.push.musicfeed.platform.application.service.music.MusicPackEntitiesWrapper
import ru.push.musicfeed.platform.application.service.music.findOrFirst
import ru.push.musicfeed.platform.application.service.music.formKey
import ru.push.musicfeed.platform.application.service.music.toDto
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType.LOCAL
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.COMMON_EXTERNAL_LINK
import ru.push.musicfeed.platform.data.model.music.MusicSourceType.TRACK_LOCAL_FILE

@Service
class MusicPackService(
    private val musicPackRepository: MusicPackRepository,
    private val musicCollectionRepository: MusicCollectionRepository,
    private val tagRepository: TagRepository,
    private val externalSourceService: ExternalSourceService,
    private val musicEntitiesDao: MusicEntitiesDao,
    private val searchRequestHelper: SearchRequestHelper,
    private val clock: Clock = Clock.systemDefaultZone(),
) {

    companion object {
        private val MONTH_PLAYLIST_COLLECTION_TYPE = MusicCollectionType.YANDEX
        private val MONTH_PLAYLIST_TITLE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM")

        private val TAG_REPLACEMENT_MAP = mapOf(
            " -/," to "_",
            "^`*\\" to "",
            "&" to "and"
        )

        private fun replaceTagForbiddenChars(sourceTag: String): String {
            var result = sourceTag
            TAG_REPLACEMENT_MAP.forEach { (forbiddenChars, replacementStr) ->
                forbiddenChars.toCharArray().forEach { forbiddenChar ->
                    result = result.replace(forbiddenChar.toString(), replacementStr, false)
                }
            }
            return result
        }
    }

    private val logger = KotlinLogging.logger {}

    fun fetchRandomMusicPackByUserId(userId: Long): MusicPackDto {
        val collectionCount = musicPackRepository.countByUserId(userId)
        val randomIndex = Random.nextLong(0, collectionCount)
        return fetchMusicPackPage(userId, randomIndex.toInt(), 1).firstOrNull()
            ?: throw MusicPackNotFoundException(userId)
    }

    fun fetchMusicPackById(musicPackId: Long, userId: Long): MusicPackDto {
        return musicPackRepository.fetchWithTagsByIdAndUserId(musicPackId, userId)?.toMusicPackDto()
            ?: throw MusicPackNotFoundException(musicPackId, userId)
    }

    fun fetchMusicPackPageUrl(musicPackId: Long, userId: Long): String {
        return musicPackRepository.findPageUrlByIdAndUserId(musicPackId, userId).firstOrNull()
            ?: throw MusicPackNotFoundException(musicPackId, userId)
    }

    @Transactional(readOnly = true)
    fun fetchMusicTrackData(musicTrackId: Long, userId: Long? = null): MusicTrackDto {
        return musicEntitiesDao.fetchTrackById(musicTrackId, userId).toDto()
    }

    fun storeMusicPackLocalFileInfo(musicPackId: Long, trackLocalFileId: Long) {
        musicEntitiesDao.storeMusicPackLocalFileInfo(musicPackId, trackLocalFileId)
    }

    fun storeTrackLocalFileInfoSource(musicTrackId: Long, localFileId: Long) {
        musicEntitiesDao.appendLocalFileSourceToMusicTrack(musicTrackId, localFileId)
    }

    fun fetchMusicPackWithContent(musicPackId: Long, userId: Long): MusicPackWithContentDto {
        return musicPackRepository.fetchWithContentByIdAndUserId(musicPackId, userId)?.toMusicPackWithContentDto()
            ?: throw MusicPackNotFoundException(musicPackId, userId)
    }

    @Transactional(readOnly = true)
    fun fetchMusicPackTrackList(musicPackId: Long, userId: Long, page: Int, size: Int): MusicPackTrackListDto {
        val musicPack = fetchMusicPackById(musicPackId, userId)
        val trackListPage = musicEntitiesDao.fetchTrackListPageForMusicPack(musicPackId, userId, page, size)
        return MusicPackTrackListDto(
            musicPack = musicPack,
            contentPage = PageImpl(
                trackListPage.content.sortedBy { it.position }.toDto(),
                trackListPage.pageable,
                trackListPage.totalElements
            )
        )
    }

//    fun fetchAllMusicPackTracks(musicPackId: Long, userId: Long): List<MusicTrackDto> {
//        val musicPack = fetchMusicPackById(musicPackId, userId)
//        val musicPackTracks = musicEntitiesDao.fetchAllMusicPackTracks(musicPack.id!!)
//        return musicPackTracks.toDto()
//    }

    @Transactional(readOnly = true)
    fun getTrackLocalFileIdByTrackId(userId: Long, musicTrackId: Long): Long? {
        val track = musicEntitiesDao.fetchTrackById(musicTrackId, userId)
        return track.sources.find { it.sourceType == TRACK_LOCAL_FILE }?.localFileId
    }

    @Transactional(readOnly = true)
    fun getTrackLocalFileIdByTrackId(musicTrackId: Long): Long? {
        val track = musicEntitiesDao.fetchTrackById(musicTrackId)
        return track?.sources?.find { it.sourceType == TRACK_LOCAL_FILE }?.localFileId
    }

    fun fetchMusicPackPage(userId: Long, page: Int, size: Int): Page<MusicPackDto> {
        val idsPage = musicPackRepository.findActualIdsByUserId(userId, PageRequest.of(page, size)).mapToIds()
        val musicPacks = musicPackRepository.fetchWithTagsByIds(idsPage.content).toMusicPackDto()
        return PageImpl(musicPacks, idsPage.pageable, idsPage.totalElements)
    }

    fun fetchMusicPackPageByCollectionId(collectionId: Long, page: Int, size: Int): Page<MusicPackDto> {
        val idsPage = musicPackRepository.findActualIdsByCollectionId(collectionId, PageRequest.of(page, size))
        val musicPacks = musicPackRepository.fetchWithTagsByIds(idsPage.content).toMusicPackDto()
        return PageImpl(musicPacks, idsPage.pageable, idsPage.totalElements)
    }

    fun fetchMusicPackPageByTags(userId: Long, tags: List<String>, page: Int, size: Int): Page<MusicPackDto> {
        val idsPage = musicPackRepository.findActualIdsByUserIdAndTags(
            userId = userId,
            tags = tags,
            pageable = PageRequest.of(page, size)
        ).mapToIds()
        val musicPacks = musicPackRepository.fetchWithTagsByIds(idsPage.content).toMusicPackDto()
        return PageImpl(musicPacks, idsPage.pageable, idsPage.totalElements)
    }

    fun searchMusicPacksByTextRequest(userId: Long, collectionId: Long?, searchRequest: String, page: Int, size: Int): Page<MusicPackDto> {
        val searchRequestRegexPattern = searchRequestHelper.formSearchRegexPattern(searchRequest)!!
        val collectionIds = collectionId?.let { listOf(it) }
            ?: musicCollectionRepository.findActualIdsByUserId(userId)
        val musicPackIdsPage = musicPackRepository.findActualIdsByCollectionIdAndSearchRequest(
            collectionIds = collectionIds,
            searchRequestRegexPattern = searchRequestRegexPattern,
            limit = size,
            offset = page * size
        )
        val totalMusicPacksCount = musicPackRepository.countActualIdsByCollectionIdAndSearchRequest(
            collectionIds = collectionIds,
            searchRequestRegexPattern = searchRequestRegexPattern
        )
        val musicPacks = musicPackRepository.fetchWithTagsByIds(musicPackIdsPage).toMusicPackDto()
        return PageImpl(musicPacks, PageRequest.of(page, size), totalMusicPacksCount)
    }

    fun fetchTags(userId: Long, page: Int, size: Int): Page<TagDto> {
        return tagRepository.findByUserId(userId, PageRequest.of(page, size))
            .map { it.toDto() }
    }

    @Transactional
    fun savePacksWithExistencesCheck(
        musicPacksDto: List<MusicPackWithContentDto>,
        collection: MusicCollection
    ): List<MusicPack> {
        if (musicPacksDto.isEmpty())
            return emptyList()

        val collectionId = collection.id!!
        val sourceExternalIds = musicPacksDto.map { it.musicPack.externalId!! }.toSet()
        val existedExternalIds = musicPackRepository
            .findActualExternalIdsByCollectionIdAndExternalIdIn(collectionId, sourceExternalIds)

        val newPacksDto = musicPacksDto.filter { !existedExternalIds.contains(it.musicPack.externalId!!) }
        val newPacksWithMusicEntities = musicEntitiesDao.checkAndSaveMusicEntities(newPacksDto)

        if (newPacksWithMusicEntities.isNotEmpty()) {
            val tagValues = newPacksWithMusicEntities.flatMap { it.musicPackWithContentDto.musicPack.tags }.distinct()
            val tags = checkAndSaveTags(tagValues)

            val musicPackEditable = collection.isMusicPackEditable()
            val newPacks = newPacksWithMusicEntities.toMusicPacks(collectionId, musicPackEditable, tags)

            return musicPackRepository.saveAll(newPacks)
        }
        return emptyList()
    }

    private fun checkAndSaveTags(tagValues: List<String>): Set<Tag> {
        val normalizedTagValues = tagValues.map { replaceTagForbiddenChars(it).trim().lowercase() }
        val existedTagsMap = tagRepository.findByValueIn(normalizedTagValues)
            .associateBy { it.value }
        val newTags = normalizedTagValues.filter { !existedTagsMap.containsKey(it) }
            .distinct()
            .mapTo(mutableSetOf()) { Tag(it) }
        tagRepository.saveAll(newTags)
        newTags.addAll(existedTagsMap.values)
        return newTags
    }

    fun countMusicPacksByCollectionId(collectionId: Long): Long {
        return musicPackRepository.countByCollectionId(collectionId)
    }

    fun countMusicPacksByCollectionIds(collectionIds: List<Long>): Map<Long, Long> {
        return musicPackRepository.countByCollectionIds(collectionIds)
            .associate { (it[0] as Long) to (it[1] as Long) }
    }

    @Transactional
    fun createMusicPackFromExternalSource(sourceUrl: String, userCollection: UserCollection): MusicPackDto {
        val userId = userCollection.userId
        userCollection.checkPermissions()
        val collectionId = userCollection.collectionId

        if (musicPackRepository.existsByCollectionIdAndPageUrlAndRemovedFalse(collectionId, sourceUrl))
            throw MusicPackAlreadyExistException(collectionId, sourceUrl)

        val collection = userCollection.collection!!
        val newPackWithContentDto = externalSourceService.createMusicPackBySourceUrl(userId, collection, sourceUrl)
        val newPackWithMusicEntities = musicEntitiesDao.checkAndSaveMusicEntities(newPackWithContentDto)

        val createdMusicPackDto = newPackWithMusicEntities.musicPackWithContentDto
        val tags = checkAndSaveTags(createdMusicPackDto.musicPack.tags)

        val musicPackEditable = collection.isMusicPackEditable()
        val musicPack = newPackWithMusicEntities.toMusicPack(collectionId, musicPackEditable, tags)
        return musicPackRepository.save(musicPack)
            .toMusicPackDto()
    }

    @Transactional
    fun createMusicPackLocal(title: String, userCollection: UserCollection): MusicPackDto {
        val userId = userCollection.userId
        userCollection.checkPermissions()
        val collection = userCollection.collection!!
        val collectionId = userCollection.collectionId
        if (collection.type != LOCAL) {
            throw InvalidMusicPackCreateException(userId, collectionId)
        }

        val now = LocalDateTime.now(clock)
        val newMusicPack = MusicPack(
            externalId = null,
            collectionId = collectionId,
            title = title,
            description = null,
            coverUrl = null,
            pageUrl = null,
            tags = mutableSetOf(),
            addedAt = now,
            updatedAt = now,
            removed = false,
            editable = true
        )
        return musicPackRepository.save(newMusicPack)
            .toMusicPackDto()
    }

    @Transactional
    fun removeMusicPack(id: Long, userId: Long) {
        val musicPack = musicPackRepository.findActualByIdAndUserId(id, userId)
            ?: throw MusicPackNotFoundException(userId, id)
        val collection = musicPack.collection!!
        collection.checkUserPermissions(userId)

        musicEntitiesDao.checkAndDeleteMusicEntities(musicPack)

        musicPack.removed = true
        musicPackRepository.save(musicPack)

        try {
            musicPack.externalId
                ?.let { externalId ->
                    val removed = externalSourceService.removeMusicPack(userId, collection, musicPack.externalId!!)
                    if (!removed)
                        logger.error { "External source music pack removing fails, musicPackId = ${musicPack.id}" }
                }
        } catch (ex: Throwable) {
            logger.error {
                "External source music pack removing throws exception, musicPackId = ${musicPack.id}, ex = $ex"
            }
        }
    }

    @Transactional
    fun musicPackEditTitle(id: Long, userId: Long, newTitle: String) = findEndEditMusicPack(id, userId) {
        it.title = newTitle
    }

    @Transactional
    fun musicPackEditDescription(id: Long, userId: Long, newDescription: String) = findEndEditMusicPack(id, userId) {
        it.description = newDescription
    }

    @Transactional
    fun musicPackEditTags(id: Long, userId: Long, newTags: List<String>) = findEndEditMusicPack(id, userId) {
        val tags = checkAndSaveTags(newTags)
        it.tags.clear()
        it.tags.addAll(tags)
    }

    @Transactional
    fun musicPackEditCoverUrl(id: Long, userId: Long, newCoverUrl: String) = findEndEditMusicPack(id, userId) {
        it.coverUrl = newCoverUrl
    }

    private fun findEndEditMusicPack(id: Long, userId: Long, editFunc: (MusicPack) -> Unit): MusicPackDto {
        val musicPack = obtainMusicPackForUser(id, userId)
        val collection = musicPack.collection!!
        collection.checkUserPermissions(userId)
        editFunc(musicPack)
        return musicPackRepository.save(musicPack).toMusicPackDto()
    }

    @Transactional
    fun musicPackFormDefaultTracklist(userId: Long, musicPackId: Long): List<MusicTrackDto> {
        val musicPack = musicPackRepository.findActualByIdAndUserIdOrThrow(musicPackId, userId)
        val collection = musicPack.collection!!
        collection.checkUserPermissions(userId)
        val sourceUrl = musicPack.pageUrl
            ?: return emptyList()
        val musicTracksDto =  try {
            externalSourceService.extractTracksDataAndAddTracksToMusicPack(
                userId = userId,
                collection = collection,
                musicPackId = musicPack.externalId,
                sourceUrl = sourceUrl
            )
        } catch (ex: ExternalSourceParseException) {
            logger.info { "ExternalSourceParseException: $ex" }
            listOf(
                MusicTrackDto(
                    title = musicPack.title,
                    position = 0,
                    source = MusicSourceDto(type = COMMON_EXTERNAL_LINK, url = sourceUrl)
                )
            )
        }
        return addMusicTracksAndSaveMusicPack(musicPack, musicTracksDto).musicTracks
            .sortedBy { it.addedAt }
            .toDto()
    }

    @Transactional
    fun addTrackToMusicPackBySourceUrl(
        userId: Long,
        musicPackId: Long? = null,
        sourceUrls: List<String>
    ): AddTrackToMusicPackResultDto {
        val musicPack = obtainMusicPackForUser(musicPackId, userId)
        val collection = musicPack.collection!!
        collection.checkUserPermissions(userId)
        val musicTracksDto = sourceUrls.flatMap {
            externalSourceService.extractTracksDataAndAddTracksToMusicPack(userId, collection, musicPack.externalId, it)
        }
        addMusicTracksAndSaveMusicPack(musicPack, musicTracksDto)
        return AddTrackToMusicPackResultDto(
            added = true,
            tracks = musicTracksDto
        )
    }

    @Transactional
    fun addTrackToMusicPackById(
        userId: Long,
        musicPackId: Long? = null,
        trackId: Long
    ): MusicTrackDto {
        val musicPack = obtainMusicPackForUser(musicPackId, userId)
        val collection = musicPack.collection!!
        collection.checkUserPermissions(userId)

        val musicTrack = musicEntitiesDao.fetchTrackById(trackId, userId)
        val sourceUrl = musicTrack.sources.find { it.sourceType == collection.type.toMusicSourceType() }?.externalSourceUrl
        if (sourceUrl != null) {
            externalSourceService.addTrackToMusicPackBySourceUrl(
                userId = userId,
                collection = collection,
                musicPackId = musicPack.externalId,
                sourceUrl = sourceUrl
            )
        }

        val now = LocalDateTime.now(clock)
        val musicPackTrack = MusicPackTrack(
            musicTrackId = musicTrack.id!!,
            musicTrack = musicTrack,
            addedAt = now,
            position = musicPack.musicTracks.size
        ).apply {
            this.musicPack = musicPack
        }
        musicPack.musicTracks.add(musicPackTrack)
        musicPack.updatedAt = now
        musicPackRepository.save(musicPack)
        return musicPackTrack.toDto()
    }

    private fun addMusicTrackAndSaveMusicPack(musicPack: MusicPack, musicTrackDto: MusicTrackDto) =
        addMusicTracksAndSaveMusicPack(musicPack, listOf(musicTrackDto))

    private fun addMusicTracksAndSaveMusicPack(musicPack: MusicPack, musicTracksDto: List<MusicTrackDto>): MusicPack {
        val now = LocalDateTime.now(clock)
        val musicPackTrackEntities = musicTracksDto.map { musicEntitiesDao.checkAndSaveMusicTrack(it) }
            .mapIndexed { idx, it ->
                MusicPackTrack(
                    musicTrackId = it.id!!,
                    musicTrack = it,
                    addedAt = now,
                    position = musicPack.musicTracks.size + idx
                ).apply {
                    this.musicPack = musicPack
                }
            }
        musicPack.musicTracks.addAll(musicPackTrackEntities)
        musicPack.updatedAt = now
        return musicPackRepository.save(musicPack)
    }

    @Transactional
    fun addTrackToMusicPackByFileInfo(
        userId: Long,
        musicPackId: Long,
        fileInfo: TrackFileInfoDto
    ) {
        val musicPack = obtainMusicPackForUser(musicPackId, userId)
        val collection = musicPack.collection!!
        collection.checkUserPermissions(userId)

        val trackDto = MusicTrackDto(
            title = fileInfo.trackTitle,
            source = MusicSourceDto(type = TRACK_LOCAL_FILE, localFileId = fileInfo.trackLocalFileId)
        )
        addMusicTrackAndSaveMusicPack(musicPack, trackDto)
    }

    @Transactional
    fun replaceMusicPackTrackWithNewTrackCreation(
        userId: Long,
        musicPackId: Long,
        trackId: Long,
        newTrackTitle: String,
        newArtistName: String?,
        newAlbumName: String?
    ) {
        val musicPack = obtainMusicPackForUser(musicPackId, userId)
        val collection = musicPack.collection!!
        collection.checkUserPermissions(userId)

        val musicPackTrack = musicPack.musicTracks.find { it.musicTrackId == trackId }
            ?: throw MusicTrackNotFoundException(userId, trackId)
        val musicTrack = musicPackTrack.musicTrack
        if (!musicTrack.isEditable())
            throw MusicTrackNotEditableException(userId, trackId)

        val newArtistDto = newArtistName?.let {
            MusicArtistDto(
                name = it,
                source = null
            )
        }
        val newAlbumDto = newAlbumName?.let {
            MusicAlbumDto(
                title = it,
                artists = newArtistDto?.let { listOf(it) } ?: listOf(),
                source = null
            )
        }
        val editedTrackDto = MusicTrackDto(
            title = newTrackTitle,
            position = musicPackTrack.position,
            albumPosition = musicTrack.albumPosition,
            album = newAlbumDto ?: musicTrack.album?.toDto(),
            artists = newArtistDto?.let { listOf(it) } ?: musicTrack.artists.map { it.toDto() },
            source = musicTrack.sources.findOrFirst(TRACK_LOCAL_FILE),
        )

        musicPack.musicTracks.remove(musicPackTrack)

        val now = LocalDateTime.now(clock)
        val savedMusicTrack = musicEntitiesDao.checkAndSaveMusicTrack(editedTrackDto)
        musicPack.musicTracks.add(
            MusicPackTrack(
                musicTrackId = savedMusicTrack.id!!,
                musicTrack = savedMusicTrack,
                addedAt = musicPackTrack.addedAt,
                position = editedTrackDto.position ?: musicPack.musicTracks.size
            ).apply {
                this.musicPack = musicPack
            }
        )
        musicPack.updatedAt = now
        musicPackRepository.save(musicPack)
    }

    @Transactional
    fun replaceMusicPackTracklistArtist(
        userId: Long,
        musicPackId: Long,
        newArtistName: String,
    ) {

        val musicPack = obtainMusicPackForUser(musicPackId, userId)
        val collection = musicPack.collection!!
        collection.checkUserPermissions(userId)

        val now = LocalDateTime.now(clock)
        val newArtistDto = MusicArtistDto(
                name = newArtistName,
                source = null
            )
        val updatedMusicPackTracks = musicPack.musicTracks.map { musicPackTrack ->
            val musicTrack = musicPackTrack.musicTrack
            if (!musicTrack.isEditable())
                throw MusicTrackNotEditableException(userId, musicTrack.id)
            val editedTrackDto = MusicTrackDto(
                title = musicTrack.title,
                position = musicPackTrack.position,
                albumPosition = musicTrack.albumPosition,
                album = musicTrack.album?.toDto(),
                artists = listOf(newArtistDto),
                source = musicTrack.sources.findOrFirst(TRACK_LOCAL_FILE),
            )
            val savedMusicTrack = musicEntitiesDao.checkAndSaveMusicTrack(editedTrackDto)
            MusicPackTrack(
                musicTrackId = savedMusicTrack.id!!,
                musicTrack = savedMusicTrack,
                addedAt = musicPackTrack.addedAt,
                position = editedTrackDto.position ?: musicPack.musicTracks.size
            ).apply {
                this.musicPack = musicPack
            }
        }

        musicPack.musicTracks.clear()
        musicPack.musicTracks.addAll(updatedMusicPackTracks)
        musicPack.updatedAt = now
        musicPackRepository.save(musicPack)
    }

    @Transactional
    fun removeTrackFromMusicPackByNumber(userId: Long, musicPackId: Long, trackNumber: Int): MusicPackWithContentDto {
        val musicPack = obtainMusicPackForUser(musicPackId, userId)
        val collection = musicPack.collection!!
        collection.checkUserPermissions(userId)

        val trackPosition = trackNumber - 1
        val musicPackTrack = musicPack.musicTracks.sortedBy { it.position }[trackPosition]
        musicPack.removeTrackFromMusicPack(userId, musicPackTrack)
        musicEntitiesDao.checkAndDeleteMusicTrack(musicPackTrack.musicTrack)
        val savedMusicPack = musicPackRepository.save(musicPack)
        return savedMusicPack.toMusicPackWithContentDto()
    }

    @Transactional
    fun removeTrackFromMusicPackByTrackId(userId: Long, musicPackId: Long, trackId: Long) {
        val musicPack = obtainMusicPackForUser(musicPackId, userId)
        val collection = musicPack.collection!!
        collection.checkUserPermissions(userId)

        val musicPackTrack = musicPack.musicTracks.find { it.musicTrackId == trackId }
            ?: throw MusicTrackNotFoundException(userId, trackId)
        musicPack.removeTrackFromMusicPack(userId, musicPackTrack)
        musicEntitiesDao.checkAndDeleteMusicTrack(musicPackTrack.musicTrack)
        musicPackRepository.save(musicPack)
    }

    private fun MusicPack.removeTrackFromMusicPack(userId: Long, musicPackTrack: MusicPackTrack) {
        val now = LocalDateTime.now(clock)
        val collection = this.collection!!
        val musicTrack = musicPackTrack.musicTrack
        val musicPackExternalId = this.externalId
        if (musicPackExternalId != null) {
            externalSourceService.removeTrackFromMusicPack(userId, collection, musicPackExternalId, musicTrack)
        }

        this.musicTracks.remove(musicPackTrack)
        this.musicTracks.sortedBy { it.position }
            .forEachIndexed { index, o ->
                o.position = index
            }
        this.updatedAt = now
    }

    @Transactional
    fun changeMusicPackTrackPosition(userId: Long, musicPackId: Long, trackId: Long, newPosition: Int) {
        val musicPack = obtainMusicPackForUser(musicPackId, userId)
        val collection = musicPack.collection!!
        collection.checkUserPermissions(userId)

        val now = LocalDateTime.now(clock)
        musicPack.musicTracks
            .sortedBy { it.position }
            .apply {
                val oldPosition = this.indexOfFirst { it.musicTrackId == trackId }
                if (oldPosition == newPosition)
                    return
                val shift = if (oldPosition > newPosition) 1 else -1
                val shiftRange = if (oldPosition > newPosition) newPosition..oldPosition else oldPosition..newPosition
                this.forEachIndexed { idx, mpt ->
                    if (mpt.musicTrackId == trackId) {
                        mpt.position = newPosition
                    } else if (idx in shiftRange) {
                        mpt.position = idx + shift
                    } else {
                        mpt.position = idx
                    }
                }
            }.sortedBy { it.position }
        musicPack.updatedAt = now
        musicPackRepository.save(musicPack)
    }

    private fun obtainMusicPackForUser(musicPackId: Long?, userId: Long): MusicPack {
        val musicPack = musicPackId?.let { musicPackRepository.findActualByIdAndUserIdOrThrow(it, userId) }
            ?: findUserDefaultMusicPack(userId)
        if (!musicPack.editable)
            throw MusicPackNotEditableException(userId, musicPackId)
        return musicPack
    }

    fun findUserDefaultMusicPack(userId: Long): MusicPack {
        val collectionIds = musicCollectionRepository.findActualIdsByUserIdAndType(userId, MONTH_PLAYLIST_COLLECTION_TYPE)
        val monthlyPlaylistTitle = LocalDateTime.now(clock).format(MONTH_PLAYLIST_TITLE_FORMATTER)
        return musicPackRepository.findActualByCollectionIds(collectionIds)
            .find { it.title == monthlyPlaylistTitle }
            ?: throw MusicPackNotFoundException(userId)
    }

    private fun MusicPackRepository.findActualByIdAndUserIdOrThrow(musicPackId: Long, userId: Long): MusicPack {
        return this.findActualByIdAndUserId(musicPackId, userId)
            ?: throw MusicPackNotFoundException(userId, musicPackId)
    }

    private fun UserCollection.checkPermissions() {
        if (!isOwner || !canWrite)
            throw MusicCollectionAccessDeniedException(userId)
    }

    private fun MusicCollection.checkUserPermissions(userId: Long) {
        val userCollection = userCollections.find { it.userId == userId }!!
        userCollection.checkPermissions()
    }


    private fun List<MusicPack>.toMusicPackDto() = this.map { it.toMusicPackDto() }

    private fun MusicPack.toMusicPackDto() =
        MusicPackDto(
            id = id,
            externalId = externalId,
            collectionId = collectionId,
            title = title,
            description = description,
            addedAt = addedAt,
            updatedAt = updatedAt,
            tags = tags.map { it.value },
            pageUrl = pageUrl,
            coverUrl = coverUrl,
            editable = editable
        )

    private fun Tag.toDto() =
        TagDto(value, 1)


    private fun MusicPack.toMusicPackWithContentDto() =
        MusicPackWithContentDto(
            musicPack = this.toMusicPackDto(),
            artists = musicArtists.sortedBy { it.id }.toDto(),
            albums = musicAlbums.sortedBy { it.id }.toDto(),
            tracks = musicTracks.sortedBy { it.addedAt }.toDto()
        )

    private fun List<MusicPackArtist>.toDto(): List<MusicArtistDto> =
        sortedBy { it.position }.map { it.musicArtist.toDto() }

    @JvmName("toDtoMusicAlbum")
    private fun List<MusicPackAlbum>.toDto(): List<MusicAlbumDto> =
        sortedBy { it.position }.map { it.musicAlbum.toDto() }

    private fun MusicPackEntitiesWrapper.toMusicPack(
        collectionId: Long,
        editable: Boolean,
        tags: Set<Tag>
    ): MusicPack {
        val musicPackDto = this.musicPackWithContentDto.musicPack
        return MusicPack(
            externalId = musicPackDto.externalId,
            collectionId = collectionId,
            title = musicPackDto.title,
            description = musicPackDto.description,
            coverUrl = musicPackDto.coverUrl,
            pageUrl = musicPackDto.pageUrl,
            tags = tags.toMutableSet(),
            addedAt = musicPackDto.addedAt,
            updatedAt = musicPackDto.updatedAt,
            removed = musicPackDto.removed!!,
            editable = musicPackDto.editable ?: editable,
            musicArtists = this.formArtists(),
            musicAlbums = this.formAlbums(),
            musicTracks = this.formTracks(),
        )
    }

    private fun List<MusicPackEntitiesWrapper>.toMusicPacks(
        collectionId: Long,
        editable: Boolean,
        tags: Set<Tag>
    ): List<MusicPack> {
        val tagsMap = tags.associateBy { it.value }
        return map {
            val musicPackTags = it.musicPackWithContentDto.musicPack.tags.mapNotNullTo(mutableSetOf()) { tagsMap[it] }
            it.toMusicPack(collectionId, editable, musicPackTags)
        }
    }

    private fun MusicCollection.isMusicPackEditable(): Boolean {
        if (externalId == null) {
            return true
        }
        if (type == MusicCollectionType.RAINDROPS)
            return false
        if (type == MusicCollectionType.YANDEX) {
            if (externalId!!.endsWith("/albums"))
                return false
            if (externalId!!.endsWith("/playlists"))
                return true
        }
        return false
    }

    private fun MusicPackEntitiesWrapper.formArtists(): MutableSet<MusicPackArtist> {
        return this.musicArtists.mapIndexedTo(hashSetOf()) { index, entity ->
            MusicPackArtist(
                musicArtistId = entity.id!!,
                musicArtist = entity,
                position = index
            )
        }
    }

    private fun MusicPackEntitiesWrapper.formAlbums(): MutableSet<MusicPackAlbum> {
        return this.musicAlbums.mapIndexedTo(hashSetOf()) { index, entity ->
            MusicPackAlbum(
                musicAlbumId = entity.id!!,
                musicAlbum = entity,
                position = index
            )
        }
    }

    private fun MusicPackEntitiesWrapper.formTracks(): MutableSet<MusicPackTrack> {
        val musicTracksMap = this.musicTracks.associateBy { it.formKey() }
        val now = LocalDateTime.now(clock)
        return this.musicPackWithContentDto.tracks
            .mapTo(hashSetOf()) { musicTrackDto ->
                val musicTrackEntity = musicTracksMap[musicTrackDto.formKey()]!!
                MusicPackTrack(
                    musicTrackId = musicTrackEntity.id!!,
                    musicTrack = musicTrackEntity,
                    addedAt = musicTrackDto.addedAt ?: now,
                    position = musicTrackDto.position ?: 0
                )
            }
    }


    private fun MusicEntitiesDao.fetchTrackById(trackId: Long, userId: Long?): MusicTrack = this.fetchTrackById(trackId)
        ?: throw MusicTrackNotFoundException(userId, trackId)

    fun Page<Array<Any>>.mapToIds(): Page<Long> = map { it[0] as Long }
}