package ru.push.musicfeed.platform.data.model.music

import java.time.LocalDateTime
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.JoinTable
import javax.persistence.ManyToMany
import javax.persistence.OneToMany
import javax.persistence.Table

@Entity
@Table(name = "music_album")
class MusicAlbum(
    @Column(nullable = false)
    val title: String,

    @Column
    val year: Int? = null,

    @Column(name = "release_date")
    val releaseDate: LocalDateTime? = null,

    @ManyToMany(targetEntity = MusicArtist::class)
    @JoinTable(
        name = "music_album_artist",
        joinColumns = [JoinColumn(name = "music_album_id", referencedColumnName = "id")],
        inverseJoinColumns = [JoinColumn(name = "music_artist_id", referencedColumnName = "id")]
    )
    val artists: Set<MusicArtist>,

    @OneToMany(cascade = [CascadeType.ALL], targetEntity = MusicAlbumSource::class)
    @JoinColumn(name = "music_album_id")
    var sources: Set<MusicAlbumSource> = mutableSetOf(),

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null


    override fun toString(): String {
        return "MusicAlbum (title='$title', year=$year, releaseDate=$releaseDate, artists=$artists, id=$id)"
    }

}