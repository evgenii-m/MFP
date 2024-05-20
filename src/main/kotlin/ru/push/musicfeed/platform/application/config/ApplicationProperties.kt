package ru.push.musicfeed.platform.application.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "application-properties")
data class ApplicationProperties(
    val storageFolder: String,
    val storagePathSeparator: String = "/",
    val fileNameForbiddenChars: String = "<>:\"/\\|?*",
    val defaultHttpClientTimeoutSec: Int = 60,
    val systemAdminUserIds: List<Long>,

    val telegramBot: TelegramBotProperties,
    val raindrops: RaindropsProperties,
    val yandex: YandexProperties,
    val nts: NtsProperties = NtsProperties(),
    val soundcloud: SoundcloudProperties,
    val mixcloud: MixcloudProperties,
    val ytDlp: YtDlpProperties = YtDlpProperties(),
    val ffmpeg: FfmpegProperties = FfmpegProperties(),
    val schedulers: SchedulersProperties,
    val externalSourceResponsesRecorder: ExternalSourceResponsesRecorderProperties =
        ExternalSourceResponsesRecorderProperties(),
    val searchLogicProperties: SearchLogicProperties = SearchLogicProperties()
)

data class TelegramBotProperties(
    val token: String,
    val apiUrl: String,
    val apiPort: Int? = null,
    val timeout: Int? = 600,
)

data class RaindropsProperties(
    val apiUrl: String,
    val token: String,
    val httpClientTimeoutSec: Long = 30,
)

data class YandexProperties(
    val oAuthClientUrl: String,
    val oAuthTokenUrl: String,
    val apiUrl: String,
    val webUrl: String,
    val clientId: String,
    val clientSecret: String,
    val httpClientTimeoutSec: Long = 30,
)

data class NtsProperties(
    val trackUrlPattern: String = "^(https?://)?((m\\.|www\\.)?nts\\.live.*/episodes/)(.+)",
    val timeoutSec: Int = 30,
)

data class SoundcloudProperties(
    val soundcloudMobileBaseUrl: String,
    val timeoutSec: Long = 30,
    val trackUrlPattern: String? = "^(https?://)?((m\\.|www\\.)?soundcloud\\.com/)(.+)"
)

data class MixcloudProperties(
    val timeoutSec: Long = 30,
    val trackUrlPattern: String? = "^(https?://)?((m\\.|www\\.)?mixcloud\\.com/)(.+)",
    val userAgent: String = "Wget/1.19.4 (linux-gnu)"
)

data class YtDlpProperties(
    val execPath: String = "yt-dlp",
    val downloadTimeoutSec: Long = 600,
    val searchTimeoutSec: Long = 30,
    val logEnable: Boolean = false,
    val encoding: String = "UTF-8",
)

data class FfmpegProperties(
    val execPath: String = "ffmpeg",
    val timeoutSec: Long = 600,
    val logEnable: Boolean = false,
    val encoding: String = "UTF-8",
)

data class SchedulersProperties(
    val collectionsLoader: SchedulerBaseProperties,
    val downloadProcessStateCheck: DownloadProcessStateCheckProperties,
    val cacheTotalCleaner: SchedulerBaseProperties,
)

open class SchedulerBaseProperties(
    val intervalMs: Long,
    val initialDelayMs: Long,
    val enabled: Boolean = false,
)

class DownloadProcessStateCheckProperties(
    intervalMs: Long,
    initialDelayMs: Long,
    enabled: Boolean = false,
    val requestedHangTimeIntervalMinutes: Long = 10,
    val inProgressHangTimeIntervalMinutes: Long = 10,
) : SchedulerBaseProperties(intervalMs, initialDelayMs, enabled)

data class ExternalSourceResponsesRecorderProperties(
    val enabled: Boolean = false,
    val responsesFolderPath: String = ""
)

data class SearchLogicProperties(
    val requestMembersSplitRegexPattern: String = "[\\s\\-\\,\\/\\|]",
    val requestMemberFilterCharsRegexPattern: String = "[\\[\\]\\(\\)\\*]",
    val requestMemberMinLength: Int = 3,
)