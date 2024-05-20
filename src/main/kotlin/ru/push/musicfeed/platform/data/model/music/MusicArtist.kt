package ru.push.musicfeed.platform.data.model.music

import java.time.LocalDateTime
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.OneToMany
import javax.persistence.Table

@Entity
@Table(name = "music_artist")
class MusicArtist(
    @Column(nullable = false)
    val name: String,

    @OneToMany(cascade = [CascadeType.ALL], targetEntity = MusicArtistSource::class)
    @JoinColumn(name = "music_artist_id")
    var sources: Set<MusicArtistSource> = mutableSetOf(),

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    ) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null


    override fun toString(): String {
        return "MusicArtist (name='$name', id=$id)"
    }
}