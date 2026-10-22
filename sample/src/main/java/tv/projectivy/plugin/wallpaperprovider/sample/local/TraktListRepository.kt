package tv.projectivy.plugin.wallpaperprovider.sample.local

import retrofit2.Response
import java.io.IOException

/** Only an explicit selection can add candidates. No empty/error fallback to public catalogs. */
class TraktListRepository(private val api: TraktLocalApi) {
    suspend fun myLists(apiKey: String, authorization: String): List<TraktPopularListOption> =
        pages(allowUnpaged = true) { page -> api.personalLists(apiKey, authorization, page) }
            .mapNotNull { list ->
                val id = list.ids?.trakt?.toString() ?: return@mapNotNull null
                TraktPopularListOption("personal_list:me:$id", list.name ?: "List $id", "me", id, list.itemCount ?: 0)
            }.distinctBy { it.key }

    suspend fun candidates(selection: Set<String>, apiKey: String, authorization: String): List<WallpaperCandidate> {
        val result = mutableListOf<WallpaperCandidate>()
        for (key in selection.sorted()) {
            when (key) {
                "anticipated_movies" -> result += api.anticipatedMovies(apiKey, authorization).checkedBody()
                    .mapNotNull { it.toWallpaperCandidate(CatalogKind.ANTICIPATED_MOVIE) }
                "anticipated_shows" -> result += api.anticipatedShows(apiKey, authorization).checkedBody()
                    .mapNotNull { it.toWallpaperCandidate(CatalogKind.ANTICIPATED_SHOW) }
                "trending_movies" -> result += api.trendingMovies(apiKey, authorization).checkedBody()
                    .mapNotNull { it.toWallpaperCandidate(CatalogKind.TRENDING_MOVIE) }
                "trending_shows" -> result += api.trendingShows(apiKey, authorization).checkedBody()
                    .mapNotNull { it.toWallpaperCandidate(CatalogKind.TRENDING_SHOW) }
                "watchlist" -> result += mapItems(pages { page -> api.watchlist(apiKey, authorization, page) }, false)
                else -> {
                    val list = parseListKey(key) ?: throw IOException("A selected source is invalid. Choose your lists again.")
                    val before = api.personalList(list.userId, list.listId, apiKey, authorization).checkedBody()
                    val items = pages { page -> api.allListItems(list.userId, list.listId, apiKey, authorization, page) }
                    val after = api.personalList(list.userId, list.listId, apiKey, authorization).checkedBody()
                    check(before.updatedAt == after.updatedAt && before.itemCount == after.itemCount) {
                        "A Trakt list changed during this check. The app will try again."
                    }
                    result += mapItems(items, key.startsWith("popular_list:"))
                }
            }
        }
        return result.distinctBy(::mediaKey)
    }

    private fun mapItems(items: List<TraktListItem>, popular: Boolean): List<WallpaperCandidate> = items.mapNotNull { item ->
        when (item.type) {
            "movie" -> item.toWallpaperCandidate(if (popular) CatalogKind.POPULAR_LIST_MOVIE else CatalogKind.PERSONAL_LIST_MOVIE)
            "show" -> item.toWallpaperCandidate(if (popular) CatalogKind.POPULAR_LIST_SHOW else CatalogKind.PERSONAL_LIST_SHOW)
            else -> null // Do not silently expand episodes, seasons or people into unrelated shows.
        }
    }

    companion object {
        fun parseListKey(key: String): TraktPopularListOption? {
            val parts = key.split(':')
            if (parts.size != 3 || parts[0] !in setOf("personal_list", "popular_list") ||
                parts.drop(1).any { it.isBlank() || '/' in it || '?' in it || '#' in it }) return null
            return TraktPopularListOption(key, "${parts[1]}/${parts[2]}", parts[1], parts[2])
        }

        // Inspect the response headers. The server can clamp the requested page size.
        suspend fun <T> pages(allowUnpaged: Boolean = false, fetch: suspend (Int) -> Response<List<T>>): List<T> {
            val items = mutableListOf<T>()
            var page = 1
            var expectedPages: Int? = null
            var expectedCount: Int? = null
            while (page <= 10000) {
                val response = fetch(page)
                val body = response.checkedBody()
                val headers = response.headers()
                val pageCount = headers["X-Pagination-Page-Count"]?.toIntOrNull()
                val itemCount = headers["X-Pagination-Item-Count"]?.toIntOrNull()
                if (pageCount == null) {
                    check(page == 1 && (allowUnpaged || body.isEmpty())) { "Trakt did not return complete page information. Saved titles were kept." }
                    return body
                }
                check(headers["X-Pagination-Page"]?.toIntOrNull() == page && pageCount in 0..10000 && itemCount != null && itemCount >= 0) { "Trakt returned invalid page information." }
                if (expectedPages != null) check(pageCount == expectedPages && itemCount == expectedCount) {
                    "A Trakt list changed during this check. Saved titles were kept."
                }
                expectedPages = pageCount
                expectedCount = itemCount
                items += body
                if (page >= pageCount) {
                    check(items.size == itemCount) { "Trakt returned an incomplete list. Saved titles were kept." }
                    return items
                }
                check(body.isNotEmpty()) { "Trakt returned an empty page before the end of the list." }
                page++
            }
            throw IOException("The selected list has too many pages to load safely.")
        }
    }
}

fun mediaKey(candidate: WallpaperCandidate): String = "${if (candidate.catalog.isMovie) "movie" else "tv"}-${candidate.tmdbId}"
