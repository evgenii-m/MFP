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
@Table(name = "music_pack_album")
class MusicPackAlbum(

    @Column(name = "music_album_id", nullable = false)
    val musicAlbumId: Long,

    @ManyToOne
    @JoinColumn(name = "music_album_id", insertable = false, updatable = false)
    var musicAlbum: MusicAlbum,

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
        return "MusicPackAlbum (musicAlbumId=$musicAlbumId, position=$position, id=$id, musicPackId=$musicPackId)"
    }
}