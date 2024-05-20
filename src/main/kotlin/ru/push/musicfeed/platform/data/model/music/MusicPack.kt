package ru.push.musicfeed.platform.data.model.music

import java.time.LocalDateTime
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.JoinTable
import javax.persistence.ManyToMany
import javax.persistence.OneToMany
import javax.persistence.OneToOne
import javax.persistence.Table
import ru.push.musicfeed.platform.data.model.music.MusicPackFeature.NONE

@Entity
@Table(name = "music_pack")
class MusicPack(

    @Column(name = "external_id")
    val externalId: String?,

    @Column(name = "collection_id", nullable = false)
    val collectionId: Long,

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    var title: String,

    @Column(length = 1000)
    var description: String?,

    @Column(name = "cover_url", length = 2048)
    var coverUrl: String?,

    @Column(name = "page_url", length = 2048)
    val pageUrl: String?,

    @ManyToMany
    @JoinTable(
        name = "music_pack_tag",
        joinColumns = [JoinColumn(name = "music_pack_id", referencedColumnName = "id")],
        inverseJoinColumns = [JoinColumn(name = "tag", referencedColumnName = "value")]
    )
    val tags: MutableSet<Tag> = mutableSetOf(),

    @Column(name = "added_at", nullable = false)
    val addedAt: LocalDateTime,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @Column(nullable = false)
    var removed: Boolean = false,

    @Column(nullable = false)
    var editable: Boolean = false,

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    var feature: MusicPackFeature = NONE,

    ) {

    constructor(
        externalId: String?,
        collectionId: Long,
        title: String,
        description: String?,
        coverUrl: String?,
        pageUrl: String?,
        tags: MutableSet<Tag>,
        addedAt: LocalDateTime,
        updatedAt: LocalDateTime? = null,
        removed: Boolean = false,
        editable: Boolean = false,
        feature: MusicPackFeature = NONE,
        musicArtists: MutableSet<MusicPackArtist>,
        musicAlbums: MutableSet<MusicPackAlbum>,
        musicTracks: MutableSet<MusicPackTrack>
    ) : this(
        externalId,
        collectionId,
        title,
        description,
        coverUrl,
        pageUrl,
        tags,
        addedAt,
        updatedAt,
        removed,
        editable,
        feature
    ) {
        musicArtists.forEach { it.musicPack = this }
        this.musicArtists = musicArtists
        musicAlbums.forEach { it.musicPack = this }
        this.musicAlbums = musicAlbums
        musicTracks.forEach { it.musicPack = this }
        this.musicTracks = musicTracks
    }

    companion object {
        const val TITLE_MAX_LENGTH = 300
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null

    @OneToOne
    @JoinColumn(name = "collection_id", insertable = false, updatable = false)
    val collection: MusicCollection? = null

    @OneToMany(
        mappedBy = "musicPack",
        targetEntity = MusicPackArtist::class,
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var musicArtists: MutableSet<MusicPackArtist> = mutableSetOf()

    @OneToMany(
        mappedBy = "musicPack",
        targetEntity = MusicPackAlbum::class,
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var musicAlbums: MutableSet<MusicPackAlbum> = mutableSetOf()

    @OneToMany(
        mappedBy = "musicPack",
        targetEntity = MusicPackTrack::class,
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var musicTracks: MutableSet<MusicPackTrack> = mutableSetOf()


    override fun toString(): String {
        return "MusicPack (id=$id, externalId=$externalId, collectionId=$collectionId, title='$title', " +
                "description=$description, coverUrl=$coverUrl, pageUrl='$pageUrl', tags=$tags, " +
                "addedAt=$addedAt, removed=$removed)"
    }

}