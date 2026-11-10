package app.pleromastisk

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.pleromastisk.ui.AppTheme
import app.pleromastisk.ui.FontSize
import app.pleromastisk.ui.PT
import app.pleromastisk.ui.PendingCompose
import app.pleromastisk.ui.PendingNotifications
import app.pleromastisk.ui.PleromastiskRoot
import app.pleromastisk.ui.PleromastiskTheme
import app.pleromastisk.vm.AuthViewModel

class MainActivity : ComponentActivity() {
    private val auth: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Først: snarveien kan bytte bruker, og da skal den brukerens tema brukes
        if (savedInstanceState == null) handle(intent)
        PT.choice = Graph.store.activeTheme ?: AppTheme.AUTO
        PT.systemDark = isNightMode()
        PT.fontSize = FontSize.fromId(Graph.store.fontSize)

        setContent {
            // Følger telefonens lyse/mørke modus når temaet er «Automatisk»
            PT.systemDark = isSystemInDarkTheme()
            // Lyst tema: mørke ikoner i status- og navigasjonslinjen
            val light = PT.theme.colors.light
            LaunchedEffect(light) {
                val style = if (light) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PleromastiskTheme {
                Box(Modifier.fillMaxSize().background(PT.Bg)) {
                    // Bakgrunnsbildet. Forskjøvet mot høyre slik at fjelltoppen vises i stående format.
                    Image(
                        painter = painterResource(R.drawable.bakgrunn),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = BiasAlignment(horizontalBias = 0.5f, verticalBias = 0f),
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Slør i temaets farge, så teksten alltid er lesbar
                    Box(Modifier.fillMaxSize().background(PT.Overlay))
                    PleromastiskRoot(auth)
                }
            }
        }
    }

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Ved oppstart, og når appen hentes fram igjen etter mer enn et døgn: se etter ny versjon. */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { Graph.updates.checkIfDue() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_COMPOSE) {
            // Snarveien for en bestemt bruker: bytt til den brukeren først
            val key = intent.getStringExtra(Shortcuts.EXTRA_ACCOUNT)
                ?.takeIf { k -> Graph.store.accounts.any { it.key == k } }
            if (key != null) auth.switchTo(key)
            PendingCompose.requested.value = key ?: PendingCompose.ANY
        }
        if (intent?.action == ACTION_NOTIFICATIONS) {
            // Trykk på timesoppsummeringen: bytt til brukeren den gjelder, og vis varslene
            val key = intent.getStringExtra(Shortcuts.EXTRA_ACCOUNT)
                ?.takeIf { k -> Graph.store.accounts.any { it.key == k } }
            if (key != null) auth.switchTo(key)
            PendingNotifications.requested.value = key ?: PendingCompose.ANY
        }
        intent?.data?.let { auth.handleRedirect(it) }
    }

    companion object {
        /** Snarveiene «Nytt innlegg» på app-ikonet (se [Shortcuts]). */
        const val ACTION_COMPOSE = "app.feditastic.action.COMPOSE"

        /** Trykk på timesoppsummeringen av varsler (se [NotificationDigest]). */
        const val ACTION_NOTIFICATIONS = "app.feditastic.action.NOTIFICATIONS"
    }
}
