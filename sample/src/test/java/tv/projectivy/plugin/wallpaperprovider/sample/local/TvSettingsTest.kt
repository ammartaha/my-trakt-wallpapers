package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.app.Activity
import android.app.Application
import android.view.View
import android.widget.TextView
import androidx.leanback.widget.GuidedAction
import com.traktlistbackdrops.tv.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import tv.projectivy.plugin.wallpaperprovider.sample.PreferencesManager
import tv.projectivy.plugin.wallpaperprovider.sample.SettingsActivity
import tv.projectivy.plugin.wallpaperprovider.sample.SettingsFragment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w960dp-h540dp-land-mdpi")
class TvSettingsTest {
    @Before fun setup() {
        PreferencesManager.init(RuntimeEnvironment.getApplication(), object : SecretStore {
            private val values = mutableMapOf<String, String>()
            override fun get(key: String) = values[key].orEmpty()
            override fun putAll(values: Map<String, String>) { this.values.putAll(values) }
        })
        PreferencesManager.preferences.edit().clear().commit()
    }

    @Test fun freshSettingsDoNotEnableWatchlistOrExtraCatalogs() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val fragment = controller.get().supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().single()
            val sources = fragment.actions.filter { it.id in setOf(5L, 6L) }.flatMap { it.subActions.orEmpty() }
            assertTrue(sources.isNotEmpty())
            assertTrue(sources.none { it.isChecked })
            assertTrue(SourceSelection.selected().isEmpty())
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun apiAndCreditsDialogsOpenWithTheExistingLeanbackTheme() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val fragment = activity.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().single()
            for (id in listOf(2L, 13L)) {
                fragment.onGuidedActionClicked(GuidedAction.Builder(activity).id(id).title("test").build())
                val dialog = ShadowDialog.getLatestDialog()
                assertNotNull(dialog)
                assertTrue(dialog.isShowing)
                dialog.dismiss()
            }
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun projectivySettingsAliasResolvesUnderTheNewAppId() {
        val context = RuntimeEnvironment.getApplication()
        val service = context.packageManager.getServiceInfo(android.content.ComponentName(context,
            tv.projectivy.plugin.wallpaperprovider.sample.WallpaperProviderService::class.java), android.content.pm.PackageManager.GET_META_DATA)
        val alias = service.metaData.getString("settingsActivity")!!
        assertEquals("${context.packageName}.ProjectivySettingsAlias", alias)
        val activity = context.packageManager.getActivityInfo(android.content.ComponentName(context.packageName, alias), 0)
        assertEquals("tv.projectivy.plugin.wallpaperprovider.sample.SettingsActivity", activity.targetActivity)
        assertTrue(activity.exported)
    }

    @Test fun oauthCodeAndCloseButtonFitOnA960By540LogicalTvScreen() {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(R.style.Theme_ProjectivyWallpaperProvider)
        controller.setup()
        try {
            val activity = controller.get()
            activity.setContentView(R.layout.activity_trakt_oauth)
            activity.findViewById<TextView>(R.id.oauthUrl).text = "https://trakt.tv/activate"
            activity.findViewById<TextView>(R.id.oauthCode).text = "ABCD1234"
            activity.findViewById<TextView>(R.id.oauthStatus).text = "Connected. Close this page, load My lists, then choose your lists."
            val root = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0)
            root.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 960, 540)
            val close = activity.findViewById<View>(R.id.oauthClose)
            val parent = close.parent as View
            assertTrue("Close button clipped", parent.top + close.bottom <= 540 - 30)
            assertTrue(close.isFocusable)
            assertTrue(activity.findViewById<View>(R.id.oauthQr).measuredHeight <= 540 - 80)
        } finally { controller.pause().stop().destroy() }
    }
}
