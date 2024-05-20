package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.download.DownloadProcessToMusicTrack

@Repository
interface DownloadProcessToMusicTrackRepository : JpaRepository<DownloadProcessToMusicTrack, Long> {

    fun findByProcessId(processId: Long): List<DownloadProcessToMusicTrack>

    fun findByMusicTrackIdIn(trackIds: List<Long>): List<DownloadProcessToMusicTrack>

    fun findTop1ByMusicTrackId(musicTrackId: Long): DownloadProcessToMusicTrack?

    fun existsByMusicTrackIdAndProcessId(musicTrackId: Long, processId: Long): Boolean
}