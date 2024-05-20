package ru.push.musicfeed.platform.data.repo

import org.springframework.cache.annotation.Cacheable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.application.config.CachingConfig
import ru.push.musicfeed.platform.data.model.User

@Repository
interface UserRepository : JpaRepository<User, Long> {

    @Cacheable(value = [CachingConfig.USER_DATA_BY_EXTERNAL_ID], key = "#externalId")
    fun findByExternalId(@Param("externalId") externalId: Long): User?
}