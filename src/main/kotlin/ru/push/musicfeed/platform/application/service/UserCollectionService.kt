package ru.push.musicfeed.platform.application.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.application.CollectionAlreadyExistException
import ru.push.musicfeed.platform.application.CollectionNotEditable
import ru.push.musicfeed.platform.application.CollectionNotFoundException
import ru.push.musicfeed.platform.application.UserCollectionsMaxCountObtainedException
import ru.push.musicfeed.platform.application.NoSelectedCollectionException
import ru.push.musicfeed.platform.data.model.music.MusicCollection
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import ru.push.musicfeed.platform.data.model.UserCollection
import ru.push.musicfeed.platform.data.repo.MusicCollectionRepository
import ru.push.musicfeed.platform.data.repo.MusicPackRepository
import ru.push.musicfeed.platform.data.repo.UserCollectionRepository

@Service
class UserCollectionService(
    private val userCollectionRepository: UserCollectionRepository,
    private val musicCollectionRepository: MusicCollectionRepository,
    private val musicPackRepository: MusicPackRepository
) {
    companion object {
        private const val USER_COLLECTIONS_MAX_COUNT = 10
    }

    private val logger = KotlinLogging.logger {}

    fun fetchUserCollections(userId: Long): List<UserCollection> {
        return userCollectionRepository.findActualByUserId(userId)
    }

    fun fetchUserCollection(userId: Long, collectionId: Long): UserCollection {
        return userCollectionRepository.findActualByUserIdAndCollectionId(userId, collectionId)
            ?: throw CollectionNotFoundException(userId = userId, collectionId = collectionId)
    }

    fun fetchUserCollectionByMusicPackId(userId: Long, musicPackId: Long): UserCollection {
        return userCollectionRepository.findByUserIdAndMusicPackId(userId, musicPackId)
            ?: throw CollectionNotFoundException(userId = userId, musicPackId = musicPackId)
    }

    fun fetchUserCollectionByIdOrSelected(userId: Long, collectionId: Long?): UserCollection {
        return if (collectionId != null)
            fetchUserCollection(userId, collectionId)
        else
            fetchUserCollections(userId)
                .find { it.selected }
                ?: throw NoSelectedCollectionException(userId)
    }

    @Transactional
    fun addUserCollection(
        userId: Long,
        title: String?,
        type: MusicCollectionType? = MusicCollectionType.LOCAL,
        collectionExternalId: String? = null
    ): UserCollection {
        if ((collectionExternalId != null) &&
            musicCollectionRepository.existsByExternalIdAndRemovedFalse(collectionExternalId)
        ) {
            throw CollectionAlreadyExistException(collectionExternalId)
        }

        val userCollections = userCollectionRepository.findActualByUserId(userId)
        if (userCollections.size >= USER_COLLECTIONS_MAX_COUNT)
            throw UserCollectionsMaxCountObtainedException(userId)

        val collection = musicCollectionRepository.save(
            MusicCollection(
                title = title,
                externalId = collectionExternalId,
                type = type!!,
                isSynchronized = (collectionExternalId != null)
            )
        )
        logger.info { "Saved new collection: $collection}" }

        val userCollectionsForSave = userCollections.filter { it.selected }
            .toMutableList()
        userCollectionsForSave.forEach { it.selected = false }
        val userCollection = UserCollection(
            userId = userId,
            collectionId = collection.id!!,
            collection = collection,
            isOwner = true,
            canWrite = true,
            selected = true,
        )
        userCollectionsForSave.add(userCollection)

        userCollectionRepository.saveAll(userCollectionsForSave)

        return userCollection
    }

    @Transactional
    fun markUserCollectionSelected(userId: Long, collectionId: Long) {
        val userCollections = userCollectionRepository.findActualByUserId(userId)
        val userCollectionsForSave = userCollections.filter { it.selected }
            .toMutableList()
        userCollectionsForSave.forEach { it.selected = false }

        userCollections.find { it.collectionId == collectionId }
            ?.apply { selected = true }
            ?.takeIf { userCollectionsForSave.add(it) }

        userCollectionRepository.saveAll(userCollectionsForSave)
    }

    @Transactional
    fun removeUserCollection(userId: Long, collectionId: Long) {
        val userCollection = userCollectionRepository.findActualByUserIdAndCollectionId(userId, collectionId)
            ?: throw CollectionNotFoundException(userId, collectionId)
        if (userCollection.selected) {
            userCollectionRepository.findActualByUserId(userId)
                .firstOrNull { it.collectionId != collectionId }
                ?.let {
                    userCollectionRepository.setSelectedTrueByUserIdAndCollectionId(it.userId, it.collectionId)
                }
        }
        if (userCollection.isOwner) {
            val collection = userCollection.collection!!
            musicPackRepository.setRemovedTrueByCollectionId(collection.id!!)
            collection.removed = true
            musicCollectionRepository.save(collection)
        }
        userCollectionRepository.delete(userCollection)
    }

    @Transactional
    fun userCollectionChannelBinding(userId: Long, collectionId: Long, channelName: String) {
        val userCollection = userCollectionRepository.findActualByUserIdAndCollectionId(userId, collectionId)
            ?: throw CollectionNotFoundException(userId, collectionId)
        if (!userCollection.isOwner || !userCollection.canWrite)
            throw CollectionNotEditable(userId, collectionId)

        userCollection.channelName = channelName
        userCollectionRepository.save(userCollection)
    }
}