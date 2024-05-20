package ru.push.musicfeed.platform.data.model.music

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.JoinTable
import javax.persistence.ManyToMany
import javax.persistence.Table

@Entity
@Table(name = "tag")
class Tag(

    @Id
    @Column(nullable = false, unique = true)
    val value: String,
) {

    @ManyToMany
    @JoinTable(
        name = "music_pack_tag",
        joinColumns = [JoinColumn(name = "tag", referencedColumnName = "value")],
        inverseJoinColumns = [JoinColumn(name = "music_pack_id", referencedColumnName = "id")]
    )
    val musicPacks: List<MusicPack>? = emptyList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tag) return false

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun toString(): String {
        return "Tag (value='$value')"
    }
}