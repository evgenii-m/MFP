package ru.push.musicfeed.platform.external.source.hls

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import mu.KotlinLogging

object HLSParser {
    private val log = KotlinLogging.logger {}

    private const val START_KEY = "#EXTM3U"
    private const val END_KEY = "#EXT-X-ENDLIST"
    private const val SEGMENT_KEY = "#EXTINF"

    fun obtainSegmentUrlsByHLSUrl(hlsUrl: String): List<String> {
        if (hlsUrl.isBlank())
            throw IllegalArgumentException("The hlsUrl is null or blank!")
        val url = URL(hlsUrl)
        val inputStream = url.openStream()
        val urls = readSegmentsUrls(inputStream, hlsUrl.substring(0, hlsUrl.lastIndexOf('/') + 1))
        if (urls.isEmpty())
            log.warn { "No segments URL found in the provided HLS from: $hlsUrl" }
        return urls
    }

    private fun readSegmentsUrls(inputStream: InputStream, vararg partialUrl: String): List<String> {
        val lines = inputStream.readAllLines()
        check(lines.isNotEmpty()) { "The file is empty" }
        require(lines[0] == START_KEY) { "The file must start with $START_KEY" }
        require(lines.contains(END_KEY)) { "The file must end with $END_KEY" }
        val result: MutableList<String> = ArrayList()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith(SEGMENT_KEY) && i < lines.size - 1) {
                var split = line.split(",").toTypedArray()
                if (split.size == 1 || split[1].isBlank()) {
                    var nextLine = lines[i+1].trim(' ')
                    if (nextLine.startsWith(SEGMENT_KEY)) {
                        continue
                    }
                    split = nextLine.split(",").toTypedArray()
                    if (split.size == 2)
                        nextLine = split[1].trim(' ')
                    if (!nextLine.startsWith("http")) {
                        if (partialUrl.isNotEmpty())
                            nextLine = partialUrl[0] + nextLine
                        else
                            log.warn { "The url doesn't start with 'http' and there is no partial url provided: $nextLine" }
                    }
                    result.add(nextLine)
                }
            }
            i++
        }
        return result
    }

    private fun InputStream.readAllLines(): List<String> {
        val result: MutableList<String> = ArrayList()
        val bufferedReader = BufferedReader(InputStreamReader(this))
        var line: String?
        while (bufferedReader.readLine().also { line = it } != null)
            result.add(line!!)
        bufferedReader.close()
        return result
    }
}