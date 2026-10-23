// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.projectivy.plugin.wallpaperprovider.sample.PreferencesManager
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class LocalWallpaperGenerator(
    private val context: Context,
    private val http: OkHttpClient = ApiClients.http,
    private val trakt: TraktLocalApi = ApiClients.trakt(),
    private val auth: TraktDeviceAuthRepository = TraktDeviceAuthRepository(),
    private val artwork: TmdbArtworkRepository = TmdbArtworkRepository(ApiClients.tmdb())
) {
    private val cache = WallpaperCache(context)
    private val renderer = WallpaperRenderer()

    suspend fun syncAndGenerate(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        WallpaperState.syncMutex.withLock {
            val selected = SourceSelection.selected()
            if (selected.isEmpty()) {
                PreferencesManager.syncStatus = "No sources selected. Open My lists."
                return@withLock 0
            }
            val signature = SourceSelection.signature(selected)
            val previous = cache.snapshot()
            var snapshot = previous
            if (force || previous?.signature != signature ||
                System.currentTimeMillis() - previous.updatedAt >= PreferencesManager.syncMinutes * 60_000L) {
                PreferencesManager.syncStatus = "Checking selected Trakt lists..."
                val authorization = auth.authorizationHeader()
                    ?: throw IOException("Connect Trakt to check your lists. Saved wallpapers remain available.")
                val candidates = TraktListRepository(trakt).candidates(selected, PreferencesManager.traktClientId, authorization)
                coroutineContext.ensureActive()
                if (signature != SourceSelection.signature()) return@withLock 0
                snapshot = SourceSnapshot(signature, System.currentTimeMillis(), candidates)
                cache.saveSnapshot(snapshot)
            }
            val current = snapshot
            val allowed = current.candidates.map(::mediaKey).toSet()
            cache.prune(allowed)
            if (!force && System.currentTimeMillis() - PreferencesManager["last_generation_at", 0L] < 15 * 60_000L) {
                setStatus(current, signature)
                return@withLock 0
            }
            PreferencesManager["last_generation_at"] = System.currentTimeMillis()
            val cached = cache.entries().map { it.key }.toSet()
            val previousKeys = previous?.candidates.orEmpty().map(::mediaKey).toSet()
            val missing = current.candidates.filter { mediaKey(it) !in cached && cache.failedUntil(mediaKey(it)) <= System.currentTimeMillis() }
                .shuffled().sortedBy { mediaKey(it) in previousKeys }
            var rendered = 0
            // Bound each job, including attempts for titles without usable artwork.
            for (candidate in missing.take(MAX_METADATA_ATTEMPTS_PER_RUN)) {
                coroutineContext.ensureActive()
                if (signature != SourceSelection.signature() || rendered >= MAX_WALLPAPERS_PER_RUN) break
                val details = artwork.details(candidate)
                if (details == null) {
                    cache.markUnavailable(mediaKey(candidate))
                    continue
                }
                val backdrop = downloadBitmap(details.backdropUrl, 4096)
                if (backdrop == null) {
                    cache.markUnavailable(mediaKey(candidate))
                    continue
                }
                var logo: Bitmap? = null
                val output = cache.outputFile(details)
                val temp = File(output.parentFile, output.name + ".tmp")
                try {
                    logo = details.logoUrl?.let { downloadBitmap(it, 1600) }
                    PreferencesManager.syncStatus = "Creating wallpaper ${rendered + 1} of $MAX_WALLPAPERS_PER_RUN..."
                    renderer.render(backdrop, logo, temp, details)
                    coroutineContext.ensureActive()
                    if (signature != SourceSelection.signature()) break
                    check(temp.renameTo(output)) { "Unable to save wallpaper. Check free storage." }
                    cache.publish(candidate, details, output)
                    rendered++
                } finally {
                    logo?.recycle()
                    backdrop.recycle()
                    temp.delete()
                }
            }
            cache.prune(allowed)
            setStatus(current, signature)
            rendered
        }
    }

    private fun setStatus(snapshot: SourceSnapshot, signature: String) {
        PreferencesManager.syncStatus = "${snapshot.candidates.size} selected titles; ${cache.eligible(signature).size} saved wallpapers. Last check: " +
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(snapshot.updatedAt))
    }

    // Keep the old entry point useful to callers, but Projectivy itself only reads the durable cache.
    suspend fun generate(): GeneratedWallpaper? {
        syncAndGenerate()
        return cache.choose()
    }

    private fun downloadBitmap(url: String, maxDimension: Int): Bitmap? {
        val temp = File.createTempFile("artwork-", ".download", context.cacheDir)
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) return null
                    throw ApiFailure(response.code)
                }
                if (response.body.contentLength() > 40L * 1024 * 1024) return null
                response.body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 40L * 1024 * 1024) throw IOException("Artwork is too large.")
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 32768 || bounds.outHeight > 32768) return null
            var sample = 1
            while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
            return BitmapFactory.decodeFile(temp.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        } finally {
            temp.delete()
        }
    }

    companion object {
        internal const val MAX_WALLPAPERS_PER_RUN = 10
        private const val MAX_METADATA_ATTEMPTS_PER_RUN = 20

        fun interleave(movies: List<WallpaperCandidate>, shows: List<WallpaperCandidate>): List<WallpaperCandidate> =
            (0 until maxOf(movies.size, shows.size)).flatMap { listOfNotNull(movies.getOrNull(it), shows.getOrNull(it)) }
        fun randomizedCandidates(movies: List<WallpaperCandidate>, shows: List<WallpaperCandidate>, random: Random = Random.Default): List<WallpaperCandidate> =
            (movies + shows).distinctBy(::mediaKey).shuffled(random)
        fun isRenderable(details: TmdbDetails, hasBackdrop: Boolean, hasLogo: Boolean): Boolean = hasBackdrop && details.backdropUrl.isNotBlank()
        fun parsePopularListKey(key: String): TraktPopularListOption? = TraktListRepository.parseListKey(key)?.takeIf { key.startsWith("popular_list:") }
    }
}
