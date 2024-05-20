package ru.push.musicfeed.platform.external.telegram.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCallbackQuery
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.MusicCollectionCommandFacade
import ru.push.musicfeed.platform.application.ServiceCommandFacade
import ru.push.musicfeed.platform.application.UserSettingsCommandFacade
import ru.push.musicfeed.platform.application.dto.ActionEventDto
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.external.telegram.AbstractDispatcherHandlers
import ru.push.musicfeed.platform.external.telegram.BotProvider
import ru.push.musicfeed.platform.external.telegram.Callback
import ru.push.musicfeed.platform.external.telegram.HandleActionMessage
import ru.push.musicfeed.platform.external.telegram.deleteMessageFromChat
import ru.push.musicfeed.platform.external.telegram.formUserSettingsMessage
import ru.push.musicfeed.platform.external.telegram.formatSettingsMessage
import ru.push.musicfeed.platform.external.telegram.getChatId
import ru.push.musicfeed.platform.external.telegram.getMessageId
import ru.push.musicfeed.platform.external.telegram.getUserExternalId
import ru.push.musicfeed.platform.external.telegram.markIfCollectionSelected
import ru.push.musicfeed.platform.external.telegram.sendMessageToChat
import ru.push.musicfeed.platform.external.telegram.sendOrReplaceMessage
import ru.push.musicfeed.platform.util.isUrl
import ru.push.musicfeed.platform.util.splitBySpaces

@Component
class SettingsHandlers(
    serviceCommandFacade: ServiceCommandFacade,
    val userSettingsCommandFacade: UserSettingsCommandFacade,
    val musicCollectionCommandFacade: MusicCollectionCommandFacade,
) : AbstractDispatcherHandlers(serviceCommandFacade) {

    override fun getMessageHandlers(): Map<ActionEventType, HandleActionMessage> {
        val map: MutableMap<ActionEventType, HandleActionMessage> = mutableMapOf()
        map[ActionEventType.REQUEST_ADD_COLLECTION] = {
            addMusicCollection()
        }
        map[ActionEventType.REQUEST_COLLECTION_CHANNEL_BINDING] = { actionEventDto: ActionEventDto? ->
            collectionChannelBinding(actionEventDto!!)
        }
        map[ActionEventType.REQUEST_YANDEX_AUTH] = {
            obtainYandexUserTokenByAuthCode()
        }
        map[ActionEventType.REQUEST_DELETE_TRACK_DATA] = { actionEventDto: ActionEventDto? ->
            deleteTrackData(actionEventDto!!)
        }
        map[ActionEventType.REQUEST_DELETE_MUSIC_PACK_TRACKS_DATA] = { actionEventDto: ActionEventDto? ->
            deleteMusicPackTracksData(actionEventDto!!)
        }
        return map
    }

//    override fun getCommandHandlers(): Map<Command, HandleCommand> = mapOf(
//    )

    override fun getCallbackQueryHandlers(): Map<Callback, HandleCallbackQuery> = mapOf(
        Callback.REQUEST_ADD_COLLECTION to {
            requestAddCollection()
        },
        Callback.REQUEST_COLLECTIONS_FOR_SETTINGS to {
            requestCollectionsForSettings()
        },
        Callback.REQUEST_BIND_YANDEX to {
            requestBindYandex()
        },
        Callback.REQUEST_DELETE_TRACK_DATA to {
            requestDeleteTrackData()
        },
        Callback.REQUEST_DELETE_MUSIC_PACK_TRACKS_DATA to {
            requestDeleteMusicPackTracksData()
        },

        Callback.REQUEST_COLLECTION_SETTINGS to {
            requestCollectionSettings()
        },
        Callback.SELECT_COLLECTION to {
            markUserCollectionSelected()
        },
        Callback.REMOVE_COLLECTION to {
            removeUserCollection()
        },
        Callback.REQUEST_COLLECTION_CHANNEL_BINDING to {
            requestCollectionChannelBinding()
        },
        Callback.GET_SETTINGS to {
            getSettings()
        }
    )


    private fun CommandHandlerEnvironment.getSettings() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        executeWithTry(bot, chatId, userExternalId) {
            getSettings(bot, chatId, userExternalId)
        }
    }

    private fun CallbackQueryHandlerEnvironment.getSettings() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        executeWithTry(bot, chatId, userExternalId) {
            getSettings(bot, chatId, userExternalId, getMessageId())
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestAddCollection() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        executeWithTry(bot, chatId, userExternalId) {
            serviceCommandFacade.registerActionEvent(
                type = ActionEventType.REQUEST_ADD_COLLECTION,
                userExternalId = userExternalId
            )
            bot.sendMessageToChat(
                chatId,
                "Отправьте название для новой коллекции или ссылку на внешний источник"
            )
        }
    }

    private fun MessageHandlerEnvironment.addMusicCollection() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageText = message.text!!

        executeWithTry(bot, chatId, userExternalId) {
            if (messageText.isUrl()) {
                userSettingsCommandFacade.addUserCollectionFromExternalSource(userExternalId, messageText)
            } else {
                userSettingsCommandFacade.addUserCollectionLocal(userExternalId, messageText.trim())
            }
            getSettings(bot, chatId, userExternalId)
        }
    }

    private fun MessageHandlerEnvironment.collectionChannelBinding(actionEvent: ActionEventDto) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageText = message.text!!

        executeWithTry(bot, chatId, userExternalId) {
            val collectionId = actionEvent.collectionId!!
            userSettingsCommandFacade.userCollectionChannelBinding(userExternalId, collectionId, messageText)
            checkAndHandleRequestActionEvent(
                requestActionEventType = ActionEventType.REQUEST_COLLECTION_CHANNEL_BINDING,
                targetActionEventType = ActionEventType.BOUND_COLLECTION_CHANNEL,
                targetCollectionId = collectionId,
            )
            displayCollectionSettings(
                bot = bot,
                chatId = chatId,
                userExternalId = userExternalId,
                collectionId = collectionId
            )
        }
    }

    private fun MessageHandlerEnvironment.obtainYandexUserTokenByAuthCode() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageText = message.text!!

        executeWithTry(bot, chatId, userExternalId) {
            if (messageText.isNotBlank()) {
                userSettingsCommandFacade.obtainYandexUserTokenByAuthCode(userExternalId, messageText)
            }
            getSettings(bot, chatId, userExternalId)
        }
    }

    private fun MessageHandlerEnvironment.deleteTrackData(actionEventDto: ActionEventDto) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageText = message.text!!

        executeWithTry(bot, chatId, userExternalId) {
            val trackIds = messageText.splitBySpaces().map { it.toLong() }
            serviceCommandFacade.removeTrackData(userExternalId, trackIds)
            actionEventDto.messageId?.let { bot.deleteMessageFromChat(chatId, it) }
            bot.sendMessageToChat(chatId = chatId, text = "Данные треков успешно удалены")
        }
    }

    private fun MessageHandlerEnvironment.deleteMusicPackTracksData(actionEventDto: ActionEventDto) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageText = message.text!!

        executeWithTry(bot, chatId, userExternalId) {
            val musicPackId = messageText.splitBySpaces()[0].toLong()
            serviceCommandFacade.removeMusicPackTracksData(userExternalId, musicPackId)
            actionEventDto.messageId?.let { bot.deleteMessageFromChat(chatId, it) }
            bot.sendMessageToChat(chatId = chatId, text = "Данные записи успешно удалены")
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestCollectionsForSettings() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            val userCollectionsSettings = userSettingsCommandFacade.getUserCollectionsSettings(userExternalId)
            val buttons = userCollectionsSettings.map {
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = it.title?.markIfCollectionSelected(it.selected)!!,
                        callbackData = formCallbackData(Callback.REQUEST_COLLECTION_SETTINGS, it.collectionId)
                    )
                )
            }
            bot.sendMessageToChat(
                chatId = chatId,
                text = "Выберите коллекцию для настройки",
                replyMarkup = InlineKeyboardMarkup.create(buttons)
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestBindYandex() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            val url = userSettingsCommandFacade.obtainYandexAuthorizationPageUrl(userExternalId)
            bot.sendMessageToChat(
                chatId,
                "Перейдите по ссылке ниже, подтвердите вход в аккаунт Яндекс.Музыки и пришлите " +
                        "боту полученный code из адресной строки браузера после перевода " +
                        "на страницу Яндекс.Музыки, чтобы завершить авторизацию",
                InlineKeyboardMarkup.create(
                    listOf(
                        InlineKeyboardButton.Url("Перейти на страницу авторизации", url)
                    )
                )
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestDeleteTrackData() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            val message = bot.sendMessageToChat(chatId, "Отправьте список ИД треков (через пробел) для удаления данных\n" +
                    "⚠️ данные будут удалены безвозвратно")
            serviceCommandFacade.registerActionEvent(
                type = ActionEventType.REQUEST_DELETE_TRACK_DATA,
                userExternalId = userExternalId,
                messageId = message.messageId,
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestDeleteMusicPackTracksData() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            val message = bot.sendMessageToChat(chatId, "Отправьте ИД записи для удаления данных всех связанных треков\n" +
                    "⚠️ данные будут удалены безвозвратно")
            serviceCommandFacade.registerActionEvent(
                type = ActionEventType.REQUEST_DELETE_MUSIC_PACK_TRACKS_DATA,
                userExternalId = userExternalId,
                messageId = message.messageId,
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestCollectionSettings() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val collectionId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            displayCollectionSettings(
                bot = bot,
                chatId = chatId,
                userExternalId = userExternalId,
                replacedMessageId = messageId,
                collectionId = collectionId
            )
        }
    }

    private fun displayCollectionSettings(bot: Bot, chatId: Long, userExternalId: Long, replacedMessageId: Long? = null, collectionId: Long) {
        val collectionInfo = musicCollectionCommandFacade.getCollection(userExternalId, collectionId)
        val buttons = InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = Callback.SELECT_COLLECTION.title,
                    callbackData = formCallbackData(Callback.SELECT_COLLECTION, collectionId)
                ),
                InlineKeyboardButton.CallbackData(
                    text = Callback.REMOVE_COLLECTION.title,
                    callbackData = formCallbackData(Callback.REMOVE_COLLECTION, collectionId)
                ),
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = Callback.REQUEST_COLLECTION_CHANNEL_BINDING.title,
                    callbackData = formCallbackData(Callback.REQUEST_COLLECTION_CHANNEL_BINDING, collectionId)
                ),
            )
        )
        bot.sendOrReplaceMessage(
            chatId = chatId,
            replacedMessageId = replacedMessageId,
            text = collectionInfo.formatSettingsMessage(),
            replyMarkup = buttons
        )
    }

    private fun CallbackQueryHandlerEnvironment.markUserCollectionSelected() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val collectionId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            userSettingsCommandFacade.markUserCollectionSelected(userExternalId, collectionId)
            bot.deleteMessage(ChatId.fromId(chatId), messageId)
            getSettings(bot, chatId, userExternalId)
        }
    }

    private fun CallbackQueryHandlerEnvironment.removeUserCollection() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val collectionId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            userSettingsCommandFacade.removeUserCollection(userExternalId, collectionId)
            bot.deleteMessage(ChatId.fromId(chatId), messageId)
            getSettings(bot, chatId, userExternalId)
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestCollectionChannelBinding() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val collectionId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            val message = bot.sendMessageToChat(chatId, "Отправьте название канала")
            serviceCommandFacade.registerActionEvent(
                type = ActionEventType.REQUEST_COLLECTION_CHANNEL_BINDING,
                userExternalId = userExternalId,
                messageId = message.messageId,
                collectionId = collectionId
            )
        }
    }

    internal fun getSettings(bot: Bot, chatId: Long, userExternalId: Long, replacedMessageId: Long? = null) {
        val userSettings = userSettingsCommandFacade.getUserSettings(userExternalId)
        val buttons: MutableList<List<InlineKeyboardButton>> = mutableListOf(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = Callback.REQUEST_COLLECTIONS_FOR_SETTINGS.title,
                    callbackData = formCallbackData(Callback.REQUEST_COLLECTIONS_FOR_SETTINGS)
                ),
            ),
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = Callback.REQUEST_ADD_COLLECTION.title,
                    callbackData = formCallbackData(Callback.REQUEST_ADD_COLLECTION)
                )
            ),
        )

        if (userSettings.isSystemAdminUser) {
            buttons.addAll(
                listOf(
                    listOf(
                        InlineKeyboardButton.CallbackData(
                            text = Callback.REQUEST_BIND_YANDEX.title,
                            callbackData = formCallbackData(Callback.REQUEST_BIND_YANDEX)
                        ),
                    ),
                    listOf(
                        InlineKeyboardButton.CallbackData(
                            text = Callback.REQUEST_DELETE_TRACK_DATA.title,
                            callbackData = formCallbackData(Callback.REQUEST_DELETE_TRACK_DATA)
                        ),
                    ),
                    listOf(
                        InlineKeyboardButton.CallbackData(
                            text = Callback.REQUEST_DELETE_MUSIC_PACK_TRACKS_DATA.title,
                            callbackData = formCallbackData(Callback.REQUEST_DELETE_MUSIC_PACK_TRACKS_DATA)
                        ),
                    ),
                )
            )
        }

        bot.sendOrReplaceMessage(
            chatId = chatId,
            replacedMessageId = replacedMessageId,
            text = userSettings.formUserSettingsMessage(),
            replyMarkup = InlineKeyboardMarkup.create(buttons)
        )
    }

}