package ru.push.musicfeed.platform.data.model.music

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType.LAZY
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "music_pack_track")
class MusicPackTrack(

    @Column(name = "music_track_id", nullable = false)
    val musicTrackId: Long,

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "music_track_id", insertable = false, updatable = false)
    var musicTrack: MusicTrack,

    @Column(name = "added_at", nullable = false)
    val addedAt: LocalDateTime,

    @Column(nullable = false)
    var position: Int
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "music_pack_id", nullable = false)
    var musicPack: MusicPack? = null

    @Column(name = "music_pack_id", insertable = false, updatable = false)
    val musicPackId: Long? = null


    override fun toString(): String {
        return "MusicPackTrack (musicTrackId=$musicTrackId, musicPackId=$musicPackId, " +
                "addedAt=$addedAt, position=$position)"
    }
}