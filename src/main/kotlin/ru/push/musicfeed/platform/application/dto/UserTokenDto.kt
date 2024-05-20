package ru.push.musicfeed.platform.application.dto

import java.time.LocalDateTime

data class UserTokenDto(
    val value: String,
    val expirationDate: LocalDateTime,
    val accountName: String
)
