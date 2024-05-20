package ru.push.musicfeed.platform.external.source.hls

import java.io.File
import java.net.URI
import mu.KotlinLogging
import okio.buffer
import okio.sink
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.service.download.DownloadProcessHandler

@Component
class HLSDownloader(
    private val processHandler: DownloadProcessHandler,
) {

    companion object {
        @Suppress("JAVA_CLASS_ON_COMPANION")
        private val log = KotlinLogging.logger(javaClass.enclosingClass.canonicalName)
    }

    @Throws(IllegalArgumentException::class)
    suspend fun download(hlsUrl: String, outputFilePath: String, processInfoId: Long) {
        // todo add validations for parameters
        val segmentUrls = HLSParser.obtainSegmentUrlsByHLSUrl(hlsUrl)
        processHandler.start(processInfoId, segmentUrls.size)
        try {
            download(segmentUrls, outputFilePath, processInfoId)
        } catch (e: Throwable) {
            log.error(e) { "HLS download exception: $e" }
            processHandler.fail(processInfoId, e)
        }
    }

    private fun download(urls: List<String>, outputFilePath: String, processInfoId: Long) {
        val output = File(outputFilePath)
        output.parentFile.mkdirs()
        output.createNewFile()
        val bufferedSink = output.sink().buffer()
        var index = 1
        var bytesDownloaded = 0L
        for (url in urls) {
            URI(url).toURL().openStream().use { inputStream ->
                val buffer = ByteArray(1024)
                var bytesCount: Int
                while (inputStream.read(buffer).also { bytesCount = it } > 0) {
                    bufferedSink.write(buffer, 0, bytesCount)
                    bufferedSink.flush()
                    bytesDownloaded += bytesCount.toLong()
                    log.trace { "Downloaded bytes: $bytesDownloaded" }
                }
            }
            processHandler.progress(processInfoId, index)
            index++
        }
        bufferedSink.close()
        log.trace { "Total downloaded bytes: $bytesDownloaded" }
        processHandler.completeSuccess(processInfoId, index-1)
    }


}