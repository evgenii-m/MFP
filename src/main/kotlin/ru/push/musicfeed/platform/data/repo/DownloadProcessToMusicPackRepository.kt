package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.download.DownloadProcessToMusicPack

@Repository
interface DownloadProcessToMusicPackRepository : JpaRepository<DownloadProcessToMusicPack, Long> {

    fun findByProcessId(processId: Long): List<DownloadProcessToMusicPack>

    fun existsByMusicPackIdAndProcessId(musicPackId: Long, processId: Long): Boolean
}