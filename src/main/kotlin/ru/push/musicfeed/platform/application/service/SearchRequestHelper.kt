package ru.push.musicfeed.platform.application.service

import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.config.SearchLogicProperties

@Component
class SearchRequestHelper(
    applicationProperties: ApplicationProperties,
    private val searchProperties: SearchLogicProperties = applicationProperties.searchLogicProperties,
) {

    fun splitRequestToMembers(request: String): List<String> {
        return request.split(Regex(searchProperties.requestMembersSplitRegexPattern))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun formSearchRegexPattern(requestMembers: List<String>): String? {
        val normalizedMembers = requestMembers
            .map { it.replace(Regex(searchProperties.requestMemberFilterCharsRegexPattern), ".") }
            .filter { it.length >= searchProperties.requestMemberMinLength }
            .map { it.lowercase() }
            .toSet()
        return normalizedMembers.takeIf { it.isNotEmpty() }?.joinToString("|")?.let { ".*$it.*" }
    }

    fun formSearchRegexPattern(request: String): String? {
        return formSearchRegexPattern(splitRequestToMembers(request))
    }
}