package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.ActionEvent

@Repository
interface ActionEventRepository : JpaRepository<ActionEvent, Long> {

    fun findTop1ByUserIdOrderByEventTimeDesc(@Param("userId") userId: Long): List<ActionEvent>
}