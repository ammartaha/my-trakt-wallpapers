package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.app.Application
import android.content.Context
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import tv.projectivy.plugin.wallpaperprovider.sample.PreferencesManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class OfflineAndPrivacyTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        PreferencesManager.init(context, object : SecretStore {
            private val values = mutableMapOf<String, String>()
            override fun get(key: String) = values[key].orEmpty()
            override fun putAll(values: Map<String, String>) { this.values.putAll(values) }
        })
        PreferencesManager.preferences.edit().clear().commit()
        WallpaperCache(context).clear()
        PreferencesManager.accountScope = "test-account"
        PreferencesManager.selectedCatalogs = "personal_list:me:123"
    }

    private fun candidate(id: Long, movie: Boolean = true) = WallpaperCandidate(
        if (movie) CatalogKind.PERSONAL_LIST_MOVIE else CatalogKind.PERSONAL_LIST_SHOW, "Same title", 2026, id, null, id, null)

    private fun put(cache: WallpaperCache, candidate: WallpaperCandidate) {
        val details = TmdbDetails(candidate.tmdbId, candidate.title, 2026, "https://example.org/backdrop.jpg", null, isMovie = candidate.catalog.isMovie)
        val file = cache.outputFile(details)
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte()))
        cache.publish(candidate, details, file)
    }

    @Test fun cacheSurvivesNewInstancesAndKeepsFilesOutsideEvictableCache() {
        val cache = WallpaperCache(context)
        val movie = candidate(1)
        cache.saveSnapshot(SourceSnapshot(SourceSelection.signature(), 1L, listOf(movie)))
        put(cache, movie)

        val reopened = WallpaperCache(context)
        val entry = reopened.eligible().single()
        val file = java.io.File(reopened.directory, entry.fileName)
        assertTrue(file.absolutePath.startsWith(context.filesDir.absolutePath))
        assertFalse(file.absolutePath.startsWith(context.cacheDir.absolutePath))
        assertTrue(file.length() > 0)
        assertEquals(1, reopened.eligible().size)
    }

    @Test fun androidFileProviderSharesOnlyGeneratedFiles() {
        // AndroidX deliberately uses '/' in its Android path check. It cannot map Windows paths.
        // This integration test runs on Linux GitHub Actions; the durable cache tests run on both.
        org.junit.Assume.assumeTrue("Android FileProvider needs Unix file paths", java.io.File.separatorChar == '/')
        val cache = WallpaperCache(context)
        val movie = candidate(1)
        cache.saveSnapshot(SourceSnapshot(SourceSelection.signature(), 1, listOf(movie)))
        put(cache, movie)
        val saved = cache.choose()!!
        assertTrue(saved.contentUri.startsWith("content://${context.packageName}.wallpaperfiles/"))
        assertArrayEquals(java.io.File(saved.filePath).readBytes(), context.contentResolver.openInputStream(android.net.Uri.parse(saved.contentUri))!!.use { it.readBytes() })
        val outside = java.io.File(context.filesDir, "private-data.txt").apply { writeText("not shared") }
        assertTrue(runCatching { cache.contentUri(outside) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun deselectionAccountChangeAndRemovedMembersCannotReturnOldImages() {
        val cache = WallpaperCache(context)
        val first = candidate(1)
        val second = candidate(2)
        cache.saveSnapshot(SourceSnapshot(SourceSelection.signature(), 1, listOf(first, second)))
        put(cache, first); put(cache, second)
        cache.saveSnapshot(SourceSnapshot(SourceSelection.signature(), 2, listOf(second)))
        assertEquals(listOf("movie-2"), cache.eligible().map { it.key })
        val oldSignature = SourceSelection.signature()
        PreferencesManager.selectedCatalogs = "personal_list:me:456"
        assertNull(cache.choose())
        assertTrue(cache.eligible(oldSignature).isEmpty())
        PreferencesManager.selectedCatalogs = "personal_list:me:123"
        PreferencesManager.accountScope = "different-account"
        assertNull(cache.choose())
        PreferencesManager.selectedCatalogs = ""
        assertNull(cache.choose())
    }

    @Test fun identicalMovieAndTvTitlesUseDifferentFilesAndCacheIsBounded() {
        val cache = WallpaperCache(context)
        val movie = candidate(7)
        val show = candidate(7, false)
        cache.saveSnapshot(SourceSnapshot(SourceSelection.signature(), 1, listOf(movie, show)))
        put(cache, movie); put(cache, show)
        assertEquals(2, cache.entries().map { it.fileName }.distinct().size)
        cache.prune(setOf("movie-7", "tv-7"), limit = 1)
        assertEquals(1, cache.eligible().size)
        assertEquals(1, cache.directory.listFiles()!!.size)
        cache.prune(emptySet())
        assertTrue(cache.eligible().isEmpty())
        assertTrue(cache.directory.listFiles()!!.isEmpty())
    }

    @Test fun missingFilesAndCorruptSnapshotDoNotProduceDeadOrUnrelatedUris() {
        val cache = WallpaperCache(context)
        val movie = candidate(1)
        cache.saveSnapshot(SourceSnapshot(SourceSelection.signature(), 1, listOf(movie)))
        put(cache, movie)
        cache.directory.listFiles()!!.single().delete()
        assertNull(cache.choose())
        java.io.File(context.filesDir, "source-snapshot.json").writeText("{broken")
        assertNull(cache.choose())
    }

    @Test fun rotatingTheProtectedImageDoesNotLookLikeNewlyPublishedData() {
        val cache = WallpaperCache(context)
        val movies = listOf(candidate(1), candidate(2))
        cache.saveSnapshot(SourceSnapshot(SourceSelection.signature(), 1, movies))
        movies.forEach { put(cache, it) }
        PreferencesManager["last_wallpaper_key"] = "movie-1"
        cache.prune(setOf("movie-1", "movie-2"))
        val before = cache.publicationState()
        PreferencesManager["last_wallpaper_key"] = "movie-2"
        cache.prune(setOf("movie-1", "movie-2"))
        assertEquals("movie-2", cache.entries().first().key)
        assertEquals(before, cache.publicationState())
        // Without this stable comparison, a refresh can trigger another rotation and refresh loop.
    }

    @Test fun exportedAndImportedPreferencesCannotExposeOrReplaceCredentials() {
        PreferencesManager.preferences.edit().remove("selected_catalogs").commit()
        assertEquals("", PreferencesManager.selectedCatalogs)
        PreferencesManager.traktClientId = "private-client-fixture"
        PreferencesManager.tmdbApiKey = "private-key-fixture"
        PreferencesManager.traktAccessToken = "private-access-fixture"
        PreferencesManager.traktRefreshToken = "private-refresh-fixture"
        PreferencesManager.traktDeviceCode = "private-device-fixture"
        PreferencesManager.preferences.edit().putString("unrelated_private_pref", "private-fixture").commit()
        val exported = PreferencesManager.export()
        assertFalse(exported.contains("private"))
        assertFalse(exported.contains("token"))
        assertFalse(PreferencesManager.preferences.all.values.any { it == "private-access-fixture" || it == "private-key-fixture" })
        assertTrue(PreferencesManager.import("""{"trakt_access_token":"attacker","account_scope":"attacker","sync_minutes":15}"""))
        assertEquals("private-access-fixture", PreferencesManager.traktAccessToken)
        assertEquals("test-account", PreferencesManager.accountScope)
        assertEquals(15, PreferencesManager.syncMinutes)
        assertFalse(PreferencesManager.import("""{"selected_catalogs":123,"sync_minutes":360}"""))
        assertEquals(15, PreferencesManager.syncMinutes)
        assertFalse(PreferencesManager.import("""{"sync_minutes":"60"}"""))
        assertFalse(PreferencesManager.import("""{"pref_refresh_on_idle_exit":"true"}"""))
    }

    @Test fun refreshIsSerializedAcrossRepositoriesAndNeverSendsAClientSecret() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            PreferencesManager.traktClientId = "client-fixture"
            PreferencesManager.traktRefreshToken = "old-refresh-fixture"
            PreferencesManager.traktExpiresAt = 0
            server.enqueue(MockResponse().setBody("""{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600,"created_at":1000}"""))
            fun api() = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
                .addConverterFactory(GsonConverterFactory.create()).build().create(TraktLocalApi::class.java)
            val headers = (1..8).map { async { TraktDeviceAuthRepository(api()) { 1000L }.authorizationHeader() } }.awaitAll()
            assertTrue(headers.all { it == "Bearer new-access" })
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals("old-refresh-fixture", json["refresh_token"].asString)
            assertFalse(json.has("client_secret"))
            assertEquals("new-refresh", PreferencesManager.traktRefreshToken)
            assertEquals(4600L, PreferencesManager.traktExpiresAt)
        } finally { server.shutdown() }
    }

    @Test fun expiredOrInvalidRefreshRequiresLoginButKeepsOfflineCacheScope() = runTest {
        val server = MockWebServer(); server.start()
        try {
            PreferencesManager.traktClientId = "client-fixture"
            PreferencesManager.traktRefreshToken = "invalid-refresh"
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant","error_description":"session not found"}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(TraktLocalApi::class.java)
            assertNull(TraktDeviceAuthRepository(api) { 1000L }.authorizationHeader())
            assertEquals("", PreferencesManager.traktRefreshToken)
            assertEquals("test-account", PreferencesManager.accountScope)
        } finally { server.shutdown() }
    }

    @Test fun failedSecondPageKeepsTheLastCompleteSnapshotAndSavedFile() = runTest {
        val cache = WallpaperCache(context)
        val old = candidate(99)
        val snapshot = SourceSnapshot(SourceSelection.signature(), 123L, listOf(old))
        cache.saveSnapshot(snapshot)
        put(cache, old)
        PreferencesManager.traktClientId = "client-fixture"
        PreferencesManager.traktAccessToken = "valid-access-fixture"
        PreferencesManager.traktExpiresAt = System.currentTimeMillis() / 1000L + 3600
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBody("""{"name":"List","item_count":2,"updated_at":"one"}"""))
            server.enqueue(MockResponse().setHeader("X-Pagination-Page", 1).setHeader("X-Pagination-Page-Count", 2)
                .setHeader("X-Pagination-Item-Count", 2).setHeader("X-Pagination-Limit", 1)
                .setBody("""[{"type":"movie","movie":{"title":"Uncommitted title","ids":{"tmdb":1}}}]"""))
            server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))
            val client = OkHttpClient.Builder().callTimeout(5, java.util.concurrent.TimeUnit.SECONDS).build()
            val api = Retrofit.Builder().baseUrl(server.url("/")).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(TraktLocalApi::class.java)
            val error = runCatching { LocalWallpaperGenerator(context, http = client, trakt = api).syncAndGenerate(force = true) }.exceptionOrNull()
            assertTrue(error is ApiFailure)
            assertEquals(snapshot, cache.snapshot())
            assertEquals(listOf("movie-99"), cache.eligible().map { it.key })
            assertTrue(java.io.File(cache.directory, cache.eligible().single().fileName).exists())
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun rateLimitsUseServerDelayAndBackOffWhenHeaderIsMissing() {
        assertEquals(11000L, RateLimitPolicy.retryAt("10", 1000))
        assertTrue(RateLimitPolicy.retryAt(null, 1000, 2) >= 241000)
        assertTrue(RateLimitPolicy.retryAt("invalid", 1000, 2) <= 246000)
    }

    @Test fun pendingLoginCannotRestoreAnOldDeviceCodeAfterDisconnect() = runTest {
        PreferencesManager.traktClientId = "old-client"
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setBodyDelay(300, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setBody("""{"device_code":"old-device","user_code":"ABCD1234","verification_url":"https://trakt.tv/activate","expires_in":600,"interval":7}"""))
            val api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(TraktLocalApi::class.java)
            val login = async(kotlinx.coroutines.Dispatchers.IO) { TraktDeviceAuthRepository(api) { 1000L }.start() }
            assertNotNull(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS))
            val disconnect = async {
                TraktDeviceAuthRepository.refreshMutex.withLock {
                    PreferencesManager.clearLogin()
                    PreferencesManager.traktClientId = "new-client"
                }
            }
            disconnect.await()
            assertTrue(login.await().isSuccess)
            assertEquals("", PreferencesManager.traktDeviceCode)
            assertEquals("new-client", PreferencesManager.traktClientId)
        } finally { server.shutdown() }
    }
}
