package app.pleromastisk

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import app.pleromastisk.data.SavedAccount
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Snarveiene ved langt trykk på app-ikonet: «Nytt innlegg» for hver innlogget bruker, med
 * brukerens profilbilde som ikon. Oppdateres når brukerlisten endres.
 */
object Shortcuts {
    /** Nøkkelen (SavedAccount.key) til brukeren innlegget skal skrives som. */
    const val EXTRA_ACCOUNT = "app.feditastic.extra.ACCOUNT"

    private const val ID_PREFIX = "compose_"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    /** Det som sist ble publisert (nøkkel, navn, profilbilde), så vi ikke publiserer unødig. */
    private var published: List<Triple<String, String, String>>? = null

    fun update(context: Context, accounts: List<SavedAccount>) {
        val app = context.applicationContext
        scope.launch {
            val signature = accounts.map { Triple(it.key, it.handle, it.avatar) }
            if (signature == published) return@launch
            published = signature
            job?.cancel()
            job = launch { runCatching { publish(app, accounts) } }
        }
    }

    private suspend fun publish(context: Context, accounts: List<SavedAccount>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(1)
        val shortcuts = accounts.take(max).mapIndexed { rank, account ->
            ShortcutInfoCompat.Builder(context, ID_PREFIX + account.key)
                .setShortLabel("@" + account.handle.substringBefore('@').ifBlank { account.handle })
                .setLongLabel(context.getString(R.string.shortcut_compose_as, account.handle))
                .setIcon(avatarIcon(context, account.avatar))
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_COMPOSE)
                        .putExtra(EXTRA_ACCOUNT, account.key),
                )
                .setRank(rank)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)

        // Snarveier som er festet på hjemskjermen for brukere som er logget ut, slås av
        val keep = accounts.map { ID_PREFIX + it.key }.toSet()
        val gone = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .map { it.id }
            .filter { it.startsWith(ID_PREFIX) && it !in keep }
        if (gone.isNotEmpty()) {
            ShortcutManagerCompat.disableShortcuts(context, gone, context.getString(R.string.shortcut_logged_out))
        }
        // Festede snarveier for brukere som er logget inn igjen, slås på
        ShortcutManagerCompat.enableShortcuts(context, shortcuts)
    }

    /** Profilbildet som rundt ikon; blyanten hvis det ikke kan hentes. */
    private suspend fun avatarIcon(context: Context, url: String): IconCompat {
        if (url.isNotBlank()) {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(192)
                .allowHardware(false)
                .transformations(CircleCropTransformation())
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                runCatching { return IconCompat.createWithBitmap(result.drawable.toBitmap()) }
            }
        }
        return IconCompat.createWithResource(context, R.drawable.ic_shortcut_compose)
    }
}
