package ru.push.musicfeed.platform.data.model

import java.time.LocalDateTime
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType.LAZY
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.OneToMany
import javax.persistence.OneToOne
import javax.persistence.Table
import ru.push.musicfeed.platform.data.model.download.DownloadProcessInfo
import ru.push.musicfeed.platform.data.model.music.MusicPackTrackLocalFile

@Entity
@Table(name = "track_local_file")
class TrackLocalFile(

    @Column(name = "file_path", nullable = false)
    val filePath: String,

    @Column(name = "track_title", nullable = false)
    val trackTitle: String,

    @Column(name = "track_duration_sec", nullable = false)
    val trackDurationSec: Long,

    @Column(name = "download_process_id")
    val downloadProcessId: Long? = null,

    @Column(name = "source_track_local_file_id")
    val sourceTrackFileId: Long? = null,

    @Column(name = "added_at", nullable = false)
    val addedAt: LocalDateTime,
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    var id: Long? = null

    @OneToOne(fetch = LAZY)
    @JoinColumn(name = "download_process_id", insertable = false, updatable = false)
    val downloadProcess: DownloadProcessInfo? = null

    @OneToMany(cascade = [CascadeType.REMOVE], orphanRemoval = true, targetEntity = FileExternalInfo::class)
    @JoinColumn(name = "track_local_file_id", insertable = false, updatable = false)
    val externalInfos: List<FileExternalInfo> = listOf()

    @OneToMany(cascade = [CascadeType.REMOVE], orphanRemoval = true, targetEntity = MusicPackTrackLocalFile::class)
    @JoinColumn(name = "track_local_file_id", insertable = false, updatable = false)
    var musicPackRels: List<MusicPackTrackLocalFile> = listOf()
}