package tv.projectivy.plugin.wallpaperprovider.sample.local

import com.traktlistbackdrops.tv.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import tv.projectivy.plugin.wallpaperprovider.sample.PreferencesManager
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class ApiFailure(val status: Int, val provider: String = "Trakt") : IOException(when (status) {
    401 -> if (provider == "TMDB") "TMDB API key was rejected. Check API settings." else "Trakt login expired. Select Connect Trakt again."
    403 -> "API access denied. Check your registered API app."
    404 -> "A selected list is not available. Check My lists."
    420, 423, 426 -> "Trakt account limits block this request. Check your Trakt account."
    429 -> "$provider is busy. The app will wait before it tries again."
    else -> "API request failed (HTTP $status). Saved wallpapers remain available."
})

fun <T> Response<T>.checkedBody(provider: String = "Trakt"): T {
    if (!isSuccessful) {
        if (code() == 401 && provider == "Trakt") PreferencesManager.traktExpiresAt = 0L
        throw ApiFailure(code(), provider)
    }
    return body() ?: throw IOException("The API returned an empty response.")
}

object RateLimitPolicy {
    fun retryAt(header: String?, nowMillis: Long, attempt: Int = 0): Long {
        header?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let {
            return nowMillis + it.coerceAtMost(604800L) * 1000L
        }
        if (!header.isNullOrBlank()) {
            val parsed = runCatching {
                SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }.parse(header)?.time
            }.getOrNull()
            if (parsed != null && parsed > nowMillis) return parsed
        }
        return nowMillis + (60_000L shl attempt.coerceIn(0, 6)) + kotlin.random.Random.nextLong(1000L, 5000L)
    }
}

object ApiClients {
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            // Refresh tokens are single use. Do not replay an uncertain POST.
            .retryOnConnectionFailure(false)
            .addInterceptor { chain ->
                val original = chain.request()
                val isTrakt = original.url.host == "api.trakt.tv" || original.url.host == "auth.trakt.tv"
                if (isTrakt && PreferencesManager.traktRetryAt > System.currentTimeMillis()) throw ApiFailure(429)
                val request = original.newBuilder()
                    .header("User-Agent", "MyTraktWallpapers/${BuildConfig.VERSION_NAME}")
                    .apply { if (isTrakt) header("Content-Type", "application/json") }
                    .build()
                chain.proceed(request).also { response ->
                    if (isTrakt && response.code == 429) {
                        val attempt = PreferencesManager["rate_limit_attempt", 0]
                        PreferencesManager.traktRetryAt = RateLimitPolicy.retryAt(response.header("Retry-After"), System.currentTimeMillis(), attempt)
                        PreferencesManager["rate_limit_attempt"] = (attempt + 1).coerceAtMost(6)
                    } else if (isTrakt && response.isSuccessful) PreferencesManager["rate_limit_attempt"] = 0
                }
            }.build()
    }

    private fun <T> create(base: String, type: Class<T>): T = Retrofit.Builder()
        .baseUrl(base).client(http).addConverterFactory(GsonConverterFactory.create()).build().create(type)

    fun trakt(): TraktLocalApi = create(BuildConfig.TRAKT_API_URL, TraktLocalApi::class.java)
    fun auth(): TraktLocalApi = create(BuildConfig.TRAKT_AUTH_URL, TraktLocalApi::class.java)
    fun tmdb(): TmdbLocalApi = create(BuildConfig.TMDB_API_URL, TmdbLocalApi::class.java)
}
