package ru.push.musicfeed.platform.data.model.download

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "download_process_to_music_pack")
class DownloadProcessToMusicPack(

    @Column(name = "process_id", nullable = false)
    val processId: Long,

    @Column(name = "music_pack_id", nullable = false)
    val musicPackId: Long,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id", referencedColumnName = "id", insertable = false, updatable = false)
    var processInfo: DownloadProcessInfo? = null
}