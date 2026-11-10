package app.pleromastisk.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.pleromastisk.R
import app.pleromastisk.data.Emoji
import app.pleromastisk.data.Mention
import coil.compose.AsyncImage

private val EmojiRegex = Regex(":([A-Za-z0-9_\\-]+):")

/** Gjør instansens HTML om til stilet tekst med klikkbare lenker og egendefinerte emojier. */
fun htmlToRich(
    html: String,
    emojis: List<Emoji>,
    linkColor: Color,
    onHashtag: (String) -> Unit = {},
    mentions: List<Mention> = emptyList(),
    onProfile: (id: String?, url: String) -> Unit = { _, _ -> },
    /** false = lenker får bare utseendet, ikke klikk (se [RichText] med maxLines). */
    clickableLinks: Boolean = true,
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val cleaned = html
        .replace(Regex("<span class=\"invisible\">.*?</span>", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("<span class=\"ellipsis\">(.*?)</span>", RegexOption.DOT_MATCHES_ALL), "\$1…")
    val parsed = parseHtml(cleaned).withParagraphBreaks()
    val plain = parsed.text
    val codeBg = linkColor.copy(alpha = 0.14f)
    val base = buildAnnotatedString {
        append(plain)
        for (ind in parsed.indents) {
            addStyle(ParagraphStyle(textIndent = TextIndent(ind.first.em, ind.rest.em)), ind.start, ind.end)
        }
        for ((mark, start, end) in parsed.marks) {
            when (mark) {
                is HtmlMark.Link -> {
                    val style = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    val url = mark.url
                    val linkText = plain.substring(start, end)
                    val tag = hashtagFromLink(url, linkText)
                    if (!clickableLinks) {
                        addStyle(style.style ?: SpanStyle(), start, end)
                    } else if (tag == null && (linkText.startsWith("@") || mentions.any { it.url == url })) {
                        // @omtaler åpner profilen i appen (via egen instans) i stedet for nettsiden
                        val id = mentionFor(url, linkText, mentions)?.id
                        addLink(
                            LinkAnnotation.Clickable(
                                tag = "mention:$url",
                                styles = style,
                                linkInteractionListener = { onProfile(id, url) },
                            ),
                            start, end,
                        )
                    } else if (tag != null) {
                        // Emneknagger åpnes i appen i stedet for i nettleseren
                        addLink(
                            LinkAnnotation.Clickable(
                                tag = "hashtag:$tag",
                                styles = style,
                                linkInteractionListener = { onHashtag(tag) },
                            ),
                            start, end,
                        )
                    } else {
                        addLink(LinkAnnotation.Url(url, style), start, end)
                    }
                }
                HtmlMark.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                HtmlMark.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                HtmlMark.Strike -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                HtmlMark.Underline -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                HtmlMark.Code, HtmlMark.CodeBlock ->
                    addStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.92.em, background = codeBg), start, end)
                HtmlMark.Quote -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                HtmlMark.Marker -> addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold), start, end)
                HtmlMark.Sup -> addStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.75.em), start, end)
                HtmlMark.Sub -> addStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.75.em), start, end)
                is HtmlMark.Heading -> addStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = when (mark.level) { 1 -> 1.3.em; 2 -> 1.2.em; 3 -> 1.1.em; else -> 1.em },
                    ),
                    start, end,
                )
            }
        }
    }
    return withEmojis(base, emojis)
}

/** Finner omtalen en lenke peker på: først på adresse, deretter på «@navn» / «@navn@domene». */
internal fun mentionFor(url: String, linkText: String, mentions: List<Mention>): Mention? {
    mentions.firstOrNull { it.url == url }?.let { return it }
    val name = linkText.removePrefix("@").trim()
    if (name.isEmpty()) return null
    mentions.firstOrNull { it.acct.equals(name, ignoreCase = true) }?.let { return it }
    return mentions.filter { it.username.equals(name.substringBefore('@'), ignoreCase = true) }.singleOrNull()
}

fun withEmojis(src: AnnotatedString, emojis: List<Emoji>): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    if (emojis.isEmpty()) return src to emptyMap()
    val byCode = emojis.associateBy { it.shortcode }
    val inline = mutableMapOf<String, InlineTextContent>()
    val out = buildAnnotatedString {
        var last = 0
        for (m in EmojiRegex.findAll(src.text)) {
            val emoji = byCode[m.groupValues[1]] ?: continue
            append(src.subSequence(last, m.range.first))
            val id = "emoji_${emoji.shortcode}"
            appendInlineContent(id, ":${emoji.shortcode}:")
            inline.getOrPut(id) {
                InlineTextContent(
                    Placeholder(1.3.em, 1.3.em, PlaceholderVerticalAlign.TextCenter),
                ) {
                    AsyncImage(model = emoji.url, contentDescription = emoji.shortcode, modifier = Modifier.fillMaxSize())
                }
            }
            last = m.range.last + 1
        }
        append(src.subSequence(last, src.length))
    }
    return out to inline
}

/** Lar innlegg åpne en emneknagg-tidslinje når brukeren trykker på #emneknagg. */
val LocalHashtagClick = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Trykk på en @omtale: konto-ID hvis den er kjent, ellers lenken (slås opp via instansen). */
val LocalProfileLinkClick = staticCompositionLocalOf<(id: String?, url: String) -> Unit> { { _, _ -> } }

/** HTML-innhold fra et innlegg eller en profil. */
@Composable
fun RichText(
    html: String,
    emojis: List<Emoji>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = PT.FgText,
    maxLines: Int = Int.MAX_VALUE,
    mentions: List<Mention> = emptyList(),
    /** Tekst lengre enn dette kortes ned med en «Vis alt»-knapp (null = vis alt). */
    collapseAfter: Int? = null,
) {
    val onHashtag = LocalHashtagClick.current
    val onProfile = LocalProfileLinkClick.current
    // Compose 1.7 kræsjer (IndexOutOfBounds i getPathForRange) når en lenke ligger i et avsnitt som
    // faller utenfor maxLines, så avkortede forhåndsvisninger får lenker uten klikk.
    val clickableLinks = maxLines == Int.MAX_VALUE
    val (text, inline) = remember(html, emojis, onHashtag, mentions, onProfile, clickableLinks) {
        htmlToRich(html, emojis, PT.Link, onHashtag, mentions, onProfile, clickableLinks)
    }
    val cut = collapseAfter?.let { collapsePoint(text.text, it) }
    if (cut == null) {
        Text(
            text, modifier, style = style, color = color, maxLines = maxLines,
            overflow = TextOverflow.Ellipsis, inlineContent = inline,
        )
        return
    }
    var expanded by rememberSaveable(html) { mutableStateOf(false) }
    val shown = if (expanded) text else remember(text, cut) {
        buildAnnotatedString { append(text.subSequence(0, cut)); append(" …") }
    }
    Column(modifier) {
        Text(shown, style = style, color = color, inlineContent = inline)
        PButton(
            text = stringResource(if (expanded) R.string.read_less else R.string.read_more),
            onClick = { expanded = !expanded },
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Grensen for når innlegg kortes ned med «Vis alt». */
const val CollapseChars = 750

/**
 * Hvor en tekst lengre enn [limit] tegn skal kuttes: ved siste linjeskift eller mellomrom før grensen
 * (så ord og emojikoder ikke deles), eller null når teksten er kort nok.
 */
fun collapsePoint(text: String, limit: Int): Int? {
    if (text.length <= limit) return null
    val nl = text.lastIndexOf('\n', limit)
    if (nl >= limit * 2 / 3) return text.substring(0, nl).trimEnd().length
    val sp = text.lastIndexOf(' ', limit)
    return if (sp >= limit / 2) text.substring(0, sp).trimEnd().length else limit
}

/** Vanlig tekst (f.eks. visningsnavn) som kan inneholde :emoji:-koder. */
@Composable
fun EmojiText(
    text: String,
    emojis: List<Emoji>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = PT.FgText,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val (rich, inline) = remember(text, emojis) { withEmojis(AnnotatedString(text), emojis) }
    Text(
        rich, modifier, style = style, color = color, fontWeight = fontWeight, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, inlineContent = inline,
    )
}
