package ru.push.musicfeed.platform.external.telegram

import com.github.kotlintelegrambot.Bot
import com.github.kotlintelegrambot.bot
import com.github.kotlintelegrambot.dispatch
import com.github.kotlintelegrambot.dispatcher.audio
import com.github.kotlintelegrambot.dispatcher.callbackQuery
import com.github.kotlintelegrambot.dispatcher.command
import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.MessageHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.media.MediaHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.message
import com.github.kotlintelegrambot.entities.BotCommand
import com.github.kotlintelegrambot.entities.ChatId
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.Message
import com.github.kotlintelegrambot.entities.ParseMode
import com.github.kotlintelegrambot.entities.ReplyMarkup
import com.github.kotlintelegrambot.entities.TelegramFile
import com.github.kotlintelegrambot.entities.files.Audio
import com.github.kotlintelegrambot.entities.inputmedia.InputMediaAudio
import com.github.kotlintelegrambot.entities.inputmedia.MediaGroup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import com.github.kotlintelegrambot.logging.LogLevel
import com.github.kotlintelegrambot.network.ResponseError
import com.github.kotlintelegrambot.network.bimap
import com.github.kotlintelegrambot.types.TelegramBotResult
import com.github.kotlintelegrambot.types.TelegramBotResult.Error.HttpError
import com.github.kotlintelegrambot.types.TelegramBotResult.Error.InvalidResponse
import com.github.kotlintelegrambot.types.TelegramBotResult.Error.TelegramApi
import java.io.File
import java.lang.RuntimeException
import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ServiceCommandFacade
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.dto.TrackFileInfoDto
import ru.push.musicfeed.platform.data.model.ActionEventType
import ru.push.musicfeed.platform.util.mergeMaps

class TelegramError(
    message: String,
    val errorDescription: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)

@Component
class BotProvider(
    private val applicationProperties: ApplicationProperties,
    val serviceCommandFacade: ServiceCommandFacade,
    val dispatcherHandlers: List<AbstractDispatcherHandlers>,
) {
    companion object {
        const val CALLBACK_DATA_DELIMITER = "/"
        const val CALLBACK_DATA_LIST_SEPARATOR = ","

        const val DEFAULT_TAGS_RESPONSE_PAGE_SIZE = 15
        const val DEFAULT_COLLECTION_CONTENT_PAGE_SIZE = 8
        const val DEFAULT_MUSIC_PACK_TRACK_LIST_PAGE_SIZE = 10
    }

    final val logger = KotlinLogging.logger {}

    private var bot: Bot = bot {
        token = applicationProperties.telegramBot.token
        apiUrl = applicationProperties.telegramBot.apiUrl +
                (applicationProperties.telegramBot.apiPort?.let { ":$it/" } ?: "/")
        timeout = applicationProperties.telegramBot.timeout!!

        logLevel = LogLevel.Error

        dispatch {
            message {
                handleMessageAction(
                    dispatcherHandlers
                        .map { it.getMessageHandlers() }
                        .mergeMaps()
                )
            }

            audio {
                handleMessageAction(
                    dispatcherHandlers
                        .map { it.getAudioMessageHandlers() }
                        .mergeMaps()
                )
            }

            dispatcherHandlers
                .map { it.getCommandHandlers() }
                .mergeMaps()
                .map {
                    command(it.key.value) { it.value(this) }
                }

            dispatcherHandlers
                .map { it.getCallbackQueryHandlers() }
                .mergeMaps()
                .map {
                    callbackQuery(it.key.value) { it.value(this) }
                }
        }
    }

    init {
        Command.values()
            .map { BotCommand(it.value, it.description) }
            .let { bot.setMyCommands(it) }
        bot.startPolling()
        logger.info { "Bot polling started" }
    }

    private fun MessageHandlerEnvironment.handleMessageAction(handlers: Map<ActionEventType, HandleActionMessage>) {
        val messageText = message.text
        if (messageText.isNullOrBlank() || messageText.startsWith("/")) {
            logger.trace { "Handle message action skipped, message not suitable: $messageText" }
            return
        }

        val userExternalId = getUserExternalId()
        val lastActionEvent = serviceCommandFacade.getLastActionEvent(userExternalId)
        if (lastActionEvent == null) {
            logger.debug { "No action event for user, userExternalId - $userExternalId" }
            return
        }

        val handler = handlers[lastActionEvent.type]
        if (handler != null) {
            handler(this, lastActionEvent)
        } else {
            logger.info {
                "Handle message action skipped, message: $messageText, lastActionEvent: $lastActionEvent"
            }
        }
    }

    private fun MediaHandlerEnvironment<Audio>.handleMessageAction(handlers: Map<ActionEventType, HandleActionAudioMessage>) {
        val messageAudio = message.audio
        if (messageAudio == null) {
            logger.trace { "Handle audio message action skipped, message not suitable: ${message.messageId}" }
            return
        }

        val userExternalId = getUserExternalId()
        val lastActionEvent = serviceCommandFacade.getLastActionEvent(userExternalId)
        if (lastActionEvent == null) {
            logger.debug { "No action event for user, userExternalId - $userExternalId" }
            return
        }

        handlers[lastActionEvent.type]
            ?.let { it(this, lastActionEvent) }
            ?: logger.info {
                "Handle audio message action skipped, message: ${message.messageId}, lastActionEvent: $lastActionEvent"
            }
    }
}

val FAKE_USER_EXTERNAL_ID: Long? = null

fun MessageHandlerEnvironment.getChatId() = this.message.chat.id
fun MessageHandlerEnvironment.getUserExternalId() = FAKE_USER_EXTERNAL_ID ?: this.message.from!!.id

fun MediaHandlerEnvironment<Audio>.getChatId() = this.message.chat.id
fun MediaHandlerEnvironment<Audio>.getUserExternalId() = FAKE_USER_EXTERNAL_ID ?: this.message.from!!.id

fun CommandHandlerEnvironment.getChatId() = this.message.chat.id
fun CommandHandlerEnvironment.getUserExternalId() = FAKE_USER_EXTERNAL_ID ?: this.message.from!!.id

fun CallbackQueryHandlerEnvironment.getMessageId() = this.callbackQuery.message!!.messageId
fun CallbackQueryHandlerEnvironment.getChatId() = this.callbackQuery.message!!.chat.id
fun CallbackQueryHandlerEnvironment.getUserExternalId() = FAKE_USER_EXTERNAL_ID ?: this.callbackQuery.from.id

fun Bot.sendMessageToChannel(
    channelName: String,
    text: String,
    parseMode: ParseMode = ParseMode.HTML,
    disableWebPagePreview: Boolean = false
): Message {
    return sendMessageWithHandler(
        chatId = ChatId.fromChannelUsername(channelName),
        text = text,
        parseMode = parseMode,
        disableWebPagePreview = disableWebPagePreview
    )
}

private fun Bot.sendMessageWithHandler(
    chatId: ChatId,
    text: String,
    parseMode: ParseMode? = ParseMode.HTML,
    replyMarkup: ReplyMarkup? = null,
    disableWebPagePreview: Boolean = false
): Message {
    return sendMessage(
        chatId = chatId,
        text = text,
        parseMode = parseMode,
        replyMarkup = replyMarkup,
        disableWebPagePreview = disableWebPagePreview
    ).foldWithErrorHandle { it }
}

private fun <T> TelegramBotResult<Message>.foldWithErrorHandle(ifSuccess: (Message) -> T): T = this.fold(ifSuccess) {
    if (it is TelegramApi)
        throw TelegramError("Obtained Telegram API error: ${it.errorCode} - ${it.description}", it.description)
    if (it is HttpError)
        throw TelegramError("Obtained HTTP error: ${it.httpCode} - ${it.description}", it.description)
    if (it is InvalidResponse)
        throw TelegramError("Obtained HTTP error: ${it.httpCode} - ${it.httpStatusMessage} - ${it.body?.result?.text}", it.httpStatusMessage)
    throw TelegramError("Obtained unknown error when message send")
}

fun Bot.sendMessagesBatchToChat(
    chatId: Long,
    messagesForSend: List<String>,
    replyMarkup: ReplyMarkup? = null,
    parseMode: ParseMode? = ParseMode.HTML,
    disableWebPagePreview: Boolean = false
): List<Message> {
    return messagesForSend.mapIndexed { index, messageForSend ->
        sendMessageToChat(
            chatId,
            messageForSend,
            if (index == messagesForSend.size - 1) replyMarkup else null,
            parseMode,
            disableWebPagePreview
        )
    }
}

fun Bot.sendMessageToChat(
    chatId: Long,
    text: String,
    replyMarkup: ReplyMarkup? = null,
    parseMode: ParseMode? = ParseMode.HTML,
    disableWebPagePreview: Boolean = false
): Message {
    return sendMessageWithHandler(ChatId.fromId(chatId), text, parseMode, replyMarkup, disableWebPagePreview)
}

fun Bot.sendMessageToChat(
    chatId: ChatId,
    text: String,
    replyMarkup: ReplyMarkup? = null,
    parseMode: ParseMode? = ParseMode.HTML,
    disableWebPagePreview: Boolean = false
): Message {
    return sendMessageWithHandler(chatId, text, parseMode, replyMarkup, disableWebPagePreview)
}

fun Bot.deleteMessageFromChat(chatId: Long, messageId: Long, ): Boolean {
    return try {
        deleteMessage(ChatId.fromId(chatId), messageId).get()
    } catch (ex: Throwable) {
        false
    }
}

fun Bot.deleteMessageFromChat(chatId: ChatId, messageId: Long, ): Boolean {
    return try {
        deleteMessage(chatId, messageId).get()
    } catch (ex: Throwable) {
        false
    }
}

fun Bot.replaceMessage(
    chatId: Long,
    messageId: Long,
    text: String,
    replyMarkup: ReplyMarkup? = null,
    parseMode: ParseMode? = ParseMode.HTML,
    disableWebPagePreview: Boolean = false
) {
    editMessageText(
        chatId = ChatId.fromId(chatId),
        messageId = messageId,
        text = text,
        parseMode = parseMode,
        replyMarkup = replyMarkup,
        disableWebPagePreview = disableWebPagePreview
    ).bimap(
        { it?.result },
        { it.handleTelegramResponseError() }
    )
}

fun <T> ResponseError.handleTelegramResponseError(): T {
    val errorDescription = errorBody?.string() ?: exception?.localizedMessage
    throw TelegramError("Telegram send message error: $errorDescription", errorDescription)
}

fun Bot.sendOrReplaceMessage(
    chatId: Long,
    replacedMessageId: Long? = null,
    text: String,
    parseMode: ParseMode? = ParseMode.HTML,
    replyMarkup: ReplyMarkup? = null,
    disableWebPagePreview: Boolean = false
) {
    if (replacedMessageId != null) {
        replaceMessage(
            chatId = chatId,
            messageId = replacedMessageId,
            text = text,
            replyMarkup = replyMarkup,
            parseMode = parseMode,
            disableWebPagePreview = disableWebPagePreview
        )
    } else {
        sendMessageToChat(
            chatId = chatId,
            text = text,
            replyMarkup = replyMarkup,
            parseMode = parseMode,
            disableWebPagePreview = disableWebPagePreview
        )
    }
}

fun Bot.replaceButtons(chatId: Long, messageId: Long, replyMarkup: ReplyMarkup?) {
    editMessageReplyMarkup(
        chatId = ChatId.fromId(chatId),
        messageId = messageId,
        inlineMessageId = null,
        replyMarkup = replyMarkup
    ).bimap(
        { it?.result },
        { it.handleTelegramResponseError() }
    )
}

fun Bot.clearButtons(chatId: Long, messageId: Long) = replaceButtons(chatId, messageId, null)

fun Bot.sendAudio(
    chatId: ChatId,
    infoDto: TrackFileInfoDto,
): Message {
    return this.sendAudio(
        chatId = chatId,
        audio = infoDto.fileExternalId?.let { TelegramFile.ByFileId(it) }
            ?: TelegramFile.ByFile(File(infoDto.filePath)),
        title = infoDto.trackTitle,
        performer = infoDto.performer
    ).bimap(
        { it?.result!! },
        { it.handleTelegramResponseError() }
    )
}

fun Bot.sendAudioGroup(
    chatId: ChatId,
    text: String? = null,
    parseMode: ParseMode? = ParseMode.HTML,
    fileInfos: List<TrackFileInfoDto>,
): List<Message> {
    val audioArray = fileInfos.mapIndexed { idx, fileInfo ->
        InputMediaAudio(
            media = TelegramFile.ByFileId(fileInfo.fileExternalId!!),
            title = fileInfo.trackTitle,
            performer = fileInfo.performer,
            caption = text?.takeIf { idx == (fileInfos.size - 1) },
            parseMode = parseMode?.modeName
        )
    }.toTypedArray()
    return this.sendMediaGroup(
        chatId = chatId,
        mediaGroup = MediaGroup.from(*audioArray)
    ).getOrNull()
        ?: throw TelegramError("sendAudioGroup return empty list")
}

fun Bot.replaceButtonInKeyboard(
    chatId: Long,
    messageId: Long,
    sourceKeyboard: List<List<InlineKeyboardButton>>,
    updatedButton: InlineKeyboardButton,
    callbackDataCondition: (List<String>) -> Boolean
) {
    val buttons: MutableList<List<InlineKeyboardButton>> = MutableList(sourceKeyboard.size) { listOf() }
    sourceKeyboard.forEach { buttonsRow ->
        val callbackData = (buttonsRow[0] as InlineKeyboardButton.CallbackData).callbackData
        val callbackDataParts = callbackData.split(BotProvider.CALLBACK_DATA_DELIMITER)
        if (callbackDataCondition(callbackDataParts)) {
            buttons.add(listOf(updatedButton))
        } else {
            buttons.add(buttonsRow)
        }
    }
    val updatedKeyboard = InlineKeyboardMarkup.create(buttons)
    replaceButtons(
        chatId = chatId,
        messageId = messageId,
        replyMarkup = updatedKeyboard
    )
}