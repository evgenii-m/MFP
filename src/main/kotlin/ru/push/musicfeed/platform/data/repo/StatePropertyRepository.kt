package ru.push.musicfeed.platform.data.repo

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import ru.push.musicfeed.platform.data.model.StateProperty

@Repository
interface StatePropertyRepository : JpaRepository<StateProperty, Long> {

}