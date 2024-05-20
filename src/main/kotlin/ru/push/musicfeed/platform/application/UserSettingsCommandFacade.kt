package ru.push.musicfeed.platform.application

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.dto.UserCollectionSettingsDto
import ru.push.musicfeed.platform.application.dto.UserSettingsDto
import ru.push.musicfeed.platform.application.service.ActionEventService
import ru.push.musicfeed.platform.application.service.ExternalSourceService
import ru.push.musicfeed.platform.application.service.UserCollectionService
import ru.push.musicfeed.platform.application.service.UserService
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import ru.push.musicfeed.platform.data.model.TokenType
import ru.push.musicfeed.platform.external.source.yandex.YandexMusicAuthExternalSourceClient
import ru.push.musicfeed.platform.util.LogFunction

@Component
class UserSettingsCommandFacade(
    private val userService: UserService,
    private val userCollectionService: UserCollectionService,
    private val actionEventService: ActionEventService,
    private val externalSourceService: ExternalSourceService,
    private val yandexMusicAuthClient: YandexMusicAuthExternalSourceClient,
) {

    private val logger = KotlinLogging.logger {}

    @LogFunction
    fun getUserSettings(userExternalId: Long): UserSettingsDto {
        val userCollectionSettings = getUserCollectionsSettings(userExternalId)
        val yandexMusicAccountName = userService.getUserYandexMusicAccountName(userExternalId)
        return UserSettingsDto(
            userCollections = userCollectionSettings,
            yandexMusicAccountName = yandexMusicAccountName,
            isSystemAdminUser = userService.isSystemAdminUser(userExternalId)
        )
    }

    @LogFunction
    fun getUserCollectionsSettings(userExternalId: Long): List<UserCollectionSettingsDto> {
        val user = userService.findUserByExternalId(userExternalId)
        return userCollectionService.fetchUserCollections(user.id!!)
            .map {
                val collection = it.collection!!
                UserCollectionSettingsDto(
                    collection.id!!,
                    collection.externalId,
                    collection.title,
                    it.selected
                )
            }
    }

    @LogFunction
    fun addUserCollectionFromExternalSource(userExternalId: Long, externalSourceUrl: String) {
        val user = userService.findUserByExternalId(userExternalId)
        val collectionInfo = externalSourceService.obtainCollectionInfoBySourceUrl(user.id!!, externalSourceUrl)
        val userCollection = userCollectionService.addUserCollection(
            userId = user.id!!,
            title = collectionInfo.title,
            type = collectionInfo.type,
            collectionExternalId = collectionInfo.externalId
        )
        actionEventService.registerActionEvent(
            type = ActionEventType.ADDED_COLLECTION,
            userId = user.id!!,
            collectionId = userCollection.collectionId
        )
    }

    @LogFunction
    fun addUserCollectionLocal(userExternalId: Long, title: String) {
        if (title.isBlank())
            throw InvalidCollectionCreateParameterException()
        val user = userService.findUserByExternalId(userExternalId)
        val userCollection = userCollectionService.addUserCollection(
            userId = user.id!!,
            title = title,
            type = MusicCollectionType.LOCAL
        )
        actionEventService.registerActionEvent(
            type = ActionEventType.ADDED_COLLECTION,
            userId = user.id!!,
            collectionId = userCollection.collectionId
        )
    }

    @LogFunction
    fun markUserCollectionSelected(userExternalId: Long, collectionId: Long) {
        val user = userService.findUserByExternalId(userExternalId)
        userCollectionService.markUserCollectionSelected(user.id!!, collectionId)
    }

    @LogFunction
    fun removeUserCollection(userExternalId: Long, collectionId: Long) {
        val user = userService.findUserByExternalId(userExternalId)
        userCollectionService.removeUserCollection(user.id!!, collectionId)
    }

    @LogFunction
    fun userCollectionChannelBinding(userExternalId: Long, collectionId: Long, channelName: String) {
        val user = userService.findUserByExternalId(userExternalId)
        userCollectionService.userCollectionChannelBinding(user.id!!, collectionId, channelName)
    }

    @LogFunction
    fun obtainYandexAuthorizationPageUrl(userExternalId: Long): String {
        val user = userService.findUserByExternalId(userExternalId)
        actionEventService.registerActionEvent(type = ActionEventType.REQUEST_YANDEX_AUTH, userId = user.id!!)
        return yandexMusicAuthClient.obtainAuthorizationPageUrl()
    }

    @LogFunction
    fun obtainYandexUserTokenByAuthCode(userExternalId: Long, authCode: String) {
        val user = userService.findUserByExternalId(userExternalId)
        val userId = user.id!!
        val userTokenDto = yandexMusicAuthClient.obtainUserTokenByAuthCode(authCode)
        externalSourceService.saveUserToken(userId, TokenType.YANDEX, userTokenDto)
        actionEventService.registerActionEvent(type = ActionEventType.YANDEX_AUTH_SUCCESS, userId = userId)
    }
}