// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample.local

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class TmdbDetailsResponse(
    val id: Long,
    val title: String? = null,
    val name: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("first_air_date") val firstAirDate: String? = null,
    @SerializedName("backdrop_path") val backdropPath: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val runtime: Int? = null,
    @SerializedName("episode_run_time") val episodeRunTime: List<Int>? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    @SerializedName("vote_count") val voteCount: Int? = null,
    val genres: List<TmdbGenre>? = null
)

data class TmdbGenre(val id: Int, val name: String)

data class TmdbImagesResponse(val logos: List<TmdbLogo> = emptyList())

data class TmdbLogo(
    @SerializedName("file_path") val filePath: String,
    @SerializedName("iso_639_1") val iso6391: String? = null
)

interface TmdbLocalApi {
    @GET("movie/{id}")
    suspend fun movieDetails(@Path("id") id: Long, @Query("api_key") apiKey: String): Response<TmdbDetailsResponse>

    @GET("tv/{id}")
    suspend fun showDetails(@Path("id") id: Long, @Query("api_key") apiKey: String): Response<TmdbDetailsResponse>

    @GET("movie/{id}/images")
    suspend fun movieImages(@Path("id") id: Long, @Query("api_key") apiKey: String): Response<TmdbImagesResponse>

    @GET("tv/{id}/images")
    suspend fun showImages(@Path("id") id: Long, @Query("api_key") apiKey: String): Response<TmdbImagesResponse>
}
