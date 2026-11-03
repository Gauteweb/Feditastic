package app.pleromastisk.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pleromastisk.Graph
import app.pleromastisk.R
import app.pleromastisk.data.Healthcheck
import app.pleromastisk.data.JobStats
import app.pleromastisk.userMessage
import java.text.NumberFormat

/**
 * Helsestatusen til Pleroma-tjeneren brukeren er logget inn på, fra /api/pleroma/healthcheck:
 * sunn eller ikke, databasetilkoblinger, minnebruk og jobbkøene siden tjeneren startet.
 */
@Composable
fun HealthDialog(onDismiss: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    // null = laster; Result med null = sjekken er slått av på tjeneren
    val result by produceState<Result<Healthcheck?>?>(null, attempt) {
        value = null
        value = runCatching { Graph.api.healthcheck() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, PT.Border),
        containerColor = PT.Fg,
        title = { Text(stringResource(R.string.health_title), color = PT.TopBarText, fontWeight = FontWeight.Bold) },
        text = {
            val r = result
            when {
                r == null -> LoadingRow()
                r.isFailure -> Text(r.exceptionOrNull()!!.userMessage(), color = PT.Text)
                r.getOrNull() == null -> Text(stringResource(R.string.health_disabled), color = PT.Text)
                else -> HealthContent(r.getOrThrow()!!)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_close), color = PT.Link) }
        },
        dismissButton = {
            TextButton(onClick = { attempt++ }, enabled = result != null) {
                Text(stringResource(R.string.health_refresh), color = PT.Link)
            }
        },
    )
}

@Composable
private fun HealthContent(h: Healthcheck) {
    val nf = remember { NumberFormat.getIntegerInstance() }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val healthy = h.healthy == true
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                if (healthy) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = null,
                tint = if (healthy) PT.Green else PT.Red,
                modifier = Modifier.size(22.dp),
            )
            Text(
                stringResource(if (healthy) R.string.health_ok else R.string.health_bad),
                color = PT.Text,
                fontWeight = FontWeight.Bold,
            )
        }

        HorizontalDivider(color = PT.Border)
        // Tittelen får hele bredden, tallene står på linjen under
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.health_db), color = PT.Text)
            Text(
                stringResource(R.string.health_db_value, h.active, h.idle) +
                    (h.poolSize?.let { " / " + stringResource(R.string.health_db_pool, it) } ?: ""),
                color = PT.Text,
                fontWeight = FontWeight.Bold,
            )
        }
        h.memoryUsed?.let { StatRow(stringResource(R.string.health_memory), "%.0f MB".format(it)) }

        h.jobQueueStats?.let { jobs ->
            HorizontalDivider(color = PT.Border)
            Text(stringResource(R.string.health_jobs), color = PT.TopBarText, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.health_jobs_hint), color = PT.Muted)
            StatRow(stringResource(R.string.health_jobs_total), nf.format(jobs.processedJobs))
            jobs.queues.entries.sortedByDescending { it.value.processedJobs }.forEach { (name, stats) ->
                QueueRow(queueLabel(name), stats, nf)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color = PT.Text) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = PT.Text, modifier = Modifier.weight(1f, fill = false))
        Text(value, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

/** Én jobbkø: hvor mange jobber som gikk bra, og feilene i rødt hvis det er noen. */
@Composable
private fun QueueRow(label: String, s: JobStats, nf: NumberFormat) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = PT.Text, modifier = Modifier.weight(1f, fill = false))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("✓ " + nf.format(s.success), color = PT.Green)
            Text("✗ " + nf.format(s.failure), color = if (s.failure > 0) PT.Red else PT.Muted)
        }
    }
}

/** Lesbart navn på Pleromas jobbkøer; ukjente køer vises med navnet sitt. */
@Composable
private fun queueLabel(name: String): String = when (name) {
    "federator_incoming" -> stringResource(R.string.health_queue_incoming)
    "federator_outgoing" -> stringResource(R.string.health_queue_outgoing)
    "background" -> stringResource(R.string.health_queue_background)
    "search_indexing" -> stringResource(R.string.health_queue_search)
    "web_push" -> stringResource(R.string.health_queue_push)
    "slow" -> stringResource(R.string.health_queue_slow)
    else -> name.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
