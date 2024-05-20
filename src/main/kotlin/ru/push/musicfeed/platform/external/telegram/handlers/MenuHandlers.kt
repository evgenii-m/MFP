package ru.push.musicfeed.platform.external.telegram.handlers

import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.HandleCommand
import com.github.kotlintelegrambot.entities.InlineKeyboardMarkup
import com.github.kotlintelegrambot.entities.keyboard.InlineKeyboardButton
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.ServiceCommandFacade
import ru.push.musicfeed.platform.application.UserSettingsCommandFacade
import ru.push.musicfeed.platform.external.telegram.AbstractDispatcherHandlers
import ru.push.musicfeed.platform.external.telegram.Callback
import ru.push.musicfeed.platform.external.telegram.Command
import ru.push.musicfeed.platform.external.telegram.getChatId
import ru.push.musicfeed.platform.external.telegram.getUserExternalId
import ru.push.musicfeed.platform.external.telegram.sendMessageToChat

@Component
class MenuHandlers(
    serviceCommandFacade: ServiceCommandFacade,
    private val userSettingsCommandFacade: UserSettingsCommandFacade
) : AbstractDispatcherHandlers(serviceCommandFacade) {

    override fun getCommandHandlers(): Map<Command, HandleCommand> = mapOf(
        Command.MENU to {
            displayMenu()
        }
    )


    private fun CommandHandlerEnvironment.displayMenu() {
        val chatId = getChatId()
        val userExternalId = getUserExternalId()

        executeWithTry(bot, chatId, userExternalId) {
            val userSettings = userSettingsCommandFacade.getUserSettings(userExternalId)

            val buttons = InlineKeyboardMarkup.create(listOf(
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = Callback.GET_COLLECTIONS.title,
                        callbackData = formCallbackData(Callback.GET_COLLECTIONS)
                    ),
                    InlineKeyboardButton.CallbackData(
                        text = Callback.GET_TAGS.title,
                        callbackData = formCallbackData(Callback.GET_TAGS)
                    ),
                ),
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = Callback.GET_RECENT.title,
                        callbackData = formCallbackData(Callback.GET_RECENT)
                    ),
                    InlineKeyboardButton.CallbackData(
                        text = Callback.GET_RANDOM.title,
                        callbackData = formCallbackData(Callback.GET_RANDOM)
                    ),
                    InlineKeyboardButton.CallbackData(
                        text = Callback.REQUEST_SEARCH_MUSIC_PACK.title,
                        callbackData = formCallbackData(Callback.REQUEST_SEARCH_MUSIC_PACK)
                    ),
                ),
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = Callback.DOWNLOADS_LIST.title,
                        callbackData = formCallbackData(Callback.DOWNLOADS_LIST)
                    ),
                    InlineKeyboardButton.CallbackData(
                        text = Callback.DOWNLOAD_REQUEST.title,
                        callbackData = formCallbackData(Callback.DOWNLOAD_REQUEST)
                    ),
                ),
                listOf(
                    InlineKeyboardButton.CallbackData(
                        text = Callback.GET_SETTINGS.title,
                        callbackData = formCallbackData(Callback.GET_SETTINGS)
                    ),
                ),
            ))
            bot.sendMessageToChat(chatId, "Главное меню", buttons)
        }
    }

}