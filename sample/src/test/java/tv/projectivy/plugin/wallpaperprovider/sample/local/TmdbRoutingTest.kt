package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.app.Application
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import tv.projectivy.plugin.wallpaperprovider.sample.PreferencesManager
import java.util.concurrent.TimeUnit

/** Regression for list/trending movies previously sent to the TMDB TV endpoints. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TmdbRoutingTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var repository: TmdbArtworkRepository

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        PreferencesManager.init(context, object : SecretStore {
            private val values = mutableMapOf<String, String>()
            override fun get(key: String): String = values[key].orEmpty()
            override fun putAll(values: Map<String, String>) { this.values.putAll(values) }
        })
        PreferencesManager.preferences.edit().clear().commit()
        PreferencesManager.tmdbApiKey = API_KEY
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(client).addConverterFactory(GsonConverterFactory.create())
            .build().create(TmdbLocalApi::class.java)
        repository = TmdbArtworkRepository(api)
    }

    @After
    fun tearDown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun everyCatalogUsesItsMovieOrTvEndpointAndMapsMetadata() = runTest {
        for ((index, catalog) in CatalogKind.entries.withIndex()) {
            val id = 100L + index
            server.enqueue(json(detailsJson(id, catalog.isMovie)))
            server.enqueue(json("""{"logos":[{"file_path":"/english-logo.png","iso_639_1":"en"}]}"""))

            val result = repository.details(candidate(catalog, id))

            assertNotNull("Details for $catalog", result)
            result!!
            assertEquals("Art title", result.title)
            assertEquals(2025, result.year)
            assertEquals("An overview.", result.overview)
            assertEquals("A tagline.", result.tagline)
            assertEquals(8.4, result.rating!!, 0.001)
            assertEquals(if (catalog.isMovie) 121 else 44, result.runtime)
            assertEquals(listOf("Drama", "Mystery"), result.genres)
            assertEquals(catalog.isMovie, result.isMovie)
            assertEquals("https://image.tmdb.org/t/p/original/backdrop.jpg", result.backdropUrl)
            assertEquals("https://image.tmdb.org/t/p/original/english-logo.png", result.logoUrl)

            val path = "/${if (catalog.isMovie) "movie" else "tv"}/$id"
            val detailsRequest = takeRequest()
            val imagesRequest = takeRequest()
            assertEquals("Details route for $catalog", path, detailsRequest.requestUrl?.encodedPath)
            assertEquals("Images route for $catalog", "$path/images", imagesRequest.requestUrl?.encodedPath)
            assertEquals(API_KEY, detailsRequest.requestUrl?.queryParameter("api_key"))
            assertEquals(API_KEY, imagesRequest.requestUrl?.queryParameter("api_key"))
        }
        assertEquals(CatalogKind.entries.size * 2, server.requestCount)
    }

    @Test
    fun noLogoStillReturnsDetailsForTextTitleFallbackAndUnratedTitlesHaveNoRating() = runTest {
        server.enqueue(json(detailsJson(42, true).replace("\"vote_count\":99", "\"vote_count\":0")))
        server.enqueue(json("""{"logos":[]}"""))

        val result = repository.details(candidate(CatalogKind.PERSONAL_LIST_MOVIE, 42))

        assertNotNull(result)
        assertEquals("Art title", result!!.title)
        assertNull(result.logoUrl)
        assertNull(result.rating)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun missingImagesEndpointStillAllowsTheBackdrop() = runTest {
        server.enqueue(json(detailsJson(42, false)))
        server.enqueue(json("{}", status = 404))

        val result = repository.details(candidate(CatalogKind.PERSONAL_LIST_SHOW, 42))

        assertNotNull(result)
        assertNull(result!!.logoUrl)
        assertEquals(false, result.isMovie)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun missingBackdropDoesNotFetchImagesOrProduceDetails() = runTest {
        server.enqueue(json("""{"id":42,"title":"No backdrop","backdrop_path":null}"""))

        assertNull(repository.details(candidate(CatalogKind.TRENDING_MOVIE, 42)))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun missingRuntimeKeyMakesNoApiRequest() = runTest {
        PreferencesManager.tmdbApiKey = ""

        assertNull(repository.details(candidate(CatalogKind.PERSONAL_LIST_MOVIE, 42)))
        assertEquals(0, server.requestCount)
    }

    private fun candidate(catalog: CatalogKind, id: Long): WallpaperCandidate =
        WallpaperCandidate(catalog, "Trakt fallback title", 2024, id, null, id, null)

    private fun detailsJson(id: Long, movie: Boolean): String {
        val titleAndDate = if (movie) """"title":"Art title","release_date":"2025-06-01","runtime":121"""
            else """"name":"Art title","first_air_date":"2025-06-01","episode_run_time":[44]"""
        return """{"id":$id,$titleAndDate,"backdrop_path":"/backdrop.jpg","overview":"An overview.",
            "tagline":"A tagline.","vote_average":8.4,"vote_count":99,
            "genres":[{"id":18,"name":"Drama"},{"id":9648,"name":"Mystery"}]}"""
    }

    private fun json(body: String, status: Int = 200): MockResponse = MockResponse()
        .setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private fun takeRequest(): RecordedRequest {
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(request)
        return request!!
    }

    companion object {
        private const val API_KEY = "runtime-tmdb-test-key"
    }
}
