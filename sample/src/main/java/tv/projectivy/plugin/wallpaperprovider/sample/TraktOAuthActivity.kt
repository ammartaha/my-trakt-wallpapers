// Modified for My Trakt Wallpapers (2026).
package tv.projectivy.plugin.wallpaperprovider.sample

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.traktlistbackdrops.tv.R
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tv.projectivy.plugin.wallpaperprovider.sample.local.*

class TraktOAuthActivity : FragmentActivity() {
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trakt_oauth)
        PreferencesManager.init(applicationContext)
        findViewById<Button>(R.id.oauthClose).setOnClickListener { finish() }
        scope.launch {
            try {
                val repo = TraktDeviceAuthRepository()
                if (PreferencesManager.traktDeviceCode.isBlank() || PreferencesManager.deviceExpiresAt <= System.currentTimeMillis() / 1000) {
                    val result = repo.start()
                    if (result.isFailure) {
                        showStatus("Unable to start login. Check API settings and your internet connection.")
                        return@launch
                    }
                }
                showCode()
                while (isActive && System.currentTimeMillis() / 1000 < PreferencesManager.deviceExpiresAt) {
                    val serverWait = (PreferencesManager.traktRetryAt - System.currentTimeMillis()).coerceAtLeast(0)
                    delay(maxOf(PreferencesManager.devicePollSeconds * 1000L, serverWait))
                    when (val result = repo.poll()) {
                        TraktPollResult.Approved -> {
                            showStatus("Connected. Close this page, load My lists, then choose your lists.")
                            delay(1500)
                            finish()
                            return@launch
                        }
                        TraktPollResult.Pending -> showStatus("Waiting for Trakt approval...")
                        TraktPollResult.SlowDown -> showStatus("Trakt asked us to wait. Login will continue.")
                        else -> {
                            PreferencesManager.traktDeviceCode = ""
                            PreferencesManager.traktUserCode = ""
                            showStatus(when (result) {
                                TraktPollResult.Denied -> "Login was denied. Close this page and connect again."
                                is TraktPollResult.Failed -> result.message
                                else -> "This code is no longer valid. Close this page and connect again."
                            })
                            return@launch
                        }
                    }
                }
                PreferencesManager.traktDeviceCode = ""
                PreferencesManager.traktUserCode = ""
                showStatus("This code expired. Close this page and connect again.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                showStatus("Login could not finish. Check your internet connection, then connect again.")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showCode() {
        val verification = PreferencesManager.traktVerificationUrl.toHttpUrlOrNull()
        check(verification != null && verification.isHttps && verification.host in setOf("trakt.tv", "app.trakt.tv", "auth.trakt.tv")) {
            "Trakt returned an invalid activation address."
        }
        val code = PreferencesManager.traktUserCode
        val activation = verification.newBuilder().addPathSegment(code).build().toString()
        findViewById<TextView>(R.id.oauthUrl).text = verification.toString()
        findViewById<TextView>(R.id.oauthCode).text = code
        findViewById<ImageView>(R.id.oauthQr).setImageBitmap(QrCodeGenerator.generate(activation))
        showStatus("Scan the QR code, or open the address on your phone and enter the code.")
    }

    private fun showStatus(message: String) { findViewById<TextView>(R.id.oauthStatus).text = message }
}
