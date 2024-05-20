package ru.push.musicfeed.platform.data.model

import java.time.LocalDateTime
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "user_token")
class UserToken(

    @Column(nullable = false)
    @Enumerated(value = EnumType.STRING)
    val type: TokenType,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "account_name", nullable = false)
    var accountName: String,

    @Column(nullable = false)
    var value: String,

    @Column(name = "expiration_date")
    var expirationDate: LocalDateTime? = null

) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null

    @ManyToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    val user: User? = null


    override fun toString(): String {
        return "UserToken (id=$id, type=$type, userId=$userId, accountName=$accountName, value=$value, " +
                "expirationDate=$expirationDate)"
    }
}