package ru.push.musicfeed.platform.application.service

import mu.KotlinLogging
import org.springframework.stereotype.Service
import ru.push.musicfeed.platform.application.UserNotFoundException
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.data.model.TokenType
import ru.push.musicfeed.platform.data.model.User
import ru.push.musicfeed.platform.data.repo.UserRepository
import ru.push.musicfeed.platform.data.repo.UserTokenRepository

@Service
class UserService(
    private val applicationProperties: ApplicationProperties,
    private val userRepository: UserRepository,
    private val userTokenRepository: UserTokenRepository,
) {
    private val logger = KotlinLogging.logger {}

    fun findUserByExternalId(userExternalId: Long): User {
        return userRepository.findByExternalId(userExternalId)
            ?: throw UserNotFoundException(userExternalId)
    }

//    @Transactional
//    fun findUserByExternalIdOrCreateNew(userExternalId: Long): User {
//        return userRepository.findByExternalId(userExternalId)
//            ?: let {
//                userRepository.save(User(userExternalId))
//                    .also { logger.info { "Saved new user: $it" } }
//            }
//    }

    fun getUserYandexMusicAccountName(userExternalId: Long): String? {
        return userTokenRepository.findByUserExternalIdAndType(userExternalId, TokenType.YANDEX)
            .firstOrNull()
            ?.accountName
    }

    fun isSystemAdminUser(userExternalId: Long): Boolean {
        val user = findUserByExternalId(userExternalId)
        return applicationProperties.systemAdminUserIds.contains(user.id!!)
    }
}