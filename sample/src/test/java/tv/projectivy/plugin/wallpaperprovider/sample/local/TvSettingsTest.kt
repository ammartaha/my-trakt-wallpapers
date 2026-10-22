package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import org.robolectric.Shadows.shadowOf
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

    @Test fun newRedirectDefaultsToLoopbackAndKeepsSavedHttpsValuesExact() {
        assertEquals("http://localhost/", PreferencesManager.traktRedirectUri)
        val registered = "https://Example.com:443/Callback?source=TV&path=%2F"
        PreferencesManager.traktRedirectUri = registered
        PreferencesManager.init(RuntimeEnvironment.getApplication())
        assertEquals(registered, PreferencesManager.traktRedirectUri)
    }

    @Test fun redirectValidationAcceptsHttpsAndExactLoopbackHosts() {
        listOf("https://github.com/", "https://example.com/oauth?source=TV&path=%2F",
            "HTTPS://Example.com:443/Callback", "http://localhost/", "http://127.0.0.1/",
            "http://[::1]/", "http://localhost:8080/callback", "http://127.0.0.1:1234/callback",
            "http://[::1]:8080/callback", "HTTP://LOCALHOST/Callback?source=TV").forEach { value ->
            assertTrue("Should accept $value", SettingsFragment.isValidTraktRedirectUri(value))
        }
    }

    @Test fun redirectValidationRejectsRemoteHttpAndLoopbackLookalikes() {
        listOf("http://example.com/", "http://localhost.example.com/", "http://example.localhost/",
            "http://localhost./", "http://127.0.0.1.example.com/", "http://127.1/",
            "http://2130706433/", "http://0x7f000001/", "http://[::ffff:127.0.0.1]/",
            "http://[0:0:0:0:0:0:0:1]/", "http://[::1%25lo0]/").forEach { value ->
            assertFalse("Should reject $value", SettingsFragment.isValidTraktRedirectUri(value))
        }
    }

    @Test fun redirectValidationRejectsMalformedUrisCredentialsAndFragments() {
        listOf("", "localhost", "/callback", "https:example.com", "https://", "https:///callback",
            "https://bad host/", "https://example.com/%zz", " https://example.com/", "http://localhost/ ",
            "https://example.com:65536/", "http://localhost:65536/", "http://localhost:/",
            "http://localhost:-1/", "http://[::1/", "http://user@localhost/", "http://@localhost/",
            "http://localhost@example.com/", "https://user:password@example.com/",
            "http://localhost/#token", "https://example.com/#", "https://example.com/#token").forEach { value ->
            assertFalse("Should reject $value", SettingsFragment.isValidTraktRedirectUri(value))
        }
    }

    @Test fun apiDialogShowsTheLoopbackDefaultAndRegisteredRedirectLabel() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val fragment = activity.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().single()
            fragment.onGuidedActionClicked(GuidedAction.Builder(activity).id(2L).title("API settings").build())
            val dialog = ShadowDialog.getLatestDialog()
            val views = dialog.window!!.decorView.textViews()
            assertTrue(views.any { it.text.toString() == "Registered Trakt redirect URI" })
            assertEquals("http://localhost/", views.filterIsInstance<EditText>()[1].text.toString())
            dialog.dismiss()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun apiDialogRejectsRemoteHttpWithoutChangingSavedSettings() {
        PreferencesManager.traktRedirectUri = "https://example.com/callback"
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val fragment = activity.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().single()
            fragment.onGuidedActionClicked(GuidedAction.Builder(activity).id(2L).title("API settings").build())
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            shadowOf(Looper.getMainLooper()).idle()
            val fields = dialog.window!!.decorView.textViews().filterIsInstance<EditText>()
            fields[0].setText("test-client")
            fields[1].setText("http://localhost.example.com/")
            fields[2].setText("test-key")
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertNotNull(fields[1].error)
            assertTrue(dialog.isShowing)
            assertEquals("https://example.com/callback", PreferencesManager.traktRedirectUri)
            assertTrue(PreferencesManager.traktClientId.isEmpty())
            dialog.dismiss()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun aboutShowsTheRequiredTraktNotice() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        try {
            val activity = controller.get()
            val fragment = activity.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>().single()
            fragment.onGuidedActionClicked(GuidedAction.Builder(activity).id(13L).title("About").build())
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(dialog.window!!.decorView.textViews().any {
                it.text.contains("My Trakt Wallpapers uses Trakt but is not endorsed or certified by Trakt.")
            })
            dialog.dismiss()
        } finally { controller.pause().stop().destroy() }
    }

    private fun View.textViews(): List<TextView> = buildList {
        if (this@textViews is TextView) add(this@textViews)
        if (this@textViews is ViewGroup) {
            for (index in 0 until childCount) addAll(getChildAt(index).textViews())
        }
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
