package ru.push.musicfeed.platform.external.source.mixcloud

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlin.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.context.ActiveProfiles
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.ExternalSourceResponsesRecorderProperties
import ru.push.musicfeed.platform.application.config.MixcloudProperties
import ru.push.musicfeed.platform.application.config.YtDlpProperties
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.service.download.DownloadProcessHandler
import ru.push.musicfeed.platform.application.service.track.TrackDataHelper
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.REQUESTED
import ru.push.musicfeed.platform.external.source.ytdlp.YtDlpService

@ActiveProfiles("integration-test")
class MixcloudDownloaderTest {

    companion object {
        private const val STORAGE_FOLDER = "media/test"
        private const val TIMEOUT_SECONDS: Long = 600
        private const val USER_ID: Long = 1
    }

    @Mock
    lateinit var applicationProperties: ApplicationProperties

    @Mock
    lateinit var downloadProcessHandler: DownloadProcessHandler

    private val mixcloudProperties: MixcloudProperties = MixcloudProperties()
    private lateinit var mixcloudTrackPageDownloadSourceDataExtractor: MixcloudTrackPageDownloadSourceDataExtractor
    private lateinit var mixcloudDownloader: MixcloudDownloader

    @BeforeEach
    fun setUp() {
        File(STORAGE_FOLDER).takeIf { it.exists() }?.deleteRecursively()
        MockitoAnnotations.openMocks(this)
        whenever(applicationProperties.storageFolder).thenReturn(STORAGE_FOLDER)
        whenever(applicationProperties.mixcloud).thenReturn(mixcloudProperties)
        whenever(applicationProperties.externalSourceResponsesRecorder)
            .thenReturn(ExternalSourceResponsesRecorderProperties())
        whenever(applicationProperties.storagePathSeparator).thenReturn("/")
        whenever(applicationProperties.fileNameForbiddenChars).thenReturn("<>:\"/\\|?*")

        initMixcloudDownloader(timeoutSec = TIMEOUT_SECONDS)
    }

    private fun initMixcloudDownloader(timeoutSec: Long) {
        val ytDlpProperties = YtDlpProperties(
            execPath = "w:\\python\\Scripts\\yt-dlp.exe",
            downloadTimeoutSec = timeoutSec,
            logEnable = true
        )
        whenever(applicationProperties.ytDlp).thenReturn(ytDlpProperties)

        val trackLocalFileHelper = TrackDataHelper(applicationProperties)
        this.mixcloudTrackPageDownloadSourceDataExtractor = MixcloudTrackPageDownloadSourceDataExtractor(
            applicationProperties = applicationProperties,
            mixcloudHelper = MixcloudHelper(applicationProperties)
        )
        this.mixcloudDownloader = MixcloudDownloader(
            applicationProperties = applicationProperties,
            mixcloudTrackPageDownloadSourceDataExtractor = mixcloudTrackPageDownloadSourceDataExtractor,
            processHandler = downloadProcessHandler,
            trackLocalFileHelper = trackLocalFileHelper,
            ytDlpService = YtDlpService(applicationProperties = applicationProperties, processHandler = downloadProcessHandler)
        )
    }

    @Test
    fun `should download mp3 from Mixcloud by correct URL`() {
        val sourceUrl = "https://www.mixcloud.com/NTSRadio/the-one-glove-breakfast-show-w-macca-6th-january-2024/"
        val expectedTrackTitle = "NTSRadio - The One Glove Breakfast Show w/ Macca - 6th January 2024"
        val expectedFilePath = "$STORAGE_FOLDER/NTSRadio - The One Glove Breakfast Show w  Macca - 6th January 2024.mp3"
        File(expectedFilePath).takeIf { it.exists() && it.isFile }?.delete()

        val processRequestId = 1L
        val initProcessInfo = DownloadProcessInfoDto(
            downloadProcessId = 1,
            sourceUrl = sourceUrl,
            filePath = expectedFilePath,
            trackTitle = expectedTrackTitle,
            duration = Duration.ZERO,
            totalParts = 0,
            downloadedParts = 0,
            status = REQUESTED
        )
        whenever(downloadProcessHandler
            .initialize(eq(USER_ID), eq(sourceUrl), eq(expectedFilePath), eq(expectedTrackTitle), anyOrNull())
        ).thenReturn(initProcessInfo)

        val completeSuccessLatch = CountDownLatch(1).also { latch ->
            doAnswer { latch.countDown() }
                .whenever(downloadProcessHandler).completeSuccess(eq(initProcessInfo.downloadProcessId), any())
            doAnswer { latch.countDown() }
                .whenever(downloadProcessHandler).fail(eq(initProcessInfo.downloadProcessId), any())
        }

        val result = mixcloudDownloader.requestDownload(USER_ID, processRequestId, sourceUrl)
        assertThat(result).isEqualTo(initProcessInfo)

        completeSuccessLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        verify(downloadProcessHandler, times(1)).completeSuccess(eq(initProcessInfo.downloadProcessId), any())
        verify(downloadProcessHandler, times(0)).fail(any(), any())

        val expectedFile = File(expectedFilePath)
        assertTrue { expectedFile.totalSpace > 0 }
    }

    @Test
    fun `should terminate download mp3 from Mixcloud when time out occurred`() {
        val sourceUrl = "https://www.mixcloud.com/NTSRadio/the-one-glove-breakfast-show-w-macca-6th-january-2024/"
        val expectedTrackTitle = "NTSRadio - The One Glove Breakfast Show w/ Macca - 6th January 2024"
        val expectedFilePath = "$STORAGE_FOLDER/NTSRadio - The One Glove Breakfast Show w  Macca - 6th January 2024.mp3"
        File(expectedFilePath).takeIf { it.exists() && it.isFile }?.delete()

        val processRequestId = 1L
        val initProcessInfo = DownloadProcessInfoDto(
            downloadProcessId = 1,
            sourceUrl = sourceUrl,
            filePath = expectedFilePath,
            trackTitle = expectedTrackTitle,
            duration = Duration.ZERO,
            totalParts = 0,
            downloadedParts = 0,
            status = REQUESTED
        )
        whenever(downloadProcessHandler
            .initialize(eq(USER_ID), eq(sourceUrl), eq(expectedFilePath), eq(expectedTrackTitle), anyOrNull())
        ).thenReturn(initProcessInfo)

        initMixcloudDownloader(timeoutSec = 5)

        val completeSuccessLatch = CountDownLatch(1).also { latch ->
            doAnswer { latch.countDown() }
                .whenever(downloadProcessHandler).completeSuccess(eq(initProcessInfo.downloadProcessId), any())
            doAnswer { latch.countDown() }
                .whenever(downloadProcessHandler).fail(eq(initProcessInfo.downloadProcessId), any())
        }

        val result = mixcloudDownloader.requestDownload(USER_ID, processRequestId, sourceUrl)
        assertThat(result).isEqualTo(initProcessInfo)

        completeSuccessLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        verify(downloadProcessHandler, times(0)).completeSuccess(any(), any())
        verify(downloadProcessHandler, times(1)).fail(eq(initProcessInfo.downloadProcessId), any())

        val expectedFile = File(expectedFilePath)
        assertTrue { expectedFile.totalSpace == 0L }
    }
}