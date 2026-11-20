package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.OctoSettings
import com.example.domain.model.ServiceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.octoDataStore: DataStore<Preferences> by preferencesDataStore(name = "octopipe_prefs")

class OctoPreferences(private val context: Context) {

    private object Keys {
        val ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val CUSTOM_PIPED_URL = stringPreferencesKey("custom_piped_url")
        val CUSTOM_INVIDIOUS_URL = stringPreferencesKey("custom_invidious_url")
        val CUSTOM_PEERTUBE_URL = stringPreferencesKey("custom_peertube_url")
        val SPONSOR_BLOCK_ENABLED = booleanPreferencesKey("sponsor_block_enabled")
        val AUTO_SKIP_SPONSOR = booleanPreferencesKey("auto_skip_sponsor")
        val ENABLED_SPONSOR_CATS = stringSetPreferencesKey("enabled_sponsor_cats")
        val BACKGROUND_PLAY_ON_LEAVE = booleanPreferencesKey("background_play_on_leave")
        val AUTO_PIP_ENABLED = booleanPreferencesKey("auto_pip_enabled")
        val SUBTITLES_AUTO_ENABLE = booleanPreferencesKey("subtitles_auto_enable")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val ADS_BLOCKED_COUNT = intPreferencesKey("ads_blocked_count")
        val SPONSOR_SECONDS_SAVED = longPreferencesKey("sponsor_seconds_saved")
    }

    val settingsFlow: Flow<OctoSettings> = context.octoDataStore.data.map { prefs ->
        val storedProvider = prefs[Keys.ACTIVE_PROVIDER]
        val storedPipedUrl = (prefs[Keys.CUSTOM_PIPED_URL] ?: ServiceProvider.PIPED.defaultBaseUrl).trimEnd('/')
        // The old official Piped API endpoint has been unavailable for an extended period.
        // Migrate only the old default selection; preserve user-configured Piped instances.
        val migrateLegacyPipedDefault = storedProvider == ServiceProvider.PIPED.id &&
            storedPipedUrl == ServiceProvider.PIPED.defaultBaseUrl.trimEnd('/')
        OctoSettings(
            activeProvider = if (migrateLegacyPipedDefault) {
                ServiceProvider.INVIDIOUS
            } else {
                ServiceProvider.fromId(storedProvider ?: ServiceProvider.INVIDIOUS.id)
            },
            customPipedUrl = prefs[Keys.CUSTOM_PIPED_URL] ?: ServiceProvider.PIPED.defaultBaseUrl,
            customInvidiousUrl = prefs[Keys.CUSTOM_INVIDIOUS_URL] ?: ServiceProvider.INVIDIOUS.defaultBaseUrl,
            customPeerTubeUrl = prefs[Keys.CUSTOM_PEERTUBE_URL] ?: ServiceProvider.PEERTUBE.defaultBaseUrl,
            sponsorBlockEnabled = prefs[Keys.SPONSOR_BLOCK_ENABLED] ?: true,
            autoSkipSponsor = prefs[Keys.AUTO_SKIP_SPONSOR] ?: true,
            enabledSponsorCategories = prefs[Keys.ENABLED_SPONSOR_CATS]
                ?: setOf("sponsor", "selfpromo", "intro", "outro", "interaction"),
            backgroundPlayOnLeave = prefs[Keys.BACKGROUND_PLAY_ON_LEAVE] ?: true,
            autoPipEnabled = prefs[Keys.AUTO_PIP_ENABLED] ?: true,
            subtitlesAutoEnable = prefs[Keys.SUBTITLES_AUTO_ENABLE] ?: false,
            preferredSubtitleLang = prefs[Keys.PREFERRED_SUBTITLE_LANG] ?: "ar",
            preferredQuality = prefs[Keys.PREFERRED_QUALITY] ?: "720p",
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            adsBlockedCount = prefs[Keys.ADS_BLOCKED_COUNT] ?: 148,
            sponsorSecondsSaved = prefs[Keys.SPONSOR_SECONDS_SAVED] ?: 0L
        )
    }

    suspend fun setActiveProvider(provider: ServiceProvider) {
        context.octoDataStore.edit { it[Keys.ACTIVE_PROVIDER] = provider.id }
    }

    suspend fun setCustomUrl(provider: ServiceProvider, url: String) {
        context.octoDataStore.edit {
            val normalized = url.trim().ifBlank { provider.defaultBaseUrl }
            when (provider) {
                ServiceProvider.PIPED -> it[Keys.CUSTOM_PIPED_URL] = normalized
                ServiceProvider.INVIDIOUS -> it[Keys.CUSTOM_INVIDIOUS_URL] = normalized
                ServiceProvider.PEERTUBE -> it[Keys.CUSTOM_PEERTUBE_URL] = normalized
            }
        }
    }

    suspend fun setSponsorBlockEnabled(enabled: Boolean) {
        context.octoDataStore.edit { it[Keys.SPONSOR_BLOCK_ENABLED] = enabled }
    }

    suspend fun setAutoSkipSponsor(enabled: Boolean) {
        context.octoDataStore.edit { it[Keys.AUTO_SKIP_SPONSOR] = enabled }
    }

    suspend fun toggleSponsorCategory(categoryKey: String) {
        context.octoDataStore.edit { prefs ->
            val current = prefs[Keys.ENABLED_SPONSOR_CATS]
                ?: setOf("sponsor", "selfpromo", "intro", "outro", "interaction")
            prefs[Keys.ENABLED_SPONSOR_CATS] = if (categoryKey in current) {
                current - categoryKey
            } else {
                current + categoryKey
            }
        }
    }

    suspend fun setBackgroundPlayOnLeave(enabled: Boolean) {
        context.octoDataStore.edit { it[Keys.BACKGROUND_PLAY_ON_LEAVE] = enabled }
    }

    suspend fun setAutoPipEnabled(enabled: Boolean) {
        context.octoDataStore.edit { it[Keys.AUTO_PIP_ENABLED] = enabled }
    }

    suspend fun setSubtitlesAutoEnable(enabled: Boolean) {
        context.octoDataStore.edit { it[Keys.SUBTITLES_AUTO_ENABLE] = enabled }
    }

    suspend fun setPreferredSubtitleLang(lang: String) {
        context.octoDataStore.edit { it[Keys.PREFERRED_SUBTITLE_LANG] = lang }
    }

    suspend fun setPreferredQuality(quality: String) {
        context.octoDataStore.edit { it[Keys.PREFERRED_QUALITY] = quality }
    }

    suspend fun setDarkTheme(dark: Boolean) {
        context.octoDataStore.edit { it[Keys.DARK_THEME] = dark }
    }

    suspend fun incrementAdsBlocked(by: Int = 1) {
        context.octoDataStore.edit {
            val curr = it[Keys.ADS_BLOCKED_COUNT] ?: 148
            it[Keys.ADS_BLOCKED_COUNT] = curr + by
        }
    }

    suspend fun addSponsorSecondsSaved(seconds: Long) {
        if (seconds <= 0L) return
        context.octoDataStore.edit {
            val curr = it[Keys.SPONSOR_SECONDS_SAVED] ?: 0L
            it[Keys.SPONSOR_SECONDS_SAVED] = curr + seconds
        }
    }
}
