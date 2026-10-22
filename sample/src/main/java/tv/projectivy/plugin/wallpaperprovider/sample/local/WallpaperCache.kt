// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.sync.Mutex
import tv.projectivy.plugin.wallpaperprovider.sample.PreferencesManager
import java.io.File
import java.io.OutputStreamWriter
import java.security.MessageDigest

object WallpaperState {
    val lock = Any()
    val syncMutex = Mutex()
}

object SourceSelection {
    fun selected(): Set<String> = PreferencesManager.selectedCatalogs.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    fun signature(selection: Set<String> = selected(), scope: String = PreferencesManager.accountScope): String =
        MessageDigest.getInstance("SHA-256").digest((scope + "\n" + selection.sorted().joinToString("\n")).toByteArray())
            .joinToString("") { "%02x".format(it) }
}

data class SourceSnapshot(val signature: String, val updatedAt: Long, val candidates: List<WallpaperCandidate>)
data class CacheEntry(val key: String, val title: String, val fileName: String, val createdAt: Long)
data class CacheIndex(val entries: List<CacheEntry> = emptyList(), val failedUntil: Map<String, Long> = emptyMap())
data class CachePublicationState(val snapshot: SourceSnapshot?, val entries: Set<CacheEntry>)

class AtomicJsonFile<T>(file: File, private val type: Class<T>) {
    private val atomic = AtomicFile(file)
    private val gson = Gson()
    fun read(): T? = runCatching { atomic.openRead().bufferedReader().use { gson.fromJson(it, type) } }.getOrNull()
    fun write(value: T) {
        atomic.baseFile.parentFile?.mkdirs()
        val stream = atomic.startWrite()
        try {
            val writer = OutputStreamWriter(stream, Charsets.UTF_8)
            gson.toJson(value, writer)
            writer.flush()
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
    }
    fun delete() = atomic.delete()
}

class WallpaperCache(private val context: Context) {
    val directory: File = File(context.filesDir, "wallpapers")
    private val index = AtomicJsonFile(File(context.filesDir, "wallpaper-index.json"), CacheIndex::class.java)
    private val sources = AtomicJsonFile(File(context.filesDir, "source-snapshot.json"), SourceSnapshot::class.java)

    fun snapshot(): SourceSnapshot? = synchronized(WallpaperState.lock) { sources.read() }
    fun publicationState(): CachePublicationState = synchronized(WallpaperState.lock) {
        CachePublicationState(snapshot(), entries().toSet())
    }
    fun saveSnapshot(snapshot: SourceSnapshot) = synchronized(WallpaperState.lock) { sources.write(snapshot) }
    fun entries(): List<CacheEntry> = synchronized(WallpaperState.lock) {
        index.read()?.entries.orEmpty().filter { validFile(it)?.length()?.let { size -> size > 0 } == true }
    }

    fun eligible(signature: String? = null): List<CacheEntry> = synchronized(WallpaperState.lock) {
        val currentSignature = SourceSelection.signature()
        if (signature != null && signature != currentSignature) return@synchronized emptyList()
        val snapshot = sources.read() ?: return@synchronized emptyList()
        if (snapshot.signature != currentSignature) return@synchronized emptyList()
        val allowed = snapshot.candidates.map(::mediaKey).toSet()
        entries().filter { it.key in allowed }
    }

    fun choose(): GeneratedWallpaper? = synchronized(WallpaperState.lock) {
        val eligible = eligible()
        val previous = PreferencesManager["last_wallpaper_key", ""]
        val entry = eligible.filter { it.key != previous }.ifEmpty { eligible }.randomOrNull() ?: return@synchronized null
        val file = validFile(entry) ?: return@synchronized null
        PreferencesManager["last_wallpaper_key"] = entry.key
        GeneratedWallpaper(entry.title, file.absolutePath, contentUri(file))
    }

    fun outputFile(details: TmdbDetails): File = File(directory, "${if (details.isMovie) "movie" else "tv"}-${details.id}-v2.jpg")

    fun publish(candidate: WallpaperCandidate, details: TmdbDetails, file: File) = synchronized(WallpaperState.lock) {
        check(file.canonicalFile.parentFile == directory.canonicalFile && file.length() > 0)
        val old = index.read() ?: CacheIndex()
        val entry = CacheEntry(mediaKey(candidate), details.title, file.name, System.currentTimeMillis())
        index.write(old.copy(entries = old.entries.filter { it.key != entry.key } + entry, failedUntil = old.failedUntil - entry.key))
    }

    fun failedUntil(key: String): Long = synchronized(WallpaperState.lock) { index.read()?.failedUntil?.get(key) ?: 0L }
    fun markUnavailable(key: String) = synchronized(WallpaperState.lock) {
        val old = index.read() ?: CacheIndex()
        index.write(old.copy(failedUntil = old.failedUntil + (key to (System.currentTimeMillis() + 24 * 60 * 60 * 1000L))))
    }

    fun prune(allowed: Set<String>, limit: Int = PreferencesManager.cacheCount, byteLimit: Long = 512L * 1024 * 1024) = synchronized(WallpaperState.lock) {
        val old = index.read() ?: CacheIndex()
        val current = PreferencesManager["last_wallpaper_key", ""]
        var bytes = 0L
        val keep = mutableListOf<CacheEntry>()
        val sorted = entries().filter { it.key in allowed }.sortedWith(compareByDescending<CacheEntry> { it.key == current }.thenByDescending { it.createdAt })
        for (entry in sorted) {
            val size = validFile(entry)?.length() ?: continue
            if (keep.size < limit && bytes + size <= byteLimit) {
                keep += entry
                bytes += size
            }
        }
        // Update metadata before deleting files, so a concurrent reader cannot select an evicted file.
        index.write(CacheIndex(keep, old.failedUntil.filter { it.key in allowed && it.value > System.currentTimeMillis() }))
        val names = keep.map { it.fileName }.toSet()
        directory.listFiles()?.filter { it.isFile && it.name !in names }?.forEach { it.delete() }
    }

    fun clear() = synchronized(WallpaperState.lock) {
        sources.delete()
        index.delete()
        directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        PreferencesManager["last_wallpaper_key"] = ""
    }

    private fun validFile(entry: CacheEntry): File? {
        if (!entry.fileName.matches(Regex("(movie|tv)-[0-9]+-v2\\.jpg"))) return null
        return File(directory, entry.fileName).takeIf { it.isFile }
    }

    fun contentUri(file: File): String = FileProvider.getUriForFile(context, "${context.packageName}.wallpaperfiles", file).toString()

    companion object {
        fun safeFileName(title: String, year: Int?): String {
            val cleanTitle = title.map { if (it.isLetterOrDigit() || it in " ._-") it else ' ' }
                .joinToString("").trim().replace(Regex("\\s+"), " ")
            return "$cleanTitle - ${year ?: "Unknown"}.jpg"
        }
    }
}
