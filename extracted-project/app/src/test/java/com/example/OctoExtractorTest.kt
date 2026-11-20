package com.example

import com.example.core.network.OctoExtractorEngine
import com.example.domain.model.ServiceProvider
import com.example.domain.model.SponsorCategory
import com.example.ui.components.OctoFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OctoExtractorTest {

    private val engine = OctoExtractorEngine()

    @Test
    fun extractVideoIdFromYouTubeUrls() {
        assertEquals(
            "dQw4w9WgXcQ",
            engine.extractVideoIdFromInput("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            engine.extractVideoIdFromInput("https://youtu.be/dQw4w9WgXcQ?t=42")
        )
        assertEquals(
            "dQw4w9WgXcQ",
            engine.extractVideoIdFromInput("https://youtube.com/shorts/dQw4w9WgXcQ")
        )
        assertNull(engine.extractVideoIdFromInput("تعلم برمجة أندرويد"))
    }

    @Test
    fun verifyServiceProviderAndSponsorMapping() {
        assertEquals(ServiceProvider.PIPED, ServiceProvider.fromId("piped"))
        assertEquals(ServiceProvider.INVIDIOUS, ServiceProvider.fromId("invidious"))
        assertEquals(ServiceProvider.PEERTUBE, ServiceProvider.fromId("peertube"))
        assertEquals(SponsorCategory.SPONSOR, SponsorCategory.fromKey("sponsor"))
        assertEquals(SponsorCategory.SELF_PROMO, SponsorCategory.fromKey("selfpromo"))
    }

    @Test
    fun verifyFormatters() {
        assertEquals("02:05", OctoFormatters.formatDuration(125))
        assertEquals("1:01:05", OctoFormatters.formatDuration(3665))
        assertEquals("1.5 MB", OctoFormatters.formatBytes(1_572_864))
    }
}
