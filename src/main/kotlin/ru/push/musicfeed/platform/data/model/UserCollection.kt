package ru.push.musicfeed.platform.data.model

import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import ru.push.musicfeed.platform.data.model.music.MusicCollection


class UserCollectionId() : Serializable {
    var userId: Long = 0
    var collectionId: Long = 0

    constructor(userId: Long, collectionId: Long) : this() {
        this.userId = userId
        this.collectionId = collectionId
    }

    override fun toString(): String {
        return "UserCollectionId (userId=$userId, collectionId=$collectionId)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserCollectionId) return false

        if (userId != other.userId) return false
        if (collectionId != other.collectionId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + collectionId.hashCode()
        return result
    }
}

@Entity
@IdClass(UserCollectionId::class)
@Table(name = "user_collection")
class UserCollection(

    @Id
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Id
    @Column(name = "collection_id", nullable = false)
    val collectionId: Long,

    @ManyToOne
    @JoinColumn(name = "collection_id", insertable = false, updatable = false)
    val collection: MusicCollection? = null,

    @Column(name = "is_owner", nullable = false)
    val isOwner: Boolean = false,

    @Column(name = "can_write", nullable = false)
    val canWrite: Boolean = false,

    @Column(name = "selected", nullable = false)
    var selected: Boolean = false,
) {

    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: User? = null

    @Column(name = "channel_name")
    var channelName: String? = null

    override fun toString(): String {
        return "UserCollection (userId=$userId, collectionId=$collectionId, isOwner=$isOwner, " +
                "canWrite=$canWrite, selected=$selected, channelName=$channelName)"
    }
}