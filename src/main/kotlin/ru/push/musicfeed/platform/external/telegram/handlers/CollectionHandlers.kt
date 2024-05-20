package ru.push.musicfeed.platform.external.telegram.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCallbackQuery
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCommand
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import mu.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.MusicCollectionCommandFacade
import ru.push.musicfeed.platform.application.ServiceCommandFacade
import ru.push.musicfeed.platform.application.dto.ActionEventDto
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.external.telegram.AbstractDispatcherHandlers
import ru.push.musicfeed.platform.external.telegram.BotProvider
import ru.push.musicfeed.platform.external.telegram.Callback
import ru.push.musicfeed.platform.external.telegram.Command
import ru.push.musicfeed.platform.external.telegram.HandleActionMessage
import ru.push.musicfeed.platform.external.telegram.MessageHelper
import ru.push.musicfeed.platform.external.telegram.deleteMessageFromChat
import ru.push.musicfeed.platform.external.telegram.formatMessage
import ru.push.musicfeed.platform.external.telegram.getChatId
import ru.push.musicfeed.platform.external.telegram.getMessageId
import ru.push.musicfeed.platform.external.telegram.getUserExternalId
import ru.push.musicfeed.platform.external.telegram.sendMessageToChat
import ru.push.musicfeed.platform.external.telegram.sendOrReplaceMessage
import ru.push.musicfeed.platform.util.cut
import ru.push.musicfeed.platform.util.getIndexOffset
import ru.push.musicfeed.platform.util.isUrl
import ru.push.musicfeed.platform.util.trimToPage

@Component
class CollectionHandlers(
    serviceCommandFacade: ServiceCommandFacade,
    val musicCollectionCommandFacade: MusicCollectionCommandFacade,
) : AbstractDispatcherHandlers(serviceCommandFacade) {

    companion object {
        private const val MUSIC_PACK_ADDED_SUCCESS_MESSAGE = "Запись добавлена в коллекцию"
        private const val DEFAULT_COLLECTIONS_RESPONSE_PAGE_SIZE = 10
        private const val PAGE_WIDE_SHIFT = 5
    }

    private val logger = KotlinLogging.logger {}

    override fun getMessageHandlers(): Map<ActionEventType, HandleActionMessage> {
        val map: MutableMap<ActionEventType, HandleActionMessage> = mutableMapOf()
        map[ActionEventType.REQUEST_ADD_MUSIC_PACK] = { actionEventDto: ActionEventDto? ->
            addMusicPack(actionEventDto?.collectionId)
        }
        map[ActionEventType.REQUEST_COLLECTION_CONTENT_PAGE_NUMBER] = { actionEventDto: ActionEventDto? ->
            getCollectionContentPage(actionEventDto!!)
        }
        return map
    }

    override fun getCommandHandlers(): Map<Command, HandleCommand> = mapOf(
        Command.GET_COLLECTIONS to {
            getCollections()
        },
        Command.ADD_MUSIC_PACK to {
            addMusicPack()
        },
    )

    override fun getCallbackQueryHandlers(): Map<Callback, HandleCallbackQuery> = mapOf(
        Callback.REQUEST_COLLECTION_CONTENT_PAGE_NUMBER to {
            requestCollectionContentPageNumber()
        },
        Callback.GET_COLLECTION_CONTENT_PAGE to {
            getCollectionContent()
        },
        Callback.GET_COLLECTION_CONTENT_WIDE_SHIFT_BACK to {
            getCollectionContent()
        },
        Callback.GET_COLLECTION_CONTENT_BACK to {
            getCollectionContent()
        },
        Callback.GET_COLLECTION_CONTENT_WIDE_SHIFT_NEXT to {
            getCollectionContent()
        },
        Callback.GET_COLLECTION_CONTENT_NEXT to {
            getCollectionContent()
        },
        Callback.GET_COLLECTIONS to {
            getCollections()
        },

        Callback.REQUEST_ADD_MUSIC_PACK to {
            requestAddMusicPack()
        },
        Callback.REQUEST_REMOVE_MUSIC_PACK to {
            requestRemoveMusicPack()
        },
        Callback.CANCEL_REMOVE_MUSIC_PACK to {
            cancelRemoveMusicPack()
        },
        Callback.REMOVE_MUSIC_PACK to {
            removeMusicPack()
        },
        Callback.RETURN_TO_COLLECTION_CONTENT to {
            getCollectionContent()
        },
    )


    private fun CommandHandlerEnvironment.getCollections() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val page = 0

        executeWithTry(bot, chatId, userExternalId) {
            if (args.isNotEmpty()) {
                getCollectionContent(
                    bot = bot,
                    chatId = chatId,
                    userExternalId = userExternalId,
                    collectionId = args[0].toLong(),
                    page = page,
                    size = BotProvider.DEFAULT_COLLECTION_CONTENT_PAGE_SIZE
                )
            } else {
                displayCollectionsList(bot, chatId, userExternalId)
            }
        }
    }

    private fun CallbackQueryHandlerEnvironment.getCollections() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            displayCollectionsList(bot, chatId, userExternalId, getMessageId())
        }
    }

    private fun displayCollectionsList(bot: Bot, chatId: Long, userExternalId: Long, replacedMessageId: Long? = null) {
        val page = 0
        val size = DEFAULT_COLLECTIONS_RESPONSE_PAGE_SIZE
        val collectionsPage = musicCollectionCommandFacade.getCollections(userExternalId)
            .trimToPage(page, size)

        if (collectionsPage.isEmpty()) {
            logger.warn { "Collections not found: userExternalId = $userExternalId, page = $page, size = $size" }
            bot.sendMessageToChat(chatId, "Пока нет коллекций, можно добавить через \"Настройки\"")
            return
        }

        val buttons = InlineKeyboardMarkup.create(
            collectionsPage
                .map {
                    InlineKeyboardButton.CallbackData(
                        text = it.title!!,
                        callbackData = formCallbackData(Callback.GET_COLLECTION_CONTENT_PAGE, it.id!!)
                    )
                }.chunked(1)
        )
        bot.sendOrReplaceMessage(
            chatId = chatId,
            replacedMessageId = replacedMessageId,
            text = "Выберите коллекцию",
            replyMarkup = buttons
        )
    }

    private fun CallbackQueryHandlerEnvironment.requestCollectionContentPageNumber() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val collectionId = queryData[1].toLong()

        bot.sendMessageToChat(chatId, "Отправьте номер страницы")
        serviceCommandFacade.registerActionEvent(
            type = ActionEventType.REQUEST_COLLECTION_CONTENT_PAGE_NUMBER,
            userExternalId = userExternalId,
            collectionId = collectionId,
            messageId = messageId
        )
    }

    private fun MessageHandlerEnvironment.getCollectionContentPage(actionEventDto: ActionEventDto) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            val messageId = actionEventDto.messageId!!
            val collectionId = actionEventDto.collectionId!!
            val page = minOf(0, message.text!!.toInt() - 1)
            val size = BotProvider.DEFAULT_COLLECTION_CONTENT_PAGE_SIZE
            getCollectionContent(
                bot = bot,
                chatId = chatId,
                userExternalId = userExternalId,
                collectionId = collectionId,
                page = page,
                size = size
            )
            bot.deleteMessageFromChat(chatId, messageId)
        }
    }

    private fun CallbackQueryHandlerEnvironment.getCollectionContent() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()

        val collectionId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else BotProvider.DEFAULT_COLLECTION_CONTENT_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            getCollectionContent(bot, chatId, messageId, userExternalId, collectionId, page, size)
        }
    }

    private fun getCollectionContent(
        bot: Bot,
        chatId: Long,
        replacedMessageId: Long? = null,
        userExternalId: Long,
        collectionId: Long,
        page: Int,
        size: Int
    ) {
        val collectionContent = musicCollectionCommandFacade.getCollectionWithContent(userExternalId, collectionId, page, size)
        val buttons = createCollectionContentButtons(collectionId, collectionContent.contentPage)
        bot.sendOrReplaceMessage(
            chatId = chatId,
            replacedMessageId = replacedMessageId,
            text = collectionContent.formatMessage(),
            replyMarkup = buttons,
            disableWebPagePreview = true
        )
    }

    private fun createCollectionContentButtons(
        collectionId: Long,
        collectionsPage: Page<MusicPackDto>
    ): InlineKeyboardMarkup {
        val offset = collectionsPage.getIndexOffset()
        val page = collectionsPage.number
        val size = collectionsPage.size

        val buttons = ArrayList<List<InlineKeyboardButton>>()
        buttons.addAll(
            collectionsPage.content.toInlineKeyboardButtons(offset, Callback.GET_MUSIC_PACK, collectionId)
        )

        val navButtons = ArrayList<InlineKeyboardButton>()
        navButtons.add(
            InlineKeyboardButton.CallbackData(
                text = Callback.REQUEST_COLLECTION_CONTENT_PAGE_NUMBER.title,
                callbackData = formCallbackData(Callback.REQUEST_COLLECTION_CONTENT_PAGE_NUMBER, collectionId)
            )
        )
        if (!collectionsPage.isFirst) {
            val pageWideShift = maxOf(0, page - PAGE_WIDE_SHIFT)
            navButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.GET_COLLECTION_CONTENT_WIDE_SHIFT_BACK.title,
                    callbackData = formCallbackData(Callback.GET_COLLECTION_CONTENT_WIDE_SHIFT_BACK, collectionId, pageWideShift, size)
                )
            )
            navButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.GET_COLLECTION_CONTENT_BACK.title,
                    callbackData = formCallbackData(Callback.GET_COLLECTION_CONTENT_BACK, collectionId, page - 1, size)
                )
            )
        }
        if (!collectionsPage.isLast) {
            navButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.GET_COLLECTION_CONTENT_NEXT.title,
                    callbackData = formCallbackData(Callback.GET_COLLECTION_CONTENT_NEXT, collectionId, page + 1, size)
                )
            )
            val pageWideShift = minOf(collectionsPage.totalPages - 1, page + PAGE_WIDE_SHIFT)
            navButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.GET_COLLECTION_CONTENT_WIDE_SHIFT_NEXT.title,
                    callbackData = formCallbackData(Callback.GET_COLLECTION_CONTENT_WIDE_SHIFT_NEXT, collectionId, pageWideShift, size)
                )
            )
        }
        buttons.add(navButtons)

        buttons.add(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = Callback.REQUEST_SEARCH_MUSIC_PACK.title,
                    callbackData = formCallbackData(Callback.REQUEST_SEARCH_MUSIC_PACK, collectionId)
                ),
                InlineKeyboardButton.CallbackData(
                    text = Callback.REQUEST_ADD_MUSIC_PACK.title,
                    callbackData = formCallbackData(Callback.REQUEST_ADD_MUSIC_PACK, collectionId)
                ),
                InlineKeyboardButton.CallbackData(
                    text = Callback.REQUEST_REMOVE_MUSIC_PACK.title,
                    callbackData = formCallbackData(Callback.REQUEST_REMOVE_MUSIC_PACK, collectionId, page, size)
                )
            )
        )

        return InlineKeyboardMarkup.create(buttons)
    }

    private fun CallbackQueryHandlerEnvironment.requestAddMusicPack() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val collectionId = queryData[1].toLong()

        bot.sendMessageToChat(chatId, "Отправьте название или ссылку для добавления записи")
        serviceCommandFacade.registerActionEvent(
            type = ActionEventType.REQUEST_ADD_MUSIC_PACK,
            userExternalId = userExternalId,
            collectionId = collectionId
        )
    }

    private fun MessageHandlerEnvironment.addMusicPack(collectionId: Long?) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageText = message.text!!.trim()

        executeWithTry(bot, chatId, userExternalId) {
            val stubMessage = bot.sendMessageToChat(chatId, MessageHelper.REQUEST_IN_PROGRESS_STUB_MESSAGE)
            if (messageText.isUrl()) {
                addMusicPackBySourceUrl(bot, chatId, userExternalId, collectionId, messageText, stubMessage.messageId)
            } else {
                val title = messageText
                addMusicPackLocal(bot, chatId, userExternalId, collectionId, title, stubMessage.messageId)
            }
        }
    }

    private fun CommandHandlerEnvironment.addMusicPack() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            args.validateArgsCount(1)
            val sourceUrl = args[0]
            val collectionId = if (args.size > 1) args[1].toLong() else null

            addMusicPackBySourceUrl(bot, chatId, userExternalId, collectionId, sourceUrl)
        }
    }

    private fun addMusicPackBySourceUrl(
        bot: Bot,
        chatId: Long,
        userExternalId: Long,
        collectionId: Long?,
        sourceUrl: String,
        replacedMessageId: Long? = null
    ) {
        val musicPack = musicCollectionCommandFacade.addMusicPackFromExternalSource(
            userExternalId = userExternalId,
            sourceUrl = sourceUrl,
            collectionId = collectionId
        )
        bot.sendOrReplaceMessage(chatId, replacedMessageId, MUSIC_PACK_ADDED_SUCCESS_MESSAGE)
        displayMusicPackWithControls(bot = bot, chatId = chatId, musicPack = musicPack, collectionId = collectionId)
    }

    private fun addMusicPackLocal(
        bot: Bot,
        chatId: Long,
        userExternalId: Long,
        collectionId: Long?,
        title: String,
        replacedMessageId: Long? = null
    ) {
        val musicPack = musicCollectionCommandFacade.addMusicPackLocal(
            userExternalId = userExternalId,
            title = title,
            collectionId = collectionId
        )
        bot.sendOrReplaceMessage(chatId, replacedMessageId, MUSIC_PACK_ADDED_SUCCESS_MESSAGE)
        displayMusicPackWithControls(bot = bot, chatId = chatId, musicPack = musicPack, collectionId = collectionId)
    }

    private fun CallbackQueryHandlerEnvironment.requestRemoveMusicPack() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(3)

        val chatId = getChatId()
        val replacedMessageId = getMessageId()
        val userExternalId = getUserExternalId()
        val collectionId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else BotProvider.DEFAULT_COLLECTION_CONTENT_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            val collectionContent = musicCollectionCommandFacade.getCollectionWithContent(userExternalId, collectionId, page, size)
            val buttons = createRemoveMusicPackButtons(collectionId, collectionContent.contentPage)
            bot.sendOrReplaceMessage(
                chatId = chatId,
                replacedMessageId = replacedMessageId,
                text = collectionContent.formatMessage(),
                replyMarkup = buttons,
                disableWebPagePreview = true
            )
            bot.sendMessageToChat(chatId, "Выберите запись для удаления по номеру из списка выше")
                .also {
                    serviceCommandFacade.registerActionEvent(
                        type = ActionEventType.REQUEST_REMOVE_MUSIC_PACK,
                        userExternalId = userExternalId,
                        messageId = it.messageId,
                        collectionId = collectionId
                    )
                }
        }
    }

    private fun createRemoveMusicPackButtons(
        collectionId: Long,
        collectionsPage: Page<MusicPackDto>
    ): InlineKeyboardMarkup {
        val offset = collectionsPage.getIndexOffset()
        val buttons = ArrayList<List<InlineKeyboardButton>>()
        buttons.addAll(
            collectionsPage.content.toInlineKeyboardButtons(
                offset = offset,
                callback = Callback.REMOVE_MUSIC_PACK,
                collectionId,
                collectionsPage.number,
                collectionsPage.size
            )
        )
        buttons.add(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = Callback.CANCEL_REMOVE_MUSIC_PACK.title,
                    callbackData = formCallbackData(
                        Callback.CANCEL_REMOVE_MUSIC_PACK,
                        collectionId,
                        collectionsPage.number,
                        collectionsPage.size
                    )
                )
            )
        )
        return InlineKeyboardMarkup.create(buttons)
    }

    private fun CallbackQueryHandlerEnvironment.cancelRemoveMusicPack() {
        checkAndHandleRequestActionEvent(
            requestActionEventType = ActionEventType.REQUEST_REMOVE_MUSIC_PACK,
            targetActionEventType = ActionEventType.ACTION_CANCELED,
        )

        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val collectionId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else BotProvider.DEFAULT_COLLECTION_CONTENT_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            getCollectionContent(bot, chatId, messageId, userExternalId, collectionId, page, size)
        }
    }

    private fun CallbackQueryHandlerEnvironment.removeMusicPack() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(5)

        val chatId = getChatId()
        val replacedMessageId = getMessageId()
        val userExternalId = getUserExternalId()
        val musicPackId = queryData[1].toLong()
        val collectionId = queryData[2].toLong()
        val page = queryData[3].toInt()
        val size = queryData[4].toInt()

        executeWithTry(bot, chatId, userExternalId) {
            musicCollectionCommandFacade.removeMusicPack(musicPackId = musicPackId, collectionId = collectionId, userExternalId = userExternalId)
            getCollectionContent(bot, chatId, replacedMessageId, userExternalId, collectionId, page, size)
            checkAndHandleRequestActionEvent(
                requestActionEventType = ActionEventType.REQUEST_REMOVE_MUSIC_PACK,
                targetActionEventType = ActionEventType.REMOVED_MUSIC_PACK,
                targetCollectionId = collectionId,
                targetMusicPackId = musicPackId
            )
        }
    }
}