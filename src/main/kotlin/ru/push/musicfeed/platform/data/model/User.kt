package ru.push.musicfeed.platform.data.model

import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.OneToMany
import javax.persistence.Table

@Entity
@Table(name = "user_data")
class User(

    @Column(name = "external_id", nullable = false)
    val externalId: Long,

) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null

    @OneToMany(mappedBy = "user", cascade = [CascadeType.REMOVE], orphanRemoval = true)
    var userTokens: MutableList<UserToken> = mutableListOf()

    @OneToMany(mappedBy = "user", cascade = [CascadeType.REMOVE], orphanRemoval = true)
    var userCollections: MutableList<UserCollection> = mutableListOf()

    override fun toString(): String {
        return "User (externalId=$externalId, id=$id)"
    }
}
