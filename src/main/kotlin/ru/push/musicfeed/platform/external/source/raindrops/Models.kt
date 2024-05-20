package ru.push.musicfeed.platform.external.source.raindrops

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class RaindropList(
    val result: Boolean,
    val items: Array<Raindrop>,
    val count: Int,
    val collectionId: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RaindropList

        if (collectionId != other.collectionId) return false

        return true
    }

    override fun hashCode(): Int {
        return collectionId.hashCode()
    }

    override fun toString(): String {
        return "RaindropList(result=$result, items=${items.contentToString()}, count=$count, " +
                "collectionId=$collectionId)"
    }
}

data class RaindropResult(
    val result: Boolean,
    val item: Raindrop,
    val author: Boolean,
)

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Raindrop(
    @JsonProperty("_id") val id: Long,
    val collectionId: Long,
    val title: String,
    val cover: String?,
    val created: LocalDateTime,
    val lastUpdate: LocalDateTime,
    val domain: String?,
    val excerpt: String?,
    val link: String,
    val tags: Array<String>,
    val removed: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Raindrop

        if (id != other.id) return false
        if (collectionId != other.collectionId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + collectionId.hashCode()
        return result
    }

    override fun toString(): String {
        return "Raindrop(id='$id', collectionId=$collectionId, title='$title', cover=$cover, " +
                "created=$created, lastUpdate=$lastUpdate, domain=$domain, excerpt=$excerpt, " +
                "link='$link', tags=${tags.contentToString()})"
    }
}

data class CollectionResult(
    val result: Boolean,
    val item: Collection?,
)

data class Collection(
    @JsonProperty("_id") val id: Long,
    val count: Int? = null,
    val title: String? = null,
)

data class CreateRaindropRequest(
    val title: String,
    val link: String,
    val collectionId: Long,
    val excerpt: String?,
    val cover: String?,
    val tags: List<String>?,
)

data class BooleanResult(
    val result: Boolean
)