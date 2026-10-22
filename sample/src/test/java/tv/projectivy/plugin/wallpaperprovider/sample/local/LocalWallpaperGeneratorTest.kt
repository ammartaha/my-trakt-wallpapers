// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample.local

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class LocalWallpaperGeneratorTest {
    @Test
    fun alternatesMovieAndShowCandidatesByDefault() {
        val movies = listOf(
            WallpaperCandidate(CatalogKind.ANTICIPATED_MOVIE, "Movie A", 2026, 1, "tt1", 1, null),
            WallpaperCandidate(CatalogKind.ANTICIPATED_MOVIE, "Movie B", 2026, 2, "tt2", 2, null)
        )
        val shows = listOf(
            WallpaperCandidate(CatalogKind.ANTICIPATED_SHOW, "Show A", 2026, 3, "tt3", 3, 4)
        )

        assertEquals(listOf("Movie A", "Show A", "Movie B"), LocalWallpaperGenerator.interleave(movies, shows).map { it.title })
    }

    @Test
    fun needsBackdropButAllowsTextTitleWhenLogoIsMissing() {
        val details = TmdbDetails(
            id = 1,
            title = "Movie",
            year = 2026,
            backdropUrl = "https://image.tmdb.org/t/p/original/backdrop.jpg",
            logoUrl = "https://image.tmdb.org/t/p/original/logo.png"
        )

        assertEquals(true, LocalWallpaperGenerator.isRenderable(details, hasBackdrop = true, hasLogo = true))
        assertEquals(false, LocalWallpaperGenerator.isRenderable(details, hasBackdrop = false, hasLogo = true))
        assertEquals(true, LocalWallpaperGenerator.isRenderable(details, hasBackdrop = true, hasLogo = false))
        assertEquals(true, LocalWallpaperGenerator.isRenderable(details.copy(logoUrl = null), hasBackdrop = true, hasLogo = false))
    }

    @Test
    fun randomizesMovieAndShowCandidates() {
        val movies = listOf(
            WallpaperCandidate(CatalogKind.ANTICIPATED_MOVIE, "Movie A", 2026, 1, "tt1", 1, null),
            WallpaperCandidate(CatalogKind.ANTICIPATED_MOVIE, "Movie B", 2026, 2, "tt2", 2, null)
        )
        val shows = listOf(
            WallpaperCandidate(CatalogKind.ANTICIPATED_SHOW, "Show A", 2026, 3, "tt3", 3, 4),
            WallpaperCandidate(CatalogKind.ANTICIPATED_SHOW, "Show B", 2026, 4, "tt4", 4, 5)
        )

        val randomized = LocalWallpaperGenerator.randomizedCandidates(movies, shows, Random(7))

        assertEquals(setOf("Movie A", "Movie B", "Show A", "Show B"), randomized.map { it.title }.toSet())
        assertEquals(4, randomized.size)
    }
}
