package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Application
import tv.projectivy.plugin.wallpaperprovider.sample.local.WallpaperWork

class WallpaperApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PreferencesManager.init(this)
        WallpaperWork.schedule(this)
    }
}
