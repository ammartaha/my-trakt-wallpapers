// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample.local

import com.google.gson.annotations.SerializedName

enum class CatalogKind(val isMovie: Boolean) {
    ANTICIPATED_MOVIE(true),
    ANTICIPATED_SHOW(false),
    TRENDING_MOVIE(true),
    TRENDING_SHOW(false),
    POPULAR_LIST_MOVIE(true),
    POPULAR_LIST_SHOW(false),
    PERSONAL_LIST_MOVIE(true),
    PERSONAL_LIST_SHOW(false)
}

data class TraktIds(
    val trakt: Long? = null,
    val slug: String? = null,
    val imdb: String? = null,
    val tmdb: Long? = null,
    val tvdb: Long? = null
)

data class TraktMediaSummary(
    val title: String,
    val year: Int? = null,
    val ids: TraktIds
)

data class TraktAnticipatedItem(
    @SerializedName("list_count") val listCount: Int,
    val movie: TraktMediaSummary? = null,
    val show: TraktMediaSummary? = null
)

data class TraktTrendingItem(
    val watchers: Int? = null,
    val movie: TraktMediaSummary? = null,
    val show: TraktMediaSummary? = null
)

data class TraktListItem(
    val type: String? = null,
    val movie: TraktMediaSummary? = null,
    val show: TraktMediaSummary? = null
)

data class TraktPopularListItem(
    @SerializedName("like_count") val likeCount: Int? = null,
    @SerializedName("comment_count") val commentCount: Int? = null,
    val list: TraktListSummary? = null,
    val user: TraktUserSummary? = null
)

data class TraktListSummary(
    val name: String? = null,
    @SerializedName("item_count") val itemCount: Int? = null,
    val ids: TraktListIds? = null,
    val user: TraktUserSummary? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val privacy: String? = null
)

data class TraktListIds(
    val trakt: Long? = null,
    val slug: String? = null
)

data class TraktUserSummary(
    val username: String? = null,
    val ids: TraktUserIds? = null
)

data class TraktUserIds(
    val slug: String? = null
)

data class TraktPopularListOption(
    val key: String,
    val title: String,
    val userId: String,
    val listId: String,
    val itemCount: Int = 0
)

data class WallpaperCandidate(
    val catalog: CatalogKind,
    val title: String,
    val year: Int?,
    val tmdbId: Long,
    val imdbId: String?,
    val traktId: Long?,
    val tvdbId: Long?
)

data class TmdbDetails(
    val id: Long,
    val title: String,
    val year: Int?,
    val backdropUrl: String,
    val logoUrl: String?,
    val overview: String = "",
    val rating: Double? = null,
    val runtime: Int? = null,
    val genres: List<String> = emptyList(),
    val tagline: String = "",
    val isMovie: Boolean = true
)

data class GeneratedWallpaper(
    val title: String,
    val filePath: String,
    val contentUri: String
)
