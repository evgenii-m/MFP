package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.music.MusicPackTrack

@Repository
interface MusicPackTrackRepository : JpaRepository<MusicPackTrack, Long> {

    fun deleteByMusicPackId(musicPackId: Long)

    fun deleteByMusicTrackIdIn(musicTrackIds: List<Long>)
}