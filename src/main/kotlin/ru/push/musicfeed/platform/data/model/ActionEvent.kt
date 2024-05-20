package ru.push.musicfeed.platform.data.model

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

// todo: make abstract data field with JSON content dependent of ActionEventType
@Entity
@Table(name = "action_event")
class ActionEvent(

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "collection_id")
    val collectionId: Long? = null,

    @Column(name = "music_pack_id")
    val musicPackId: Long? = null,

    @Column(name = "message_id")
    val messageId: Long? = null,

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    val type: ActionEventType,

    @Column(name = "event_time", nullable = false)
    val eventTime: LocalDateTime,

    @Column(name = "event_data_id")
    val eventDataId: Long? = null,

) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null

    override fun toString(): String {
        return "ActionEvent (id=$id, userId=$userId, collectionId=$collectionId, musicPackId=$musicPackId, " +
                "messageId=$messageId, type=$type, eventTime=$eventTime, eventDataId=$eventDataId)"
    }

}