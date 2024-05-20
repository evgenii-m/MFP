package ru.push.musicfeed.platform.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

object DownloaderScope: CoroutineScope {
    override val coroutineContext = Dispatchers.Default
}

object TelegramBotScope: CoroutineScope {
    override val coroutineContext = Dispatchers.Default
}