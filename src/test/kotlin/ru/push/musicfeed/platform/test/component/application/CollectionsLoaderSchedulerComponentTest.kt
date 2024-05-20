package ru.push.musicfeed.platform.test.component.application

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockitoAnnotations
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.scheduler.CollectionsLoaderScheduler
import ru.push.musicfeed.platform.data.repo.MusicAlbumRepository
import ru.push.musicfeed.platform.data.repo.MusicArtistRepository
import ru.push.musicfeed.platform.data.repo.MusicCollectionRepository
import ru.push.musicfeed.platform.data.repo.MusicPackRepository
import ru.push.musicfeed.platform.data.repo.MusicTrackRepository
import ru.push.musicfeed.platform.data.repo.TagRepository
import ru.push.musicfeed.platform.external.source.raindrops.RaindropsMusicPackExternalSourceClient
import ru.push.musicfeed.platform.test.component.AbstractParentComponentTest

@ActiveProfiles("component-test")
class CollectionsLoaderSchedulerComponentTest : AbstractParentComponentTest() {

    companion object {
        const val COLLECTION_ID = 1001L
        val NOW: Instant = Instant.parse("2022-12-01T10:00:00.00Z")
    }

    @TestConfiguration
    internal class ClockConfig {

        @Primary
        @Bean
        fun fixedClock(): Clock {
            return Clock.fixed(NOW, ZoneOffset.UTC)
        }

    }

    @Autowired
    lateinit var applicationProperties: ApplicationProperties

    @SpyBean
    lateinit var raindropsMusicPackSource: RaindropsMusicPackExternalSourceClient

    @Autowired
    lateinit var musicPackRepository: MusicPackRepository

    @Autowired
    lateinit var tagRepository: TagRepository

    @Autowired
    lateinit var musicCollectionRepository: MusicCollectionRepository

    @Autowired
    lateinit var musicArtistRepository: MusicArtistRepository

    @Autowired
    lateinit var musicAlbumRepository: MusicAlbumRepository

    @Autowired
    lateinit var musicTrackRepository: MusicTrackRepository

    @Autowired
    lateinit var collectionsLoaderScheduler: CollectionsLoaderScheduler

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    @Transactional
    fun shouldLoadCollectionsFromRaindropsWhenDbIsEmpty() {
        jdbcExecutor.precondition("db/sql/user_collection.sql")

        collectionsLoaderScheduler.schedulerLogic()

        val collection = musicCollectionRepository.findById(COLLECTION_ID)
        assertThat(collection)
            .map { it.lastScanTime }
            .isEqualTo(Optional.of(NOW.atOffset(ZoneOffset.UTC).toLocalDateTime()))

        val musicPacks = musicPackRepository.findAll()
        assertThat(musicPacks)
            .hasSize(3)
            .extracting({ it.externalId })
            .containsExactlyInAnyOrder(Tuple.tuple("307682926"), Tuple.tuple("307696878"), Tuple.tuple("388908611"))
    }

}