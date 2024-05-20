package ru.push.musicfeed.platform.external.telegram

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCallbackQuery
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCommand
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.media.MediaHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.files.Audio
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import kotlinx.coroutines.launch
import mu.KotlinLogging
import ru.push.musicfeed.platform.application.ExternalSourceSearchNotFoundException
import ru.push.musicfeed.platform.application.ServiceCommandFacade
import ru.push.musicfeed.platform.application.TrackFileCommandFacade
import ru.push.musicfeed.platform.application.TrackLocalFileNotFoundException
import ru.push.musicfeed.platform.application.UserNotFoundException
import ru.push.musicfeed.platform.application.dto.ActionEventDto
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.application.dto.MusicPackTracksFileInfo
import ru.push.musicfeed.platform.application.dto.TrackFileInfoDto
import ru.push.musicfeed.platform.coroutines.TelegramBotScope
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.external.telegram.BotProvider.Companion.DEFAULT_COLLECTION_CONTENT_PAGE_SIZE


typealias HandleActionMessage = MessageHandlerEnvironment.(actionEventDto: ActionEventDto?) -> Unit
typealias HandleActionAudioMessage = MediaHandlerEnvironment<Audio>.(actionEventDto: ActionEventDto?) -> Unit


abstract class AbstractDispatcherHandlers(
    val serviceCommandFacade: ServiceCommandFacade
) {
    companion object {
        private const val CALLBACK_DATA_LENGTH_LIMIT = 32  // 64 bytes by documentation, string char = 2 bytes

        const val CALLBACK_DATA_DELIMITER = "/"
        const val CALLBACK_DATA_LIST_SEPARATOR = ","

        // todo: move to applicationProperties.telegram
        val SUPPRESSED_ERRORS_KEYWORDS = listOf(
            "Bad Request: message is not modified"
        )
    }

    val telegramBotScope = TelegramBotScope

    private val logger = KotlinLogging.logger {}

    open fun getMessageHandlers(): Map<ActionEventType, HandleActionMessage> = mapOf()

    open fun getAudioMessageHandlers(): Map<ActionEventType, HandleActionAudioMessage> = mapOf()

    open fun getCommandHandlers(): Map<Command, HandleCommand> = mapOf()

    open fun getCallbackQueryHandlers(): Map<Callback, HandleCallbackQuery> = mapOf()


    internal fun executeWithTry(bot: Bot, chatId: Long, userExternalId: Long, body: () -> Unit) {
        executeWithTry(bot, chatId, userExternalId, "Ошибка при выполнении операции", body)
    }

    internal fun executeWithTry(bot: Bot, channelName: String, userExternalId: Long, errorMessage: String, body: () -> Unit) {
        executeWithTry(bot, ChatId.fromChannelUsername(channelName), userExternalId, errorMessage, body)
    }

    internal fun executeWithTry(bot: Bot, chatId: Long, userExternalId: Long, errorMessage: String, body: () -> Unit) {
        executeWithTry(bot, ChatId.fromId(chatId), userExternalId, errorMessage, body)
    }

    internal fun executeWithTry(bot: Bot, chatId: ChatId, userExternalId: Long, errorMessage: String, body: () -> Unit) {
        telegramBotScope.launch {
            try {
                body()
            } catch (ex: TelegramError) {
                logger.error(ex) { "BotProvider Telegram error: $ex" }
                if (!needSuppressError(ex.errorDescription)) {
                    serviceCommandFacade.registerActionEvent(
                        type = ActionEventType.EXCEPTION,
                        userExternalId = userExternalId
                    )
                    bot.sendMessageToChat(chatId, errorMessage)
                }

            } catch (ex: UserNotFoundException) {
                logger.error(ex) { "BotProvider UserNotFoundException: $ex" }
                bot.sendMessageToChat(chatId, "Ошибка доступа, обратитесь к администратору")

            } catch (ex: TrackLocalFileNotFoundException) {
                logger.error(ex) { "BotProvider TrackLocalFileNotFoundException: $ex" }
                if (ex.musicTrackId != null) {
                    bot.sendMessageToChat(chatId, "Ошибка в данных трека, необходимо сбросить, ИД:\n${ex.musicTrackId}")
                } else {
                    // todo сделать автоматическую перезагрузку
                    bot.sendMessageToChat(chatId, "Ошибка в данных трека, необходимо перезагрузить")
                }
                serviceCommandFacade.registerActionEvent(
                    type = ActionEventType.EXCEPTION,
                    userExternalId = userExternalId
                )

            } catch (ex: ExternalSourceSearchNotFoundException) {
                logger.info { "ExternalSourceSearchNotFoundException: $ex" }
                bot.sendMessageToChat(chatId, "По запросу ничего не найдено")

            } catch (ex: Exception) {
                bot.sendMessageToChat(chatId, errorMessage)
                logger.error(ex) { "BotProvider Exception: $ex" }
                serviceCommandFacade.registerActionEvent(
                    type = ActionEventType.EXCEPTION,
                    userExternalId = userExternalId
                )
            }
        }
    }

    private fun needSuppressError(errorDescription: String?): Boolean {
        return errorDescription != null && SUPPRESSED_ERRORS_KEYWORDS.any { errorDescription.contains(it, true) }
    }

    fun formCallbackData(callback: Callback, vararg args: Any): String =
        formCallbackData(callback.value, *args)

    private fun formCallbackData(vararg args: Any): String {
        if (args.find { it.toString().contains(CALLBACK_DATA_DELIMITER) } != null) {
            logger.error { "Args contains invalid forbidden char '$CALLBACK_DATA_DELIMITER', args: $args" }
            throw IllegalArgumentException()
        }
        return args.joinToString(CALLBACK_DATA_DELIMITER) { it.toString() }
            .also {
                if (it.length >= CALLBACK_DATA_LENGTH_LIMIT)
                    logger.warn { "Potential problem callback data, length - ${it.length}, data - $it" }
            }
    }

    internal fun List<MusicPackDto>.toInlineKeyboardButtons(
        offset: Int,
        callback: Callback,
        vararg additionalArgs: Any
    ): List<List<InlineKeyboardButton>> {
        return mapIndexed { index, musicPack ->
            InlineKeyboardButton.CallbackData(
                text = "${offset + index + 1}",
                callbackData = formCallbackData(callback, musicPack.id!!, *additionalArgs)
            )
        }
            .toList()
            .chunked(DEFAULT_COLLECTION_CONTENT_PAGE_SIZE / 2)
    }

    internal fun displayMusicPackWithControls(
        bot: Bot,
        chatId: Long,
        replacedMessageId: Long? = null,
        musicPack: MusicPackDto,
        collectionId: Long?
    ) {
        val musicPackId = musicPack.id!!
        val buttons: MutableList<MutableList<InlineKeyboardButton>> = mutableListOf()

        val topButtons: MutableList<InlineKeyboardButton> = mutableListOf(
            InlineKeyboardButton.CallbackData(
                text = Callback.GET_MUSIC_PACK_TRACK_LIST_PAGE.title,
                callbackData = formCallbackData(Callback.GET_MUSIC_PACK_TRACK_LIST_PAGE, musicPackId)
            ),
            InlineKeyboardButton.CallbackData(
                text = Callback.MUSIC_PACK_DOWNLOAD_REQUEST.title,
                callbackData = formCallbackData(Callback.MUSIC_PACK_DOWNLOAD_REQUEST, musicPackId)
            )
        )
        if (collectionId != null) {
            topButtons.add(
                InlineKeyboardButton.CallbackData(
                    text = Callback.PUBLISH_MUSIC_PACK.title,
                    callbackData = formCallbackData(Callback.PUBLISH_MUSIC_PACK, musicPackId, collectionId)
                ),
            )
//            topButtons.add(
//                InlineKeyboardButton.CallbackData(
//                    text = Callback.RETURN_TO_COLLECTION_CONTENT.title,
//                    callbackData = formCallbackData(Callback.RETURN_TO_COLLECTION_CONTENT, collectionId)
//                )
//            )
        }
        buttons.add(topButtons)

        if (musicPack.editable == true) {
            buttons.add(
                mutableListOf(
                    InlineKeyboardButton.CallbackData(
                        text = Callback.MUSIC_PACK_EDIT_TITLE_REQUEST.title,
                        callbackData = formCallbackData(Callback.MUSIC_PACK_EDIT_TITLE_REQUEST, musicPackId)
                    ),
                    InlineKeyboardButton.CallbackData(
                        text = Callback.MUSIC_PACK_EDIT_DESCRIPTION_REQUEST.title,
                        callbackData = formCallbackData(Callback.MUSIC_PACK_EDIT_DESCRIPTION_REQUEST, musicPackId)
                    ),
                    InlineKeyboardButton.CallbackData(
                        text = Callback.MUSIC_PACK_EDIT_TAGS_REQUEST.title,
                        callbackData = formCallbackData(Callback.MUSIC_PACK_EDIT_TAGS_REQUEST, musicPackId)
                    ),
                    InlineKeyboardButton.CallbackData(
                        text = Callback.MUSIC_PACK_EDIT_COVER_REQUEST.title,
                        callbackData = formCallbackData(Callback.MUSIC_PACK_EDIT_COVER_REQUEST, musicPackId)
                    ),
                )
            )
        }

        bot.sendOrReplaceMessage(
            chatId = chatId,
            text = musicPack.formMusicPackFullMessage(),
            replyMarkup = InlineKeyboardMarkup.create(buttons),
//            replacedMessageId = replacedMessageId
        )
    }


    fun CallbackQueryHandlerEnvironment.checkAndHandleRequestActionEvent(
        requestActionEventType: ActionEventType,
        targetActionEventType: ActionEventType,
        targetMessageId: Long? = null,
        targetCollectionId: Long? = null,
        targetMusicPackId: Long? = null,
    ) {
        val userExternalId = getUserExternalId()
        serviceCommandFacade.getLastActionEvent(userExternalId)
            ?.takeIf { it.type == requestActionEventType }
            ?.let { requestActionMessage ->
                serviceCommandFacade.registerActionEvent(
                    type = targetActionEventType,
                    userExternalId = userExternalId,
                    messageId = targetMessageId,
                    collectionId = targetCollectionId,
                    musicPackId = targetMusicPackId
                )
                requestActionMessage.messageId?.let {
                    bot.deleteMessage(ChatId.fromId(getChatId()), it)
                }
            }
    }

    fun MessageHandlerEnvironment.checkAndHandleRequestActionEvent(
        requestActionEventType: ActionEventType,
        targetActionEventType: ActionEventType,
        targetMessageId: Long? = null,
        targetCollectionId: Long? = null,
        targetMusicPackId: Long? = null,
    ) {
        val userExternalId = getUserExternalId()
        serviceCommandFacade.getLastActionEvent(userExternalId)
            ?.takeIf { it.type == requestActionEventType }
            ?.let { requestActionEvent ->
                serviceCommandFacade.registerActionEvent(
                    type = targetActionEventType,
                    userExternalId = userExternalId,
                    messageId = targetMessageId,
                    collectionId = targetCollectionId,
                    musicPackId = targetMusicPackId
                )
                requestActionEvent.messageId?.let {
                    bot.deleteMessage(ChatId.fromId(getChatId()), it)
                }
            }
    }

    fun List<Any>.validateArgsCount(minCount: Int) {
        if (this.size < minCount)
            throw IllegalArgumentException("Must be at least $minCount args")
    }

    fun Message.audioFileId(): String? = this.audio?.fileId

}

abstract class TrackFileSupportDispatcherHandlers(
    serviceCommandFacade: ServiceCommandFacade,
    val trackFileCommandFacade: TrackFileCommandFacade
) : AbstractDispatcherHandlers(serviceCommandFacade) {

    internal fun sendTrackFileToChat(bot: Bot, chatId: Long, userExternalId: Long, infoDto: TrackFileInfoDto) {
        sendTrackFile(bot, ChatId.fromId(chatId), userExternalId, infoDto)
    }

    internal fun sendTrackFile(bot: Bot, chatId: ChatId, userExternalId: Long, infoDto: TrackFileInfoDto) {
        val fileExternalId = infoDto.fileExternalId
        if (fileExternalId != null) {
            bot.sendAudio(
                chatId = chatId,
                infoDto = infoDto
            )
        } else {
            val fileSendingMessage = bot.sendMessageToChat(chatId, infoDto.formFileSendingMessage())
            telegramBotScope.launch {
                executeWithTry(bot, chatId, userExternalId, "Произошла ошибка при отправке файла, попробуйте ещё раз") {
                    val resultMessage = bot.sendAudio(
                        chatId = chatId,
                        infoDto = infoDto
                    )
                    bot.deleteMessageFromChat(chatId, fileSendingMessage.messageId)
                    resultMessage.audioFileId()
                        ?.let { trackFileCommandFacade.saveTelegramFileId(infoDto.trackLocalFileId, it) }
                }
            }
        }
    }

    internal fun sendTrackFileGroupToChat(bot: Bot, chatId: Long, userExternalId: Long, musicPackTracksFileInfo: MusicPackTracksFileInfo) {
        sendTrackFileGroup(bot, ChatId.fromId(chatId), userExternalId, musicPackTracksFileInfo)
    }

    internal fun sendTrackFileGroupToChannel(bot: Bot, channelName: String, userExternalId: Long, musicPackTracksFileInfo: MusicPackTracksFileInfo) {
        sendTrackFileGroup(bot, ChatId.fromChannelUsername(channelName), userExternalId, musicPackTracksFileInfo)
    }

    internal fun sendTrackFileGroup(bot: Bot, chatId: ChatId, userExternalId: Long, musicPackTracksFileInfo: MusicPackTracksFileInfo) {
        val trackListFileInfo = musicPackTracksFileInfo.trackListFileInfo
        val allHasExternalId = trackListFileInfo.all { it.fileExternalId != null }
        if (!allHasExternalId || trackListFileInfo.size <= 1) {
            trackListFileInfo.forEach { sendTrackFile(bot, chatId, userExternalId, it) }
            return
        }

        val musicPack = musicPackTracksFileInfo.musicPack

        val messages = trackListFileInfo.chunked(10)
            .flatMap {
                if (it.size <= 1) {
                    listOf(
                        bot.sendAudio(
                            chatId = chatId,
                            infoDto = it[0]
                        )
                    )
                } else {
                    bot.sendAudioGroup(chatId = chatId, fileInfos = it)
                }
            }.toMutableList()

        trackListFileInfo.filter { it.fileExternalId == null }.forEach { trackFileInfo ->
            val audioMessage = messages.firstOrNull { it.audio?.title == trackFileInfo.trackTitle }
            if (audioMessage != null) {
                messages.remove(audioMessage)
                audioMessage.audioFileId()
                    ?.let { trackFileCommandFacade.saveTelegramFileId(trackFileInfo.trackLocalFileId, it) }
            }
        }
    }
}