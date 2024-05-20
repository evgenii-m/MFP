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
@Table(name = "music_track_source")
class MusicTrackSource(

    @Column(name = "source_type", nullable = false)
    @Enumerated(value = EnumType.STRING)
    val sourceType: MusicSourceType,

    @Column(name = "external_source_url")
    val externalSourceUrl: String? = null,

    @Column(name = "local_file_id")
    var localFileId: Long? = null,

) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null


    override fun toString(): String {
        return "MusicTrackSource (id=$id, sourceType=$sourceType, externalSourceUrl='$externalSourceUrl', localFileId=$localFileId)"
    }
}