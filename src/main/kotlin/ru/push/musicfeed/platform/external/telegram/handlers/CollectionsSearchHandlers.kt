package ru.push.musicfeed.platform.external.telegram.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCallbackQuery
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
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
import ru.push.musicfeed.platform.external.telegram.HandleActionMessage
import ru.push.musicfeed.platform.external.telegram.MessageHelper
import ru.push.musicfeed.platform.external.telegram.formMusicPackFullMessage
import ru.push.musicfeed.platform.external.telegram.clearButtons
import ru.push.musicfeed.platform.external.telegram.deleteMessageFromChat
import ru.push.musicfeed.platform.external.telegram.formMusicPackShortMessage
import ru.push.musicfeed.platform.external.telegram.getChatId
import ru.push.musicfeed.platform.external.telegram.getMessageId
import ru.push.musicfeed.platform.external.telegram.getUserExternalId
import ru.push.musicfeed.platform.external.telegram.replaceButtons
import ru.push.musicfeed.platform.external.telegram.replaceMessage
import ru.push.musicfeed.platform.external.telegram.sendMessageToChat
import ru.push.musicfeed.platform.external.telegram.sendOrReplaceMessage

@Component
class CollectionsSearchHandlers(
    serviceCommandFacade: ServiceCommandFacade,
    val musicCollectionCommandFacade: MusicCollectionCommandFacade,
) : AbstractDispatcherHandlers(serviceCommandFacade) {

    companion object {
        const val DEFAULT_MUSIC_PACKS_RESPONSE_PAGE_SIZE = 3
        const val DEFAULT_TAGS_RESPONSE_PAGE_SIZE = 15
    }

    override fun getMessageHandlers(): Map<ActionEventType, HandleActionMessage> {
        val map: MutableMap<ActionEventType, HandleActionMessage> = mutableMapOf()
        map[ActionEventType.REQUEST_SEARCH_MUSIC_PACK] = { actionEventDto: ActionEventDto? ->
            searchMusicPackByTextRequest(actionEventDto?.collectionId)
        }
        return map
    }

//    override fun getCommandHandlers(): Map<Command, HandleCommand> = mapOf(
//        Command.RANDOM to {
//            getRandomMusicPack()
//        },
//        Command.RECENT to {
//            getRecentMusicPacks()
//        },
//        Command.TAGS to {
//            getTags()
//        }
//    )

    override fun getCallbackQueryHandlers(): Map<Callback, HandleCallbackQuery> = mapOf(
        Callback.GET_RECENT to {
            getRecentMusicPacks()
        },
        Callback.MUSIC_PACKS_RECENT_NEXT to {
            getRecentMusicPacksNext()
        },
        Callback.GET_RANDOM to {
            getRandomMusicPack()
        },
        Callback.GET_TAGS to {
            getTags()
        },
        Callback.TAGS_BACK to {
            getTagsPage()
        },
        Callback.TAGS_NEXT to {
            getTagsPage()
        },
        Callback.TAGS_SELECT to {
            searchMusicPacksByTags(true)
        },
        Callback.MUSIC_PACKS_SEARCH_BY_TAGS_NEXT to {
            searchMusicPacksByTags(false)
        },
        Callback.MUSIC_PACKS_SEARCH_REQUEST_NEXT to {
            searchMusicPackByRequestNext()
        },

        Callback.REQUEST_SEARCH_MUSIC_PACK to {
            requestSearchMusicPack()
        },
    )


    private fun CommandHandlerEnvironment.getRandomMusicPack() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            displayRandomMusicPack(bot, chatId, userExternalId)
        }
    }

    private fun CallbackQueryHandlerEnvironment.getRandomMusicPack() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            displayRandomMusicPack(bot, chatId, userExternalId, getMessageId())
        }
    }

    private fun displayRandomMusicPack(bot: Bot, chatId: Long, userExternalId: Long, replacedMessageId: Long? = null) {
        val musicPack = musicCollectionCommandFacade.getRandomMusicPack(userExternalId)
        val buttons = InlineKeyboardMarkup.create(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = Callback.GET_RANDOM.title,
                    callbackData = formCallbackData(Callback.GET_RANDOM)
                )
            )
        )
        bot.sendOrReplaceMessage(
            chatId = chatId,
            replacedMessageId = replacedMessageId,
            text = musicPack.formMusicPackFullMessage(),
            replyMarkup = buttons
        )
    }

    private fun CommandHandlerEnvironment.getRecentMusicPacks() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        executeWithTry(bot, chatId, userExternalId) {
            getRecentMusicPacks(bot, chatId, userExternalId, 0, DEFAULT_MUSIC_PACKS_RESPONSE_PAGE_SIZE)
        }
    }

    private fun CallbackQueryHandlerEnvironment.getRecentMusicPacks() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        executeWithTry(bot, chatId, userExternalId) {
            getRecentMusicPacks(bot, chatId, userExternalId, 0, DEFAULT_MUSIC_PACKS_RESPONSE_PAGE_SIZE)
        }
    }

    private fun CallbackQueryHandlerEnvironment.getRecentMusicPacksNext() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()

        bot.deleteMessageFromChat(chatId, messageId)
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val page = queryData[1].toInt()
        val size = if (queryData.size > 2) queryData[2].toInt() else DEFAULT_MUSIC_PACKS_RESPONSE_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            getRecentMusicPacks(bot, chatId, userExternalId, page, size)
        }
    }

    private fun CommandHandlerEnvironment.getTags() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            if (args.isNotEmpty()) {
                searchMusicPacksByTags(bot, chatId, userExternalId, args, 0, DEFAULT_MUSIC_PACKS_RESPONSE_PAGE_SIZE)
            } else {
                displayTagsList(bot, chatId, userExternalId)
            }
        }
    }

    private fun CallbackQueryHandlerEnvironment.getTags() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            displayTagsList(bot, chatId, userExternalId, getMessageId())
        }
    }

    private fun displayTagsList(bot: Bot, chatId: Long, userExternalId: Long, replacedMessageId: Long? = null) {
        val tags = musicCollectionCommandFacade.getTags(userExternalId, 0, DEFAULT_TAGS_RESPONSE_PAGE_SIZE)
            .map { it.value }
        if (!tags.isEmpty) {
            bot.sendOrReplaceMessage(
                chatId = chatId,
                replacedMessageId = replacedMessageId,
                text = "Выберите теги для поиска",
                replyMarkup = createTagsButtons(tags)
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.getTagsPage() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val page = queryData[1].toInt()
        val size = if (queryData.size > 2) queryData[2].toInt() else BotProvider.DEFAULT_TAGS_RESPONSE_PAGE_SIZE

        val tags = musicCollectionCommandFacade.getTags(userExternalId, page, size)
            .map { it.value }
        if (!tags.isEmpty) {
            val buttons = createTagsButtons(tags)
            bot.replaceButtons(chatId, messageId, buttons)
        } else {
            bot.clearButtons(chatId, messageId)
        }
    }

    private fun CallbackQueryHandlerEnvironment.searchMusicPacksByTags(replaceMessage: Boolean) {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val tag = queryData[1]
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else DEFAULT_MUSIC_PACKS_RESPONSE_PAGE_SIZE

        if (replaceMessage) {
            bot.replaceMessage(chatId, messageId, "Записи с тегом #$tag")
        }

        executeWithTry(bot, chatId, userExternalId) {
            searchMusicPacksByTags(bot, chatId, userExternalId, listOf(tag), page, size)
        }
    }

    internal fun getRecentMusicPacks(
        bot: Bot,
        chatId: Long,
        userExternalId: Long,
        page: Int,
        size: Int
    ) {
        val result = musicCollectionCommandFacade.getRecentMusicPacks(userExternalId, page, size)
        result.displayMusicPacksPage(
            bot = bot,
            chatId = chatId,
            mainButtonCallback = Callback.GET_MUSIC_PACK,
            nextButtonCallback = Callback.MUSIC_PACKS_RECENT_NEXT
        )
    }

    internal fun searchMusicPacksByTags(
        bot: Bot,
        chatId: Long,
        userExternalId: Long,
        tags: List<String>,
        page: Int,
        size: Int
    ) {
        val result = musicCollectionCommandFacade.searchMusicPacksByTags(userExternalId, tags, page, size)
        if (result.isEmpty) {
            bot.sendMessageToChat(chatId, MessageHelper.MUSIC_PACKS_NOT_FOUND_MESSAGE)
        } else {
            result.displayMusicPacksPage(
                bot = bot,
                chatId = chatId,
                mainButtonCallback = Callback.GET_MUSIC_PACK,
                nextButtonCallback = Callback.MUSIC_PACKS_SEARCH_BY_TAGS_NEXT
            )
        }
    }

    private fun createTagsButtons(tagsPage: Page<String>): InlineKeyboardMarkup {
        val currentPage = tagsPage.pageable.pageNumber
        val buttons = ArrayList<List<InlineKeyboardButton>>(tagsPage.size + 2)
        if (!tagsPage.isFirst) {
            buttons.add(
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = Callback.TAGS_BACK.title,
                        callbackData = formCallbackData(Callback.TAGS_BACK, currentPage - 1)
                    )
                )
            )
        }
        buttons.addAll(
            tagsPage.map {
                InlineKeyboardButton.CallbackData(
                    text = it,
                    callbackData = formCallbackData(Callback.TAGS_SELECT, it)
                )
            }.chunked(3)
        )
        if (!tagsPage.isLast) {
            buttons.add(
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = Callback.TAGS_NEXT.title,
                        callbackData = formCallbackData(Callback.TAGS_NEXT, currentPage + 1)
                    )
                )
            )
        }
        return InlineKeyboardMarkup.create(buttons)
    }

    private fun MessageHandlerEnvironment.searchMusicPackByTextRequest(collectionId: Long?) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageText = message.text!!.trim()

        executeWithTry(bot, chatId, userExternalId) {
            val result = musicCollectionCommandFacade.searchMusicPacksBySearchRequest(
                userExternalId = userExternalId,
                collectionId = collectionId,
                searchRequest = messageText,
                page = 0,
                size = DEFAULT_MUSIC_PACKS_RESPONSE_PAGE_SIZE
            )
            if (result.isEmpty) {
                bot.sendMessageToChat(chatId, MessageHelper.MUSIC_PACKS_NOT_FOUND_MESSAGE)
            } else {
                result.displayMusicPacksPage(
                    bot = bot,
                    chatId = chatId,
                    mainButtonCallback = Callback.GET_MUSIC_PACK,
                    nextButtonCallback = Callback.MUSIC_PACKS_SEARCH_REQUEST_NEXT,
                    collectionId = collectionId
                )
            }
        }
    }

    private fun CallbackQueryHandlerEnvironment.searchMusicPackByRequestNext() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()

        bot.deleteMessageFromChat(chatId, messageId)
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val page = queryData[1].toInt()
        val collectionId = queryData.getOrNull(2)?.toLong()
        val size = queryData.getOrNull(3)?.toInt() ?: DEFAULT_MUSIC_PACKS_RESPONSE_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            val result = musicCollectionCommandFacade.searchMusicPacksBySearchRequest(
                userExternalId = userExternalId,
                collectionId = collectionId,
                page = page,
                size = size
            )
            result.displayMusicPacksPage(
                bot = bot,
                chatId = chatId,
                mainButtonCallback = Callback.GET_MUSIC_PACK,
                nextButtonCallback = Callback.MUSIC_PACKS_SEARCH_REQUEST_NEXT,
                collectionId = collectionId
            )
        }
    }

    private fun Page<MusicPackDto>.displayMusicPacksPage(
        bot: Bot,
        chatId: Long,
        mainButtonCallback: Callback,
        nextButtonCallback: Callback,
        collectionId: Long? = null
    ) {
        val page = this.number
        val size = this.size
        val musicPacks = this.content
        musicPacks.forEach { musicPackDto ->
            val buttons = mutableListOf(
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = mainButtonCallback.title,
                        callbackData = formCallbackData(mainButtonCallback, musicPackDto.id!!)
                    )
                )
            )
            bot.sendMessageToChat(
                chatId = chatId,
                text = musicPackDto.formMusicPackShortMessage(),
                replyMarkup = InlineKeyboardMarkup.create(buttons)
            )
        }
        if (this.totalPages > (page + 1)) {
            val navButtons = listOf(
                InlineKeyboardButton.CallbackData(
                    text = nextButtonCallback.title,
                    callbackData = collectionId?.let { formCallbackData(nextButtonCallback, page + 1, collectionId) }
                        ?: formCallbackData(nextButtonCallback, page + 1)
                )
            )
            bot.sendMessageToChat(
                chatId = chatId,
                text = "Записи ${(page * size) + 1} - ${(page * size) + size} из ${this.totalElements}",
                replyMarkup = InlineKeyboardMarkup.create(navButtons)
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestSearchMusicPack() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val collectionId = queryData.getOrNull(1)?.toLong()

        bot.sendMessageToChat(chatId, "Отправьте запрос для поиска записи${collectionId?.let { " в коллекции" } ?: ""}")
        serviceCommandFacade.registerActionEvent(
            type = ActionEventType.REQUEST_SEARCH_MUSIC_PACK,
            userExternalId = userExternalId,
            collectionId = collectionId
        )
    }
}