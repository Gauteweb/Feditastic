package app.pleromastisk.data

import android.content.Context
import app.pleromastisk.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** En nyere utgivelse på GitHub enn den som er installert. */
data class AvailableUpdate(val version: String, val url: String)

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

/**
 * Ser etter nye utgivelser på GitHub: ved hver oppstart av appen, og ellers høyst én gang i døgnet.
 * Bare i bygg der [BuildConfig.UPDATE_CHECK] er på (GitHub-utgivelsene) – F-Droid oppdaterer selv.
 */
class UpdateChecker(context: Context) {
    private val sp = context.getSharedPreferences("update_check", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _update = MutableStateFlow(stored())
    val update: StateFlow<AvailableUpdate?> = _update.asStateFlow()

    private var checkedThisRun = false

    /** Sjekker hvis appen nettopp er startet eller det er over et døgn siden sist. Feil ignoreres. */
    suspend fun checkIfDue() {
        if (!BuildConfig.UPDATE_CHECK) return
        val now = System.currentTimeMillis()
        if (checkedThisRun && now - sp.getLong(KEY_CHECKED_AT, 0) < DAY_MS) return
        checkedThisRun = true
        runCatching { fetchLatest() }.onSuccess { release ->
            sp.edit()
                .putLong(KEY_CHECKED_AT, now)
                .putString(KEY_VERSION, release?.tagName)
                .putString(KEY_URL, release?.htmlUrl)
                .apply()
            _update.value = stored()
        }
    }

    /** Siste kjente utgivelse, hvis den er nyere enn appen (også uten nett). */
    private fun stored(): AvailableUpdate? {
        if (!BuildConfig.UPDATE_CHECK) return null
        val version = sp.getString(KEY_VERSION, null) ?: return null
        val url = sp.getString(KEY_URL, null)?.takeIf { it.startsWith("https://") } ?: return null
        return if (isNewer(version, BuildConfig.VERSION_NAME)) AvailableUpdate(version.removePrefix("v"), url) else null
    }

    private suspend fun fetchLatest(): GitHubRelease? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Feditastic/${BuildConfig.VERSION_NAME}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            json.decodeFromString<GitHubRelease>(resp.body?.string().orEmpty())
                .takeIf { !it.draft && !it.prerelease && it.tagName.isNotBlank() }
        }
    }

    companion object {
        private const val LATEST_URL = "https://api.github.com/repos/Gauteweb/Feditastic/releases/latest"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val KEY_CHECKED_AT = "checked_at"
        private const val KEY_VERSION = "latest_version"
        private const val KEY_URL = "latest_url"

        /** «v2.3» > «2.2.4»? Sammenligner tallene ett og ett; manglende tall regnes som 0. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
                .split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
