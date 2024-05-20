package ru.push.musicfeed.platform.external.telegram

// todo подумать о рефакторинге в sealed interface/class для команд и обратных вызовов, для каждого конкретного задавать сигнатуру с ожидаемыми параметрами

enum class Command(
    val value: String,
    val description: String,
) {
    MENU("m", "Главное меню"),
    DOWNLOAD("d", "Скачать музыку"),
    GET_COLLECTIONS("cl", "Посмотреть коллекции"),
    ADD_MUSIC_PACK("add", "Добавить запись в коллекцию"),
//    RECENT("recent", "Последние записи из коллекций"),
//    RANDOM("random", "Случайная запись из коллекций"),
//    TAGS("tags", "Искать записи по тегам"),
//    LIKE_TRACK("like", "Добавить трек в избранные записи"),
}

// ВАЖНО: особенность работы библиотеки telegram-bot
// Если значение коллбэка 1 полностью совпадает с началом значения коллбэка 2, то для коллбэка 2
// будут вызываться оба обработчика, что может являться неожиданностью.
// Поэтому важно следить, чтобы значения коллбэков не пересекались по полному вхождению
enum class Callback(
    val value: String,
    val title: String = ""
) {
    GET_COLLECTIONS("getCollections", "\uD83D\uDCE6 Коллекции"),
    GET_RECENT("getRecent", "\uD83D\uDCA1\uD83D\uDCC0 Новое"),
    GET_RANDOM("getRandom", "\uD83C\uDFB2 \uD83D\uDCC0 Любое"),
    GET_TAGS("getTags", "#️⃣ Тэги"),
    DOWNLOAD_REQUEST("downloadRequest", "\uD83E\uDDF2 Скачать"),
    DOWNLOADS_LIST("downloadsList", "\uD83D\uDDC3 Загрузки"),
    GET_SETTINGS("getSettings", "⚙️Настройки"),

    MUSIC_PACKS_SEARCH_BY_TAGS_NEXT("nextSearchTags", "Далее"),
    MUSIC_PACKS_RECENT_NEXT("recentNext", "Далее"),
    MUSIC_PACKS_SEARCH_REQUEST_NEXT("mpSearchNext", "Далее"),
    TAGS_BACK("tagsBack", "Назад"),
    TAGS_NEXT("tagsNext", "Далее"),
    TAGS_SELECT("tagsSel"),

    GET_COLLECTION_CONTENT_PAGE("colContPage"),
    GET_COLLECTION_CONTENT_WIDE_SHIFT_BACK("colContWSBack", "⏪️"),
    GET_COLLECTION_CONTENT_BACK("colContBack", "⬅️"),
    GET_COLLECTION_CONTENT_WIDE_SHIFT_NEXT("colContWSNext", "⏩️"),
    GET_COLLECTION_CONTENT_NEXT("colContNext", "➡️"),
    REQUEST_COLLECTION_CONTENT_PAGE_NUMBER("reqColContPage", "▶️\uD83D\uDD22️"),
    RETURN_TO_COLLECTION_CONTENT("returnToColCont", "↩️"),

    GET_MUSIC_PACK("getPack", "↕️ Подробнее"),
    REQUEST_SEARCH_MUSIC_PACK("reqSearchPack", "\uD83D\uDD0E \uD83D\uDCC0 Найти"),
    REQUEST_ADD_MUSIC_PACK("reqAddPack", "\uD83C\uDD95 \uD83D\uDCC0 Добавить"),
    REQUEST_REMOVE_MUSIC_PACK("reqRemovePack", "\uD83D\uDEAB \uD83D\uDCC0 Удалить"),
    CANCEL_REMOVE_MUSIC_PACK("cancelRemovePack", "Отмена ⤴️"),
    REMOVE_MUSIC_PACK("packRemove"),
    RETURN_TO_MUSIC_PACK("returnToPack", "↩️"),

    GET_MUSIC_PACK_TRACK_LIST_PAGE("packTracksPage", "\uD83C\uDFB6 Треклист"),
    PUBLISH_MUSIC_PACK("packPublish", "\uD83D\uDCE4 Опубликовать"),
    MUSIC_PACK_DOWNLOAD_REQUEST("dwnldPackReq", "\uD83E\uDDF2 Скачать"),
    MUSIC_PACK_EXTRACT_TRACK_REQUEST("packExtractTrack", "✂️Извлечь"),
    MUSIC_PACK_EDIT_TITLE_REQUEST("packTitleEdit", "✏️ \uD83C\uDFF7"),
    MUSIC_PACK_EDIT_DESCRIPTION_REQUEST("packDescnEdit", "✏️ \uD83D\uDCC4"),
    MUSIC_PACK_EDIT_TAGS_REQUEST("packTagsEdit", "✏️  #️⃣"),
    MUSIC_PACK_EDIT_COVER_REQUEST("packCoverEdit", "✏️ \uD83D\uDDBC"),

    GET_MUSIC_PACK_TRACK_LIST_BACK("packTracksBack", "⬅️"),
    GET_MUSIC_PACK_TRACK_LIST_NEXT("packTracksNext", "➡️"),

    MUSIC_PACK_TRACK_LIST_DOWNLOAD_FILE("packTracksFile"),
    MUSIC_PACK_TRACK_LIST_PAGE_DOWNLOAD_FILES("packTrPageFiles", "\uD83E\uDDF2 \uD83C\uDFB5"),

    MUSIC_PACK_TRACK_LIST_REQUEST_ADD("reqAddTrack", "\uD83C\uDD95 \uD83C\uDFB5"),
    MUSIC_PACK_TRACK_LIST_ADD_TRACK("trackAdd"),

    MUSIC_PACK_TRACK_LIST_REQUEST_CHANGE_POSITION("reqChPosTrack", "⬇️⬆️️\uD83C\uDFB5"),
    MUSIC_PACK_TRACK_LIST_CHANGE_POSITION_TRACK_SELECT("selChPosTrack"),
    MUSIC_PACK_TRACK_LIST_CHANGE_POSITION_NUMBER_SELECT("selChPosTrNum"),
    MUSIC_PACK_TRACK_LIST_CANCEL_CHANGE_POSITION("cancelChPosTrack", "Отмена ⤴️"),

    MUSIC_PACK_TRACK_LIST_REQUEST_EDIT("reqEditTrack", "✏️ \uD83C\uDFB5"),
    MUSIC_PACK_TRACK_LIST_EDIT_TRACK_SELECT("selEditTrack"),
    MUSIC_PACK_TRACK_LIST_CANCEL_EDIT("cancelEditTrack", "Отмена ⤴️"),

    MUSIC_PACK_TRACK_LIST_SHOW_ADDITIONAL_BUTTONS("showTlAdtnlBtns", "⤵️Ещё"),
    MUSIC_PACK_TRACK_LIST_HIDE_ADDITIONAL_BUTTONS("hideTlAdtnlBtns", "⤴️Скрыть"),
    MUSIC_PACK_TRACK_LIST_REQUEST_REMOVE("reqRemoveTrack", "\uD83D\uDEAB \uD83C\uDFB5"),
    MUSIC_PACK_TRACK_LIST_REMOVE("packTrackRemove"),
    MUSIC_PACK_TRACK_LIST_CANCEL_REMOVE("cancelRemoveTrack", "Отмена ⤴️"),
    MUSIC_PACK_TRACK_LIST_REQUEST_EDIT_ARTIST_FOR_ALL("reqEditMpTlArt", "✏️ \uD83C\uDFB9"),

    REQUEST_COLLECTIONS_FOR_SETTINGS("reqColForSet", "✏️\uD83D\uDCE6 Настроить коллекции"),
    REQUEST_ADD_COLLECTION("reqAddCol", "\uD83C\uDD95 \uD83D\uDCE6 Добавить коллекцию"),
    REQUEST_BIND_YANDEX("reqAuthYandex", "\uD83D\uDD17 Привязать Яндекс.Музыку"),
    REQUEST_DELETE_TRACK_DATA("reqDelTrackData", "\uD83D\uDEAB Удалить данные трека"),
    REQUEST_DELETE_MUSIC_PACK_TRACKS_DATA("reqDelMpTlData", "\uD83D\uDEAB Удалить данные записи"),

    REQUEST_COLLECTION_SETTINGS("reqColSet"),
    SELECT_COLLECTION("selCol", "⭐️Выбрать основной"),
    REMOVE_COLLECTION("removeCol", "\uD83D\uDEAB \uD83D\uDCE6 Удалить"),
    REQUEST_COLLECTION_CHANNEL_BINDING("reqColChBind", "\uD83D\uDD17 Привязать канал"),

    REQUEST_DOWNLOAD_BY_TRACK_ID("reqDwnldById"),
    GET_DOWNLOAD_PROCESS_STATUS("snglDwnldStat"),
    GET_DOWNLOAD_PROCESS_STATUS_FOR_LIST("listDwnldStat", "\uD83D\uDD04 Статус"),
    RETRY_DOWNLOAD("retryDwnld"),
    DOWNLOAD_FILE("dwnldFile"),

    DOWNLOADS_LIST_BACK("dwnldListBack", "⬅️"),
    DOWNLOADS_LIST_NEXT("dwnldListNext", "➡️"),
    DOWNLOADS_ITEM_SELECT("dwnldItemSlct"),
    DOWNLOADS_ITEM_SOURCE_URL("dwnldItemUrl", "Источник ↗️"),
    DOWNLOADS_LIST_RETURN("dwnldListRtrn", "↩️"),

    DOWNLOADS_ITEM_DOWNLOAD_FILE("dwnldItemFile", "\uD83E\uDDF2"),
    DOWNLOADS_ITEM_REMOVE("dwnldItemRemove", "\uD83D\uDEAB"),
    DOWNLOADS_ITEM_STOP("dwnldItemStop", " \uD83D\uDED1 Остановить"),
    DOWNLOADS_ITEM_RETRY("dwnldItemRetry", "\uD83D\uDD02 Повторить"),
    DOWNLOADS_ITEM_STATUS("dwnldItemStatus", "\uD83D\uDD04 Статус"),
    DOWNLOADS_ITEM_EXTRACT_TRACK_REQUEST("dwnldItemExtract", "✂️Извлечь"),
}