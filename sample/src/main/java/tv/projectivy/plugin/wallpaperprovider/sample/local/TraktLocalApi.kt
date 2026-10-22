// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample.local

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

data class TraktDeviceCodeRequest(@SerializedName("client_id") val clientId: String)

data class TraktDeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String,
    @SerializedName("user_code") val userCode: String,
    @SerializedName("verification_url") val verificationUrl: String,
    @SerializedName("expires_in") val expiresIn: Int,
    val interval: Int
)

data class TraktDeviceTokenRequest(
    val code: String,
    @SerializedName("client_id") val clientId: String
)

data class TraktRefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("client_id") val clientId: String,
    @SerializedName("redirect_uri") val redirectUri: String,
    @SerializedName("grant_type") val grantType: String = "refresh_token"
)

data class TraktTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("created_at") val createdAt: Long
)

interface TraktLocalApi {
    @Headers("trakt-api-version: 2")
    @POST("oauth/device/code")
    suspend fun requestDeviceCode(
        @Header("trakt-api-key") apiKey: String,
        @Body body: TraktDeviceCodeRequest
    ): Response<TraktDeviceCodeResponse>

    @Headers("trakt-api-version: 2")
    @POST("oauth/device/token")
    suspend fun requestDeviceToken(
        @Header("trakt-api-key") apiKey: String,
        @Body body: TraktDeviceTokenRequest
    ): Response<TraktTokenResponse>

    @Headers("trakt-api-version: 2")
    @POST("oauth/token")
    suspend fun refreshToken(
        @Header("trakt-api-key") apiKey: String,
        @Body body: TraktRefreshTokenRequest
    ): Response<TraktTokenResponse>

    @Headers("trakt-api-version: 2")
    @GET("movies/anticipated")
    suspend fun anticipatedMovies(
        @Header("trakt-api-key") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 20
    ): Response<List<TraktAnticipatedItem>>

    @Headers("trakt-api-version: 2")
    @GET("shows/anticipated")
    suspend fun anticipatedShows(
        @Header("trakt-api-key") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 20
    ): Response<List<TraktAnticipatedItem>>

    @Headers("trakt-api-version: 2")
    @GET("movies/trending")
    suspend fun trendingMovies(
        @Header("trakt-api-key") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 20
    ): Response<List<TraktTrendingItem>>

    @Headers("trakt-api-version: 2")
    @GET("shows/trending")
    suspend fun trendingShows(
        @Header("trakt-api-key") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 20
    ): Response<List<TraktTrendingItem>>

    @Headers("trakt-api-version: 2")
    @GET("lists/popular")
    suspend fun popularLists(
        @Header("trakt-api-key") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): Response<List<TraktPopularListItem>>

    @Headers("trakt-api-version: 2")
    @GET("users/{id}/lists/{list_id}/items/{type}")
    suspend fun listItems(
        @retrofit2.http.Path("id") userId: String,
        @retrofit2.http.Path("list_id") listId: String,
        @retrofit2.http.Path("type") type: String,
        @Header("trakt-api-key") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 250,
        @Query("page") page: Int = 1
    ): Response<List<TraktListItem>>

    @Headers("trakt-api-version: 2")
    @GET("users/me/lists")
    suspend fun personalLists(
        @Header("trakt-api-key") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 250
    ): Response<List<TraktListSummary>>

    @Headers("trakt-api-version: 2")
    @GET("users/{id}/lists/{list_id}")
    suspend fun personalList(
        @retrofit2.http.Path("id") userId: String,
        @retrofit2.http.Path("list_id") listId: String,
        @Header("trakt-api-key") apiKey: String,
        @Header("Authorization") authorization: String
    ): Response<TraktListSummary>

    @Headers("trakt-api-version: 2")
    @GET("users/{id}/lists/{list_id}/items")
    suspend fun allListItems(
        @retrofit2.http.Path("id") userId: String,
        @retrofit2.http.Path("list_id") listId: String,
        @Header("trakt-api-key") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 250
    ): Response<List<TraktListItem>>

    @Headers("trakt-api-version: 2")
    @GET("users/me/watchlist")
    suspend fun watchlist(
        @Header("trakt-api-key") apiKey: String,
        @Header("Authorization") authorization: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 250
    ): Response<List<TraktListItem>>
}
