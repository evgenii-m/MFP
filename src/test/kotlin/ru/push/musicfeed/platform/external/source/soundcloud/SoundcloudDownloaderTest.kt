package ru.push.musicfeed.platform.external.source.soundcloud

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.test.context.ActiveProfiles
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.ExternalSourceResponsesRecorderProperties
import ru.push.musicfeed.platform.application.config.SoundcloudProperties
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.service.download.DownloadProcessHandler
import ru.push.musicfeed.platform.application.service.track.TrackDataHelper
import ru.push.musicfeed.platform.data.model.download.DownloadStatus.REQUESTED
import ru.push.musicfeed.platform.external.source.hls.HLSDownloader
import ru.push.musicfeed.platform.external.http.DefaultHttpClientProvider
import ru.push.musicfeed.platform.external.http.HttpClientProvider
import ru.push.musicfeed.platform.external.source.ytdlp.YtDlpService

@ActiveProfiles("integration-test")
class SoundcloudDownloaderTest {
    companion object {
        private const val STORAGE_FOLDER = "media/test"
        private const val TIMEOUT_SECONDS: Long = 30
        private const val USER_ID: Long = 1
    }

    @Mock
    lateinit var applicationProperties: ApplicationProperties

    @Mock
    lateinit var downloadProcessHandler: DownloadProcessHandler
    @Mock
    lateinit var ytDlpService: YtDlpService

    private val soundcloudProperties: SoundcloudProperties = SoundcloudProperties("https://m.soundcloud.com")
    private val httpClientProvider: HttpClientProvider = DefaultHttpClientProvider.construct(30)
    private lateinit var soundcloudTrackPageDownloadSourceDataExtractor: SoundcloudTrackPageDownloadSourceDataExtractor
    private lateinit var soundcloudApiProvider: SoundcloudApiProvider
    private lateinit var soundcloudDownloader: SoundcloudDownloader

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(applicationProperties.storageFolder).thenReturn(STORAGE_FOLDER)
        whenever(applicationProperties.soundcloud).thenReturn(soundcloudProperties)
        whenever(applicationProperties.externalSourceResponsesRecorder)
            .thenReturn(ExternalSourceResponsesRecorderProperties())
        whenever(applicationProperties.storagePathSeparator).thenReturn("/")
        whenever(applicationProperties.fileNameForbiddenChars).thenReturn("")

        val trackLocalFileHelper = TrackDataHelper(applicationProperties)
        this.soundcloudApiProvider = SoundcloudApiProvider(applicationProperties)
        this.soundcloudApiProvider.setHttpClientProvider(httpClientProvider)
        this.soundcloudTrackPageDownloadSourceDataExtractor = SoundcloudTrackPageDownloadSourceDataExtractor(applicationProperties)
        this.soundcloudDownloader = SoundcloudDownloader(
            applicationProperties = applicationProperties,
            soundcloudTrackPageDownloadSourceDataExtractor = soundcloudTrackPageDownloadSourceDataExtractor,
            soundcloudApiProvider = soundcloudApiProvider,
            processHandler = downloadProcessHandler,
            trackLocalFileHelper = trackLocalFileHelper,
            hlsDownloader = HLSDownloader(downloadProcessHandler),
            ytDlpService = ytDlpService
        )
    }

    @Test
    fun `should download mp3 from HLS when correct Soundcloud URL`() = runBlocking {
        val sourceUrl = "https://soundcloud.com/blablarism/uday"
        val expectedTrackTitle = "blablarism - uday"
        val expectedFilePath = "$STORAGE_FOLDER/$expectedTrackTitle.mp3"
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
            .initialize(eq(USER_ID), eq(sourceUrl), eq(expectedFilePath), eq(expectedTrackTitle), any())
        ).thenReturn(initProcessInfo)

        val completeSuccessLatch = CountDownLatch(1).also { latch ->
            doAnswer { latch.countDown() }
                .whenever(downloadProcessHandler).completeSuccess(eq(initProcessInfo.downloadProcessId), any())
        }

        val result = soundcloudDownloader.requestDownload(USER_ID, processRequestId, sourceUrl)
        assertThat(result).isEqualTo(initProcessInfo)

        completeSuccessLatch.await(TIMEOUT_SECONDS, SECONDS)

        val expectedFile = File(expectedFilePath)
        assertTrue { expectedFile.totalSpace > 0 }
    }
}