package ru.push.musicfeed.platform.data.model.music

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "music_album_source")
class MusicAlbumSource(

    @Column(name = "source_type", nullable = false)
    @Enumerated(value = EnumType.STRING)
    val sourceType: MusicSourceType,

    @Column(name = "external_source_url")
    val externalSourceUrl: String? = null,

    ) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null


    override fun toString(): String {
        return "MusicAlbumSource (id=$id, sourceType=$sourceType, externalSourceUrl='$externalSourceUrl')"
    }
}