package ru.push.musicfeed.platform.external.source.soundcloud

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.SoundcloudProperties


class SoundcloudTrackPageDownloadSourceDataExtractorTest {
    private val soundcloudProperties: SoundcloudProperties = SoundcloudProperties("")
    private val applicationProperties: ApplicationProperties = mock {
        whenever(it.soundcloud).thenReturn(soundcloudProperties)
    }
    private val soundcloudTrackPageDownloadSourceDataExtractor: SoundcloudTrackPageDownloadSourceDataExtractor = SoundcloudTrackPageDownloadSourceDataExtractor(applicationProperties)

    @Test
    fun `should obtain HLS URL from Soundcloud track page`() {
        val result = soundcloudTrackPageDownloadSourceDataExtractor.extractData("https://m.soundcloud.com/blablarism/uday")
        assertThat(result)
            .extracting({it.artistName}, {it.trackName})
            .containsExactly("blablarism", "uday")
        val resultHlsUrl = result.hlsRequestUrl
        println("Result HLS URL - $resultHlsUrl")
        assertThat(resultHlsUrl)
            .matches("https://api-mobi\\.soundcloud\\.com/media/soundcloud:tracks:699309352/.*/stream/hls\\?client_id=.+")
    }
}