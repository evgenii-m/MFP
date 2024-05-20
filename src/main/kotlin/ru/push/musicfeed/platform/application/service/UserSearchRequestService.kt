package ru.push.musicfeed.platform.application.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.data.model.SearchRequestData
import ru.push.musicfeed.platform.data.repo.SearchRequestDataRepository

@Service
class UserSearchRequestService(
    private val searchRequestDataRepository: SearchRequestDataRepository,
) {

    @Transactional
    fun updateUserSearchRequest(userId: Long, request: String) {
        val data = searchRequestDataRepository.findTop1ByUserIdOrderByIdDesc(userId)
            ?.apply { data = request }
            ?: SearchRequestData(userId, request)
        searchRequestDataRepository.save(data)
    }

    fun obtainUserSearchRequestText(userId: Long): String? {
        return searchRequestDataRepository.findTop1ByUserIdOrderByIdDesc(userId)?.data
    }
}