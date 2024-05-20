package ru.push.musicfeed.platform.application.dto

import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.toDuration
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import ru.push.musicfeed.platform.data.model.download.DownloadProcessInfo
import ru.push.musicfeed.platform.data.model.download.DownloadStatus

data class DownloadProcessInfoDto(
    val downloadProcessId: Long,
    val sourceUrl: String,
    val trackTitle: String,
    val filePath: String,
    val duration: Duration,
    val totalParts: Int,
    val downloadedParts: Int? = 0,
    val status: DownloadStatus,
    var externalId: String? = null,
    val errorDescription: String? = null,
    val addedAt: LocalDateTime? = null,
)

data class DownloadProcessInfoList(
    val requestId: Long? = null,
    val dtoList: Page<DownloadProcessInfoDto>,
    val errorDescription: String? = null,
) {

    companion object {
        fun fromDto(dto: DownloadProcessInfoDto) = fromDtoList(listOf(dto))
        fun fromDtoList(dtoList: List<DownloadProcessInfoDto>) = DownloadProcessInfoList(dtoList = PageImpl(dtoList))
    }

    override fun toString(): String {
        return """
            DownloadProcessInfoList(requestId=$requestId, 
                dtoList=${dtoList.content.joinToString { it.toString() }},  
                errorDescription=$errorDescription
            )
        """.trimIndent()
    }
}

fun DownloadProcessInfo.toDto(): DownloadProcessInfoDto = DownloadProcessInfoDto(
    downloadProcessId = this.id!!,
    sourceUrl = this.sourceUrl,
    trackTitle = this.trackTitle ?: this.filePath,
    filePath = this.filePath,
    duration = this.trackDurationSec.toDuration(SECONDS),
    totalParts = this.totalParts,
    downloadedParts = this.downloadedParts,
    status = this.status,
    errorDescription = this.errorDescription,
    addedAt = this.addedAt,
)
