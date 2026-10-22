// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample.local

import com.traktlistbackdrops.tv.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BuildConfigContractTest {
    @Test
    fun providerBuildConfigFieldsExist() {
        assertEquals("com.sww.projectivy.traktwallpapers", BuildConfig.APPLICATION_ID)
        assertEquals("https://api.themoviedb.org/3/", BuildConfig.TMDB_API_URL.ifBlank { "https://api.themoviedb.org/3/" })
        assertEquals(false, BuildConfig::class.java.fields.any { it.name in setOf("TRAKT_CLIENT_ID", "TRAKT_CLIENT_SECRET", "TMDB_API_KEY") })
        assertEquals("https://api.trakt.tv/", BuildConfig.TRAKT_API_URL.ifBlank { "https://api.trakt.tv/" })
        assertEquals("https://auth.trakt.tv/", BuildConfig.TRAKT_AUTH_URL)
    }
}
