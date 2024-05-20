package ru.push.musicfeed.platform.external.telegram.handlers

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCallbackQuery
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.media.MediaHandlerEnvironment
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.files.Audio
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.network.bimap
import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.DownloaderCommandFacade
import ru.push.musicfeed.platform.application.MusicCollectionCommandFacade
import ru.push.musicfeed.platform.application.ServiceCommandFacade
import ru.push.musicfeed.platform.application.TrackFileAlreadyExistsException
import ru.push.musicfeed.platform.application.TrackFileCommandFacade
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.dto.ActionEventDto
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.application.dto.MusicPackTrackListDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.data.model.FileExternalType
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.FAIL
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.IN_PROGRESS
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.REQUESTED
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.SUCCESS
import ru.push.musicfeed.platform.external.telegram.BotProvider
import ru.push.musicfeed.platform.external.telegram.Callback
import ru.push.musicfeed.platform.external.telegram.HandleActionAudioMessage
import ru.push.musicfeed.platform.external.telegram.TrackFileSupportDispatcherHandlers
import ru.push.musicfeed.platform.external.telegram.HandleActionMessage
import ru.push.musicfeed.platform.external.telegram.MessageHelper
import ru.push.musicfeed.platform.external.telegram.MessageHelper.Companion.DOWNLOAD_STARTED_MESSAGE
import ru.push.musicfeed.platform.external.telegram.MessageHelper.Companion.REQUEST_DOWNLOAD_STUB_MESSAGE
import ru.push.musicfeed.platform.external.telegram.MessageHelper.Companion.REQUEST_IN_PROGRESS_STUB_MESSAGE
import ru.push.musicfeed.platform.external.telegram.deleteMessageFromChat
import ru.push.musicfeed.platform.external.telegram.formFailButtonText
import ru.push.musicfeed.platform.external.telegram.formMessage
import ru.push.musicfeed.platform.external.telegram.formProgressButtonText
import ru.push.musicfeed.platform.external.telegram.formSuccessButtonText
import ru.push.musicfeed.platform.external.telegram.formCompactTitle
import ru.push.musicfeed.platform.external.telegram.formFullTitle
import ru.push.musicfeed.platform.external.telegram.formMusicPackPublicationMessage
import ru.push.musicfeed.platform.external.telegram.formSuccessMessage
import ru.push.musicfeed.platform.external.telegram.getChatId
import ru.push.musicfeed.platform.external.telegram.getMessageId
import ru.push.musicfeed.platform.external.telegram.getUserExternalId
import ru.push.musicfeed.platform.external.telegram.handleTelegramResponseError
import ru.push.musicfeed.platform.external.telegram.replaceButtons
import ru.push.musicfeed.platform.external.telegram.sendMessageToChannel
import ru.push.musicfeed.platform.external.telegram.sendMessageToChat
import ru.push.musicfeed.platform.external.telegram.sendOrReplaceMessage
import ru.push.musicfeed.platform.util.getIndexOffset

@Component
class MusicPackHandlers(
    private val applicationProperties: ApplicationProperties,
    serviceCommandFacade: ServiceCommandFacade,
    trackFileCommandFacade: TrackFileCommandFacade,
    private val downloaderCommandFacade: DownloaderCommandFacade,
    private val musicCollectionCommandFacade: MusicCollectionCommandFacade,
) : TrackFileSupportDispatcherHandlers(serviceCommandFacade, trackFileCommandFacade) {

    companion object {
    }

    private val logger = KotlinLogging.logger {}

    override fun getMessageHandlers(): Map<ActionEventType, HandleActionMessage> {
        val map: MutableMap<ActionEventType, HandleActionMessage> = mutableMapOf()

        map[ActionEventType.REQUEST_MUSIC_PACK_EDIT_TITLE] = { actionEventDto: ActionEventDto? ->
            musicPackEditTitle(actionEventDto!!)
        }
        map[ActionEventType.REQUEST_MUSIC_PACK_EDIT_DESCRIPTION] = { actionEventDto: ActionEventDto? ->
            musicPackEditDescription(actionEventDto!!)
        }
        map[ActionEventType.REQUEST_MUSIC_PACK_EDIT_TAGS] = { actionEventDto: ActionEventDto? ->
            musicPackEditTags(actionEventDto!!)
        }
        map[ActionEventType.REQUEST_MUSIC_PACK_EDIT_COVER] = { actionEventDto: ActionEventDto? ->
            musicPackEditCover(actionEventDto!!)
        }

        map[ActionEventType.REQUEST_ADD_MUSIC_TRACK_TO_MUSIC_PACK] = { actionEventDto: ActionEventDto? ->
            addTrack(actionEventDto!!)
        }
        map[ActionEventType.REQUEST_REMOVE_MUSIC_TRACK_FROM_MUSIC_PACK] = { actionEventDto: ActionEventDto? ->
            removeTrack(actionEventDto?.musicPackId)
        }
        map[ActionEventType.REQUEST_EXTRACT_TRACK] = { actionEventDto: ActionEventDto? ->
            extractTrack(actionEventDto!!)
        }
        map[ActionEventType.REQUEST_EDIT_MUSIC_TRACK_DATA] = { actionEventDto: ActionEventDto? ->
            musicPackTrackEdit(actionEventDto!!)
        }
        map[ActionEventType.REQUEST_EDIT_MUSIC_PACK_TRACKLIST_ARTISTS] = { actionEventDto: ActionEventDto? ->
            musicPackEditTracklistArtists(actionEventDto!!)
        }
        map[ActionEventType.REQUEST_SELECT_TRACK_NEW_POSITION_FOR_CHANGE] = { actionEventDto: ActionEventDto? ->
            musicPackTrackListChangePosition(actionEventDto!!)
        }

        return map
    }

    override fun getAudioMessageHandlers(): Map<ActionEventType, HandleActionAudioMessage> {
        val map: MutableMap<ActionEventType, HandleActionAudioMessage> = mutableMapOf()
        map[ActionEventType.REQUEST_ADD_MUSIC_TRACK_TO_MUSIC_PACK] = { actionEventDto: ActionEventDto? ->
            addTrack(actionEventDto?.musicPackId!!, actionEventDto.messageId)
        }
        map[ActionEventType.ADDED_MUSIC_TRACK_TO_MUSIC_PACK] = { actionEventDto: ActionEventDto? ->
            addTrack(actionEventDto?.musicPackId!!, actionEventDto.messageId)
        }
        return map
    }

//    override fun getCommandHandlers(): Map<Command, HandleCommand> = mapOf(
//        Command.LIKE_TRACK to {
//            likeTrack()
//        }
//    )

    override fun getCallbackQueryHandlers(): Map<Callback, HandleCallbackQuery> = mapOf(
        Callback.GET_MUSIC_PACK to {
            getMusicPack()
        },

        Callback.GET_MUSIC_PACK_TRACK_LIST_PAGE to {
            getMusicPackTrackList()
        },
        Callback.GET_MUSIC_PACK_TRACK_LIST_BACK to {
            getMusicPackTrackList(getMessageId())
        },
        Callback.GET_MUSIC_PACK_TRACK_LIST_NEXT to {
            getMusicPackTrackList(getMessageId())
        },

        Callback.MUSIC_PACK_TRACK_LIST_SHOW_ADDITIONAL_BUTTONS to {
            getMusicPackTrackList(getMessageId(), true)
        },
        Callback.MUSIC_PACK_TRACK_LIST_HIDE_ADDITIONAL_BUTTONS to {
            getMusicPackTrackList(getMessageId())
        },

        Callback.MUSIC_PACK_TRACK_LIST_REQUEST_ADD to {
            requestAddTrack()
        },
        Callback.MUSIC_PACK_TRACK_LIST_ADD_TRACK to {
            addTrackById()
        },
        Callback.MUSIC_PACK_TRACK_LIST_REQUEST_REMOVE to {
            requestRemoveTrack()
        },
        Callback.MUSIC_PACK_TRACK_LIST_REMOVE to {
            removeTrack()
        },
        Callback.MUSIC_PACK_TRACK_LIST_CANCEL_REMOVE to {
            cancelRemoveTrack()
        },
        Callback.MUSIC_PACK_TRACK_LIST_REQUEST_EDIT to {
            requestMusicPackTrackEdit()
        },
        Callback.MUSIC_PACK_TRACK_LIST_EDIT_TRACK_SELECT to {
            musicPackTrackSelectForEdit()
        },
        Callback.MUSIC_PACK_TRACK_LIST_CANCEL_EDIT to {
            musicPackTrackListEditCancel()
        },
        Callback.MUSIC_PACK_TRACK_LIST_REQUEST_CHANGE_POSITION to {
            requestMusicPackTrackListChangePosition()
        },
        Callback.MUSIC_PACK_TRACK_LIST_CHANGE_POSITION_TRACK_SELECT to {
            musicPackTrackListChangePositionTrackSelect()
        },
        Callback.MUSIC_PACK_TRACK_LIST_CHANGE_POSITION_NUMBER_SELECT to {
            musicPackTrackListChangePositionNumberSelect()
        },
        Callback.MUSIC_PACK_TRACK_LIST_CANCEL_CHANGE_POSITION to {
            musicPackTrackListChangePositionCancel()
        },
        Callback.MUSIC_PACK_TRACK_LIST_DOWNLOAD_FILE to {
            requestMusicPackTrackListDownloadFile()
        },
        Callback.MUSIC_PACK_TRACK_LIST_PAGE_DOWNLOAD_FILES to {
            requestMusicPackTrackListPageDownloadFiles()
        },
        Callback.MUSIC_PACK_TRACK_LIST_REQUEST_EDIT_ARTIST_FOR_ALL to {
            requestMusicPackEditArtistForAllTracklist()
        },

        Callback.PUBLISH_MUSIC_PACK to {
            publishMusicPackToCollectionChannel()
        },

        Callback.MUSIC_PACK_DOWNLOAD_REQUEST to {
            requestMusicPackTrackListFullDownload()
        },
        Callback.MUSIC_PACK_EXTRACT_TRACK_REQUEST to {
            requestExtractTrack()
        },
        Callback.MUSIC_PACK_EDIT_TITLE_REQUEST to {
            requestMusicPackEdit(ActionEventType.REQUEST_MUSIC_PACK_EDIT_TITLE, "Отправьте новый текст для заголовка")
        },
        Callback.MUSIC_PACK_EDIT_DESCRIPTION_REQUEST to {
            requestMusicPackEdit(ActionEventType.REQUEST_MUSIC_PACK_EDIT_DESCRIPTION, "Отправьте новый текст для описания")
        },
        Callback.MUSIC_PACK_EDIT_TAGS_REQUEST to {
            requestMusicPackEdit(ActionEventType.REQUEST_MUSIC_PACK_EDIT_TAGS, "Отправьте список новых тегов через пробел")
        },
        Callback.MUSIC_PACK_EDIT_COVER_REQUEST to {
            requestMusicPackEdit(ActionEventType.REQUEST_MUSIC_PACK_EDIT_COVER, "Отправьте ссылку для новой обложки")
        },
    )


    private fun CallbackQueryHandlerEnvironment.getMusicPack() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val musicPackId = queryData[1].toLong()
        val collectionId = if (queryData.size > 2) queryData[2].toLong() else null

        executeWithTry(bot, chatId, userExternalId) {
            val musicPack = musicCollectionCommandFacade.getMusicPack(musicPackId, userExternalId)
            displayMusicPackWithControls(bot, chatId, messageId, musicPack, collectionId)
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestMusicPackTrackListFullDownload() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            val stubMessage = bot.sendMessageToChat(chatId, REQUEST_DOWNLOAD_STUB_MESSAGE)
            val processInfoList = downloaderCommandFacade.requestTrackListDownloadForMusicPack(
                userExternalId = userExternalId,
                musicPackId = musicPackId,
            )
            displayDownloadProcessInfoList(
                bot = bot,
                chatId = chatId,
                replacedMessageId = stubMessage.messageId,
                dtoList = processInfoList.dtoList.content,
                requestId = processInfoList.requestId.takeIf { processInfoList.dtoList.size > 1 }
            )
        }
    }

    private fun displayDownloadProcessInfo(bot: Bot, chatId: Long, processInfo: DownloadProcessInfoDto, replacedMessageId: Long? = null) {
        val buttons = listOf(formButtonForDownloadProcessStatus(processInfo))
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


    private fun CallbackQueryHandlerEnvironment.requestMusicPackEdit(actionEventType: ActionEventType, messageText: String) {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            val message = bot.sendMessageToChat(chatId, messageText)
            serviceCommandFacade.registerActionEvent(
                type = actionEventType,
                userExternalId = userExternalId,
                messageId = message.messageId,
                musicPackId = musicPackId
            )
        }
    }

    private fun MessageHandlerEnvironment.musicPackEditTitle(actionEventDto: ActionEventDto) {
        musicPackEdit(
            actionEventDto = actionEventDto,
            requestActionEventType = ActionEventType.REQUEST_MUSIC_PACK_EDIT_TITLE,
            targetActionEventType = ActionEventType.EDITED_MUSIC_PACK_DATA
        ) { userExternalId: Long, musicPackId: Long, text: String ->
            musicCollectionCommandFacade.musicPackEditTitle(userExternalId, musicPackId, text)
        }
    }

    private fun MessageHandlerEnvironment.musicPackEditDescription(actionEventDto: ActionEventDto) {
        musicPackEdit(
            actionEventDto = actionEventDto,
            requestActionEventType = ActionEventType.REQUEST_MUSIC_PACK_EDIT_DESCRIPTION,
            targetActionEventType = ActionEventType.EDITED_MUSIC_PACK_DATA
        ) { userExternalId: Long, musicPackId: Long, text: String ->
            musicCollectionCommandFacade.musicPackEditDescription(userExternalId, musicPackId, text)
        }
    }

    private fun MessageHandlerEnvironment.musicPackEditTags(actionEventDto: ActionEventDto) {
        musicPackEdit(
            actionEventDto = actionEventDto,
            requestActionEventType = ActionEventType.REQUEST_MUSIC_PACK_EDIT_TAGS,
            targetActionEventType = ActionEventType.EDITED_MUSIC_PACK_DATA
        ) { userExternalId: Long, musicPackId: Long, text: String ->
            musicCollectionCommandFacade.musicPackEditTags(userExternalId, musicPackId, text)
        }
    }

    private fun MessageHandlerEnvironment.musicPackEditCover(actionEventDto: ActionEventDto) {
        musicPackEdit(
            actionEventDto = actionEventDto,
            requestActionEventType = ActionEventType.REQUEST_MUSIC_PACK_EDIT_COVER,
            targetActionEventType = ActionEventType.EDITED_MUSIC_PACK_DATA
        ) { userExternalId: Long, musicPackId: Long, text: String ->
            musicCollectionCommandFacade.musicPackEditCover(userExternalId, musicPackId, text)
        }
    }

    private fun MessageHandlerEnvironment.musicPackEdit(
        actionEventDto: ActionEventDto,
        requestActionEventType: ActionEventType,
        targetActionEventType: ActionEventType,
        editFunc: (Long, Long, String) -> MusicPackDto
    ) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = actionEventDto.musicPackId!!
        val collectionId = actionEventDto.collectionId
        val text = message.text!!.trim()

        executeWithTry(bot, chatId, userExternalId) {
            val musicPack = editFunc(userExternalId, musicPackId, text)
            checkAndHandleRequestActionEvent(
                requestActionEventType = requestActionEventType,
                targetActionEventType = targetActionEventType,
                targetCollectionId = actionEventDto.collectionId,
                targetMusicPackId = actionEventDto.musicPackId,
            )
            displayMusicPackWithControls(
                bot = bot,
                chatId = chatId,
                musicPack = musicPack,
                collectionId = collectionId
            )
        }
    }


    private fun CallbackQueryHandlerEnvironment.getMusicPackTrackList(
        replacedMessageId: Long? = null,
        showAdditionalButtons: Boolean? = false,
    ) {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            displayMusicPackTrackList(
                bot = bot,
                chatId = chatId,
                replacedMessageId = replacedMessageId,
                showAdditionalButtons = showAdditionalButtons,
                musicPackId = musicPackId,
                userExternalId = userExternalId,
                page = page,
                size = size
            )
        }
    }

    private fun displayMusicPackTrackList(
        bot: Bot,
        chatId: Long,
        replacedMessageId: Long? = null,
        showAdditionalButtons: Boolean? = false,
        musicPackId: Long,
        userExternalId: Long,
        page: Int,
        size: Int
    ) {
        val musicPackTrackList = musicCollectionCommandFacade.getMusicPackTrackList(musicPackId, userExternalId, page, size)
        bot.sendOrReplaceMessage(
            chatId = chatId,
            replacedMessageId = replacedMessageId,
            text = musicPackTrackList.formMessage(),
            replyMarkup = InlineKeyboardMarkup.create(
                createMusicPackTrackListButtons(musicPackId, musicPackTrackList, showAdditionalButtons)
            ),
            disableWebPagePreview = true
        )
    }

    private fun createMusicPackTrackListButtons(
        musicPackId: Long,
        musicPackTrackList: MusicPackTrackListDto?,
        showAdditionalButtons: Boolean? = false
    ): MutableList<List<InlineKeyboardButton>> {
        val musicPackTracksPage = musicPackTrackList?.contentPage
        val page = musicPackTracksPage?.number ?: 0
        val size = musicPackTracksPage?.size ?: BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        if (musicPackTrackList?.musicPack?.editable != true)
            return mutableListOf()

        val mainActionButtons = mutableListOf(
            InlineKeyboardButton.CallbackData(
                text = Callback.MUSIC_PACK_TRACK_LIST_REQUEST_ADD.title,
                callbackData = formCallbackData(Callback.MUSIC_PACK_TRACK_LIST_REQUEST_ADD, musicPackId, page, size)
            )
        )
        if (musicPackTracksPage == null || musicPackTracksPage.isEmpty) {
            return mutableListOf(mainActionButtons)
        }
        mainActionButtons.add(
            InlineKeyboardButton.CallbackData(
                text = Callback.MUSIC_PACK_TRACK_LIST_PAGE_DOWNLOAD_FILES.title,
                callbackData = formCallbackData(Callback.MUSIC_PACK_TRACK_LIST_PAGE_DOWNLOAD_FILES, musicPackId, page, size)
            )
        )
        mainActionButtons.add(
            InlineKeyboardButton.CallbackData(
                text = Callback.MUSIC_PACK_TRACK_LIST_REQUEST_CHANGE_POSITION.title,
                callbackData = formCallbackData(Callback.MUSIC_PACK_TRACK_LIST_REQUEST_CHANGE_POSITION, musicPackId, page, size)
            )
        )

        val additionalActionButtons: MutableList<InlineKeyboardButton> = mutableListOf()
        if (showAdditionalButtons != true) {
            mainActionButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.MUSIC_PACK_TRACK_LIST_SHOW_ADDITIONAL_BUTTONS.title,
                    callbackData = formCallbackData(Callback.MUSIC_PACK_TRACK_LIST_SHOW_ADDITIONAL_BUTTONS, musicPackId, page, size)
                )
            )
        } else {
            mainActionButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.MUSIC_PACK_TRACK_LIST_HIDE_ADDITIONAL_BUTTONS.title,
                    callbackData = formCallbackData(Callback.MUSIC_PACK_TRACK_LIST_HIDE_ADDITIONAL_BUTTONS, musicPackId, page, size)
                )
            )
            additionalActionButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.MUSIC_PACK_TRACK_LIST_REQUEST_REMOVE.title,
                    callbackData = formCallbackData(Callback.MUSIC_PACK_TRACK_LIST_REQUEST_REMOVE, musicPackId, page, size)
                )
            )
            additionalActionButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.MUSIC_PACK_TRACK_LIST_REQUEST_EDIT.title,
                    callbackData = formCallbackData(Callback.MUSIC_PACK_TRACK_LIST_REQUEST_EDIT, musicPackId, page, size)
                )
            )
            additionalActionButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.MUSIC_PACK_TRACK_LIST_REQUEST_EDIT_ARTIST_FOR_ALL.title,
                    callbackData = formCallbackData(Callback.MUSIC_PACK_TRACK_LIST_REQUEST_EDIT_ARTIST_FOR_ALL, musicPackId, page, size)
                )
            )
        }

        val buttons: MutableList<List<InlineKeyboardButton>> = mutableListOf()
        val offset = musicPackTracksPage.getIndexOffset()
        val downloadButtons = formTrackListPageButtons(musicPackTracksPage.content, offset, Callback.MUSIC_PACK_TRACK_LIST_DOWNLOAD_FILE)
        buttons.addAll(downloadButtons)

        val navButtons: MutableList<InlineKeyboardButton> = mutableListOf()
        if (!musicPackTracksPage.isFirst) {
            navButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.GET_MUSIC_PACK_TRACK_LIST_BACK.title,
                    callbackData = formCallbackData(Callback.GET_MUSIC_PACK_TRACK_LIST_BACK, musicPackId, page - 1, size)
                )
            )
        }
        if (!musicPackTracksPage.isLast) {
            navButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.GET_MUSIC_PACK_TRACK_LIST_NEXT.title,
                    callbackData = formCallbackData(Callback.GET_MUSIC_PACK_TRACK_LIST_NEXT, musicPackId, page + 1, size)
                )
            )
        }

        buttons.add(navButtons)
        buttons.add(mainActionButtons)
        if (additionalActionButtons.isNotEmpty())
            buttons.add(additionalActionButtons)
        return buttons
    }

    private fun formTrackListPageButtons(
        tracks: List<MusicTrackDto>,
        offset: Int,
        callback: Callback,
        vararg additionalArgs: Any
    ): List<List<InlineKeyboardButton>> {
        return tracks.mapIndexed { index, musicTrack ->
            val number = offset + index + 1
            InlineKeyboardButton.CallbackData(
                text = "$number",
                callbackData = formCallbackData(callback, musicTrack.id!!, *additionalArgs)
            )
        }.toList().chunked(BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE / 2)
    }


    private fun CallbackQueryHandlerEnvironment.requestAddTrack() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val musicPackId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            bot.sendMessageToChat(chatId, "Отправьте аудиофайл, запрос для поиска или ссылку для скачивания")
            serviceCommandFacade.registerActionEvent(
                type = ActionEventType.REQUEST_ADD_MUSIC_TRACK_TO_MUSIC_PACK,
                userExternalId = userExternalId,
                musicPackId = musicPackId,
                messageId = messageId
            )
        }
    }

    private fun MessageHandlerEnvironment.addTrack(actionEventDto: ActionEventDto) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val searchText = message.text!!
        val musicPackId = actionEventDto.musicPackId!!
        val replacedMessageId = actionEventDto.messageId

        executeWithTry(bot, chatId, userExternalId) {
            addTrackToMusicPack(bot, chatId, userExternalId, searchText, musicPackId, replacedMessageId)
        }
    }

    private fun addTrackToMusicPack(
        bot: Bot,
        chatId: Long,
        userExternalId: Long,
        searchText: String,
        musicPackId: Long? = null,
        replacedMessageId: Long? = null,
    ) {
        val stubMessage = bot.sendMessageToChat(chatId, REQUEST_IN_PROGRESS_STUB_MESSAGE)
        val result = musicCollectionCommandFacade.addTrackToMusicPack(
            userExternalId,
            searchText,
            musicPackId
        )
        bot.deleteMessageFromChat(chatId, stubMessage.messageId)

        if (result.added) {
            val firstAddedTrack = result.tracks.first()
            val size = BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE
            val page = firstAddedTrack.position?.takeIf { it > size }?.let { it - size } ?: 0
            bot.sendMessageToChat(chatId, result.formSuccessMessage())
            if (musicPackId != null) {
                displayMusicPackTrackList(
                    bot = bot,
                    chatId = chatId,
                    musicPackId = musicPackId,
                    userExternalId = userExternalId,
                    replacedMessageId = replacedMessageId,
                    page = page,
                    size = size
                )
            }

        } else {
            val formCallback = { callback: Callback, trackId: Long ->
                musicPackId?.let { formCallbackData(callback, trackId, it) } ?: formCallbackData(callback, trackId)
            }
            val buttons = InlineKeyboardMarkup.create(
                result.tracks.map { track ->
                    listOf(
                        InlineKeyboardButton.CallbackData(
                            text = track.formCompactTitle(),
                            callbackData = formCallback(Callback.MUSIC_PACK_TRACK_LIST_ADD_TRACK, track.id!!)
                        )
                    )
                }
            )
            bot.sendMessageToChat(
                chatId = chatId,
                text = "Найдено несколько результатов, выберите подходящий",
                replyMarkup = buttons
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.addTrackById() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val trackId = queryData[1].toLong()
        val musicPackId = if (queryData.size > 2) queryData[2].toLong() else null
        val page = if (queryData.size > 3) queryData[3].toInt() else 0
        val size = if (queryData.size > 4) queryData[4].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            val addedTrack = musicCollectionCommandFacade.addTrackToMusicPackById(userExternalId, musicPackId, trackId)
            bot.sendMessageToChat(chatId, "Трек [<b>${addedTrack.title}</b>] добавлен в запись")
            if (musicPackId != null) {
                displayMusicPackTrackList(
                    bot = bot,
                    chatId = chatId,
                    musicPackId = musicPackId,
                    userExternalId = userExternalId,
                    replacedMessageId = messageId,
                    page = page,
                    size = size
                )
            }
        }
    }

    private fun CommandHandlerEnvironment.likeTrack() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        args.validateArgsCount(1)
        val searchText = args.joinToString(" ") { it }

        executeWithTry(bot, chatId, userExternalId) {
            addTrackToMusicPack(bot, chatId, userExternalId, searchText)
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestRemoveTrack() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val musicPackId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            val musicPackTrackList = musicCollectionCommandFacade.getMusicPackTrackList(musicPackId, userExternalId, page, size)
            replaceTrackListButtons(
                bot = bot,
                chatId = chatId,
                messageId = messageId,
                musicPackTrackList = musicPackTrackList,
                mainAction = Callback.MUSIC_PACK_TRACK_LIST_REMOVE,
                cancelAction = Callback.MUSIC_PACK_TRACK_LIST_CANCEL_REMOVE
            )
            bot.sendMessageToChat(chatId, "Выберите номер трека для удаления")
                .also {
                    serviceCommandFacade.registerActionEvent(
                        type = ActionEventType.REQUEST_REMOVE_MUSIC_TRACK_FROM_MUSIC_PACK,
                        userExternalId = userExternalId,
                        messageId = it.messageId,
                        musicPackId = musicPackId
                    )
                }
        }
    }

    private fun replaceTrackListButtons(
        bot: Bot,
        chatId: Long,
        messageId: Long,
        musicPackTrackList: MusicPackTrackListDto,
        mainAction: Callback,
        cancelAction: Callback,
    ) {
        val buttons: MutableList<List<InlineKeyboardButton>> = mutableListOf()
        val musicPackTracksPage = musicPackTrackList.contentPage
        val musicPackId = musicPackTrackList.musicPack.id!!
        val offset = musicPackTracksPage.getIndexOffset()
        val page = musicPackTracksPage.pageable.pageNumber
        val size = musicPackTracksPage.pageable.pageSize
        buttons.addAll(
            formTrackListPageButtons(musicPackTracksPage.content, offset, mainAction, musicPackId)
        )
        buttons.add(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = cancelAction.title,
                    callbackData = formCallbackData(cancelAction, musicPackId, page, size)
                )
            )
        )
        bot.replaceButtons(chatId, messageId, InlineKeyboardMarkup.create(buttons))
    }

    private fun replaceTrackListButtonsForChangePosition(
        bot: Bot,
        chatId: Long,
        messageId: Long,
        changedMusicTrackId: Long,
        musicPackTrackList: MusicPackTrackListDto,
    ) {
        val buttons: MutableList<List<InlineKeyboardButton>> = mutableListOf()
        val musicPackTracksPage = musicPackTrackList.contentPage
        val musicPackId = musicPackTrackList.musicPack.id!!
        val offset = musicPackTracksPage.getIndexOffset()
        val page = musicPackTracksPage.pageable.pageNumber
        val size = musicPackTracksPage.pageable.pageSize
        buttons.addAll(
            musicPackTracksPage.content
                .mapIndexed { index, _ ->
                    val number = offset + index + 1
                    InlineKeyboardButton.CallbackData(
                        text = "$number",
                        callbackData = formCallbackData(
                            Callback.MUSIC_PACK_TRACK_LIST_CHANGE_POSITION_NUMBER_SELECT,
                            changedMusicTrackId,
                            number - 1,
                            musicPackId
                        )
                    )
                }.toList()
                .chunked(BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE / 2)
        )
        buttons.add(
            listOf(
                InlineKeyboardButton.CallbackData(
                    text = Callback.MUSIC_PACK_TRACK_LIST_CANCEL_CHANGE_POSITION.title,
                    callbackData = formCallbackData(Callback.MUSIC_PACK_TRACK_LIST_CANCEL_CHANGE_POSITION, musicPackId, page, size)
                )
            )
        )
        bot.replaceButtons(chatId, messageId, InlineKeyboardMarkup.create(buttons))
    }


    private fun CallbackQueryHandlerEnvironment.removeTrack() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(3)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val musicTrackId = queryData[1].toLong()
        val musicPackId = queryData[2].toLong()
        val page = if (queryData.size > 3) queryData[3].toInt() else 0
        val size = if (queryData.size > 4) queryData[4].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            musicCollectionCommandFacade.removeTrackFromMusicPackByTrackId(userExternalId, musicPackId, musicTrackId)
            bot.deleteMessageFromChat(chatId, messageId)
            bot.sendMessageToChat(chatId, "Трек удален из записи")
            displayMusicPackTrackList(
                bot = bot,
                chatId = chatId,
                musicPackId = musicPackId,
                userExternalId = userExternalId,
                page = page,
                size = size
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.cancelRemoveTrack() {
        checkAndHandleRequestActionEvent(
            requestActionEventType = ActionEventType.REQUEST_REMOVE_MUSIC_TRACK_FROM_MUSIC_PACK,
            targetActionEventType = ActionEventType.ACTION_CANCELED,
        )

        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val musicPackId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            displayMusicPackTrackList(
                bot = bot,
                chatId = chatId,
                replacedMessageId = messageId,
                musicPackId = musicPackId,
                userExternalId = userExternalId,
                page = page,
                size = size
            )
        }
    }

    private fun MessageHandlerEnvironment.removeTrack(musicPackId: Long?) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val trackNumber = message.text!!.toInt()

        executeWithTry(bot, chatId, userExternalId) {
            musicCollectionCommandFacade.removeTrackFromMusicPackByNumber(
                userExternalId, musicPackId!!, trackNumber
            )
            bot.sendMessageToChat(chatId, "Трек удален из записи")
            displayMusicPackTrackList(
                bot = bot,
                chatId = chatId,
                musicPackId = musicPackId,
                userExternalId = userExternalId,
                page = 0,
                size = BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestMusicPackTrackEdit() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val musicPackId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            bot.sendMessageToChat(chatId, "Выберите номер трека для редактирования")
            val musicPackTrackList = musicCollectionCommandFacade.getMusicPackTrackList(musicPackId, userExternalId, page, size)
            replaceTrackListButtons(
                bot = bot,
                chatId = chatId,
                messageId = messageId,
                musicPackTrackList = musicPackTrackList,
                mainAction = Callback.MUSIC_PACK_TRACK_LIST_EDIT_TRACK_SELECT,
                cancelAction = Callback.MUSIC_PACK_TRACK_LIST_CANCEL_EDIT
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.musicPackTrackSelectForEdit() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(3)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicTrackId = queryData[1].toLong()
        val musicPackId = queryData[2].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            val trackData = musicCollectionCommandFacade.getMusicPackTrackData(
                userExternalId = userExternalId,
                musicPackId = musicPackId,
                musicTrackId = musicTrackId
            )
            bot.sendMessageToChat(
                chatId = chatId,
                text = """
                Отправьте построчно новые название для трека, имя артиста (не обязательно) и название альбома (не обязательно)
                Трек для редактирования:
                <code>${trackData.title}</code>
                ${trackData.artists?.joinToString("; ") { it.name }?.takeIf { it.isNotBlank() }?.let { "<code>$it</code>" } ?: ""}
                ${trackData.album?.let { "<code>${it.title}</code>" } ?: ""}
            """.trimIndent()
            ).also {
                serviceCommandFacade.registerActionEvent(
                    type = ActionEventType.REQUEST_EDIT_MUSIC_TRACK_DATA,
                    userExternalId = userExternalId,
                    messageId = it.messageId,
                    musicPackId = musicPackId,
                    musicTrackId = musicTrackId
                )
            }
        }
    }

    private fun MessageHandlerEnvironment.musicPackTrackEdit(actionEventDto: ActionEventDto) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = actionEventDto.musicPackId!!
        val musicTrackId = actionEventDto.eventDataId!!
        val page = 0
        val size = BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId, "Не удалось изменить данные трека") {
            val newData = message.text!!.split(Regex("\\n")).map { it.trim() }
            if (newData.isEmpty())
                throw IllegalArgumentException("Must be at least 1 entry")

            musicCollectionCommandFacade.editMusicPackTrackData(
                userExternalId = userExternalId,
                musicPackId = musicPackId,
                musicTrackId = musicTrackId,
                newTrackTitle = newData[0],
                newArtistName = newData.getOrNull(1),
                newAlbumName = newData.getOrNull(2),
            )
            checkAndHandleRequestActionEvent(
                requestActionEventType = ActionEventType.REQUEST_EDIT_MUSIC_TRACK_DATA,
                targetActionEventType = ActionEventType.EDITED_MUSIC_TRACK_DATA,
            )
            displayMusicPackTrackList(
                bot = bot,
                chatId = chatId,
                musicPackId = musicPackId,
                userExternalId = userExternalId,
                page = page,
                size = size
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.musicPackTrackListEditCancel() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val musicPackId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            serviceCommandFacade.registerActionEvent(
                type = ActionEventType.ACTION_CANCELED,
                userExternalId = userExternalId,
            )
            displayMusicPackTrackList(
                bot = bot,
                chatId = chatId,
                replacedMessageId = messageId,
                musicPackId = musicPackId,
                userExternalId = userExternalId,
                page = page,
                size = size
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestMusicPackEditArtistForAllTracklist() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            bot.sendMessageToChat(
                chatId = chatId,
                text = "Отправьте новое имя артиста (если несколько, разделить символом <code>;</code>) для треклиста"
            ).also {
                serviceCommandFacade.registerActionEvent(
                    type = ActionEventType.REQUEST_EDIT_MUSIC_PACK_TRACKLIST_ARTISTS,
                    userExternalId = userExternalId,
                    messageId = it.messageId,
                    musicPackId = musicPackId,
                )
            }
        }
    }

    private fun MessageHandlerEnvironment.musicPackEditTracklistArtists(actionEventDto: ActionEventDto) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = actionEventDto.musicPackId!!
        val messageText = message.text!!
        val page = 0
        val size = BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId, "Не удалось изменить данные") {
            checkAndHandleRequestActionEvent(
                requestActionEventType = ActionEventType.REQUEST_EDIT_MUSIC_PACK_TRACKLIST_ARTISTS,
                targetActionEventType = ActionEventType.EDITED_MUSIC_PACK_TRACKLIST_DATA,
            )
            musicCollectionCommandFacade.editMusicPackTracklistData(
                userExternalId = userExternalId,
                musicPackId = musicPackId,
                newArtistName = messageText
            )
            displayMusicPackTrackList(
                bot = bot,
                chatId = chatId,
                musicPackId = musicPackId,
                userExternalId = userExternalId,
                page = page,
                size = size
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestMusicPackTrackListChangePosition() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val musicPackId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            bot.sendMessageToChat(chatId, "Выберите номер трека для перемещения в плейлисте")
                .also {
                    serviceCommandFacade.registerActionEvent(
                        type = ActionEventType.REQUEST_SELECT_TRACK_FOR_POSITION_CHANGE,
                        userExternalId = userExternalId,
                        messageId = it.messageId,
                        musicPackId = musicPackId
                    )
                }
            val musicPackTrackList = musicCollectionCommandFacade.getMusicPackTrackList(musicPackId, userExternalId, page, size)
            replaceTrackListButtons(
                bot = bot,
                chatId = chatId,
                messageId = messageId,
                musicPackTrackList = musicPackTrackList,
                mainAction = Callback.MUSIC_PACK_TRACK_LIST_CHANGE_POSITION_TRACK_SELECT,
                cancelAction = Callback.MUSIC_PACK_TRACK_LIST_CANCEL_CHANGE_POSITION
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.musicPackTrackListChangePositionTrackSelect() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(3)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val musicTrackId = queryData[1].toLong()
        val musicPackId = queryData[2].toLong()
        val page = if (queryData.size > 3) queryData[3].toInt() else 0
        val size = if (queryData.size > 4) queryData[4].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            val trackData = musicCollectionCommandFacade.getMusicPackTrackData(
                userExternalId = userExternalId,
                musicPackId = musicPackId,
                musicTrackId = musicTrackId
            )
            val message = bot.sendMessageToChat(
                chatId = chatId,
                text = "Выберите номер позиции для трека <b>${trackData.formFullTitle()}</b> или отправьте номер сообщением"
            )
            serviceCommandFacade.registerActionEvent(
                type = ActionEventType.REQUEST_SELECT_TRACK_NEW_POSITION_FOR_CHANGE,
                userExternalId = userExternalId,
                messageId = message.messageId,
                musicPackId = musicPackId,
                musicTrackId = musicTrackId
            )
            val musicPackTrackList = musicCollectionCommandFacade.getMusicPackTrackList(musicPackId, userExternalId, page, size)
            replaceTrackListButtonsForChangePosition(
                bot = bot,
                chatId = chatId,
                messageId = messageId,
                changedMusicTrackId = musicTrackId,
                musicPackTrackList = musicPackTrackList
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.musicPackTrackListChangePositionNumberSelect() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(4)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val musicTrackId = queryData[1].toLong()
        val musicTrackNumber = queryData[2].toInt()
        val musicPackId = queryData[3].toLong()
        val page = if (queryData.size > 4) queryData[4].toInt() else 0
        val size = if (queryData.size > 5) queryData[5].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            musicCollectionCommandFacade.changeMusicPackTrackPosition(
                userExternalId = userExternalId,
                musicPackId = musicPackId,
                musicTrackId = musicTrackId,
                newPosition = musicTrackNumber,
            )
            checkAndHandleRequestActionEvent(
                requestActionEventType = ActionEventType.REQUEST_SELECT_TRACK_FOR_POSITION_CHANGE,
                targetActionEventType = ActionEventType.CHANGED_MUSIC_TRACK_POSITION,
            )
            bot.deleteMessageFromChat(chatId, messageId)
            bot.sendMessageToChat(chatId, "Трек успешно перемещен на позицию <b>${musicTrackNumber + 1}</b>")
            displayMusicPackTrackList(
                bot = bot,
                chatId = chatId,
                musicPackId = musicPackId,
                userExternalId = userExternalId,
                page = page,
                size = size
            )
        }
    }

    private fun MessageHandlerEnvironment.musicPackTrackListChangePosition(actionEventDto: ActionEventDto) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = actionEventDto.musicPackId!!
        val musicTrackId = actionEventDto.eventDataId!!
        val messageId = actionEventDto.messageId!!
        val messageText = message.text!!
        val page = 0
        val size = BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            val musicTrackNumber = messageText.toInt() - 1
            musicCollectionCommandFacade.changeMusicPackTrackPosition(
                userExternalId = userExternalId,
                musicPackId = musicPackId,
                musicTrackId = musicTrackId,
                newPosition = musicTrackNumber,
            )
            checkAndHandleRequestActionEvent(
                requestActionEventType = ActionEventType.REQUEST_SELECT_TRACK_FOR_POSITION_CHANGE,
                targetActionEventType = ActionEventType.CHANGED_MUSIC_TRACK_POSITION,
            )
            bot.deleteMessageFromChat(chatId, messageId)
            bot.sendMessageToChat(chatId, "Трек успешно перемещен на позицию <b>${musicTrackNumber + 1}</b>")
            displayMusicPackTrackList(
                bot = bot,
                chatId = chatId,
                musicPackId = musicPackId,
                userExternalId = userExternalId,
                page = page,
                size = size
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.musicPackTrackListChangePositionCancel() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageId = getMessageId()
        val musicPackId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            serviceCommandFacade.registerActionEvent(
                type = ActionEventType.ACTION_CANCELED,
                userExternalId = userExternalId,
            )
            displayMusicPackTrackList(
                bot = bot,
                chatId = chatId,
                replacedMessageId = messageId,
                musicPackId = musicPackId,
                userExternalId = userExternalId,
                page = page,
                size = size
            )
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestMusicPackTrackListDownloadFile() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicTrackId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            val trackFileInfo = musicCollectionCommandFacade.getTrackFileInfoForMusicTrack(userExternalId, musicTrackId)
            if (trackFileInfo != null) {
                sendTrackFileToChat(bot, chatId, userExternalId, trackFileInfo)
            } else {
                val processInfo = downloaderCommandFacade.requestSingleTrackDownloadForMusicTrack(userExternalId, musicTrackId)
                if (processInfo.status == SUCCESS) {
                    val infoDto = downloaderCommandFacade.getDownloadedTrackFileInfo(processInfo.downloadProcessId)
                    sendTrackFileToChat(bot, chatId, userExternalId, infoDto)
                    musicCollectionCommandFacade.storeMusicTrackLocalFileInfo(userExternalId, musicTrackId, infoDto.trackLocalFileId)
                } else {
                    displayDownloadProcessInfo(bot, chatId, processInfo)
                }
            }
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestMusicPackTrackListPageDownloadFiles() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = queryData[1].toLong()
        val page = if (queryData.size > 2) queryData[2].toInt() else 0
        val size = if (queryData.size > 3) queryData[3].toInt() else BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            val stubMessage = bot.sendMessageToChat(chatId, REQUEST_DOWNLOAD_STUB_MESSAGE)
            val musicPackTracksFileInfo = musicCollectionCommandFacade.getTrackFileInfosForMusicPackTrackList(
                userExternalId = userExternalId,
                musicPackId = musicPackId,
                page = page,
                size = size
            )
            val trackListFileInfo = musicPackTracksFileInfo.trackListFileInfo
            if (trackListFileInfo.isNotEmpty()) {
                bot.deleteMessageFromChat(chatId, stubMessage.messageId)
                sendTrackFileGroupToChat(bot, chatId, userExternalId, musicPackTracksFileInfo)
            } else {
                val processInfoList = downloaderCommandFacade.requestTrackListDownloadForMusicPackTrackListPage(
                    userExternalId = userExternalId,
                    musicPackId = musicPackId,
                    page = page,
                    size = size
                )
                displayDownloadProcessInfoList(
                    bot = bot,
                    chatId = chatId,
                    replacedMessageId = stubMessage.messageId,
                    dtoList = processInfoList.dtoList.content,
                    requestId = processInfoList.requestId.takeIf { processInfoList.dtoList.size > 1 }
                )
            }
        }
    }

    private fun displayDownloadProcessInfoList(
        bot: Bot,
        chatId: Long,
        replacedMessageId: Long? = null,
        requestId: Long? = null,
        dtoList: List<DownloadProcessInfoDto>,
    ) {
        val buttons = dtoList.map { infoDto -> listOf(formButtonForDownloadProcessStatus(infoDto, requestId)) }.toMutableList()
        if (requestId != null) {
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

    private fun CallbackQueryHandlerEnvironment.publishMusicPackToCollectionChannel() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(3)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = queryData[1].toLong()
        val collectionId = queryData[2].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            val collectionInfo = musicCollectionCommandFacade.getCollection(userExternalId, collectionId)
            if (collectionInfo.channelName?.isNotBlank() != true) {
                bot.sendMessageToChat(chatId, "Для публикации записи необходимо привязать канал к коллекции через настройки")
                return@executeWithTry
            }
            val channelName = collectionInfo.channelName

            val stubMessage = bot.sendMessageToChat(chatId, REQUEST_IN_PROGRESS_STUB_MESSAGE)
            val musicPackTracksFileInfo = musicCollectionCommandFacade.getTrackFileInfosForMusicPackTrackList(
                userExternalId = userExternalId,
                musicPackId = musicPackId,
                page = 0,
                size = 50,
                needCountChecks = false
            )
            val musicPack = musicPackTracksFileInfo.musicPack
            bot.sendMessageToChannel(
                channelName = channelName,
                text = musicPack.formMusicPackPublicationMessage()
            )
            sendTrackFileGroupToChannel(bot, channelName, userExternalId, musicPackTracksFileInfo)

            bot.sendOrReplaceMessage(chatId, stubMessage.messageId, "Запись успешно опубликована в канале <b>@$channelName</b>")
        }
    }

    private fun CallbackQueryHandlerEnvironment.requestExtractTrack() {
        val queryData = callbackQuery.data.split(BotProvider.CALLBACK_DATA_DELIMITER)
        queryData.validateArgsCount(2)

        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = queryData[1].toLong()

        executeWithTry(bot, chatId, userExternalId) {
            serviceCommandFacade.registerActionEvent(
                type = ActionEventType.REQUEST_EXTRACT_TRACK,
                userExternalId = userExternalId,
                musicPackId = musicPackId
            )
            bot.sendMessageToChat(chatId, MessageHelper.REQUEST_EXTRACT_TRACK_MESSAGE)
        }
    }

    private fun MessageHandlerEnvironment.extractTrack(actionEventDto: ActionEventDto) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val musicPackId = actionEventDto.musicPackId
        val downloadProcessId = actionEventDto.eventDataId

        val request = message.text!!.parseExtractTrackRequest()
            ?: return

        executeWithTry(bot, chatId, userExternalId, "Не удалось извлечь трек") {
            try {
                val trackFileInfo = trackFileCommandFacade.extractPartFromTrackFile(
                    userExternalId = userExternalId,
                    musicPackId = musicPackId,
                    downloadProcessId = downloadProcessId,
                    targetTrackTitle = request.trackTitle,
                    startTimestamp = request.startTimestamp,
                    endTimestamp = request.endTimestamp
                )
                sendTrackFileToChat(bot, chatId, userExternalId, trackFileInfo)
            } catch (ex: TrackFileAlreadyExistsException) {
                bot.sendMessageToChat(chatId, "Такой трек уже существует, выберите другое название или задайте другой интервал")
            }
        }
    }

    private fun String.parseExtractTrackRequest(): ExtractTrackRequest? {
        val requestText = this.trim()
        return try {
            val tsSeparatorIndex = requestText.indexOf(' ')
            val startTimestamp = requestText.substring(0, tsSeparatorIndex)
            val titleSeparatorIndex = requestText.indexOf(' ', tsSeparatorIndex + 1)
            val endTimestamp: String
            val trackTitle: String?
            if (titleSeparatorIndex < 0) {
                endTimestamp = requestText.substring(tsSeparatorIndex + 1)
                trackTitle = null
            } else {
                endTimestamp = requestText.substring(tsSeparatorIndex + 1, titleSeparatorIndex)
                trackTitle = requestText.substring(titleSeparatorIndex + 1)
            }
            ExtractTrackRequest(startTimestamp, endTimestamp, trackTitle)
        } catch (ex: Throwable) {
            logger.info { "Invalid extract track request: $requestText" }
            null
        }
    }

    private data class ExtractTrackRequest(
        val startTimestamp: String,
        val endTimestamp: String,
        val trackTitle: String?
    )

    private fun MediaHandlerEnvironment<Audio>.addTrack(musicPackId: Long, replacedMessageId: Long?) {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()
        val messageAudio = message.audio!!
        val fileExternalId = messageAudio.fileId
        val page = 0
        val size = BotProvider.DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE

        executeWithTry(bot, chatId, userExternalId) {
            val audioFile = bot.getFile(fileExternalId).bimap(
                { it?.result!! },
                { it.handleTelegramResponseError() }
            )
            val audioFileUrl = formBotFileUrl(audioFile.filePath!!)
            val trackTitle = messageAudio.title ?: messageAudio.fileName
            trackFileCommandFacade.storeTrackFileFromExternalSource(
                userExternalId = userExternalId,
                fileExternalId = fileExternalId,
                fileExternalType = FileExternalType.TELEGRAM,
                fileExternalUrl = audioFileUrl,
                trackArtistName = messageAudio.performer,
                trackTitle = trackTitle,
                trackDurationSec = messageAudio.duration,
                musicPackId = musicPackId
            )
            bot.sendMessageToChat(chatId, "Трек [<b>$trackTitle</b>] добавлен в запись")
            displayMusicPackTrackList(
                bot = bot,
                chatId = chatId,
                musicPackId = musicPackId,
                userExternalId = userExternalId,
                replacedMessageId = replacedMessageId,
                page = page,
                size = size
            )
        }
    }

    private fun formBotFileUrl(filePath: String): String {
        val matchResult = Regex(".*(/music/.*)").find(filePath)
            ?: throw IllegalArgumentException("Invalid file path: $filePath")
        val relativeFilePath = matchResult.groupValues[1]
        return applicationProperties.telegramBot.let { "${it.apiUrl}/file/bot${it.token}${relativeFilePath}" }
    }

}
