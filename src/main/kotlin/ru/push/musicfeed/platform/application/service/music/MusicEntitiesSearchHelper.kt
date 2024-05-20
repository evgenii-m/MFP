package ru.push.musicfeed.platform.application.service.music

import kotlin.math.min
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.push.musicfeed.platform.application.config.ApplicationProperties
import ru.push.musicfeed.platform.application.dto.MusicTrackDto
import ru.push.musicfeed.platform.application.service.SearchRequestHelper
import ru.push.musicfeed.platform.data.TrackDataSearchResultProj
import ru.push.musicfeed.platform.data.model.music.MusicSourceType
import ru.push.musicfeed.platform.data.repo.MusicTrackRepository


data class SearchResult(
    val tracks: List<MusicTrackDto>,
    val hasFullMatch: Boolean,
)

@Component
class MusicEntitiesSearchHelper(
    applicationProperties: ApplicationProperties,
    private val musicTrackRepository: MusicTrackRepository,
    private val searchRequestHelper: SearchRequestHelper,
) {
    private val matchingFactorCalculator: MatchingFactorCalculator =
        DefaultMatchingFactorCalculator(applicationProperties.searchLogicProperties.requestMembersSplitRegexPattern)
    private val excludedMusicTrackSourceTypes: Set<MusicSourceType> =
        setOf(MusicSourceType.NOT_DEFINED, MusicSourceType.YANDEX_MUSIC)

    @Transactional(readOnly = true)
    fun searchTrack(
        searchText: String,
        resultLimit: Int = 5,
    ): SearchResult {
        val requestMembers = searchRequestHelper.splitRequestToMembers(searchText)
        val searchTitleRegexCompositePattern = searchRequestHelper.formSearchRegexPattern(requestMembers)!!
        val searchResult = musicTrackRepository.findByTitleRegexMatch(searchTitleRegexCompositePattern)
        val trackIdToMatchingFactors = searchResult.associateBy(
            { it.trackId },
            { it.determineMatchingFactor(requestMembers) }
        )
        val resultTrackIds = trackIdToMatchingFactors.entries.map { it.key }

        val resultTracks = musicTrackRepository.fetchByIdIn(resultTrackIds)
            .filter { it.sources.any { source -> !excludedMusicTrackSourceTypes.contains(source.sourceType) } }
            .sortedByDescending { trackIdToMatchingFactors[it.id!!] }
            .let { it.subList(0, min(it.size, resultLimit)) }
            .toDto()
        return SearchResult(resultTracks, resultTracks.hasFullMatch(requestMembers))
    }

    private fun TrackDataSearchResultProj.determineMatchingFactor(requestMembers: List<String>): MatchingFactor {
        return if (artistsNames?.isNotBlank() == true)
            maxOf(
                matchingFactorCalculator.calculateFactor("$artistsNames $trackTitle", requestMembers),
                matchingFactorCalculator.calculateFactor("$trackTitle $artistsNames", requestMembers)
            )
        else
            matchingFactorCalculator.calculateFactor(trackTitle, requestMembers)
    }

    private fun List<MusicTrackDto>.hasFullMatch(requestMembers: List<String>): Boolean {
        return this.any {
            return if (it.artists?.isNotEmpty() == true) {
                matchingFactorCalculator.isFullMatch("${it.artists.joinToString(" ") { it.name }} ${it.title}", requestMembers)
                        || matchingFactorCalculator.isFullMatch("${it.title} ${it.artists.joinToString(" ") { it.name }}", requestMembers)
            } else {
                matchingFactorCalculator.isFullMatch(it.title, requestMembers)
            }
        }
    }
}


interface MatchingFactorCalculator {
    fun calculateFactor(trackResult: String, requestMembers: List<String>): MatchingFactor
    fun isFullMatch(trackResult: String, requestMembers: List<String>): Boolean
}

private class DefaultMatchingFactorCalculator(
    private val requestMembersSplitRegexPattern: String
) : MatchingFactorCalculator {

    override fun calculateFactor(trackResult: String, requestMembers: List<String>): MatchingFactor {
        val splitRegex = Regex(requestMembersSplitRegexPattern)
        val trackMembers = trackResult.split(splitRegex)
        var totalStrongMatches = 0
        var totalSoftMatches = 0
        var longestStrongMatchSequenceSize = 0
        var currentStrongMatchSequenceSize = 0
        for (currentRequestMember in requestMembers) {
            for (trackMember in trackMembers) {
                if (trackMember.contentEquals(currentRequestMember, ignoreCase = true)) {
                    totalStrongMatches++
                    currentStrongMatchSequenceSize++
                    if (currentStrongMatchSequenceSize > longestStrongMatchSequenceSize) {
                        longestStrongMatchSequenceSize = currentStrongMatchSequenceSize
                    }
                    break
                } else {
                    currentStrongMatchSequenceSize = 0
                    if (trackMember.contains(currentRequestMember, ignoreCase = true)) {
                        totalSoftMatches++
                        break
                    }
                }
            }
        }
        return MatchingFactor.Simple(
            totalStrongMatches = totalStrongMatches,
            totalSoftMatches = totalSoftMatches,
            longestStrongMatchSequenceSize = longestStrongMatchSequenceSize
        )
    }

    override fun isFullMatch(trackResult: String, requestMembers: List<String>): Boolean {
        val splitRegex = Regex(requestMembersSplitRegexPattern)
        val trackMembers = trackResult.split(splitRegex).toSet()
        return requestMembers.all { r -> trackMembers.any { t -> t.contentEquals(r, true)} }
    }
}


sealed class MatchingFactor : Comparable<MatchingFactor> {
    abstract val totalStrongMatches: Int
    abstract val totalSoftMatches: Int
    abstract val longestStrongMatchSequenceSize: Int

    override fun toString(): String {
        return "MatchingFactor (strong = $totalStrongMatches, soft = $totalSoftMatches, seqSize = $longestStrongMatchSequenceSize)"
    }

    class Simple(
        override val totalStrongMatches: Int,
        override val totalSoftMatches: Int,
        override val longestStrongMatchSequenceSize: Int
    ) : MatchingFactor() {
        override fun compareTo(other: MatchingFactor): Int {
            return if (this.longestStrongMatchSequenceSize == other.longestStrongMatchSequenceSize) {
                if (this.totalStrongMatches == other.totalStrongMatches)
                    this.totalSoftMatches.compareTo(other.totalSoftMatches)
                else
                    this.totalStrongMatches.compareTo(other.totalStrongMatches)
            } else {
                this.longestStrongMatchSequenceSize.compareTo(other.longestStrongMatchSequenceSize)
            }
        }
    }
}

