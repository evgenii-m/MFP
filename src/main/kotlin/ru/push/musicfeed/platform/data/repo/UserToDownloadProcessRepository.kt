package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.download.UserToDownloadProcess
import ru.push.musicfeed.platform.data.model.download.UserToDownloadProcessId

@Repository
interface UserToDownloadProcessRepository : JpaRepository<UserToDownloadProcess, UserToDownloadProcessId> {

    fun deleteByUserIdAndProcessId(userId: Long, processId: Long)
}