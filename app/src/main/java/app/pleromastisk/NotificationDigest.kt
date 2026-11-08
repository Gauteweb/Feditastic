package app.pleromastisk

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pleromastisk.data.SavedAccount
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * Timesoppsummering av varsler: én gang i timen henter appen nye favorittmarkeringer, delinger
 * svar og følgere for hver innlogget bruker, og viser et varsel på telefonen om noe har skjedd.
 * Appen spør selv (ingen push-tjeneste eller tredjepart).
 */
object NotificationDigest {
    private const val WORK = "notification_digest"
    private const val CHANNEL = "digest"
    private const val PREFS = "digest"

    /** Henter aldri mer enn et døgn tilbake, f.eks. etter at telefonen har vært av. */
    private const val MAX_LOOKBACK_MS = 24 * 3600_000L
    private const val MAX_PAGES = 10

    /** Starter den timevise sjekken (gjør ingenting om den allerede er satt opp). */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DigestWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Android 13 og nyere krever at brukeren gir lov til varsler. Vi spør bare én gang. */
    fun shouldAskPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !prefs(context).getBoolean("permission_asked", false)

    fun markPermissionAsked(context: Context) {
        prefs(context).edit().putBoolean("permission_asked", true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Selve sjekken, for alle innloggede brukere. */
    suspend fun run(context: Context) {
        val accounts = Graph.store.accounts
        val prefs = prefs(context)
        val now = System.currentTimeMillis()
        for (account in accounts) {
            val key = "checked_" + account.key
            // Første gang: den siste timen. Ellers: siden forrige sjekk.
            val since = prefs.getLong(key, now - 3600_000L).coerceAtLeast(now - MAX_LOOKBACK_MS)
            val counts = runCatching { count(account, since) }.getOrNull() ?: continue
            prefs.edit().putLong(key, now).apply()
            if (counts.total > 0) show(context, account, counts, multiple = accounts.size > 1)
        }
        // Fjern det vi husket om brukere som er logget ut
        val keep = accounts.map { "checked_" + it.key }.toSet()
        prefs.all.keys.filter { it.startsWith("checked_") && it !in keep }
            .forEach { prefs.edit().remove(it).apply() }
    }

    private data class Counts(val favourites: Int, val reblogs: Int, val replies: Int, val follows: Int) {
        val total get() = favourites + reblogs + replies + follows
    }

    private suspend fun count(account: SavedAccount, since: Long): Counts {
        var favourites = 0
        var reblogs = 0
        var replies = 0
        var follows = 0
        var maxId: String? = null
        repeat(MAX_PAGES) {
            val page = Graph.api.notificationsFor(account, maxId)
            for (n in page) {
                val at = epochMillis(n.createdAt) ?: continue
                if (at <= since) return Counts(favourites, reblogs, replies, follows)
                when (n.type) {
                    "favourite" -> favourites++
                    "reblog" -> reblogs++
                    "mention" -> replies++
                    "follow" -> follows++
                }
            }
            if (page.size < 40) return Counts(favourites, reblogs, replies, follows)
            maxId = page.last().id
        }
        return Counts(favourites, reblogs, replies, follows)
    }

    private fun epochMillis(iso: String): Long? =
        runCatching { OffsetDateTime.parse(iso).toInstant() }
            .recoverCatching { Instant.parse(iso) }
            .getOrNull()?.toEpochMilli()

    private fun show(context: Context, account: SavedAccount, counts: Counts, multiple: Boolean) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.digest_channel))
                .setDescription(context.getString(R.string.digest_channel_desc))
                .build(),
        )
        val res = context.resources
        // Bare linjene der noe har skjedd
        val lines = listOf(
            R.plurals.digest_favourites to counts.favourites,
            R.plurals.digest_reblogs to counts.reblogs,
            R.plurals.digest_replies to counts.replies,
            R.plurals.digest_follows to counts.follows,
        ).filter { it.second > 0 }.map { (id, n) -> res.getQuantityString(id, n, n) }
        val open = PendingIntent.getActivity(
            context,
            account.key.hashCode(),
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_NOTIFICATIONS)
                .putExtra(Shortcuts.EXTRA_ACCOUNT, account.key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            // Brukernavnet først i overskriften, så det synes også når Android har samlet
            // varslene i en gruppe (der vises bare overskriftene)
            .setContentTitle(
                context.getString(R.string.digest_title_for, account.handle.substringBefore('@').ifBlank { account.handle }),
            )
            .setContentText(lines.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            // Med flere brukere: vis hvilken bruker oppsummeringen gjelder
            .apply { if (multiple) setSubText(account.handle) }
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            // Én oppsummering per bruker; den nye erstatter den forrige
            manager.notify(account.key.hashCode(), notification)
        } catch (_: SecurityException) {
            // Tillatelsen ble trukket tilbake underveis
        }
    }
}

class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        NotificationDigest.run(applicationContext)
        return Result.success()
    }
}
