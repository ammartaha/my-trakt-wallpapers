// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlin.collections.all
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.mapTo
import tv.projectivy.plugin.wallpaperprovider.sample.local.SecretStore
import tv.projectivy.plugin.wallpaperprovider.sample.local.KeystoreSecretStore
import tv.projectivy.plugin.wallpaperprovider.sample.local.TraktTokenResponse
import java.util.UUID
import tv.projectivy.plugin.wallpaperprovider.sample.local.WallpaperState

object PreferencesManager {
    private const val IMAGE_URL_KEY = "image_url_key"
    private const val SERVER_URL_KEY = "server_url_key"
    private const val SELECTED_LAYOUT_KEY = "selected_layout_key"
    private const val GENRE_FILTER_KEY = "genre_filter_key"
    private const val AGE_FILTER_KEY = "age_filter_key"
    private const val YEAR_FILTER_KEY = "year_filter_key"
    private const val MIN_RATING_KEY = "pref_min_rating"
    private const val MAX_RATING_KEY = "pref_max_rating"
    private const val REFRESH_ON_IDLE_EXIT_KEY = "pref_refresh_on_idle_exit"
    private const val LAST_WALLPAPER_URI_KEY = "last_wallpaper_uri"
    private const val LAST_WALLPAPER_AUTHOR_KEY = "last_wallpaper_author"
    private const val TRAKT_ACCESS_TOKEN_KEY = "trakt_access_token"
    private const val TRAKT_REFRESH_TOKEN_KEY = "trakt_refresh_token"
    private const val TRAKT_EXPIRES_AT_KEY = "trakt_expires_at"
    private const val TRAKT_DEVICE_CODE_KEY = "trakt_device_code"
    private const val TRAKT_USER_CODE_KEY = "trakt_user_code"
    private const val TRAKT_VERIFICATION_URL_KEY = "trakt_verification_url"
    private const val SELECTED_CATALOGS_KEY = "selected_catalogs"
    private const val POPULAR_LIST_OPTIONS_KEY = "popular_list_options"

    lateinit var preferences: SharedPreferences
    private lateinit var secrets: SecretStore
    private val exportKeys = setOf(SELECTED_CATALOGS_KEY, REFRESH_ON_IDLE_EXIT_KEY, "sync_minutes", "cache_count")

    @Synchronized
    fun init(context: Context, secretStore: SecretStore? = null) {
        if (!::preferences.isInitialized || secretStore != null) {
            preferences = PreferenceManager.getDefaultSharedPreferences(context)
            secrets = secretStore ?: KeystoreSecretStore(context.applicationContext)
        }
    }

    private inline fun SharedPreferences.edit(operation: (SharedPreferences.Editor) -> Unit) {
        val editor = this.edit()
        operation(editor)
        editor.apply()
    }

    operator fun set(key: String, value: Any?) {
        when (value) {
            is String? -> preferences.edit { it.putString(key, value) }
            is Int -> preferences.edit { it.putInt(key, value) }
            is Boolean -> preferences.edit { it.putBoolean(key, value) }
            is Float -> preferences.edit { it.putFloat(key, value) }
            is Long -> preferences.edit { it.putLong(key, value) }
            else -> throw UnsupportedOperationException("Not yet implemented")
        }
    }

    inline operator fun <reified T : Any> get(
        key: String,
        defaultValue: T? = null
    ): T =
        when (T::class) {
            String::class -> preferences.getString(key, defaultValue as String? ?: "") as T
            Int::class -> preferences.getInt(key, defaultValue as? Int ?: -1) as T
            Boolean::class -> preferences.getBoolean(key, defaultValue as? Boolean ?: false) as T
            Float::class -> preferences.getFloat(key, defaultValue as? Float ?: -1f) as T
            Long::class -> preferences.getLong(key, defaultValue as? Long ?: -1) as T
            else -> throw UnsupportedOperationException("Not yet implemented")
        }

    var imageUrl: String
        get() = PreferencesManager[IMAGE_URL_KEY, "https://images.pexels.com/photos/462162/pexels-photo-462162.jpeg"]
        set(value) { PreferencesManager[IMAGE_URL_KEY]=value }

    var serverUrl: String
        get() = PreferencesManager[SERVER_URL_KEY, "http://192.168.1.100:5000"]
        set(value) { PreferencesManager[SERVER_URL_KEY] = value }

    var selectedLayout: String
        get() = PreferencesManager[SELECTED_LAYOUT_KEY, "default"]
        set(value) { PreferencesManager[SELECTED_LAYOUT_KEY] = value }

    var genreFilter: String
        get() = PreferencesManager[GENRE_FILTER_KEY, ""]
        set(value) { PreferencesManager[GENRE_FILTER_KEY] = value }

    var ageFilter: String
        get() = PreferencesManager[AGE_FILTER_KEY, ""]
        set(value) { PreferencesManager[AGE_FILTER_KEY] = value }
    
    var yearFilter: String
        get() = PreferencesManager[YEAR_FILTER_KEY, ""]
        set(value) { PreferencesManager[YEAR_FILTER_KEY] = value }

    var minRating: Float
        get() = PreferencesManager[MIN_RATING_KEY, 7.0f]
        set(value) { PreferencesManager[MIN_RATING_KEY] = value }

    var maxRating: Float
        get() = PreferencesManager[MAX_RATING_KEY, 10.0f]
        set(value) { PreferencesManager[MAX_RATING_KEY] = value }

    var refreshOnIdleExit: Boolean
        get() = PreferencesManager[REFRESH_ON_IDLE_EXIT_KEY, true]
        set(value) { PreferencesManager[REFRESH_ON_IDLE_EXIT_KEY] = value }

    var lastWallpaperUri: String
        get() = PreferencesManager[LAST_WALLPAPER_URI_KEY, ""]
        set(value) { PreferencesManager[LAST_WALLPAPER_URI_KEY] = value }

    var lastWallpaperAuthor: String
        get() = PreferencesManager[LAST_WALLPAPER_AUTHOR_KEY, ""]
        set(value) { PreferencesManager[LAST_WALLPAPER_AUTHOR_KEY] = value }

    var preferredClient: String
        get() = PreferencesManager["preferred_client_key", "org.moonfin.androidtv"]
        set(value) { PreferencesManager["preferred_client_key"] = value }

    var traktAccessToken: String
        get() = secrets.get(TRAKT_ACCESS_TOKEN_KEY)
        set(value) { secrets.putAll(mapOf(TRAKT_ACCESS_TOKEN_KEY to value)) }

    var traktRefreshToken: String
        get() = secrets.get(TRAKT_REFRESH_TOKEN_KEY)
        set(value) { secrets.putAll(mapOf(TRAKT_REFRESH_TOKEN_KEY to value)) }

    var traktExpiresAt: Long
        get() = secrets.get(TRAKT_EXPIRES_AT_KEY).toLongOrNull() ?: 0L
        set(value) { secrets.putAll(mapOf(TRAKT_EXPIRES_AT_KEY to value.toString())) }

    var traktDeviceCode: String
        get() = secrets.get(TRAKT_DEVICE_CODE_KEY)
        set(value) { secrets.putAll(mapOf(TRAKT_DEVICE_CODE_KEY to value)) }

    var traktUserCode: String
        get() = secrets.get(TRAKT_USER_CODE_KEY)
        set(value) { secrets.putAll(mapOf(TRAKT_USER_CODE_KEY to value)) }

    var traktVerificationUrl: String
        get() = PreferencesManager[TRAKT_VERIFICATION_URL_KEY, ""]
        set(value) { PreferencesManager[TRAKT_VERIFICATION_URL_KEY] = value }

    var selectedCatalogs: String
        get() = PreferencesManager[SELECTED_CATALOGS_KEY, ""]
        set(value) { synchronized(WallpaperState.lock) { PreferencesManager[SELECTED_CATALOGS_KEY] = value } }

    var popularListOptions: String
        get() = PreferencesManager[POPULAR_LIST_OPTIONS_KEY, ""]
        set(value) { PreferencesManager[POPULAR_LIST_OPTIONS_KEY] = value }

    var personalListOptions: String
        get() = PreferencesManager["personal_list_options", ""]
        set(value) { PreferencesManager["personal_list_options"] = value }

    var traktClientId: String
        get() = secrets.get("client_id")
        set(value) { secrets.putAll(mapOf("client_id" to value.trim())) }

    var traktRedirectUri: String
        get() = PreferencesManager["redirect_uri", "https://github.com/"]
        set(value) { PreferencesManager["redirect_uri"] = value.trim() }

    var tmdbApiKey: String
        get() = secrets.get("tmdb_api_key")
        set(value) { secrets.putAll(mapOf("tmdb_api_key" to value.trim())) }

    var accountScope: String
        get() = PreferencesManager["account_scope", ""]
        set(value) { synchronized(WallpaperState.lock) { PreferencesManager["account_scope"] = value } }

    var syncMinutes: Int
        get() = PreferencesManager["sync_minutes", 60].takeIf { it in setOf(15, 60, 360) } ?: 60
        set(value) { PreferencesManager["sync_minutes"] = value.takeIf { it in setOf(15, 60, 360) } ?: 60 }

    var cacheCount: Int
        get() = PreferencesManager["cache_count", 60].coerceIn(10, 60)
        set(value) { PreferencesManager["cache_count"] = value.coerceIn(10, 60) }

    var syncStatus: String
        get() = PreferencesManager["sync_status", "Choose your lists, then select Refresh now."]
        set(value) { PreferencesManager["sync_status"] = value }

    var traktRetryAt: Long
        get() = PreferencesManager["trakt_retry_at", 0L]
        set(value) { PreferencesManager["trakt_retry_at"] = value }

    var deviceExpiresAt: Long
        get() = PreferencesManager["device_expires_at", 0L]
        set(value) { PreferencesManager["device_expires_at"] = value }

    var devicePollSeconds: Int
        get() = PreferencesManager["device_poll_seconds", 5]
        set(value) { PreferencesManager["device_poll_seconds"] = value.coerceAtLeast(1) }

    fun saveTokens(token: TraktTokenResponse, now: Long, newLogin: Boolean = false) {
        require(token.accessToken.isNotBlank() && token.refreshToken.isNotBlank()) { "Trakt returned empty login data." }
        secrets.putAll(mapOf(TRAKT_ACCESS_TOKEN_KEY to token.accessToken,
            TRAKT_REFRESH_TOKEN_KEY to token.refreshToken,
            TRAKT_EXPIRES_AT_KEY to (token.createdAt.takeIf { it > 0 }?.plus(token.expiresIn) ?: (now + token.expiresIn)).toString(),
            TRAKT_DEVICE_CODE_KEY to "", TRAKT_USER_CODE_KEY to ""))
        if (newLogin) accountScope = UUID.randomUUID().toString()
    }

    fun clearLogin() {
        secrets.putAll(mapOf(TRAKT_ACCESS_TOKEN_KEY to "", TRAKT_REFRESH_TOKEN_KEY to "",
            TRAKT_EXPIRES_AT_KEY to "0", TRAKT_DEVICE_CODE_KEY to "", TRAKT_USER_CODE_KEY to ""))
        accountScope = UUID.randomUUID().toString()
    }

    fun export(): String {
        return convertSharedPreferencesToJson(preferences)
    }

    fun import(prefs: String): Boolean {
        try {
            importPreferencesFromJson(preferences, prefs)
        } catch (e: Exception) {
            Log.e("import", "Error importing preferences", e)
            return false
        }
        return true
    }

    private fun convertSharedPreferencesToJson(sharedPreferences: SharedPreferences): String {
        val prefsJsonObject = buildJsonObject {
            sharedPreferences.all.forEach { (key, value) ->
                if (key !in exportKeys) return@forEach
                when (value) {
                    is Int -> put(key, JsonPrimitive(value))
                    is Long -> put(key, JsonPrimitive(value))
                    is Float -> put(key, JsonPrimitive(value))
                    is Boolean -> put(key, JsonPrimitive(value))
                    is String -> put(key, JsonPrimitive(value))
                    is Set<*> -> if (value.all { it is String }) {
                        put(key, JsonArray(value.map { JsonPrimitive(it as String) }))
                    } else {
                        throw IllegalArgumentException("Unsupported set type")
                    }
                    else -> throw IllegalArgumentException("Unsupported preference type ${value?.javaClass} for key $key")
                }
            }
        }
        return prefsJsonObject.toString()
    }

    private fun importPreferencesFromJson(sharedPreferences: SharedPreferences, jsonString: String) {
        val jsonElement = Json.parseToJsonElement(jsonString)

        if (jsonElement is JsonObject) {
            val editor = sharedPreferences.edit()
            jsonElement.forEach { (key, value) ->
                if (key !in exportKeys) return@forEach
                require(value is JsonPrimitive) { "Invalid setting type" }
                when (key) {
                    SELECTED_CATALOGS_KEY -> require(value.isString && value.content.length <= 32768) { "Invalid source selection" }
                    REFRESH_ON_IDLE_EXIT_KEY -> require(!value.isString && value.booleanOrNull != null) { "Invalid idle setting" }
                    "sync_minutes" -> require(!value.isString && value.intOrNull in setOf(15, 60, 360)) { "Invalid check interval" }
                    "cache_count" -> require(!value.isString && value.intOrNull in setOf(10, 30, 60)) { "Invalid cache limit" }
                }
                when {
                    value.isString -> editor.putString(key, value.content)
                    value.booleanOrNull != null -> editor.putBoolean(key, value.boolean)
                    value.intOrNull != null -> editor.putInt(key, value.int)
                }
            }
            synchronized(WallpaperState.lock) { editor.apply() }
        } else {
            throw IllegalArgumentException("Expected JSON object for preferences import")
        }
    }
}
