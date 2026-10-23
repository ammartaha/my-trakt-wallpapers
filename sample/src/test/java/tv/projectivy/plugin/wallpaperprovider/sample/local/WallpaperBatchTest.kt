package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import tv.projectivy.plugin.wallpaperprovider.sample.PreferencesManager
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperBatchTest {
    private lateinit var context: Context
    private lateinit var cache: WallpaperCache

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferencesManager.init(context, object : SecretStore {
            private val values = mutableMapOf<String, String>()
            override fun get(key: String) = values[key].orEmpty()
            override fun putAll(values: Map<String, String>) { this.values.putAll(values) }
        })
        PreferencesManager.preferences.edit().clear().commit()
        cache = WallpaperCache(context)
        cache.clear()
        PreferencesManager.accountScope = "batch-test-account"
        PreferencesManager.selectedCatalogs = "personal_list:me:123"
        PreferencesManager.tmdbApiKey = "batch-test-key"
        PreferencesManager.syncMinutes = 360
        PreferencesManager.cacheCount = 60
        PreferencesManager["last_generation_at"] = 0L
    }

    @Test fun createsTenDurableJpegsThenContinuesWithoutRenderingExistingTitles() = runTest {
        val candidates = candidates(12)
        val snapshot = SourceSnapshot(SourceSelection.signature(), System.currentTimeMillis(), candidates)
        cache.saveSnapshot(snapshot)
        val metadata = FakeTmdbApi()
        val artworkRequests = mutableListOf<String>()
        val generator = generator(metadata, artworkRequests)

        assertEquals(10, generator.syncAndGenerate())
        val first = WallpaperCache(context).eligible()
        assertEquals(10, first.size)
        assertTrue(first.any { it.key.startsWith("movie-") })
        assertTrue(first.any { it.key.startsWith("tv-") })
        assertDurableJpegs(first)
        val firstBytes = first.associate { it.key to File(cache.directory, it.fileName).readBytes() }
        assertEquals(10, metadata.detailRequests.size)
        assertEquals(10, artworkRequests.size)

        // Allow the next job without making the fresh source snapshot need a Trakt request.
        PreferencesManager["last_generation_at"] = 0L
        assertEquals(2, generator.syncAndGenerate())
        val reopened = WallpaperCache(context)
        val all = reopened.eligible()
        assertEquals(candidates.map(::mediaKey).toSet(), all.map { it.key }.toSet())
        assertEquals(12, all.size)
        assertEquals(6, all.count { it.key.startsWith("movie-") })
        assertEquals(6, all.count { it.key.startsWith("tv-") })
        assertDurableJpegs(all)
        for (entry in first) {
            assertEquals(entry, all.single { it.key == entry.key })
            assertArrayEquals(firstBytes.getValue(entry.key), File(reopened.directory, entry.fileName).readBytes())
        }
        assertEquals(12, metadata.detailRequests.size)
        assertEquals(candidates.map(::mediaKey).toSet(), metadata.detailRequests.toSet())
        assertEquals(12, metadata.imageRequests.size)
        assertEquals(12, artworkRequests.size)
        assertEquals(12, artworkRequests.toSet().size)
        assertEquals(snapshot, reopened.snapshot())
        assertEquals(setOf("personal_list:me:123"), SourceSelection.selected())

        PreferencesManager["last_generation_at"] = 0L
        assertEquals(0, generator.syncAndGenerate())
        assertEquals(12, metadata.detailRequests.size)
        assertEquals(12, artworkRequests.size)
        assertEquals(all.toSet(), WallpaperCache(context).eligible().toSet())
    }

    @Test fun stopsAfterTwentyFailedMetadataAttemptsAndSkipsThoseFailuresNextRun() = runTest {
        val candidates = candidates(25)
        cache.saveSnapshot(SourceSnapshot(SourceSelection.signature(), System.currentTimeMillis(), candidates))
        val metadata = FakeTmdbApi(missing = true)
        val artworkRequests = mutableListOf<String>()
        val generator = generator(metadata, artworkRequests)

        assertEquals(0, generator.syncAndGenerate())
        assertEquals(20, metadata.detailRequests.size)
        assertEquals(20, metadata.detailRequests.toSet().size)
        assertEquals(20, candidates.count { cache.failedUntil(mediaKey(it)) > System.currentTimeMillis() })
        assertTrue(metadata.imageRequests.isEmpty())
        assertTrue(artworkRequests.isEmpty())
        assertTrue(cache.eligible().isEmpty())

        PreferencesManager["last_generation_at"] = 0L
        assertEquals(0, generator.syncAndGenerate())
        assertEquals(25, metadata.detailRequests.size)
        assertEquals(candidates.map(::mediaKey).toSet(), metadata.detailRequests.toSet())
        assertTrue(candidates.all { cache.failedUntil(mediaKey(it)) > System.currentTimeMillis() })
        assertTrue(artworkRequests.isEmpty())
        assertTrue(cache.entries().isEmpty())
    }

    private fun candidates(count: Int): List<WallpaperCandidate> = (1..count).map { index ->
        val id = (index + 1L) / 2
        WallpaperCandidate(
            if (index % 2 == 1) CatalogKind.PERSONAL_LIST_MOVIE else CatalogKind.PERSONAL_LIST_SHOW,
            "Batch title $id", 2026, id, null, null, null)
    }

    private fun generator(metadata: FakeTmdbApi, artworkRequests: MutableList<String>): LocalWallpaperGenerator {
        val bitmap = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val image = try {
            ByteArrayOutputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                stream.toByteArray()
            }
        } finally { bitmap.recycle() }
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            // A real Trakt/API request is a test failure. Every artwork response is local fake data.
            check(request.url.host == "image.tmdb.org" && request.url.encodedPath.startsWith("/t/p/original/batch-")) {
                "Unexpected network request in batch test: ${request.url}"
            }
            artworkRequests += request.url.encodedPath
            okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(image.toResponseBody()).build()
        }.build()
        val trakt = Retrofit.Builder().baseUrl("https://trakt.test/").client(http)
            .addConverterFactory(GsonConverterFactory.create()).build().create(TraktLocalApi::class.java)
        return LocalWallpaperGenerator(context, http, trakt, TraktDeviceAuthRepository(trakt), TmdbArtworkRepository(metadata))
    }

    private fun assertDurableJpegs(entries: List<CacheEntry>) {
        for (entry in entries) {
            val file = File(cache.directory, entry.fileName)
            assertTrue(file.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator))
            assertFalse(file.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator))
            assertTrue(file.length() > 0)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            assertEquals("image/jpeg", bounds.outMimeType)
            assertEquals(3840, bounds.outWidth)
            assertEquals(2160, bounds.outHeight)
        }
        assertEquals(entries.size, cache.directory.listFiles().orEmpty().size)
        assertTrue(cache.directory.listFiles().orEmpty().all { it.extension == "jpg" })
    }

    private class FakeTmdbApi(private val missing: Boolean = false) : TmdbLocalApi {
        val detailRequests = mutableListOf<String>()
        val imageRequests = mutableListOf<String>()

        private fun details(kind: String, id: Long): Response<TmdbDetailsResponse> {
            detailRequests += "$kind-$id"
            return if (missing) Response.error(404, "{}".toResponseBody())
            else Response.success(TmdbDetailsResponse(id, title = if (kind == "movie") "Batch title $id" else null,
                name = if (kind == "tv") "Batch title $id" else null, backdropPath = "/batch-$kind-$id.png"))
        }

        private fun images(kind: String, id: Long): Response<TmdbImagesResponse> {
            imageRequests += "$kind-$id"
            return Response.success(TmdbImagesResponse())
        }

        override suspend fun movieDetails(id: Long, apiKey: String) = details("movie", id)
        override suspend fun showDetails(id: Long, apiKey: String) = details("tv", id)
        override suspend fun movieImages(id: Long, apiKey: String) = images("movie", id)
        override suspend fun showImages(id: Long, apiKey: String) = images("tv", id)
    }
}
