package ru.push.musicfeed.platform.external.telegram.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCallbackQuery
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCommand
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.DownloaderCommandFacade
import ru.push.musicfeed.platform.application.ServiceCommandFacade
import ru.push.musicfeed.platform.application.TrackFileCommandFacade
import ru.push.musicfeed.platform.application.TrackLocalFileNotFoundException
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoList
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.data.model.download.DownloadStatus
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.FAIL
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.IN_PROGRESS
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.REQUESTED
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.SUCCESS
import ru.push.musicfeed.platform.external.telegram.BotProvider
import ru.push.musicfeed.platform.external.telegram.Callback
import ru.push.musicfeed.platform.external.telegram.Command
import ru.push.musicfeed.platform.external.telegram.TrackFileSupportDispatcherHandlers
import ru.push.musicfeed.platform.external.telegram.HandleActionMessage
import ru.push.musicfeed.platform.external.telegram.MessageHelper
import ru.push.musicfeed.platform.external.telegram.MessageHelper.Companion.DOWNLOAD_STARTED_MESSAGE
import ru.push.musicfeed.platform.external.telegram.deleteMessageFromChat
import ru.push.musicfeed.platform.external.telegram.formCommonButtonText
import ru.push.musicfeed.platform.external.telegram.formCompactTitle
import ru.push.musicfeed.platform.external.telegram.formFailButtonText
import ru.push.musicfeed.platform.external.telegram.formDownloadsListMessage
import ru.push.musicfeed.platform.external.telegram.formInfoMessage
import ru.push.musicfeed.platform.external.telegram.formProgressButtonText
import ru.push.musicfeed.platform.external.telegram.formSuccessButtonText
import ru.push.musicfeed.platform.external.telegram.getChatId
import ru.push.musicfeed.platform.external.telegram.getMessageId
import ru.push.musicfeed.platform.external.telegram.getUserExternalId
import ru.push.musicfeed.platform.external.telegram.replaceButtonInKeyboard
import ru.push.musicfeed.platform.external.telegram.replaceMessage
import ru.push.musicfeed.platform.external.telegram.sendMessageToChat
import ru.push.musicfeed.platform.external.telegram.sendOrReplaceMessage
import ru.push.musicfeed.platform.util.isUrl
import ru.push.musicfeed.platform.util.splitBySpaces

@Component
class DownloaderHandlers(
    serviceCommandFacade: ServiceCommandFacade,
    trackFileCommandFacade: TrackFileCommandFacade,
    private val downloaderCommandFacade: DownloaderCommandFacade,
) : TrackFileSupportDispatcherHandlers(serviceCommandFacade, trackFileCommandFacade) {

    companion object {
        private const val DEFAULT_PAGE_SIZE: Int = 5
    }

    override fun getMessageHandlers(): Map<ActionEventType, HandleActionMessage> {
        val map: MutableMap<ActionEventType, HandleActionMessage> = mutableMapOf()
        map[ActionEventType.REQUEST_TRACK_DOWNLOAD] = {
            requestTrackDownload()
        }
        return map
    }

    override fun getCommandHandlers(): Map<Command, HandleCommand> = mapOf(
        Command.DOWNLOAD to {
            requestDownload()
        }
    )

    override fun getCallbackQueryHandlers(): Map<Callback, HandleCallbackQuery> = mapOf(
        Callback.DOWNLOAD_REQUEST to {
            requestDownload()
        },
        Callback.REQUEST_DOWNLOAD_BY_TRACK_ID to {
            requestDownloadByTrackId()
        },
        Callback.GET_DOWNLOAD_PROCESS_STATUS to {
            getDownloadProcessStatus()
        },
        Callback.GET_DOWNLOAD_PROCESS_STATUS_FOR_LIST to {
            getDownloadProcessStatusForList()
        },
        Callback.RETRY_DOWNLOAD to {
            retryDownload()
        },
        Callback.DOWNLOAD_FILE to {
            obtainDownloadedFile()
        },

        Callback.DOWNLOADS_LIST to {
            obtainDownloadsListPage()
        },
        Callback.DOWNLOADS_LIST_BACK to {
            obtainDownloadsListPage()
        },
        Callback.DOWNLOADS_LIST_NEXT to {
            obtainDownloadsListPage()
        },
        Callback.DOWNLOADS_ITEM_SELECT to {
            downloadsItemSelect()
        },

        Callback.DOWNLOADS_LIST_RETURN to {
            obtainDownloadsListPage()
        },
        Callback.DOWNLOADS_ITEM_DOWNLOAD_FILE to {
            obtainDownloadedFile()
        },
        Callback.DOWNLOADS_ITEM_REMOVE to {
            removeDownloadsItem()
        },
        Callback.DOWNLOADS_ITEM_STOP to {
            stopDownloadProcess()
        },
        Callback.DOWNLOADS_ITEM_RETRY to {
            retryDownloadsItem()
        },
        Callback.DOWNLOADS_ITEM_STATUS to {
            downloadsItemSelect()
        },
        Callback.DOWNLOADS_ITEM_EXTRACT_TRACK_REQUEST to {
            requestExtractTrack()
        },
    )

    private fun CommandHandlerEnvironment.requestDownload() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            if (args.isNotEmpty()) {
                val stubMessage = bot.sendMessageToChat(chatId, MessageHelper.REQUEST_IN_PROGRESS_STUB_MESSAGE)
                val sourceUrl = args[0]
                val processInfoList = downloaderCommandFacade.requestSingleTrackDownload(userExternalId, sourceUrl)
                displayDownloadProcessInfoList(
                    bot = bot,
                    chatId = chatId,
                    userExternalId = userExternalId,
                    replacedMessageId = stubMessage.messageId,
                    processInfoList = processInfoList
                )
            } else {
                displayDownloadTrackRequest(bot, chatId, userExternalId)
            }
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestDownload() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            displayDownloadTrackRequest(bot, chatId, userExternalId, getMessageId())
        }
    }

    private fun displayDownloadTrackRequest(bot: Bot, chatId: Long, userExternalId: Long, replacedMessageId: Long? = null) {
        serviceCommandFacade.registerActionEvent(
            type = ActionEventType.REQUEST_TRACK_DOWNLOAD,
            userExternalId = userExternalId,
        )
        bot.sendOrReplaceMessage(chatId, replacedMessageId, "Отправьте запрос или ссылку для скачивания")
    }

    private fun displayDownloadProcessInfoList(
        bot: Bot,
        chatId: Long,
        userExternalId: Long,
        replacedMessageId: Long? = null,
        processInfoList: DownloadProcessInfoList,
    ) {
        var dtoList = processInfoList.dtoList.content
        if (dtoList.size == 1 && dtoList[0].status == SUCCESS) {
            replacedMessageId?.let { bot.deleteMessageFromChat(chatId, it) }
            // todo: need refactoring
            try {
                val trackFileInfo = downloaderCommandFacade.getDownloadedTrackFileInfo(dtoList[0].downloadProcessId)
                sendTrackFileToChat(bot, chatId, userExternalId, trackFileInfo)
                return
            } catch (ex: TrackLocalFileNotFoundException) {
                dtoList = downloaderCommandFacade.getDownloadProcessInfoList(processInfoList.requestId!!).dtoList.content
            }
        }

        val requestId = processInfoList.requestId
        val buttons = dtoList.map { infoDto -> listOf(formButtonForDownloadProcessStatus(infoDto, requestId)) }.toMutableList()
        if (requestId != null && dtoList.size > 1) {
            buttons.add(
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = Callback.GET_DOWNLOAD_PROCESS_STATUS_FOR_LIST.title,
                        callbackData = formCallbackData(Callback.GET_DOWNLOAD_PROCESS_STATUS_FOR_LIST, requestId)
                    )
                )
            )
        }
        bot.sendOrReplaceMessage(
            chatId = chatId,
            replacedMessageId = replacedMessageId,
            text = DOWNLOAD_STARTED_MESSAGE,
            replyMarkup = InlineKeyboardMarkup.create(buttons),
            disableWebPagePreview = true
        )
    }

    private fun formButtonForDownloadProcessStatus(infoDto: DownloadProcessInfoDto, requestId: Long? = null): InlineKeyboardButton.CallbackData {
        val downloadProcessId = infoDto.downloadProcessId
        val callbackData: (Callback) -> String = { callback ->
            requestId?.let { formCallbackData(callback, downloadProcessId, it) }
                ?: formCallbackData(callback, downloadProcessId)
        }
        return when (infoDto.status) {
            REQUESTED, IN_PROGRESS ->
                InlineKeyboardButton.CallbackData(
                    text = infoDto.formProgressButtonText(),
                    callbackData = callbackData(Callback.GET_DOWNLOAD_PROCESS_STATUS)
                )
            SUCCESS ->
                InlineKeyboardButton.CallbackData(
                    text = infoDto.formSuccessButtonText(),
                    callbackData = callbackData(Callback.DOWNLOAD_FILE)
                )
            FAIL ->
                InlineKeyboardButton.CallbackData(
                    text = infoDto.formFailButtonText(),
                    callbackData = callbackData(Callback.RETRY_DOWNLOAD)
                )
        }
    }

    private fun MessageHandlerEnvironment.requestTrackDownload() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageText = message.text!!.trim()
        val requestLines = messageText.splitBySpaces()
        if (requestLines.isEmpty())
            return

        telegramBotScope.launch {
            executeWithTry(bot, chatId, userExternalId) {
                val stubMessage = bot.sendMessageToChat(chatId, MessageHelper.REQUEST_IN_PROGRESS_STUB_MESSAGE)
                if (requestLines.firstOrNull()?.isUrl() == true) {
                    val processInfoList = downloaderCommandFacade.requestTrackListDownload(userExternalId, requestLines)
                    displayDownloadProcessInfoList(
                        bot = bot,
                        chatId = chatId,
                        userExternalId = userExternalId,
                        replacedMessageId = stubMessage.messageId,
                        processInfoList = processInfoList
                    )
                } else {
                    requestSearchAndDisplayResult(
                        bot = bot,
                        chatId = chatId,
                        replacedMessageId = stubMessage.messageId,
                        searchText = messageText
                    )
                }
            }
        }
    }

    private fun requestSearchAndDisplayResult(
        bot: Bot,
        chatId: Long,
        replacedMessageId: Long? = null,
        searchText: String,
    ) {
        val searchResult = downloaderCommandFacade.searchTrackForDownload(searchText)
        val buttons = InlineKeyboardMarkup.create(
            searchResult.content.map { track ->
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = track.formCompactTitle(),
                        callbackData = formCallbackData(Callback.REQUEST_DOWNLOAD_BY_TRACK_ID, track.id!!)
                    )
                )
            }
        )
        bot.sendOrReplaceMessage(
            chatId = chatId,
            replacedMessageId = replacedMessageId,
            text = "Найдено несколько результатов, выберите подходящий",
            replyMarkup = buttons,
        )
    }

    private fun CallbackQueryHandlerEnvironment.requestDownloadByTrackId() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicTrackId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            val stubMessage = bot.sendMessageToChat(chatId, MessageHelper.REQUEST_IN_PROGRESS_STUB_MESSAGE)
            val trackFileInfo = downloaderCommandFacade.getDownloadedTrackFileInfoForMusicTrack(musicTrackId)
            if (trackFileInfo != null) {
                bot.deleteMessageFromChat(chatId, stubMessage.messageId)
                sendTrackFileToChat(bot, chatId, userExternalId, trackFileInfo)
            } else {
                val processInfo = downloaderCommandFacade.requestSingleTrackDownloadForMusicTrack(userExternalId, musicTrackId)
                displayDownloadProcessInfoList(
                    bot = bot,
                    chatId = chatId,
                    userExternalId = userExternalId,
                    replacedMessageId = stubMessage.messageId,
                    processInfoList = DownloadProcessInfoList.fromDto(processInfo)
                )
            }
        }
    }

    private fun CallbackQueryHandlerEnvironment.getDownloadProcessStatusForList() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val requestId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            val processInfoList = downloaderCommandFacade.getDownloadProcessInfoList(requestId)
            displayDownloadProcessInfoList(
                bot = bot,
                chatId = chatId,
                userExternalId = userExternalId,
                replacedMessageId = messageId,
                processInfoList = processInfoList
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.getDownloadProcessStatus() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val inlineKeyboard = this.callbackQuery.message!!.replyMarkup!!.inlineKeyboard
        val downloadProcessId = queryData[1].toLong()
        val requestId = if (queryData.size > 2) queryData[2].toLong() else null

        executeWithTry(bot, chatId, userExternalId) {
            val infoDto = downloaderCommandFacade.getDownloadProcessInfo(downloadProcessId)
            displayDownloadProcessInfo(
                bot = bot,
                chatId = chatId,
                userExternalId = userExternalId,
                infoDto = infoDto,
                requestId = requestId,
                messageId = messageId,
                inlineKeyboard = inlineKeyboard
            )
        }
    }

    private fun displayDownloadProcessInfo(
        bot: Bot,
        chatId: Long,
        userExternalId: Long,
        infoDto: DownloadProcessInfoDto,
        requestId: Long? = null,
        messageId: Long,
        inlineKeyboard: List<List<InlineKeyboardButton>>
    ) {

        if (inlineKeyboard.size == 1 && infoDto.status == DownloadStatus.SUCCESS) {
            bot.deleteMessageFromChat(chatId, messageId)
            val trackInfo = downloaderCommandFacade.getDownloadedTrackFileInfo(infoDto.downloadProcessId)
            sendTrackFileToChat(bot, chatId, userExternalId, trackInfo)
            return
        }

        val updatedButton = formButtonForDownloadProcessStatus(infoDto, requestId)
        bot.replaceButtonInKeyboard(
            chatId = chatId,
            messageId = messageId,
            sourceKeyboard = inlineKeyboard,
            updatedButton = updatedButton,
            callbackDataCondition = { it.size >= 2 && it[1].toLong() == infoDto.downloadProcessId }
        )
    }

    private fun CallbackQueryHandlerEnvironment.retryDownload() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val inlineKeyboard = this.callbackQuery.message!!.replyMarkup!!.inlineKeyboard
        val downloadProcessId = queryData[1].toLong()
        val requestId = if (queryData.size > 2) queryData[2].toLong() else null

        executeWithTry(bot, chatId, userExternalId) {
            val infoDto = downloaderCommandFacade.retryDownloadProcess(userExternalId, downloadProcessId)
            displayDownloadProcessInfo(
                bot = bot,
                chatId = chatId,
                userExternalId = userExternalId,
                infoDto = infoDto,
                requestId = requestId,
                messageId = messageId,
                inlineKeyboard = inlineKeyboard
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.obtainDownloadedFile() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val downloadProcessId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            val infoDto = downloaderCommandFacade.getDownloadedTrackFileInfo(downloadProcessId)
            sendTrackFileToChat(bot, chatId, userExternalId, infoDto)
        }
    }

    private fun CallbackQueryHandlerEnvironment.obtainDownloadsListPage() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val page = if (queryData.size > 1) queryData[1].toInt() else 0
        val size = if (queryData.size > 2) queryData[2].toInt() else DEFAULT_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            val processInfoList = downloaderCommandFacade.getDownloadProcessInfoList(userExternalId, page, size)
            displayDownloadsList(bot, chatId, processInfoList, messageId)
        }
    }

    private fun displayDownloadsList(bot: Bot, chatId: Long, processInfoList: DownloadProcessInfoList, replacedMessageId: Long?) {
        val items = processInfoList.dtoList
        val page = items.number
        val size = items.size
        val itemButtons = items.map { infoDto ->
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = infoDto.formCommonButtonText(),
                    callbackData = formCallbackData(Callback.DOWNLOADS_ITEM_SELECT, infoDto.downloadProcessId, page, size)
                )
            )
        }.toMutableList()
        val navButtons = mutableListOf<InlineKeyboardButton.CallbackData>()
        if (!items.isFirst) {
            navButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.DOWNLOADS_LIST_BACK.title,
                    callbackData = formCallbackData(Callback.DOWNLOADS_LIST_BACK, page - 1, size)
                )
            )
        }
        if (!items.isLast) {
            navButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.DOWNLOADS_LIST_NEXT.title,
                    callbackData = formCallbackData(Callback.DOWNLOADS_LIST_NEXT, page + 1, size)
                )
            )
        }
        itemButtons.add(navButtons)
        bot.sendOrReplaceMessage(
            chatId = chatId,
            replacedMessageId = replacedMessageId,
            text = processInfoList.formDownloadsListMessage(),
            replyMarkup = InlineKeyboardMarkup.create(itemButtons),
            disableWebPagePreview = true
        )
    }

    private fun CallbackQueryHandlerEnvironment.downloadsItemSelect() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val downloadProcessId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else DEFAULT_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            val infoDto = downloaderCommandFacade.getDownloadProcessInfo(downloadProcessId)
            displayDownloadsItem(bot, chatId, infoDto, page, size, messageId)
        }
    }

    private fun displayDownloadsItem(
        bot: Bot,
        chatId: Long,
        infoDto: DownloadProcessInfoDto,
        page: Int,
        size: Int,
        replacedMessageId: Long? = null
    ) {
        val buttons: MutableList<List<InlineKeyboardButton>> = mutableListOf(
            formButtonsForDownloadsItem(infoDto, page, size)
        )
        buttons.add(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = Callback.DOWNLOADS_LIST_RETURN.title,
                    callbackData = formCallbackData(Callback.DOWNLOADS_LIST_RETURN, page, size)
                ),
                InlineKeyboardButton.Url(
                    text = Callback.DOWNLOADS_ITEM_SOURCE_URL.title,
                    url = infoDto.sourceUrl
                )
            )
        )

        bot.sendOrReplaceMessage(
            chatId = chatId,
            replacedMessageId = replacedMessageId,
            text = infoDto.formInfoMessage(),
            replyMarkup = InlineKeyboardMarkup.create(buttons)
        )
    }

    private fun formButtonsForDownloadsItem(infoDto: DownloadProcessInfoDto, page: Int, size: Int): List<InlineKeyboardButton> {
        val downloadProcessId = infoDto.downloadProcessId
        return when (infoDto.status) {
            REQUESTED, IN_PROGRESS ->
                listOf(
//                    InlineKeyboardButton.CallbackData(
//                        text = Callback.DOWNLOADS_ITEM_STOP.title,
//                        callbackData = formCallbackData(Callback.DOWNLOADS_ITEM_STOP, downloadProcessId)
//                    ),
                    InlineKeyboardButton.CallbackData(
                        text = Callback.DOWNLOADS_ITEM_STATUS.title,
                        callbackData = formCallbackData(Callback.DOWNLOADS_ITEM_STATUS, downloadProcessId, page, size)
                    )
                )
            SUCCESS ->
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = Callback.DOWNLOADS_ITEM_DOWNLOAD_FILE.title,
                        callbackData = formCallbackData(Callback.DOWNLOADS_ITEM_DOWNLOAD_FILE, downloadProcessId)
                    ),
                    InlineKeyboardButton.CallbackData(
                        text = Callback.DOWNLOADS_ITEM_REMOVE.title,
                        callbackData = formCallbackData(Callback.DOWNLOADS_ITEM_REMOVE, downloadProcessId)
                    ),
                    InlineKeyboardButton.CallbackData(
                        text = Callback.DOWNLOADS_ITEM_EXTRACT_TRACK_REQUEST.title,
                        callbackData = formCallbackData(Callback.DOWNLOADS_ITEM_EXTRACT_TRACK_REQUEST, downloadProcessId)
                    ),
                )
            FAIL ->
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = Callback.DOWNLOADS_ITEM_RETRY.title,
                        callbackData = formCallbackData(Callback.DOWNLOADS_ITEM_RETRY, downloadProcessId)
                    ),
                    InlineKeyboardButton.CallbackData(
                        text = Callback.DOWNLOADS_ITEM_REMOVE.title,
                        callbackData = formCallbackData(Callback.DOWNLOADS_ITEM_REMOVE, downloadProcessId)
                    )
                )
        }
    }

    private fun CallbackQueryHandlerEnvironment.removeDownloadsItem() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val downloadProcessId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else DEFAULT_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            val processInfoList = downloaderCommandFacade.removeDownloadsItem(userExternalId, downloadProcessId, page, size)
            displayDownloadsList(bot, chatId, processInfoList, messageId)
        }
    }

    private fun CallbackQueryHandlerEnvironment.stopDownloadProcess() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val downloadProcessId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId, "Данную загрузку остановить нельзя") {
            val infoDto = downloaderCommandFacade.stopDownloadProcess(userExternalId, downloadProcessId)
            bot.replaceMessage(chatId, messageId, "Загрузка остановлена")
            displayDownloadsItem(bot, chatId, infoDto, 0, DEFAULT_PAGE_SIZE)
        }
    }

    private fun CallbackQueryHandlerEnvironment.retryDownloadsItem() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val downloadProcessId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId, "Не удалось запустить повторную загрузку") {
            val infoDto = downloaderCommandFacade.retryDownloadProcess(userExternalId, downloadProcessId)
            bot.replaceMessage(chatId, messageId, "Запущена повторная загрузка")
            displayDownloadsItem(bot, chatId, infoDto, 0, DEFAULT_PAGE_SIZE)
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestExtractTrack() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val downloadProcessId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            serviceCommandFacade.registerActionEvent(
                type = ActionEventType.REQUEST_EXTRACT_TRACK,
                userExternalId = userExternalId,
                processId = downloadProcessId
            )
            bot.sendMessageToChat(chatId, MessageHelper.REQUEST_EXTRACT_TRACK_MESSAGE)
        }
    }
}