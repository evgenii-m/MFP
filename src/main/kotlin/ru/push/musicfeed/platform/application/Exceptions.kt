package ru.push.musicfeed.platform.application

import ru.push.musicfeed.platform.data.model.music.MusicCollectionType
import java.lang.RuntimeException
import ru.push.musicfeed.platform.data.model.music.MusicSourceType

class UserNotFoundException(
    userExternalId: Long,
    message: String = "User not found, userExternalId - $userExternalId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class UserHasNotPermissionsException(
    userExternalId: Long,
    message: String = "User has not permissions for action, userExternalId - $userExternalId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class CollectionAlreadyExistException(
    collectionExternalId: String,
    message: String = "Collection already exist, collectionExternalId - $collectionExternalId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class CollectionNotFoundException(
    userId: Long,
    collectionId: Long? = null,
    musicPackId: Long? = null,
    message: String = "Collection not found, userId - $userId" +
            collectionId.appendIfPresent("collectionId") +
            musicPackId.appendIfPresent("musicPackId"),
    cause: Throwable? = null
) : RuntimeException(message, cause)

class CollectionNotEditable(
    userId: Long,
    collectionId: Long,
    message: String = "Collection not editable, userId - $userId, collectionId - $collectionId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class MusicPackNotFoundException(
    userId: Long,
    musicPackId: Long? = null,
    message: String = "Music pack not found (or forbidden for user), userId - $userId, musicPackId - $musicPackId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class MusicPackNoContentToDownloadException(
    userId: Long,
    musicPackId: Long? = null,
    message: String = "Music pack hasn't content to download, userId - $userId, musicPackId - $musicPackId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class InvalidMusicPackCreateException(
    userId: Long,
    collectionId: Long,
    message: String = "Invalid music pack data for create in collection, userId - $userId, collectionId - $collectionId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class MusicTrackNotFoundException(
    userId: Long? = null,
    musicTrackId: Long? = null,
    message: String = "Music track not found, userId - $userId, musicTrackId - $musicTrackId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class MusicTrackNotEditableException(
    userId: Long,
    musicTrackId: Long? = null,
    message: String = "Music track not editable, userId - $userId, musicTrackId - $musicTrackId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class MusicPackNotEditableException(
    userId: Long,
    musicPackId: Long? = null,
    message: String = "Music pack not editable, userId - $userId, musicPackId - $musicPackId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class MusicPackAlreadyExistException(
    collectionId: Long,
    pageUrl: String,
    message: String = "Music pack already exist in collection, collectionId - $collectionId, pageUrl - $pageUrl",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class MusicPackRemoveExternalSourceException(
    userId: Long,
    musicPackId: Long? = null,
    message: String = "Couldn't remove music pack on external source, userId - $userId, musicPackId - $musicPackId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class CollectionSourceAbsentException(
    collectionExternalId: String,
    message: String = "Collection external source absent, collectionExternalId - $collectionExternalId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class UserCollectionsMaxCountObtainedException(
    userId: Long,
    message: String = "Collections max count obtained, userId - $userId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class InvalidCollectionCreateParameterException(
    message: String = "Invalid collection create parameter",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class NoSelectedCollectionException(
    userId: Long,
    message: String = "No selected collection, userId - $userId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class MusicCollectionAccessDeniedException(
    userId: Long,
    message: String = "User is not owner for music collection, userId - $userId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ExternalSourceException(
    collectionExternalId: String? = null,
    musicPackExternalId: String? = null,
    message: String = "Operation with external source throws exception, " +
            "collectionExternalId - $collectionExternalId, musicPackExternalId - $musicPackExternalId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ExternalCreateMusicPackException(
    user: String,
    musicPackExternalId: Long? = null,
    message: String = "Create music pack on external source throws exception, " +
            "user - $user, musicPackExternalId - $musicPackExternalId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ExternalChangeMusicPackContentException(
    user: String,
    musicPackExternalId: Long,
    trackExternalId: Long,
    message: String = "Change music pack content on external source throws exception, " +
            "user - $user, musicPackExternalId - $musicPackExternalId, trackExternalId - $trackExternalId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class InvalidExternalSourceUrlException(
    url: String,
    message: String = "Invalid external source url - $url",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ExternalSourceNotSupportedException(
    url: String? = null,
    musicCollectionType: MusicCollectionType? = null,
    musicSourceType: MusicSourceType? = null,
    message: String = "External source not supported, " +
            url?.appendIfPresent("url") +
            musicCollectionType?.appendIfPresent("musicCollectionType") +
            musicSourceType?.appendIfPresent("musicSourceType"),
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ExternalTrackSearchNotSupportedException(
    musicCollectionType: MusicCollectionType,
    message: String = "External track search not supported for current collection type, musicCollectionType - $musicCollectionType",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ExternalTokenNotProvidedException(
    musicCollectionType: MusicCollectionType,
    message: String = "External token for music collection not provided, musicCollectionType - $musicCollectionType",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ExternalTokenNotFoundException(
    userId: Long,
    musicCollectionType: MusicCollectionType,
    message: String = "External token for user not found, userId - $userId, musicCollectionType - $musicCollectionType",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ObtainUserTokenException(
    message: String = "Error when obtain user token by authorization code",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ExternalSourceParseException(
    sourceUrl: String,
    message: String = "External source parse exception, sourceUrl - $sourceUrl",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ExternalSourceSearchException(
    searchText: String,
    message: String = "External source search exception, searchText - '$searchText'",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class TrackLocalFileSourceAlreadyDefinedException(
    musicTrackId: Long,
    message: String = "Track local file source already defined, musicTrackId - '$musicTrackId'",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ExternalSourceSearchNotFoundException(
    searchText: String,
    collectionId: Long? = null,
    message: String = "External source search return empty result, " +
            "searchText - '$searchText'" +
            collectionId?.appendIfPresent("collectionId"),
    cause: Throwable? = null
) : RuntimeException(message, cause)

class DownloadSourceNotSupportedException(
    url: String? = null,
    trackId: Long? = null,
    message: String = "Download from source not supported " +
            url?.appendIfPresent("url") +
            trackId?.appendIfPresent("trackId"),
    cause: Throwable? = null
) : RuntimeException(message, cause)

class TrackLocalFileIsNotAccessibleException(
    trackLocalFileId: Long? = null,
    downloadProcessId: Long? = null,
    musicPackId: Long? = null,
    message: String = "Track local file is not accessible" +
            trackLocalFileId?.appendIfPresent("trackLocalFileId") +
            downloadProcessId?.appendIfPresent("downloadProcessId") +
            musicPackId?.appendIfPresent("musicPackId"),
    cause: Throwable? = null
) : RuntimeException(message, cause)

class TrackLocalFileNotFoundException(
    trackLocalFileId: Long? = null,
    downloadProcessId: Long? = null,
    val musicTrackId: Long? = null,
    filePath: String,
    message: String = "File not found, filePath - $filePath" +
            trackLocalFileId?.appendIfPresent("trackLocalFileId") +
            downloadProcessId?.appendIfPresent("downloadProcessId"),
    cause: Throwable? = null
) : RuntimeException(message, cause)

class DownloadProcessCannotBeStoppedException(
    downloadProcessId: Long,
    message: String = "Downloaded process cannot be stopped, downloadProcessId - $downloadProcessId",
    cause: Throwable? = null
) : RuntimeException(message, cause)

class TrackFileAlreadyExistsException(
    filePath: String,
    message: String = "Track file already exists, filePath - $filePath"
) : Throwable(message)

class UserSearchRequestNotDefinedException(
    userId: Long,
    message: String = "User search request not defined, userId = $userId"
) : Throwable(message)



private fun Any?.appendIfPresent(fieldName: String): String = this?.let { ", $fieldName = $it" } ?: ""