package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.TokenType
import ru.push.musicfeed.platform.data.model.UserToken

@Repository
interface UserTokenRepository : JpaRepository<UserToken, Long> {

    fun findByUserIdAndType(
        @Param("userId") userId: Long,
        @Param("type") type: TokenType
    ): List<UserToken>

    @Query(value = """
        select ut from UserToken ut
            join ut.user u
        where ut.type = :type
            and u.externalId = :userExternalId
    """)
    fun findByUserExternalIdAndType(
        @Param("userExternalId") userExternalId: Long,
        @Param("type") type: TokenType
    ): List<UserToken>
}