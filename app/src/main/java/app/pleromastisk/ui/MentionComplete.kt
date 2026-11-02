package app.pleromastisk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.pleromastisk.data.Account

/** «@navn» som er under skriving: [start] er posisjonen til @, [end] slutten av ordet, [query] teksten etter @. */
internal data class MentionQuery(val start: Int, val end: Int, val query: String)

private fun isHandleChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '.' || c == '-' || c == '@'

/**
 * Finner nevnelsen markøren står i, f.eks. «@gau|» eller «@bob@mast|». Gir null når markøren ikke
 * står i et ord som starter med @, når @ står midt i et ord (e-postadresse) eller når det er markert tekst.
 */
internal fun mentionQueryAt(text: String, cursor: Int, selectionEnd: Int = cursor): MentionQuery? {
    if (cursor != selectionEnd || cursor !in 0..text.length) return null
    var start = cursor
    while (start > 0 && isHandleChar(text[start - 1])) start--
    var end = cursor
    while (end < text.length && isHandleChar(text[end])) end++
    if (start >= text.length || text[start] != '@') return null
    // Ordet må starte med @ (ikke «navn@domene»), og vi søker først når noe er skrevet etter @
    val query = text.substring(start + 1, cursor)
    if (query.isEmpty() || query.startsWith('@')) return null
    return MentionQuery(start, end, query)
}

/** Erstatter «@navn» under skriving med «@acct » og setter markøren etter mellomrommet. */
internal fun insertMention(value: TextFieldValue, q: MentionQuery, acct: String): TextFieldValue {
    val t = value.text
    val after = t.substring(q.end)
    val insert = "@$acct" + if (after.startsWith(' ')) "" else " "
    val newText = t.substring(0, q.start) + insert + after
    val cursor = q.start + insert.length + if (after.startsWith(' ')) 1 else 0
    return TextFieldValue(newText, TextRange(cursor))
}

/** Forslagsliste over kontoer jeg følger, vist rett over verktøylinjen i skrivevinduet. */
@Composable
internal fun MentionSuggestions(accounts: List<Account>, onPick: (Account) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .background(PT.Fg)
            .verticalScroll(rememberScrollState()),
    ) {
        HorizontalDivider(color = PT.Border)
        accounts.forEach { acc ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(acc) }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(acc.avatar, 32)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    EmojiText(
                        acc.name, acc.emojis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PT.FgText, fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                    Text(
                        "@" + acc.acct, style = MaterialTheme.typography.bodySmall,
                        color = PT.Muted, maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Startteksten i et svar: nevnelsene øverst og markøren på linjen rett under dem. Har innlegget
 * emneknagger, kommer de nederst med en tom linje mellom markøren og knaggene.
 */
internal fun replyDraft(mentions: String?, tags: String?): TextFieldValue {
    val head = mentions?.split(" ")?.filter { it.isNotBlank() }?.joinToString(" ") { "@$it" }.orEmpty()
    val tail = tags?.split(" ")?.filter { it.isNotBlank() }?.joinToString(" ") { "#$it" }.orEmpty()
    val before = if (head.isEmpty()) "" else "$head\n"
    val after = if (tail.isEmpty()) "" else "\n\n$tail"
    return TextFieldValue(before + after, TextRange(before.length))
}
