package ru.push.musicfeed.platform.application.scheduler

import mu.KotlinLogging
import org.springframework.stereotype.Component
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.service.ExternalSourceService
import ru.push.musicfeed.platform.application.service.MusicPackService
import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import ru.push.musicfeed.platform.data.repo.MusicCollectionRepository
import ru.push.musicfeed.platform.data.repo.UserCollectionRepository
import ru.push.musicfeed.platform.util.TransactionHelper
import java.time.Clock
import java.time.LocalDateTime
import ru.push.musicfeed.platform.application.config.SchedulerBaseProperties

@Component
class CollectionsLoaderScheduler(
    applicationProperties: ApplicationProperties,
    private val schedulerProperties: SchedulerBaseProperties = applicationProperties.schedulers.collectionsLoader,
    schedulerName: String = "CollectionsLoader",
    schedulerEnabled: Boolean = schedulerProperties.enabled,
    intervalMs: Long = schedulerProperties.intervalMs,
    initialDelayMs: Long = schedulerProperties.initialDelayMs,
    private val createTimeDelayMinutes: Long = 5,
    private val externalSourceService: ExternalSourceService,
    private val musicPackService: MusicPackService,
    private val userCollectionRepository: UserCollectionRepository,
    private val musicCollectionRepository: MusicCollectionRepository,
    private val transactionHelper: TransactionHelper,
    private val clock: Clock = Clock.systemDefaultZone()
) : AbstractScheduler(schedulerName, schedulerEnabled, intervalMs, initialDelayMs) {

    private val logger = KotlinLogging.logger {}

    companion object {
        private val SUPPORTED_COLLECTION_TYPES = setOf(
            MusicCollectionType.RAINDROPS,
            MusicCollectionType.YANDEX,
        )
    }

    override fun schedulerLogic() {
        val startTime = LocalDateTime.now(clock)

        val collections = userCollectionRepository.findActualByCollectionIsOwnerTrueAndIsSynchronizedTrue()

        collections
            .filter { userCollection ->
                val collection = userCollection.collection!!
                if (collection.externalId == null || !SUPPORTED_COLLECTION_TYPES.contains(collection.type)) {
                    logger.warn {
                        "Obtained invalid userCollection with synchronized is true and empty externalId " +
                                "or invalid type, collectionId = ${collection.id}"
                    }
                    return@filter false
                }
                true
            }
            .forEach { userCollection ->
                val userId = userCollection.userId
                val collection = userCollection.collection!!
                try {
                    transactionHelper.withTransaction {
                        val collectionInfo = externalSourceService.obtainCollectionInfo(userId, collection)

                        val musicPacksDto = if (collection.lastScanTime == null)
                            externalSourceService.obtainAll(userId, collection)
                        else
                            externalSourceService.obtainAllAddedAfter(
                                userId,
                                collection,
                                collection.lastScanTime!!.minusMinutes(createTimeDelayMinutes)
                            )
                        musicPackService.savePacksWithExistencesCheck(musicPacksDto, collection)

                        collection.title = collectionInfo.title
                        collection.lastScanTime = startTime
                        musicCollectionRepository.save(collection)
                    }
                } catch (ex: Exception) {
                    logger.error(ex) { "Scheduler logic throws exception: ${ex.message}" }
                }
            }
    }
}