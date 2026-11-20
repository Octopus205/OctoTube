package com.example.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkModule {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun createHttpClient(
        acceptHeader: String,
        callTimeoutSeconds: Long? = 60L
    ): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (callTimeoutSeconds != null) {
                    callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
                }
            }
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36 OctoTube/1.0")
                    .header("Accept", acceptHeader)
                    .build()
                chain.proceed(request)
            }
            .build()

    // JSON API requests and media downloads must not share the same Accept header.
    val okHttpClient: OkHttpClient by lazy { createHttpClient("application/json") }

    // ExoPlayer requests video/audio segments and manifests, not JSON documents.
    val mediaOkHttpClient: OkHttpClient by lazy { createHttpClient("*/*") }

    // Long-running downloads must not be cut off by the per-request media timeout.
    val downloadOkHttpClient: OkHttpClient by lazy {
        createHttpClient("*/*", callTimeoutSeconds = null)
    }

    suspend fun getString(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from $url")
            }
            response.body?.string() ?: throw IOException("Empty body from $url")
        }
    }

    suspend fun getMediaString(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        downloadOkHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} from $url")
            response.body?.string() ?: throw IOException("Empty media response from $url")
        }
    }

    suspend fun pingUrl(url: String): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder().url(url).head().build()
            okHttpClient.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - start
                (response.code in 200..499) to elapsed
            }
        } catch (_: Exception) {
            val elapsed = System.currentTimeMillis() - start
            false to elapsed
        }
    }
}
