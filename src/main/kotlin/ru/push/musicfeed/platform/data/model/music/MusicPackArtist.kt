package ru.push.musicfeed.platform.data.model.music

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "music_pack_artist")
class MusicPackArtist(

    @Column(name = "music_artist_id", nullable = false)
    val musicArtistId: Long,

    @ManyToOne
    @JoinColumn(name = "music_artist_id", insertable = false, updatable = false)
    var musicArtist: MusicArtist,

    @Column(nullable = false)
    var position: Int
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null

    @ManyToOne
    @JoinColumn(name = "music_pack_id", nullable = false)
    var musicPack: MusicPack? = null

    @Column(name = "music_pack_id", insertable = false, updatable = false)
    val musicPackId: Long? = null

    override fun toString(): String {
        return "MusicPackArtist (musicArtistId=$musicArtistId, position=$position, id=$id, musicPackId=$musicPackId)"
    }
}