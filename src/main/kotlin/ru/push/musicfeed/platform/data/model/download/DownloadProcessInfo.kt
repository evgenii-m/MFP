package ru.push.musicfeed.platform.data.model.download

import java.time.LocalDateTime
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.OneToMany
import javax.persistence.Table

@Entity
@Table(name = "download_process_info")
class DownloadProcessInfo(

    @Column(name = "status", nullable = false)
    @Enumerated(value = EnumType.STRING)
    var status: DownloadStatus,

    @Column(name = "source_url", nullable = false)
    val sourceUrl: String,

    @Column(name = "file_path", nullable = false)
    var filePath: String,

    @Column(name = "track_title")
    var trackTitle: String? = null,

    @Column(name = "track_duration_sec", nullable = false)
    var trackDurationSec: Long,

    @Column(name = "total_parts", nullable = false)
    var totalParts: Int,

    @Column(name = "downloaded_parts")
    var downloadedParts: Int? = 0,

    @Column(name = "error_description")
    var errorDescription: String? = null,

    @Column(name = "added_at", nullable = false)
    val addedAt: LocalDateTime,

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null

    @OneToMany(mappedBy = "process", cascade = [CascadeType.REMOVE], orphanRemoval = true)
    var usersRel: MutableList<UserToDownloadProcess> = mutableListOf()

    @OneToMany(mappedBy = "processInfo", cascade = [CascadeType.REMOVE], orphanRemoval = true)
    val requestsRel: List<DownloadProcessToRequest> = mutableListOf()

    @OneToMany(mappedBy = "processInfo", cascade = [CascadeType.REMOVE], orphanRemoval = true)
    val musicTracksRel: List<DownloadProcessToMusicTrack> = mutableListOf()
}