package ru.push.musicfeed.platform.util

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionHelper {

    @Transactional
    fun <T> withTransaction(supplier: () -> T): T = supplier()

    @Transactional
    fun withTransaction(runnable: Runnable) = runnable.run()
}