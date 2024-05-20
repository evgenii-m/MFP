package ru.push.musicfeed.platform.application.config

import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableCaching
class CachingConfig {

    companion object {
        const val YT_DLP_OBTAIN_TRACK_INFO_BY_URL = "YT_DLP_OBTAIN_TRACK_INFO_BY_URL"
        const val YT_DLP_DEFAULT_SEARCH_TRACKS = "YT_DLP_DEFAULT_SEARCH_TRACKS"
        const val USER_DATA_BY_EXTERNAL_ID = "USER_DATA_BY_EXTERNAL_ID"
    }

    @Bean
    fun cacheManager(): CacheManager = ConcurrentMapCacheManager(
        YT_DLP_OBTAIN_TRACK_INFO_BY_URL,
        YT_DLP_DEFAULT_SEARCH_TRACKS,
        USER_DATA_BY_EXTERNAL_ID,
    )

}