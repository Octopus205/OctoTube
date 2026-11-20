package com.example.core.network

import com.example.domain.model.AudioStreamOption
import com.example.domain.model.ChannelDetail
import com.example.domain.model.ChapterMark
import com.example.domain.model.ServiceProvider
import com.example.domain.model.SponsorCategory
import com.example.domain.model.SponsorSegment
import com.example.domain.model.StreamDetail
import com.example.domain.model.SubtitleTrack
import com.example.domain.model.VideoStreamOption
import com.example.domain.model.VideoSummary

/**
 * Real Creative Commons direct MP4 streams (Google/Blender open media CDN)
 * used strictly as a resilient fallback when public Piped/Invidious/PeerTube
 * mirror instances are rate-limited or unreachable.
 */
object OpenMediaCatalog {

    private data class OpenStreamItem(
        val id: String,
        val title: String,
        val channelId: String,
        val channelName: String,
        val thumbnail: String,
        val videoUrl720: String,
        val videoUrl360: String,
        val durationSec: Long,
        val views: Long,
        val description: String,
        val chapters: List<ChapterMark>,
        val sponsorSegments: List<SponsorSegment>
    )

    private val catalog = listOf(
        OpenStreamItem(
            id = "cc_big_buck_bunny",
            title = "Big Buck Bunny — فيلم رسوم متحركة مفتوح المصدر (4K/HD)",
            channelId = "cc_blender_foundation",
            channelName = "Blender Foundation",
            thumbnail = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/BigBuckBunny.jpg",
            videoUrl720 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            videoUrl360 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            durationSec = 596,
            views = 4_850_000,
            description = "عرض مفتوح المصدر بدقة عالية من إنتاج مؤسسة Blender لتجربة البث الخالي من الإعلانات، والتشغيل في الخلفية، ووضع صورة داخل صورة (PiP)، والتحميل المباشر.",
            chapters = listOf(
                ChapterMark("مقدمة الغابة", 0),
                ChapterMark("ظهور الأرنب العملاق", 75),
                ChapterMark("مواجهة القوارض", 240),
                ChapterMark("الخطة النهائية", 420)
            ),
            sponsorSegments = listOf(
                SponsorSegment("cc-bbb-intro", 12_000L, 28_000L, SponsorCategory.INTRO),
                SponsorSegment("cc-bbb-sponsor", 95_000L, 118_000L, SponsorCategory.SPONSOR)
            )
        ),
        OpenStreamItem(
            id = "cc_sintel",
            title = "Sintel — رحلة سينمائية خيالية مفتوحة المصدر",
            channelId = "cc_blender_foundation",
            channelName = "Blender Foundation",
            thumbnail = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/Sintel.jpg",
            videoUrl720 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            videoUrl360 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            durationSec = 888,
            views = 3_120_000,
            description = "فيلم قصير مستقل يدعم التشغيل في الخلفية وتغيير الجودة والفصول وتخطي المقدمات عبر SponsorBlock.",
            chapters = listOf(
                ChapterMark("المشهد الثلجي", 0),
                ChapterMark("مدينة الضباب", 180),
                ChapterMark("وكر التنين", 520)
            ),
            sponsorSegments = listOf(
                SponsorSegment("cc-sintel-intro", 8_000L, 24_000L, SponsorCategory.INTRO)
            )
        ),
        OpenStreamItem(
            id = "cc_tears_of_steel",
            title = "Tears of Steel — خيال علمي ومؤثرات بصرية",
            channelId = "cc_mango_open_movie",
            channelName = "Mango Open Movie",
            thumbnail = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/TearsOfSteel.jpg",
            videoUrl720 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            videoUrl360 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            durationSec = 734,
            views = 2_490_000,
            description = "مشروع مفتوح المصدر للمؤثرات البصرية بدقة HD، جاهز للبث المباشر أو التحميل للمشاهدة دون اتصال.",
            chapters = listOf(
                ChapterMark("مختبر أمستردام", 0),
                ChapterMark("حوار الروبوتات", 210),
                ChapterMark("المواجهة الأخيرة", 490)
            ),
            sponsorSegments = listOf(
                SponsorSegment("cc-tos-self", 45_000L, 62_000L, SponsorCategory.SELF_PROMO)
            )
        ),
        OpenStreamItem(
            id = "cc_elephants_dream",
            title = "Elephants Dream — أول فيلم ثلاثي الأبعاد مفتوح",
            channelId = "cc_orange_project",
            channelName = "Orange Open Movie",
            thumbnail = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ElephantsDream.jpg",
            videoUrl720 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            videoUrl360 = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            durationSec = 653,
            views = 1_780_000,
            description = "أول فيلم رسوم متحركة ثلاثي الأبعاد مفتوح المصدر بالكامل مع مسار صوتي نقي ومناسب للتشغيل بالخلفية.",
            chapters = listOf(
                ChapterMark("بوابة الآلات", 0),
                ChapterMark("عالم الأسلاك", 260)
            ),
            sponsorSegments = emptyList()
        )
    )

    fun getSummaries(provider: ServiceProvider): List<VideoSummary> = catalog.map {
        VideoSummary(
            id = it.id,
            title = it.title,
            channelId = it.channelId,
            channelName = it.channelName,
            channelAvatarUrl = it.thumbnail,
            thumbnailUrl = it.thumbnail,
            durationSeconds = it.durationSec,
            views = it.views,
            uploadedText = "Open Media CC",
            serviceProvider = provider,
            isFallbackCatalog = true
        )
    }

    fun getDetail(videoId: String, provider: ServiceProvider): StreamDetail? {
        val item = catalog.firstOrNull { it.id == videoId } ?: return null
        return StreamDetail(
            id = item.id,
            title = item.title,
            description = item.description,
            channelId = item.channelId,
            channelName = item.channelName,
            channelAvatarUrl = item.thumbnail,
            subscriberCountText = "Creative Commons Open Channel",
            thumbnailUrl = item.thumbnail,
            durationSeconds = item.durationSec,
            views = item.views,
            likes = item.views / 30,
            uploadDate = "Open Archive",
            serviceProvider = provider,
            hlsUrl = null,
            videoStreams = listOf(
                VideoStreamOption(
                    url = item.videoUrl720,
                    qualityLabel = "720p HD",
                    height = 720,
                    mimeType = "video/mp4",
                    videoOnly = false
                ),
                VideoStreamOption(
                    url = item.videoUrl360,
                    qualityLabel = "360p SD",
                    height = 360,
                    mimeType = "video/mp4",
                    videoOnly = false
                )
            ),
            audioStreams = listOf(
                AudioStreamOption(
                    url = item.videoUrl720,
                    qualityLabel = "128 kbps AAC",
                    bitrate = 128_000L,
                    mimeType = "audio/mp4"
                )
            ),
            subtitles = listOf(
                SubtitleTrack(
                    url = "local://demo/ar",
                    languageCode = "ar",
                    languageName = "العربية (نموذج مدمج)",
                    mimeType = "text/vtt",
                    isAutoGenerated = false
                )
            ),
            chapters = item.chapters,
            relatedVideos = getSummaries(provider).filter { it.id != item.id },
            sponsorSegments = item.sponsorSegments,
            isFallbackStream = true,
            sourceInstance = "Open Creative Commons Fallback"
        )
    }

    fun getChannel(channelId: String, provider: ServiceProvider): ChannelDetail? {
        val matches = catalog.filter { it.channelId == channelId }
        if (matches.isEmpty()) return null
        val first = matches.first()
        return ChannelDetail(
            id = channelId,
            name = first.channelName,
            avatarUrl = first.thumbnail,
            bannerUrl = first.thumbnail,
            description = "قناة وسائط مفتوحة المصدر مرخصة بنظام Creative Commons.",
            subscriberCount = 840_000L,
            serviceProvider = provider,
            videos = matches.map {
                VideoSummary(
                    id = it.id,
                    title = it.title,
                    channelId = it.channelId,
                    channelName = it.channelName,
                    channelAvatarUrl = it.thumbnail,
                    thumbnailUrl = it.thumbnail,
                    durationSeconds = it.durationSec,
                    views = it.views,
                    uploadedText = "Open Media CC",
                    serviceProvider = provider,
                    isFallbackCatalog = true
                )
            }
        )
    }
}
