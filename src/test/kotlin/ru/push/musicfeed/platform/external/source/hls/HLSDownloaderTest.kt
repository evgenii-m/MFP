package ru.push.musicfeed.platform.external.source.hls

import java.io.File
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.springframework.test.context.ActiveProfiles
import ru.push.musicfeed.platform.application.service.download.DownloadProcessHandler

@ActiveProfiles("manual-test")
class HLSDownloaderTest {
    @Mock
    lateinit var downloadProcessHandler: DownloadProcessHandler

    private lateinit var downloader: HLSDownloader

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        this.downloader = HLSDownloader(downloadProcessHandler)
    }

    @Test
    fun `should download MP3 file from HLS by URL`() {
        runBlocking {
            val url = "https://cf-hls-media.sndcdn.com/playlist/e2wGKJIp5LHn.128.mp3/playlist.m3u8?Policy=eyJTdGF0ZW1lbnQiOlt7IlJlc291cmNlIjoiKjovL2NmLWhscy1tZWRpYS5zbmRjZG4uY29tL3BsYXlsaXN0L2Uyd0dLSklwNUxIbi4xMjgubXAzL3BsYXlsaXN0Lm0zdTgqIiwiQ29uZGl0aW9uIjp7IkRhdGVMZXNzVGhhbiI6eyJBV1M6RXBvY2hUaW1lIjoxNzA0NTYyNDI3fX19XX0_&Signature=HH7eWkt8xl-hCJg09zht7so0QYLtI3NEcWFpuX-s8t9mGUZ3pkCALR-yc9i33kMVVlpn99WQXmYHq0tiNi4sktwAlXdE3IWvXKE6MnyKLi7CnX0RmwlxhTWq54SKerBKVPh0xbHo1zbHJ4F3jgXzMuQNFq~Toqze51Gqt03oB9WNxMPljbBoVV~-OUAmNV6m8qHEnt6b4cLi3UxEVX0nmMMTMQk-af~qsSt5Ibqr9Q2M-aeVHhJL0yUUQ38U231nDYuhaiyyCp0MGAaYJTE6conchFTusiXucYVfLVCxCRrJ9MuXDJBzlTIz0vbP7pywgEvtm5cb0~W1wLmIhsWn2g__&Key-Pair-Id=APKAI6TU7MMXM5DG6EPQ"
            val fileName = "media/soundcloud/test.mp3"
            downloader.download(url, fileName, 1)
            assertTrue { File(fileName).totalSpace > 0 }
        }
    }
}