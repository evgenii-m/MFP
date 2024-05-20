package ru.push.musicfeed.platform.data.model.music

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType.LAZY
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import ru.push.musicfeed.platform.data.model.TrackLocalFile

@Entity
@Table(name = "music_pack_track_local_file")
class MusicPackTrackLocalFile(
    @Column(name = "music_pack_id", nullable = false)
    val musicPackId: Long,

    @Column(name = "track_local_file_id", nullable = false)
    val trackLocalFileId: Long
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "music_pack_id", insertable = false, updatable = false)
    var musicPack: MusicPack? = null

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "track_local_file_id", insertable = false, updatable = false)
    var trackLocalFile: TrackLocalFile? = null
}