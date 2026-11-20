package com.example.core.network

import com.example.domain.model.SponsorCategory
import com.example.domain.model.SponsorSegment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

@Serializable
private data class SponsorBlockResponseItem(
    val segment: List<Double> = emptyList(),
    val UUID: String = "",
    val category: String = "sponsor"
)

class SponsorBlockClient {
    suspend fun fetchSkipSegments(
        videoId: String,
        categories: Set<String>
    ): List<SponsorSegment> {
        if (videoId.isBlank() || categories.isEmpty()) return emptyList()
        if (videoId.startsWith("cc_") || videoId.length > 20) return emptyList()

        return try {
            val encodedCategories = URLEncoder.encode(
                categories.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]"),
                "UTF-8"
            )
            val url =
                "https://sponsor.ajay.app/api/skipSegments?videoID=$videoId&categories=$encodedCategories"
            val body = NetworkModule.getString(url)
            val items = NetworkModule.json.decodeFromString<List<SponsorBlockResponseItem>>(body)
            items.mapNotNull { item ->
                if (item.segment.size >= 2) {
                    val startMs = (item.segment[0] * 1000.0).toLong()
                    val endMs = (item.segment[1] * 1000.0).toLong()
                    if (endMs > startMs) {
                        SponsorSegment(
                            uuid = item.UUID.ifBlank { "$startMs-$endMs" },
                            startMs = startMs,
                            endMs = endMs,
                            category = SponsorCategory.fromKey(item.category)
                        )
                    } else null
                } else null
            }.sortedBy { it.startMs }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
