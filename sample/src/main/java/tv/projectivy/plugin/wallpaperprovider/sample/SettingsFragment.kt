// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.text.util.Linkify
import android.view.ViewGroup
import android.widget.*
import android.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.traktlistbackdrops.tv.R
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import tv.projectivy.plugin.wallpaperprovider.sample.local.*
import java.net.URI

class SettingsFragment : GuidedStepSupportFragment(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val scope = MainScope()
    private val sourceKeys = mutableMapOf<Long, String>()

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance = Guidance(
        getString(R.string.plugin_name), "4K wallpapers from the Trakt lists you choose.",
        getString(R.string.settings), AppCompatResources.getDrawable(requireContext(), R.drawable.ic_plugin))

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        PreferencesManager.init(requireContext())
        sourceKeys.clear()
        fun add(id: Long, title: String, description: String = "", children: List<GuidedAction>? = null) {
            actions += GuidedAction.Builder(requireContext()).id(id).title(title).description(description)
                .apply { if (children != null) subActions(children) }.build()
        }
        add(STATUS, "Saved wallpapers: ${WallpaperCache(requireContext()).eligible().size}", PreferencesManager.syncStatus)
        add(CONFIG, "API settings", if (PreferencesManager.traktClientId.isNotBlank() && PreferencesManager.tmdbApiKey.isNotBlank())
            "API details saved on this TV." else "Enter your Trakt client ID and TMDB API key.")
        add(CONNECT, "Connect Trakt", if (PreferencesManager.traktAccessToken.isNotBlank()) "Connected. Select this to connect again." else "Scan a QR code and approve on your phone.")
        add(LOAD_MINE, "Load my Trakt lists", "Load your own public and private lists.")
        add(MY_LISTS, "My lists", "Only checked lists are used. Movies and TV series are supported.", listActions(false))
        add(EXTRAS, "Extra catalogs (optional)", "Off unless you select them.", listActions(true))
        add(LOAD_POPULAR, "Load popular lists (optional)", "Load public list choices. This does not enable them.")
        add(REFRESH, "Refresh now", "Check selected lists and create up to ${LocalWallpaperGenerator.MAX_WALLPAPERS_PER_RUN} wallpapers.")
        add(INTERVAL, "Check interval", intervalTitle(PreferencesManager.syncMinutes),
            listOf(15, 60, 360).map { value -> GuidedAction.Builder(requireContext()).id(200L + value).title(intervalTitle(value))
                .checkSetId(2).checked(PreferencesManager.syncMinutes == value).build() })
        add(LIMIT, "Saved wallpaper limit", "${PreferencesManager.cacheCount} images; maximum 512 MiB.",
            listOf(10, 30, 60).map { value -> GuidedAction.Builder(requireContext()).id(700L + value).title("$value wallpapers")
                .checkSetId(3).checked(PreferencesManager.cacheCount == value).build() })
        actions += GuidedAction.Builder(requireContext()).id(IDLE).title("Refresh on idle exit")
            .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID).checked(PreferencesManager.refreshOnIdleExit).build()
        add(DISCONNECT, "Disconnect and clear saved data", "Remove login, list choices, and saved wallpapers.")
        add(ABOUT, "About", "Credits, privacy, and API information.")
    }

    override fun onStart() {
        super.onStart()
        PreferencesManager.preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        PreferencesManager.preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (actions.isNotEmpty()) reload()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "sync_status" && isAdded) {
            findActionById(STATUS)?.apply {
                title = "Saved wallpapers: ${WallpaperCache(requireContext()).eligible().size}"
                description = PreferencesManager.syncStatus
            }
            notifyActionChanged(findActionPositionById(STATUS))
        }
    }

    private fun listActions(extras: Boolean): List<GuidedAction> {
        val selected = SourceSelection.selected()
        val options = mutableListOf<TraktPopularListOption>()
        if (extras) {
            options += listOf("anticipated_movies" to "Anticipated movies", "anticipated_shows" to "Anticipated TV series",
                "trending_movies" to "Trending movies", "trending_shows" to "Trending TV series").map { (key, name) ->
                TraktPopularListOption(key, name, "", "")
            }
            options += savedOptions(PreferencesManager.popularListOptions)
        } else {
            options += TraktPopularListOption("watchlist", "My watchlist", "me", "watchlist")
            options += savedOptions(PreferencesManager.personalListOptions)
        }
        selected.filter { if (extras) it.startsWith("popular_list:") else it.startsWith("personal_list:") }
            .filter { key -> options.none { it.key == key } }.forEach { key ->
                options += TraktListRepository.parseListKey(key)?.copy(title = "Saved choice: $key") ?: return@forEach
            }
        return options.distinctBy { it.key }.mapIndexed { index, option ->
            val id = (if (extras) 10000L else 1000L) + index
            sourceKeys[id] = option.key
            GuidedAction.Builder(requireContext()).id(id).title(option.title)
                .description(if (option.listId.isBlank()) "Optional catalog" else if (option.key == "watchlist") "Your personal watchlist" else "${option.itemCount} items Â· ${option.userId}/${option.listId}")
                .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID).checked(option.key in selected).build()
        }
    }

    override fun onSubGuidedActionClicked(action: GuidedAction): Boolean {
        sourceKeys[action.id]?.let { key ->
            val selected = SourceSelection.selected().toMutableSet()
            if (!selected.remove(key)) selected.add(key)
            PreferencesManager.selectedCatalogs = selected.sorted().joinToString(",")
            PreferencesManager.syncStatus = if (selected.isEmpty()) "No sources selected." else "Selection changed. Checking selected lists..."
            changed(force = true)
            return true
        }
        when (action.id) {
            215L, 260L, 560L -> {
                PreferencesManager.syncMinutes = (action.id - 200).toInt()
                WallpaperWork.schedule(requireContext())
                reload()
                return true
            }
            710L, 730L, 760L -> {
                PreferencesManager.cacheCount = (action.id - 700).toInt()
                changed(force = false)
                return true
            }
        }
        return super.onSubGuidedActionClicked(action)
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            CONFIG -> apiSettings()
            CONNECT -> startActivity(Intent(requireContext(), TraktOAuthActivity::class.java))
            LOAD_MINE -> loadLists(false)
            LOAD_POPULAR -> loadLists(true)
            REFRESH -> {
                PreferencesManager.syncStatus = if (SourceSelection.selected().isEmpty()) "Choose a list in My lists first." else "Refresh queued. It will start when the TV has internet."
                WallpaperWork.requestNow(requireContext(), force = true)
                reload()
            }
            IDLE -> {
                PreferencesManager.refreshOnIdleExit = !PreferencesManager.refreshOnIdleExit
                reload()
            }
            DISCONNECT -> AlertDialog.Builder(requireContext()).setTitle("Clear saved data?")
                .setMessage("This removes your Trakt login, selected lists, and saved wallpapers. API settings stay on this TV.")
                .setNegativeButton("Cancel", null).setPositiveButton("Disconnect") { _, _ ->
                    runUi {
                        disconnect()
                        PreferencesManager.syncStatus = "Disconnected. Saved data was cleared."
                        reload()
                        (activity as? SettingsActivity)?.requestWallpaperUpdate()
                    }
                }.show()
            ABOUT -> about()
        }
    }

    private fun changed(force: Boolean) {
        WallpaperWork.schedule(requireContext())
        WallpaperWork.requestNow(requireContext(), force)
        reload()
        (activity as? SettingsActivity)?.requestWallpaperUpdate()
    }

    private fun loadLists(popular: Boolean) = runUi {
        val account = PreferencesManager.accountScope
        val clientId = PreferencesManager.traktClientId
        val auth = TraktDeviceAuthRepository().authorizationHeader()
        if (auth == null) { toast("Connect Trakt first."); return@runUi }
        val options = if (popular) ApiClients.trakt().popularLists(clientId, auth).checkedBody().mapNotNull { it.toOption() }
            else TraktListRepository(ApiClients.trakt()).myLists(clientId, auth)
        val json = Gson().toJson(options)
        val saved = TraktDeviceAuthRepository.refreshMutex.withLock {
            if (account != PreferencesManager.accountScope || clientId != PreferencesManager.traktClientId || PreferencesManager.traktAccessToken.isBlank()) false
            else {
                if (popular) PreferencesManager.popularListOptions = json else PreferencesManager.personalListOptions = json
                true
            }
        }
        if (!saved) return@runUi
        toast("Loaded ${options.size} lists. Open ${if (popular) "Extra catalogs" else "My lists"} to select them.")
        reload()
    }

    private fun apiSettings() {
        val container = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 12, 32, 12) }
        fun field(label: String, value: String, secret: Boolean = false): EditText {
            container.addView(TextView(requireContext()).apply { text = label })
            return EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_TEXT or (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                setSingleLine(true)
                if (secret) hint = if (value.isBlank()) "Enter API key" else "Saved. Leave empty to keep it."
                else setText(value)
                container.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
        val clientId = field("Trakt client ID (no client secret)", PreferencesManager.traktClientId)
        val redirect = field("Registered Trakt redirect URI", PreferencesManager.traktRedirectUri)
        val tmdbKey = field("TMDB v3 API key", PreferencesManager.tmdbApiKey, true)
        val dialog = AlertDialog.Builder(requireContext()).setTitle("API settings").setView(container)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val id = clientId.text.toString().trim()
                val uri = redirect.text.toString()
                val key = tmdbKey.text.toString().trim()
                when {
                    id.isBlank() -> clientId.error = "Enter the Trakt client ID."
                    !isValidTraktRedirectUri(uri) -> redirect.error = "Use the registered HTTPS URI or an HTTP URI on localhost, 127.0.0.1, or [::1]."
                    key.isBlank() && PreferencesManager.tmdbApiKey.isBlank() -> tmdbKey.error = "Enter the TMDB v3 API key."
                    else -> runUi {
                        if (id != PreferencesManager.traktClientId || uri != PreferencesManager.traktRedirectUri) disconnect()
                        PreferencesManager.traktClientId = id
                        PreferencesManager.traktRedirectUri = uri
                        if (key.isNotBlank()) PreferencesManager.tmdbApiKey = key
                        dialog.dismiss()
                        changed(force = true)
                        toast("Saved. Connect Trakt, then load and choose your lists.")
                    }
                }
            }
        }
        dialog.show()
    }

    private suspend fun disconnect() {
        val app = requireContext().applicationContext
        WallpaperWork.cancel(app)
        withContext(Dispatchers.IO) {
            WallpaperState.syncMutex.withLock {
                TraktDeviceAuthRepository.refreshMutex.withLock {
                    PreferencesManager.clearLogin()
                    PreferencesManager.selectedCatalogs = ""
                    PreferencesManager.personalListOptions = ""
                    PreferencesManager.popularListOptions = ""
                    PreferencesManager.traktRetryAt = 0
                    PreferencesManager["last_generation_at"] = 0L
                    WallpaperCache(app).clear()
                }
            }
        }
    }

    private fun about() {
        val box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 20, 32, 20) }
        box.addView(ImageView(requireContext()).apply {
            setImageDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.tmdb_logo)); contentDescription = "TMDB"
            adjustViewBounds = true
        }, LinearLayout.LayoutParams(210, 90))
        box.addView(TextView(requireContext()).apply {
            text = "My Trakt Wallpapers\n\nBased on johnneerdael/traktlistbackdrops and the Projectivy plugin sample. Apache License 2.0.\n\n" +
                "My Trakt Wallpapers uses Trakt but is not endorsed or certified by Trakt.\n\n" +
                "This product uses the TMDB API but is not endorsed or certified by TMDB.\nhttps://www.themoviedb.org\n\n" +
                "Trakt provides your chosen lists. TMDB provides artwork and details. Login tokens and API keys are encrypted on this TV. " +
                "Only official Trakt and TMDB services receive API requests. No analytics, external server, or hosting is used. " +
                "Disconnect removes login, list data, and saved images. Android Clear data removes all app settings.\n\n" +
                "Output: 3840 Ã— 2160 JPEG. Artwork quality depends on the source image."
            setAutoLinkMask(Linkify.WEB_URLS)
        })
        AlertDialog.Builder(requireContext()).setTitle("About").setView(ScrollView(requireContext()).apply { addView(box) })
            .setPositiveButton("Close", null).show()
    }

    private fun savedOptions(json: String): List<TraktPopularListOption> = runCatching {
        Gson().fromJson<List<TraktPopularListOption>>(json, object : TypeToken<List<TraktPopularListOption>>() {}.type).orEmpty()
    }.getOrDefault(emptyList())

    private fun runUi(block: suspend () -> Unit) {
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (isAdded) toast(if (error is ApiFailure) error.message.orEmpty() else "Unable to finish. Check your internet connection and API settings.") }
        }
    }

    private fun toast(message: String) { Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show() }
    private fun reload() {
        val updated = mutableListOf<GuidedAction>()
        onCreateActions(updated, null)
        // Every action is rebuilt. Leanback's default diff ignores new subActions and can leave
        // a row bound to an old action that expandAction can no longer find in the adapter.
        setActionsDiffCallback(null)
        setActions(updated)
    }
    private fun intervalTitle(value: Int) = when (value) { 15 -> "15 minutes"; 360 -> "6 hours"; else -> "1 hour" }

    companion object {
        internal fun isValidTraktRedirectUri(value: String): Boolean {
            val uri = runCatching { URI(value).parseServerAuthority() }.getOrNull() ?: return false
            if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawFragment != null ||
                uri.port > 65535 || uri.rawAuthority.endsWith(":")) return false
            return uri.scheme.equals("https", ignoreCase = true) ||
                (uri.scheme.equals("http", ignoreCase = true) &&
                    listOf("localhost", "127.0.0.1", "[::1]").any { uri.host.equals(it, ignoreCase = true) })
        }

        private const val STATUS = 1L
        private const val CONFIG = 2L
        private const val CONNECT = 3L
        private const val LOAD_MINE = 4L
        private const val MY_LISTS = 5L
        private const val EXTRAS = 6L
        private const val LOAD_POPULAR = 7L
        private const val REFRESH = 8L
        private const val INTERVAL = 9L
        private const val LIMIT = 10L
        private const val IDLE = 11L
        private const val DISCONNECT = 12L
        private const val ABOUT = 13L
    }
}
