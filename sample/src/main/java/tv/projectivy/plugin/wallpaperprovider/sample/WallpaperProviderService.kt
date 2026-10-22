// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.Process
import tv.projectivy.plugin.wallpaperprovider.api.*
import tv.projectivy.plugin.wallpaperprovider.sample.local.WallpaperCache
import tv.projectivy.plugin.wallpaperprovider.sample.local.WallpaperWork

class WallpaperProviderService : Service() {
    override fun onCreate() {
        super.onCreate()
        PreferencesManager.init(this)
    }

    override fun onBind(intent: Intent): IBinder = binder

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> {
            checkCaller()
            if (event is Event.LauncherIdleModeChanged && event.isIdle) return emptyList()
            // Binder only reads local files. HTTP and 4K rendering run in WorkManager.
            if (event !is Event.LauncherIdleModeChanged || PreferencesManager.refreshOnIdleExit) {
                WallpaperWork.requestNow(this@WallpaperProviderService)
            }
            val saved = WallpaperCache(this@WallpaperProviderService).choose() ?: return emptyList()
            grantUriPermission(PROJECTIVY_PACKAGE_ID, Uri.parse(saved.contentUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            return listOf(Wallpaper(uri = saved.contentUri, type = WallpaperType.IMAGE,
                displayMode = WallpaperDisplayMode.CROP, title = saved.title, author = "Trakt Â· TMDB", actionUri = null))
        }

        override fun getPreferences(): String {
            checkCaller()
            return PreferencesManager.export()
        }

        override fun setPreferences(params: String) {
            checkCaller()
            if (PreferencesManager.import(params)) {
                WallpaperWork.schedule(this@WallpaperProviderService)
                WallpaperWork.requestNow(this@WallpaperProviderService, force = true)
            }
        }
    }

    private fun checkCaller() {
        val uid = Binder.getCallingUid()
        if (uid != Process.myUid() && packageManager.getPackagesForUid(uid)?.contains(PROJECTIVY_PACKAGE_ID) != true)
            throw SecurityException("Only Projectivy Launcher can use this provider.")
    }

    companion object {
        private const val PROJECTIVY_PACKAGE_ID = "com.spocky.projengmenu"
    }
}
