package ru.push.musicfeed.platform.data.model

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "file_external_info")
class FileExternalInfo(
    @Column(name = "track_local_file_id", nullable = false)
    val trackLocalFileId: Long,

    @Column(name = "external_id", nullable = false)
    val externalId: String,

    @Column(name = "type", nullable = false)
    @Enumerated(value = EnumType.STRING)
    val type: FileExternalType,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null
}

enum class FileExternalType {
    TELEGRAM
}