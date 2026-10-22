package tv.projectivy.plugin.wallpaperprovider.sample.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretStore {
    fun get(key: String): String
    fun putAll(values: Map<String, String>)
}

/** AES-GCM key stays in Android Keystore. A token pair is saved in one durable commit. */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val prefs = context.getSharedPreferences("encrypted_credentials", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alias = "my_trakt_wallpapers.credentials.v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    private fun read(): MutableMap<String, String> {
        val encoded = prefs.getString("payload", null) ?: return mutableMapOf()
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val json = cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        return gson.fromJson(json, object : TypeToken<MutableMap<String, String>>() {}.type)
    }

    @Synchronized
    override fun get(key: String): String = read()[key].orEmpty()

    @Synchronized
    override fun putAll(values: Map<String, String>) {
        val data = read().apply { putAll(values) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(gson.toJson(data).toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "Unable to save login data. Check free storage."
        }
    }
}
