package ru.push.musicfeed.platform.util

import org.springframework.data.domain.Page

fun <T> Page<T>.getIndexOffset() = pageable.pageNumber * pageable.pageSize

fun <T> List<T>.trimToPage(page: Int, size: Int): List<T> {
    val startIndex = page * size
    val endIndex = (startIndex + size)
        .takeIf { it <= this.size }
        ?: this.size
    return if (this.size - 1 >= startIndex)
        this.subList(startIndex, endIndex)
    else
        listOf()
}