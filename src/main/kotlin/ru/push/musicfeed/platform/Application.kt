package ru.push.musicfeed.platform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.EnableAspectJAutoProxy
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.scheduler.CollectionsLoaderScheduler
import javax.annotation.PostConstruct
import ru.push.musicfeed.platform.application.scheduler.AbstractScheduler


@SpringBootApplication
@EnableAspectJAutoProxy
@EnableConfigurationProperties(ApplicationProperties::class)
class Application(
    val schedulers: List<AbstractScheduler>,
) {
    @PostConstruct
    fun setUp() {
        schedulers.forEach { it.schedule() }
    }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}