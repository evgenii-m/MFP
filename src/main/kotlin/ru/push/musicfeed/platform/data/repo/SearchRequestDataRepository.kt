package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.SearchRequestData

@Repository
interface SearchRequestDataRepository : JpaRepository<SearchRequestData, Long> {

    fun findTop1ByUserIdOrderByIdDesc(userId: Long): SearchRequestData?
}