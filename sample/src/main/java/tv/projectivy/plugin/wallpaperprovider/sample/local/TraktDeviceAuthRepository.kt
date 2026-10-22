// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample.local

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.projectivy.plugin.wallpaperprovider.sample.PreferencesManager

sealed class TraktPollResult {
    data object Pending : TraktPollResult()
    data object InvalidDeviceCode : TraktPollResult()
    data object AlreadyUsed : TraktPollResult()
    data object Expired : TraktPollResult()
    data object Denied : TraktPollResult()
    data object SlowDown : TraktPollResult()
    data object Approved : TraktPollResult()
    data class Failed(val message: String) : TraktPollResult()
}

class TraktDeviceAuthRepository(
    private val api: TraktLocalApi = ApiClients.auth(),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) {
    suspend fun start(): Result<TraktDeviceCodeResponse> = refreshMutex.withLock { runCatching {
        val clientId = PreferencesManager.traktClientId
        check(clientId.isNotBlank()) { "Open API settings and enter your Trakt client ID first." }
        val body = api.requestDeviceCode(clientId, TraktDeviceCodeRequest(clientId)).checkedBody()
        PreferencesManager.traktDeviceCode = body.deviceCode
        PreferencesManager.traktUserCode = body.userCode
        PreferencesManager.traktVerificationUrl = body.verificationUrl
        PreferencesManager.deviceExpiresAt = nowSeconds() + body.expiresIn
        PreferencesManager.devicePollSeconds = body.interval
        body
    }.onFailure { if (it is CancellationException) throw it } }

    suspend fun poll(): TraktPollResult = refreshMutex.withLock {
        if (PreferencesManager.deviceExpiresAt <= nowSeconds()) return@withLock TraktPollResult.Expired
        val clientId = PreferencesManager.traktClientId
        val code = PreferencesManager.traktDeviceCode
        if (code.isBlank()) return@withLock TraktPollResult.InvalidDeviceCode
        val response = api.requestDeviceToken(clientId, TraktDeviceTokenRequest(code, clientId))
        if (!response.isSuccessful) {
            if (response.code() == 429) PreferencesManager.devicePollSeconds += 5
            return@withLock mapPollStatus(response.code(), null)
        }
        val token = response.body() ?: return@withLock TraktPollResult.Failed("Trakt returned no login data.")
        PreferencesManager.saveTokens(token, nowSeconds(), newLogin = true)
        TraktPollResult.Approved
    }

    // Shared across all repository instances: only one single-use token refresh at a time.
    suspend fun authorizationHeader(): String? = refreshMutex.withLock {
        val access = PreferencesManager.traktAccessToken
        if (access.isNotBlank() && isTokenUsable(nowSeconds(), PreferencesManager.traktExpiresAt)) return@withLock "Bearer $access"
        val refresh = PreferencesManager.traktRefreshToken
        val clientId = PreferencesManager.traktClientId
        if (refresh.isBlank() || clientId.isBlank()) return@withLock null
        val response = api.refreshToken(clientId, TraktRefreshTokenRequest(refresh, clientId, PreferencesManager.traktRedirectUri))
        if (!response.isSuccessful) {
            val invalidGrant = response.code() == 400 && runCatching {
                JsonParser.parseString(response.errorBody()?.string()).asJsonObject.get("error")?.asString == "invalid_grant"
            }.getOrDefault(false)
            if (invalidGrant || response.code() == 401) {
                // Retain offline wallpapers until the user connects again.
                PreferencesManager.traktAccessToken = ""
                PreferencesManager.traktRefreshToken = ""
                return@withLock null
            }
            throw ApiFailure(response.code())
        }
        val token = response.body() ?: return@withLock null
        PreferencesManager.saveTokens(token, nowSeconds())
        "Bearer ${token.accessToken}"
    }

    companion object {
        internal val refreshMutex = Mutex()
        fun mapPollStatus(statusCode: Int, message: String?): TraktPollResult = when (statusCode) {
            400 -> TraktPollResult.Pending
            404 -> TraktPollResult.InvalidDeviceCode
            409 -> TraktPollResult.AlreadyUsed
            410 -> TraktPollResult.Expired
            418 -> TraktPollResult.Denied
            429 -> TraktPollResult.SlowDown
            else -> TraktPollResult.Failed(message ?: "HTTP $statusCode")
        }
        fun isTokenUsable(nowSeconds: Long, expiresAtSeconds: Long): Boolean = expiresAtSeconds - 60L > nowSeconds
    }
}
