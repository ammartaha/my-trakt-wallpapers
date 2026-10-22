// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample.local

import tv.projectivy.plugin.wallpaperprovider.sample.PreferencesManager

class TmdbArtworkRepository(private val api: TmdbLocalApi) {
    suspend fun details(candidate: WallpaperCandidate): TmdbDetails? {
        val apiKey = PreferencesManager.tmdbApiKey
        if (apiKey.isBlank()) return null
        val isMovie = candidate.catalog.isMovie

        val detailsResponse = if (isMovie) api.movieDetails(candidate.tmdbId, apiKey) else api.showDetails(candidate.tmdbId, apiKey)
        if (detailsResponse.code() == 404) return null
        val detailsBody = detailsResponse.checkedBody("TMDB")
        if (detailsBody.backdropPath.isNullOrBlank()) return null

        val imagesResponse = if (isMovie) api.movieImages(candidate.tmdbId, apiKey) else api.showImages(candidate.tmdbId, apiKey)
        val logoUrl = if (imagesResponse.code() == 404) null else selectLogoUrl(imagesResponse.checkedBody("TMDB"))
        val title = detailsBody.title ?: detailsBody.name ?: candidate.title
        val year = (detailsBody.releaseDate ?: detailsBody.firstAirDate)?.take(4)?.toIntOrNull() ?: candidate.year

        return TmdbDetails(
            id = candidate.tmdbId,
            title = title,
            year = year,
            backdropUrl = imageUrl(detailsBody.backdropPath),
            logoUrl = logoUrl,
            overview = detailsBody.overview.orEmpty(),
            tagline = detailsBody.tagline.orEmpty(),
            rating = detailsBody.voteAverage?.takeIf { it > 0 && (detailsBody.voteCount ?: 0) > 0 },
            runtime = detailsBody.runtime ?: detailsBody.episodeRunTime?.firstOrNull(),
            genres = detailsBody.genres.orEmpty().map { it.name },
            isMovie = isMovie
        )
    }

    companion object {
        fun imageUrl(path: String): String =
            "https://image.tmdb.org/t/p/original/${path.trimStart('/')}"

        fun selectLogoUrl(images: TmdbImagesResponse): String? {
            val selected = images.logos.firstOrNull { it.iso6391 == "en" } ?: images.logos.firstOrNull()
            return selected?.filePath?.let(::imageUrl)
        }
    }
}
