package ru.push.musicfeed.platform.external.telegram

import org.apache.logging.log4j.util.Strings
import ru.push.musicfeed.platform.application.dto.CollectionInfoDto
import ru.push.musicfeed.platform.application.dto.CollectionWithContentDto
import ru.push.musicfeed.platform.application.dto.MusicAlbumDto
import ru.push.musicfeed.platform.application.dto.MusicArtistDto
import ru.push.musicfeed.platform.application.dto.MusicPackDto
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.application.dto.UserSettingsDto
import ru.push.musicfeed.platform.util.cut
import ru.push.musicfeed.platform.util.getIndexOffset
import java.time.format.DateTimeFormatter
import ru.push.musicfeed.platform.application.dto.AddTrackToMusicPackResultDto
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoDto
import ru.push.musicfeed.platform.application.dto.DownloadProcessInfoList
import ru.push.musicfeed.platform.application.dto.MusicPackTrackListDto
import ru.push.musicfeed.platform.application.dto.TrackFileInfoDto
import ru.push.musicfeed.platform.data.model.download.DownloadStatus
import ru.push.musicfeed.platform.util.formatted


class MessageHelper {
    companion object {
        val REQUEST_EXTRACT_TRACK_MESSAGE = """
            Отправьте время начала и конца отрывка через пробел, а также название для трека (не обязательно), к примеру
            <code>00:00:30 00:15:30</code>
        """.trimIndent()

        val DOWNLOAD_STARTED_MESSAGE = "Загружаем, может потребоваться несколько минут"
        val REQUEST_IN_PROGRESS_STUB_MESSAGE = "Обрабатываем запрос ..."
        val REQUEST_DOWNLOAD_STUB_MESSAGE = "Готовим треки для загрузки ..."
        val MUSIC_PACKS_NOT_FOUND_MESSAGE = "Подходящих записей не найдено"
    }
}

private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
private const val COLLECTION_CONTENT_DESCRIPTION_LIMIT = 120

private fun StringBuilder.appendIfNotBlank(value: String?) = this.appendIfNotBlank(value) {"$it\n"}

private fun StringBuilder.appendIfNotBlank(value: String?, formatter: ((String) -> String)) =
    if (!value.isNullOrBlank()) append(formatter(value)) else this

private fun StringBuilder.appendIfTrue(value: String, condition: Boolean) =
    if (condition) append(value) else this


fun MusicPackDto.formMusicPackShortMessage(): String {
    return StringBuilder()
        .appendLine("<b>${title}</b>")
        .appendIfNotBlank(tags.formatTags()) { "$it\n" }
        .appendLine("<i>at ${addedAt.format(DATE_TIME_FORMATTER)}</i>")
        .append("<a href='${coverUrl ?: pageUrl}'>&#8205;</a>")
        .appendLine()
        .toString()
}

fun MusicPackDto.formMusicPackFullMessage(showAddedAt: Boolean = false): String {
    val sb = StringBuilder()
        .appendLine("<b>${title}</b>")
        .appendLine()
        .appendIfNotBlank(description)
        .appendIfNotBlank(tags.formatTags())
    if (showAddedAt) {
        sb.append("<i>at ${addedAt.format(DATE_TIME_FORMATTER)}</i>")
            .append(pageUrl?.let { " | <i><a href='${it}'>Ссылка</a></i>\n" } ?: "\n")
    } else {
        sb.appendIfNotBlank(pageUrl) { "<i><a href='${it}'>Ссылка</a></i>\n" }
    }
    sb.append("<a href='${coverUrl ?: pageUrl}'>&#8205;</a>")
    return sb.toString()
}

fun MusicPackDto.formMusicPackPublicationMessage(): String {
    return StringBuilder()
        .appendLine("<b>${title}</b>")
        .appendLine()
        .appendIfNotBlank(description) { "$it\n" }
        .appendIfNotBlank(tags.formatTags()) { "$it\n" }
        .append(pageUrl?.let { " | <i><a href='${it}'>Ссылка</a></i>" } ?: "")
        .append("<a href='${coverUrl ?: pageUrl}'>&#8205;</a>")
        .appendLine()
        .toString()
}

fun MusicPackDto.formMusicPackTrackListCaption(): String {
    return StringBuilder()
        .appendLine()
        .appendLine("<b>${title}</b>")
        .appendLine()
        .appendIfNotBlank(description) { "$it\n\n" }
        .appendIfNotBlank(tags.formatTags()) { "$it\n\n" }
        .toString()
}

private fun List<String>.formatTags(): String {
    return if (isNotEmpty())
        joinToString("  #", "#") { it }
    else Strings.EMPTY
}

fun MusicPackTrackListDto.formMessage(
    offset: Int = contentPage.getIndexOffset()
): String {
    val pageInfo = if (contentPage.content.isEmpty()) "пусто" else
        "${offset + 1} - ${minOf(offset + contentPage.pageable.pageSize.toLong(), contentPage.totalElements)} из ${contentPage.totalElements}"
    val stringBuilder = StringBuilder()
        .appendLine("<b><a href='${musicPack.pageUrl}'>${musicPack.title}</a></b>")
        .appendLine("Треклист - $pageInfo")
        .appendLine()

    contentPage.content.sortedBy { it.position }
        .mapIndexed { index, track ->
            stringBuilder
                .appendLine(track.formContentItem(offset + index + 1))
        }
    stringBuilder.appendLine()
    return stringBuilder.toString()
}

private fun MusicArtistDto.formContentItem(index: Int): String {
    val itemText = "$index: ${name.cut(100)}"
    return source?.url?.let { "$itemText\n$it" } ?: itemText
}

private fun MusicAlbumDto.formContentItem(index: Int): String {
    val artistsTitle = artists.joinToString { it.name }.cut(100)
    val stringBuilder = StringBuilder()
        .append("$index: $artistsTitle - ${title.cut(100)}")
    if (year != null)
        stringBuilder.append(" ($year)")
    else if (releaseDate != null)
        stringBuilder.append(" (${releaseDate.format(DATE_TIME_FORMATTER)})")
    stringBuilder.appendLine()
    source?.url?.let { stringBuilder.appendLine(it) }
    return stringBuilder.toString()
}

private fun MusicTrackDto.formContentItem(index: Int): String {
    val stringBuilder = StringBuilder()
        .append("$index: <b>${formFullTitleWithDuration()}</b>")
    if (album != null)
        stringBuilder.append(" <i>(${album.title.cut(100)})</i>")
    return stringBuilder.toString()
}

fun MusicTrackDto.formContentItemButtonText(): String = this.formCompactTitle()

fun MusicTrackDto.formCompactTitle(): String {
    val compactTitle = artists?.takeIf { it.isNotEmpty() }
        ?.let { "${artists.joinToString { it.name }.cut(100)} - ${title.cut(100)}" }
        ?: title.cut(200)
    val formattedDuration = duration?.formatted()
    return formattedDuration?.let { "$compactTitle ($it)" } ?: compactTitle
}

fun MusicTrackDto.formFullTitle(): String {
    return if (artists?.isNotEmpty() == true) "${artists.joinToString { it.name }} - $title" else title
}

fun MusicTrackDto.formFullTitleWithDuration(): String {
    val fullTitle = this.formFullTitle()
    val formattedDuration = duration?.formatted()
    return formattedDuration?.let { "$fullTitle ($it)" } ?: fullTitle
}

fun List<MusicTrackDto>.formSearchResultMessage(): String {
    val stringBuilder = StringBuilder()
    stringBuilder.appendLine("Найдено $size треков, чтобы добавить выберите по номеру из списка:")
        .appendLine()
    forEachIndexed { index, o ->
        stringBuilder.appendLine(o.formContentItem(index + 1))
    }
    return stringBuilder.toString()
}

fun AddTrackToMusicPackResultDto.formSuccessMessage() = """
    Треки добавлены в запись ${this.musicPack?.title?.let { "<b>$it</b>" } ?: ""}
    <b>${this.tracks.joinToString("\n    ") { it.title }}</b>
""".trimIndent()

fun CollectionWithContentDto.formatMessage(
    offset: Int = contentPage.getIndexOffset()
): String {
    val stringBuilder = StringBuilder()
        .appendLine("Коллекция <b>${collectionInfo.title}</b> (${collectionInfo.id})")
        .appendLine("Страница ${contentPage.pageable.pageNumber + 1} из ${contentPage.totalPages} (всего: ${collectionInfo.itemsCount})")
        .appendLine()

    contentPage.content.mapIndexed { index, musicPack ->
        stringBuilder
            .appendLine("<b>${offset + index + +1}: <a href='${musicPack.pageUrl}'>${musicPack.title}</a></b>")
            .appendIfNotBlank(musicPack.description) { "${it.cut(COLLECTION_CONTENT_DESCRIPTION_LIMIT)}\n" }
            .appendIfNotBlank(musicPack.tags.formatTags()) { "$it\n" }
            .appendLine("<i>at ${musicPack.addedAt.format(DATE_TIME_FORMATTER)}</i>")
            .appendLine()
    }

    return stringBuilder.toString()
}

fun UserSettingsDto.formUserSettingsMessage() = StringBuilder()
    .appendLine("<b>Настройки</b>")
    .appendLine()
    .appendIfNotBlank(yandexMusicAccountName) { "Подключен аккаунт Яндекс.Музыка - $it\n" }
    .toString()

fun CollectionInfoDto.formatSettingsMessage(): String = StringBuilder()
    .appendLine("Настройки коллекции - <b>$title</b> (${id}${externalId?.let { ", внешний ИД: $it)" } ?: ")"}")
    .appendLine()
    .appendIfNotBlank(channelName) { "Привязан канал для публикации - @$channelName\n" }
    .toString()

fun String.markIfCollectionSelected(selected: Boolean?): String {
    return if (selected == true)
        "$this - ⭐️"
    else this
}

fun DownloadProcessInfoList.formDownloadsListMessage() = """
    Список загрузок ${if (dtoList.isEmpty) "пуст" else ":"}
""".trimIndent()

fun DownloadProcessInfoDto.formProgressButtonText() = "${progress()} | $trackTitle | ${duration.formatted()}"

fun DownloadProcessInfoDto.formSuccessButtonText() = "Скачать | $trackTitle | ${duration.formatted()}"

fun DownloadProcessInfoDto.formFailButtonText() = "Ошибка | $trackTitle | ${duration.formatted()}"

fun DownloadProcessInfoDto.formCommonButtonText() = "${formStatusString()} | $trackTitle | ${duration.formatted()}"

fun DownloadProcessInfoDto.formStatusString(): String {
    return when (status) {
        DownloadStatus.REQUESTED, DownloadStatus.IN_PROGRESS -> progress()
        DownloadStatus.FAIL -> "Ошибка"
        DownloadStatus.SUCCESS -> "Готово"
    }
}

fun DownloadProcessInfoDto.formInfoMessage() = """ 
    <b>$trackTitle (${duration.formatted()})</b>
    ${addedAt?.let { "<i>at ${it.format(DATE_TIME_FORMATTER)}</i>" } ?: ""}
    ${formStatusStringExtended()}<a href='$sourceUrl'>&#8205;</a>
""".trimIndent()

fun DownloadProcessInfoDto.formStatusStringExtended(): String {
    return when (status) {
        DownloadStatus.REQUESTED, DownloadStatus.IN_PROGRESS -> "Загружается: ${progress()}"
        DownloadStatus.FAIL -> "Ошибка загрузки: $errorDescription"
        DownloadStatus.SUCCESS -> "Успешно загружено"
    }
}

fun TrackFileInfoDto.formFileSendingMessage() = "Отправляем файл ${performer?.let { "- $it " } ?: ""}- $trackTitle - ${duration.formatted()}"

fun DownloadProcessInfoDto.progress(): String {
    if (totalParts <= 0)
        return "..."
    val percents: Double = if (downloadedParts!! <= 0) 0.0 else
        (downloadedParts.toDouble() / totalParts.toDouble()) * 100
    return if (percents < 100) "${percents.toInt()}%" else "99%"
}

fun DownloadProcessInfoDto.shortSourceUrl(): String {
    val trimmedSourceUrl = sourceUrl.replace(Regex("^https?://"), "")
    val urlLength = trimmedSourceUrl.length
    return if (urlLength < 30) trimmedSourceUrl else
        "${trimmedSourceUrl.substring(0, 15)}...${trimmedSourceUrl.substring(urlLength - 15, urlLength)}"
}

fun DownloadProcessInfoDto.shortTrackTitle(): String {
    val limit = 30
    val chunkSize = limit / 2 - 1
    val titleLength = trackTitle.length
    return if (titleLength < limit) trackTitle else
        "${trackTitle.substring(0, chunkSize)}...${trackTitle.substring(titleLength - chunkSize, titleLength)}"
}
