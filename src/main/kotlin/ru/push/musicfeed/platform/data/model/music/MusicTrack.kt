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
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.Table


@Entity
@Table(name = "music_track")
class MusicTrack(
    @Column(nullable = false)
    val title: String,

    @ManyToOne
    @JoinColumn(name = "album_id")
    val album: MusicAlbum? = null,

    @ManyToMany(targetEntity = MusicArtist::class)
    @JoinTable(
        name = "music_track_artist",
        joinColumns = [JoinColumn(name = "music_track_id", referencedColumnName = "id")],
        inverseJoinColumns = [JoinColumn(name = "music_artist_id", referencedColumnName = "id")]
    )
    val artists: Set<MusicArtist>,

    @OneToMany(cascade = [CascadeType.ALL], targetEntity = MusicTrackSource::class)
    @JoinColumn(name = "music_track_id")
    var sources: MutableSet<MusicTrackSource> = mutableSetOf(),

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    @Column(name = "album_position")
    val albumPosition: Int? = null,

    @Column(name = "duration_sec")
    val durationSec: Long? = null,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null


    fun findSource(sourceType: MusicSourceType): MusicTrackSource? = sources.find { it.sourceType == sourceType }

    fun isEditable(): Boolean = NOT_EDITABLE_MUSIC_SOURCE_TYPES.none { findSource(it) != null }

    override fun toString(): String {
        return "MusicTrack (title='$title', album=$album, artists=$artists, sources=$sources, id=$id)"
    }
}