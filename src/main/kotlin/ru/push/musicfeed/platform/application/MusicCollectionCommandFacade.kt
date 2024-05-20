package ru.push.musicfeed.platform.application

import mu.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.dto.CollectionInfoDto
import ru.push.musicfeed.platform.application.dto.CollectionWithContentDto
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.application.dto.MusicPackWithContentDto
import ru.push.musicfeed.platform.application.dto.AddTrackToMusicPackResultDto
import ru.push.musicfeed.platform.application.dto.MusicPackTrackListDto
import ru.push.musicfeed.platform.application.dto.MusicPackTracksFileInfo
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.application.dto.TagDto
import ru.push.musicfeed.platform.application.dto.TrackFileInfoDto
import ru.push.musicfeed.platform.application.service.ActionEventService
import ru.push.musicfeed.platform.application.service.MusicPackService
import ru.push.musicfeed.platform.application.service.UserCollectionService
import ru.push.musicfeed.platform.application.service.UserSearchRequestService
import ru.push.musicfeed.platform.application.service.UserService
import ru.push.musicfeed.platform.application.service.track.TrackLocalFileService
import ru.push.musicfeed.platform.application.service.track.TrackSearchService
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.data.model.UserCollection
import ru.push.musicfeed.platform.util.LogFunction
import ru.push.musicfeed.platform.util.isUrl
import ru.push.musicfeed.platform.util.normalizeSearchText
import ru.push.musicfeed.platform.util.splitBySpaces

@Component
class MusicCollectionCommandFacade(
    private val musicPackService: MusicPackService,
    private val userService: UserService,
    private val userCollectionService: UserCollectionService,
    private val trackLocalFileService: TrackLocalFileService,
    private val trackSearchService: TrackSearchService,
    private val actionEventService: ActionEventService,
    private val userSearchRequestService: UserSearchRequestService,
) {

    private val logger = KotlinLogging.logger {}

    @LogFunction
    fun getMusicPack(musicPackId: Long, userExternalId: Long): MusicPackDto {
        val user = userService.findUserByExternalId(userExternalId)
        return musicPackService.fetchMusicPackById(musicPackId, user.id!!)
    }

    @LogFunction(displayResult = false)
    fun getMusicPackTrackData(userExternalId: Long, musicPackId: Long, musicTrackId: Long): MusicTrackDto {
        val user = userService.findUserByExternalId(userExternalId)
        return musicPackService.fetchMusicTrackData(musicTrackId, user.id!!)
    }

    @LogFunction(displayResult = false)
    fun getMusicPackTrackList(musicPackId: Long, userExternalId: Long, page: Int, size: Int): MusicPackTrackListDto {
        val user = userService.findUserByExternalId(userExternalId)
        return musicPackService.fetchMusicPackTrackList(musicPackId, user.id!!, page, size)
    }

    @LogFunction(displayResult = false)
    fun addTrackToMusicPack(
        userExternalId: Long,
        requestMessage: String,
        musicPackId: Long? = null,
    ): AddTrackToMusicPackResultDto {
        val user = userService.findUserByExternalId(userExternalId)
        val userId = user.id!!
        val sourceUrls = requestMessage.splitBySpaces()
        val result = if (sourceUrls.firstOrNull()?.isUrl() == true) {
            musicPackService.addTrackToMusicPackBySourceUrl(userId, musicPackId, sourceUrls)
        } else {
            val normalizedSearchText = requestMessage.normalizeSearchText()
            processMusicTrackSearch(userId, musicPackId, normalizedSearchText)
        }
        if (result.added) {
            actionEventService.registerActionEvent(
                type = ActionEventType.ADDED_MUSIC_TRACK_TO_MUSIC_PACK,
                userId = userId,
                musicPackId = musicPackId
            )
        }
        return result
    }

    // todo: move to separate service
    private fun processMusicTrackSearch(
        userId: Long,
        musicPackId: Long? = null,
        searchText: String
    ): AddTrackToMusicPackResultDto {
        val finalMusicPackId = musicPackId ?: musicPackService.findUserDefaultMusicPack(userId).id!!
        val collection = userCollectionService.fetchUserCollectionByMusicPackId(userId, finalMusicPackId).collection!!
        val searchedTracks = trackSearchService.searchTrack(
            musicCollectionType = collection.type,
            searchText = searchText
        )
        if (searchedTracks.isEmpty())
            throw ExternalSourceSearchNotFoundException(collectionId = collection.id!!, searchText = searchText)

        val fullMatchTrack = if (searchedTracks.size == 1)
            searchedTracks[0]
        else
            searchedTracks.findFirstMatchByArtistsAndTitle(searchText)
        return if (fullMatchTrack != null) {
            musicPackService.addTrackToMusicPackById(userId, musicPackId, fullMatchTrack.id!!)
            AddTrackToMusicPackResultDto(added = true, tracks = listOf(fullMatchTrack))
        } else {
            AddTrackToMusicPackResultDto(added = false, tracks = searchedTracks)
        }
    }

    private fun List<MusicTrackDto>.findFirstMatchByArtistsAndTitle(searchText: String): MusicTrackDto? {
        return this.find { trackDto ->
            val trackKey = trackDto.artists?.let { "${trackDto.artists.joinToString { it.name }} ${trackDto.title}" }
                ?: trackDto.title
            searchText == trackKey
        } ?: this.find { trackDto ->
            val trackKey = trackDto.artists?.let { "${trackDto.title} ${trackDto.artists.joinToString { it.name }}" }
                ?: trackDto.title
            searchText == trackKey
        }
    }

    @LogFunction(displayResult = false)
    fun addTrackToMusicPackById(
        userExternalId: Long,
        musicPackId: Long? = null,
        trackId: Long
    ): MusicTrackDto {
        val user = userService.findUserByExternalId(userExternalId)
        val result = musicPackService.addTrackToMusicPackById(user.id!!, musicPackId, trackId)
        actionEventService.registerActionEvent(
            type = ActionEventType.ADDED_MUSIC_TRACK_TO_MUSIC_PACK,
            userId = user.id!!,
            musicPackId = musicPackId
        )
        return result
    }

    @LogFunction(displayResult = false)
    fun removeTrackFromMusicPackByNumber(
        userExternalId: Long,
        musicPackId: Long,
        trackNumber: Int
    ): MusicPackWithContentDto {
        val user = userService.findUserByExternalId(userExternalId)
        val musicPack = musicPackService.removeTrackFromMusicPackByNumber(user.id!!, musicPackId, trackNumber)
        actionEventService.registerActionEvent(
            type = ActionEventType.REMOVED_MUSIC_TRACK_FROM_MUSIC_PACK,
            userId = user.id!!,
            musicPackId = musicPackId
        )
        return musicPack
    }

    @LogFunction(displayResult = false)
    fun removeTrackFromMusicPackByTrackId(
        userExternalId: Long,
        musicPackId: Long,
        trackId: Long
    ) {
        val user = userService.findUserByExternalId(userExternalId)
        musicPackService.removeTrackFromMusicPackByTrackId(user.id!!, musicPackId, trackId)
        actionEventService.registerActionEvent(
            type = ActionEventType.REMOVED_MUSIC_TRACK_FROM_MUSIC_PACK,
            userId = user.id!!,
            musicPackId = musicPackId
        )
    }

    @LogFunction
    fun storeMusicPackLocalFileInfo(userExternalId: Long, musicPackId: Long, trackLocalFileId: Long) {
        val user = userService.findUserByExternalId(userExternalId)
        val musicPack = musicPackService.fetchMusicPackById(musicPackId, user.id!!)
        musicPackService.storeMusicPackLocalFileInfo(musicPack.id!!, trackLocalFileId)
    }

    @LogFunction
    fun storeMusicTrackLocalFileInfo(userExternalId: Long, musicTrackId: Long, trackLocalFileId: Long) {
        val user = userService.findUserByExternalId(userExternalId)
        val trackData = musicPackService.fetchMusicTrackData(musicTrackId, user.id!!)
        musicPackService.storeTrackLocalFileInfoSource(trackData.id!!, trackLocalFileId)
    }

    @LogFunction(displayResult = false)
    fun editMusicPackTrackData(
        userExternalId: Long,
        musicPackId: Long,
        musicTrackId: Long,
        newTrackTitle: String,
        newArtistName: String? = null,
        newAlbumName: String? = null
    ) {
        val user = userService.findUserByExternalId(userExternalId)
        musicPackService.replaceMusicPackTrackWithNewTrackCreation(user.id!!, musicPackId, musicTrackId, newTrackTitle, newArtistName, newAlbumName)
    }

    @LogFunction(displayResult = false)
    fun editMusicPackTracklistData(
        userExternalId: Long,
        musicPackId: Long,
        newArtistName: String,
    ) {
        val user = userService.findUserByExternalId(userExternalId)
        musicPackService.replaceMusicPackTracklistArtist(user.id!!, musicPackId, newArtistName)
    }

    @LogFunction
    fun changeMusicPackTrackPosition(userExternalId: Long, musicPackId: Long, musicTrackId: Long, newPosition: Int) {
        val user = userService.findUserByExternalId(userExternalId)
        musicPackService.changeMusicPackTrackPosition(
            userId = user.id!!,
            musicPackId = musicPackId,
            trackId = musicTrackId,
            newPosition = newPosition
        )
    }

    @LogFunction
    fun getRandomMusicPack(userExternalId: Long): MusicPackDto {
        val user = userService.findUserByExternalId(userExternalId)
        return musicPackService.fetchRandomMusicPackByUserId(user.id!!)
    }

    @LogFunction
    fun getRecentMusicPacks(userExternalId: Long, page: Int, size: Int): Page<MusicPackDto> {
        val user = userService.findUserByExternalId(userExternalId)
        return musicPackService.fetchMusicPackPage(user.id!!, page, size)
    }

    @LogFunction
    fun searchMusicPacksByTags(userExternalId: Long, tags: List<String>, page: Int, size: Int): Page<MusicPackDto> {
        val user = userService.findUserByExternalId(userExternalId)
        return musicPackService.fetchMusicPackPageByTags(user.id!!, tags, page, size)
    }

    @LogFunction
    fun searchMusicPacksBySearchRequest(
        userExternalId: Long,
        collectionId: Long?,
        searchRequest: String? = null,
        page: Int,
        size: Int
    ): Page<MusicPackDto> {
        val user = userService.findUserByExternalId(userExternalId)
        val userId = user.id!!
        if (searchRequest != null) {
            userSearchRequestService.updateUserSearchRequest(userId, searchRequest)
        }
        val userSearchRequest = searchRequest
            ?: userSearchRequestService.obtainUserSearchRequestText(userId)
            ?: throw UserSearchRequestNotDefinedException(userId)
        return musicPackService.searchMusicPacksByTextRequest(userId, collectionId, userSearchRequest, page, size)
    }

    @LogFunction
    fun getTags(userExternalId: Long, page: Int, size: Int): Page<TagDto> {
        val user = userService.findUserByExternalId(userExternalId)
        return musicPackService.fetchTags(user.id!!, page, size)
    }

    @LogFunction
    fun addMusicPackFromExternalSource(userExternalId: Long, sourceUrl: String, collectionId: Long? = null): MusicPackDto {
        val user = userService.findUserByExternalId(userExternalId)
        val userCollection = userCollectionService.fetchUserCollectionByIdOrSelected(user.id!!, collectionId)
        val musicPack = musicPackService.createMusicPackFromExternalSource(sourceUrl, userCollection)
        actionEventService.registerActionEvent(
            type = ActionEventType.ADDED_MUSIC_PACK,
            userId = user.id!!,
            collectionId = userCollection.collectionId
        )
        return musicPack
    }

    @LogFunction
    fun addMusicPackLocal(userExternalId: Long, title: String, collectionId: Long? = null): MusicPackDto {
        val user = userService.findUserByExternalId(userExternalId)
        val userCollection = userCollectionService.fetchUserCollectionByIdOrSelected(user.id!!, collectionId)
        val musicPack = musicPackService.createMusicPackLocal(title, userCollection)
        actionEventService.registerActionEvent(
            type = ActionEventType.ADDED_MUSIC_PACK,
            userId = user.id!!,
            collectionId = userCollection.collectionId
        )
        return musicPack
    }

    @LogFunction
    fun getTrackFileInfoForMusicPack(userExternalId: Long, musicPackId: Long): TrackFileInfoDto? {
        val user = userService.findUserByExternalId(userExternalId)
        val musicPack = musicPackService.fetchMusicPackById(musicPackId = musicPackId, userId = user.id!!)
        return trackLocalFileService.obtainLocalTrackFileInfoByMusicPackId(musicPack.id!!)
    }

    @LogFunction
    fun getTrackFileInfoForMusicTrack(userExternalId: Long, musicTrackId: Long): TrackFileInfoDto? {
        val user = userService.findUserByExternalId(userExternalId)
        val trackLocalFileId = musicPackService.getTrackLocalFileIdByTrackId(user.id!!, musicTrackId)
        return trackLocalFileId?.let { trackLocalFileService.obtainLocalTrackFileInfo(it, musicTrackId) }
    }

    @LogFunction
    fun getTrackFileInfosForMusicPackTrackList(
        userExternalId: Long,
        musicPackId: Long,
        page: Int,
        size: Int,
        needCountChecks: Boolean = true
    ): MusicPackTracksFileInfo {
        val user = userService.findUserByExternalId(userExternalId)
        val musicPackTracks = musicPackService.fetchMusicPackTrackList(musicPackId, user.id!!, page, size)
        val musicPack = musicPackTracks.musicPack
        val musicPackTracksWithLocalFile = musicPackTracks.contentPage.content.filter { it.source.localFileId != null }
        if (needCountChecks && musicPackTracksWithLocalFile.size < musicPackTracks.contentPage.content.size) {
            logger.info {
                "Not all music pack tracks was downloaded, empty list will be returned for initiate download process flow" +
                        " (musicPackId=$musicPackId, page=$page, size=$size)"
            }
            return MusicPackTracksFileInfo(musicPack, emptyList())
        }
        val trackLocalFileIds = musicPackTracksWithLocalFile.map { it.source.localFileId!! }
        val trackFileInfos = trackLocalFileService.obtainLocalTrackFileInfoList(trackLocalFileIds)
        if (needCountChecks && trackFileInfos.size < musicPackTracksWithLocalFile.size) {
            logger.info {
                "Obtained track file info count is low that expected, empty list will be returned for initiate download process flow" +
                        " (musicPackId=$musicPackId, page=$page, size=$size)"
            }
            return MusicPackTracksFileInfo(musicPack, emptyList())
        }

        val trackLocalFileIdToMusicPackTrackPosition = musicPackTracksWithLocalFile
            .associateBy({
                it.source.localFileId!!
            }, {
                it.position ?: 0
            })
        return MusicPackTracksFileInfo(
            musicPack = musicPack,
            trackListFileInfo = trackFileInfos.sortedBy { trackLocalFileIdToMusicPackTrackPosition[it.trackLocalFileId] }
        )
    }

    @LogFunction
    fun removeMusicPack(musicPackId: Long, collectionId: Long, userExternalId: Long) {
        val user = userService.findUserByExternalId(userExternalId)
        musicPackService.removeMusicPack(musicPackId, user.id!!)
    }

    @LogFunction(displayResult = false)
    fun musicPackEditTitle(userExternalId: Long, musicPackId: Long, newTitle: String): MusicPackDto {
        val user = userService.findUserByExternalId(userExternalId)
        return musicPackService.musicPackEditTitle(musicPackId, user.id!!, newTitle)
    }

    @LogFunction(displayResult = false)
    fun musicPackEditDescription(userExternalId: Long, musicPackId: Long, newDescription: String): MusicPackDto {
        val user = userService.findUserByExternalId(userExternalId)
        return musicPackService.musicPackEditDescription(musicPackId, user.id!!, newDescription)
    }

    @LogFunction(displayResult = false)
    fun musicPackEditTags(userExternalId: Long, musicPackId: Long, newTags: String): MusicPackDto {
        val user = userService.findUserByExternalId(userExternalId)
        val tagsList = newTags.split(' ').map { it.trim().lowercase() }.filter { it.isNotBlank() }
        return musicPackService.musicPackEditTags(musicPackId, user.id!!, tagsList)
    }

    @LogFunction(displayResult = false)
    fun musicPackEditCover(userExternalId: Long, musicPackId: Long, newCoverUrl: String): MusicPackDto {
        val user = userService.findUserByExternalId(userExternalId)
        return musicPackService.musicPackEditCoverUrl(musicPackId, user.id!!, newCoverUrl)
    }

    @LogFunction
    fun getCollection(userExternalId: Long, collectionId: Long): CollectionInfoDto {
        val user = userService.findUserByExternalId(userExternalId)
        val collectionCount = musicPackService.countMusicPacksByCollectionId(collectionId)
        return userCollectionService.fetchUserCollection(user.id!!, collectionId).toDto(collectionCount)
    }

    @LogFunction
    fun getCollections(userExternalId: Long): List<CollectionInfoDto> {
        val user = userService.findUserByExternalId(userExternalId)
        val userCollection = userCollectionService.fetchUserCollections(user.id!!)
        val collectionIdToItemsCount = musicPackService.countMusicPacksByCollectionIds(
            userCollection.map { it.collectionId }
        )
        return userCollection.map {
            it.toDto(
                itemsCount = collectionIdToItemsCount[it.collectionId] ?: 0
            )
        }
    }

    @LogFunction
    fun getCollectionWithContent(
        userExternalId: Long,
        collectionId: Long? = null,
        page: Int,
        size: Int
    ): CollectionWithContentDto {
        val user = userService.findUserByExternalId(userExternalId)
        val userCollection = userCollectionService.fetchUserCollectionByIdOrSelected(user.id!!, collectionId)
        val targetCollectionId = userCollection.collectionId

        val collectionItemsCount = musicPackService.countMusicPacksByCollectionId(targetCollectionId)
        val musicPacks = musicPackService.fetchMusicPackPageByCollectionId(targetCollectionId, page, size)

        return CollectionWithContentDto(userCollection.toDto(collectionItemsCount), musicPacks)
    }

    private fun UserCollection.toDto(itemsCount: Long): CollectionInfoDto = CollectionInfoDto(
        id = this.collectionId,
        externalId = this.collection!!.externalId,
        title = this.collection!!.title,
        type = this.collection!!.type,
        selected = this.selected,
        channelName = this.channelName,
        itemsCount = itemsCount.toInt()
    )
}