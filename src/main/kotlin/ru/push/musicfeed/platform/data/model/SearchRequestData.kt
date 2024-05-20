package ru.push.musicfeed.platform.data.model

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table


@Entity
@Table(name = "search_request_data")
class SearchRequestData(

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "data", nullable = false)
    var data: String,
) {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null
}