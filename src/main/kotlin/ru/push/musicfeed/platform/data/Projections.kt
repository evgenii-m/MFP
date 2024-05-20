package ru.push.musicfeed.platform.data

const val SEARCH_RESULT_STR_SEPARATOR = "; "

interface TrackDataSearchResultProj {
    val trackId: Long
    val trackTitle: String
    val artistsNames: String?
}