package ru.push.musicfeed.platform.data.model.download

import java.io.Serializable
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType.LAZY
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import ru.push.musicfeed.platform.data.model.User

class UserToDownloadProcessId() : Serializable {
    var userId: Long = 0
    var processId: Long = 0

    constructor(userId: Long, processId: Long) : this() {
        this.userId = userId
        this.processId = processId
    }

    override fun toString(): String {
        return "UserToDownloadProcessId (userId=$userId, processId=$processId)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserToDownloadProcessId) return false

        if (userId != other.userId) return false
        if (processId != other.processId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + processId.hashCode()
        return result
    }
}

@Entity
@IdClass(UserToDownloadProcessId::class)
@Table(name = "user_to_download_process_info")
class UserToDownloadProcess(

    @Id
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Id
    @Column(name = "process_id", nullable = false)
    val processId: Long,

    @Column(name = "is_owner", nullable = false)
    val isOwner: Boolean = false,
) {

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: User? = null

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "process_id", insertable = false, updatable = false)
    val process: DownloadProcessInfo? = null
}