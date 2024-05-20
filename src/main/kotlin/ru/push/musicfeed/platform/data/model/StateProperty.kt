package ru.push.musicfeed.platform.data.model

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "state_property")
class StateProperty(
    @Id
    @Column(nullable = false)
    val key: String
) {
    @Column(nullable = false)
    var value: String? = null
}