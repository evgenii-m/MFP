package ru.push.musicfeed.platform.application.converter

import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.config.ApplicationProperties

@Component
class DownloadProcessInfoConverter(
    private val applicationProperties: ApplicationProperties,
) {
}