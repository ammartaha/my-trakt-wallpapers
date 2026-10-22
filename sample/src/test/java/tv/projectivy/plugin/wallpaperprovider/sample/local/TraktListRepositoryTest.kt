package tv.projectivy.plugin.wallpaperprovider.sample.local

import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Regression tests for strict source selection and complete Trakt list reads. */
class TraktListRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var repository: TraktListRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(client).addConverterFactory(GsonConverterFactory.create())
            .build().create(TraktLocalApi::class.java)
        repository = TraktListRepository(api)
    }

    @After
    fun tearDown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun privateMixedListFollowsClampedPagesAndUsesOnlySelectedSource() = runTest {
        enqueueMetadata(count = 7)
        enqueuePage("[${movie(1)},${show(1)},${movie(1)}]", page = 1, pages = 3, count = 7, limit = 3)
        enqueuePage("""[{"type":"season","show":{"title":"Not a direct show","ids":{"tmdb":8}}},
            {"type":"episode","show":{"title":"Not a direct show","ids":{"tmdb":9}}},
            {"type":"person","movie":{"title":"Not a movie item","ids":{"tmdb":10}}}]""",
            page = 2, pages = 3, count = 7, limit = 3)
        enqueuePage("[${movie(2)}]", page = 3, pages = 3, count = 7, limit = 3)
        enqueueMetadata(count = 7)

        val result = repository.candidates(setOf(SOURCE), API_KEY, AUTH)

        assertEquals(setOf("movie-1", "tv-1", "movie-2"), result.map(::mediaKey).toSet())
        assertEquals(3, result.size)
        assertEquals(CatalogKind.PERSONAL_LIST_SHOW, result.single { !it.catalog.isMovie }.catalog)
        assertEquals(5, server.requestCount)
        val requests = recordedRequests(5)
        assertEquals(listOf(LIST_PATH, "$LIST_PATH/items", "$LIST_PATH/items", "$LIST_PATH/items", LIST_PATH),
            requests.map { it.requestUrl?.encodedPath })
        assertEquals(listOf("1", "2", "3"), requests.subList(1, 4).map { it.requestUrl?.queryParameter("page") })
        assertTrue(requests.subList(1, 4).all { it.requestUrl?.queryParameter("limit") == "250" })
        assertTrue(requests.all { it.getHeader("Authorization") == AUTH && it.getHeader("trakt-api-key") == API_KEY })
    }

    @Test
    fun listBeyond250ItemsIncludesTheLastPage() = runTest {
        enqueueMetadata(count = 251)
        enqueuePage((1..250).joinToString(prefix = "[", postfix = "]") { movie(it) }, 1, 2, 251)
        enqueuePage("[${movie(251)}]", 2, 2, 251)
        enqueueMetadata(count = 251)

        val result = repository.candidates(setOf(SOURCE), API_KEY, AUTH)

        assertEquals(251, result.size)
        assertEquals(251L, result.last().tmdbId)
        assertEquals(listOf(null, "1", "2", null), recordedRequests(4).map { it.requestUrl?.queryParameter("page") })
    }

    @Test
    fun emptySelectionMakesNoRequests() = runTest {
        assertTrue(repository.candidates(emptySet(), API_KEY, AUTH).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun emptyPersonalListDoesNotFallBackToAnotherCatalog() = runTest {
        enqueueMetadata(count = 0)
        enqueuePage("[]", 1, 0, 0)
        enqueueMetadata(count = 0)

        assertTrue(repository.candidates(setOf(SOURCE), API_KEY, AUTH).isEmpty())
        assertEquals(listOf(LIST_PATH, "$LIST_PATH/items", LIST_PATH),
            recordedRequests(3).map { it.requestUrl?.encodedPath })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun failedSecondPageDoesNotReturnAPartialListOrUseAnotherSource() = runTest {
        enqueueMetadata(count = 4)
        enqueuePage("[${movie(1)},${movie(2)}]", 1, 2, 4, limit = 2)
        server.enqueue(json("{}", status = 503))

        val failure = runCatching { repository.candidates(setOf(SOURCE), API_KEY, AUTH) }.exceptionOrNull()

        assertTrue(failure is ApiFailure)
        assertEquals(503, (failure as ApiFailure).status)
        assertEquals(listOf(LIST_PATH, "$LIST_PATH/items", "$LIST_PATH/items"),
            recordedRequests(3).map { it.requestUrl?.encodedPath })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun missingPaginationForNonemptyItemsIsRejected() = runTest {
        val failure = runCatching {
            TraktListRepository.pages { Response.success(listOf(1)) }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun missingTotalCountDoesNotPublishAnUnverifiableList() = runTest {
        val headers = Headers.Builder().add("X-Pagination-Page", "1")
            .add("X-Pagination-Page-Count", "1").add("X-Pagination-Limit", "250").build()
        val failure = runCatching {
            TraktListRepository.pages { Response.success(listOf(1), headers) }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun changingPageCountsAreRejected() = runTest {
        val visited = mutableListOf<Int>()
        val failure = runCatching {
            TraktListRepository.pages { page ->
                visited += page
                pageResponse(listOf(page), page, pages = if (page == 1) 2 else 3, count = 3)
            }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals(listOf(1, 2), visited)
    }

    @Test
    fun finalItemCountRejectsATruncatedResponse() = runTest {
        val failure = runCatching {
            TraktListRepository.pages { page -> pageResponse(listOf(1, 2), page, pages = 1, count = 3) }
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun listChangesDuringReadAreRejected() = runTest {
        enqueueMetadata(count = 1)
        enqueuePage("[${movie(1)}]", 1, 1, 1)
        enqueueMetadata(count = 1, updatedAt = "2026-10-22T02:00:00Z")

        val failure = runCatching { repository.candidates(setOf(SOURCE), API_KEY, AUTH) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun myListSelectionKeyUsesNumericIdAndSurvivesRename() = runTest {
        server.enqueue(json("""[{"name":"Old name","privacy":"private","item_count":2,
            "ids":{"trakt":42,"slug":"old-name"}}, {"name":"No stable id","ids":{"slug":"no-id"}}]"""))
        server.enqueue(json("""[{"name":"New name","privacy":"private","item_count":3,
            "ids":{"trakt":42,"slug":"new-name"}}]"""))

        val before = repository.myLists(API_KEY, AUTH).single()
        val after = repository.myLists(API_KEY, AUTH).single()

        assertEquals(SOURCE, before.key)
        assertEquals(before.key, after.key)
        assertEquals("42", after.listId)
        assertEquals("me", after.userId)
        assertEquals("New name", after.title)
        assertEquals(3, after.itemCount)
        assertTrue(recordedRequests(2).all { it.requestUrl?.encodedPath == "/users/me/lists" })
    }

    private fun enqueueMetadata(count: Int, updatedAt: String = "2026-10-22T01:00:00Z") {
        server.enqueue(json("""{"name":"Private test list","privacy":"private","item_count":$count,
            "updated_at":"$updatedAt","ids":{"trakt":42,"slug":"private-test-list"}}"""))
    }

    private fun enqueuePage(body: String, page: Int, pages: Int, count: Int, limit: Int = 250) {
        server.enqueue(json(body).setHeader("X-Pagination-Page", page)
            .setHeader("X-Pagination-Page-Count", pages).setHeader("X-Pagination-Item-Count", count)
            .setHeader("X-Pagination-Limit", limit))
    }

    private fun pageResponse(items: List<Int>, page: Int, pages: Int, count: Int): Response<List<Int>> =
        Response.success(items, Headers.Builder().add("X-Pagination-Page", page.toString())
            .add("X-Pagination-Page-Count", pages.toString()).add("X-Pagination-Item-Count", count.toString()).build())

    private fun json(body: String, status: Int = 200): MockResponse = MockResponse()
        .setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)

    private fun movie(id: Int): String = """{"type":"movie","movie":{"title":"Movie $id","year":2025,"ids":{"tmdb":$id,"trakt":$id}}}"""
    private fun show(id: Int): String = """{"type":"show","show":{"title":"Show $id","year":2025,"ids":{"tmdb":$id,"trakt":$id}}}"""

    private fun recordedRequests(count: Int): List<RecordedRequest> = (1..count).map {
        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull("Expected request $it of $count", request)
        request!!
    }

    companion object {
        private const val SOURCE = "personal_list:me:42"
        private const val LIST_PATH = "/users/me/lists/42"
        private const val API_KEY = "test-client-id"
        private const val AUTH = "Bearer test-token"
    }
}
