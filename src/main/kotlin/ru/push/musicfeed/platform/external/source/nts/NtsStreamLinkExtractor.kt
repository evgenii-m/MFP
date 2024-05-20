package ru.push.musicfeed.platform.external.source.nts

import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.NtsProperties

@Component
class NtsStreamLinkExtractor(
    applicationProperties: ApplicationProperties,
    private val ntsProperties: NtsProperties = applicationProperties.nts,
) {

    fun parse(sourceUrl: String): String {
        val doc = Jsoup.connect(sourceUrl)
            .timeout(ntsProperties.timeoutSec * 1000)
            .get()
        return doc.getElementsByClass("episode__btn").first()!!.attr("data-src")
    }
}