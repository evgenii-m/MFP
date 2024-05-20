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
import javax.persistence.OneToMany
import javax.persistence.Table
import ru.push.musicfeed.platform.data.model.UserCollection

@Entity
@Table(name = "music_collection")
class MusicCollection(

    @Column
    var title: String? = null,

    @Column(name = "external_id")
    val externalId: String? = null,

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    val type: MusicCollectionType,

    @Column(name = "is_private", nullable = false)
    val isPrivate: Boolean = false,

    @Column(name = "is_synchronized", nullable = false)
    var isSynchronized: Boolean = true,

    @Column(nullable = false)
    var removed: Boolean = false,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null

    @Column(name = "last_scan_time")
    var lastScanTime: LocalDateTime? = null

    @OneToMany(mappedBy = "collection", cascade = [CascadeType.REMOVE], orphanRemoval = true)
    var userCollections: MutableList<UserCollection> = mutableListOf()

    override fun toString(): String {
        return "MusicCollection (id=$id, title=$title, externalId=$externalId, isPrivate=$isPrivate, " +
                "isSynchronized=$isSynchronized, removed=$removed, lastScanTime=$lastScanTime)"
    }
}