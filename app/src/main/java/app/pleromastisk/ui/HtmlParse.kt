package app.pleromastisk.ui

/**
 * Enkel HTML-tolker for innholdet instansene sender (Markdown som serveren har gjort om til HTML).
 * Erstatter Androids Html.fromHtml, som ikke kan <code>/<pre>, nummererte lister eller sitater,
 * og er ren Kotlin slik at den kan enhetstestes.
 */
internal sealed interface HtmlMark {
    data object Bold : HtmlMark
    data object Italic : HtmlMark
    data object Strike : HtmlMark
    data object Underline : HtmlMark
    data object Code : HtmlMark
    data object CodeBlock : HtmlMark
    data object Quote : HtmlMark
    /** Listepunkt, nummer og sitatstrek. */
    data object Marker : HtmlMark
    data object Sup : HtmlMark
    data object Sub : HtmlMark
    data class Heading(val level: Int) : HtmlMark
    data class Link(val url: String) : HtmlMark
}

internal data class MarkRange(val mark: HtmlMark, val start: Int, val end: Int)

/** Innrykk for én linje i em: [first] for første visuelle linje, [rest] for linjer som brytes. */
internal data class LineIndent(val start: Int, val end: Int, val first: Float, val rest: Float)

internal data class ParsedHtml(val text: String, val marks: List<MarkRange>, val indents: List<LineIndent>)

private val TokenRegex = Regex("""<!--.*?-->|<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:[^>"']|"[^"]*"|'[^']*')*)>""", RegexOption.DOT_MATCHES_ALL)
private val AttrRegex = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")
private val EntityRegex = Regex("""&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);""")
private val Whitespace = Regex("""\s+""")

private val NamedEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "hellip" to "…", "mdash" to "—", "ndash" to "–", "laquo" to "«", "raquo" to "»",
    "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "copy" to "©", "reg" to "®",
    "trade" to "™", "times" to "×", "middot" to "·", "bull" to "•", "shy" to "­",
    "zwj" to "‍", "zwnj" to "‌",
)

internal fun decodeEntities(s: String): String = if ('&' !in s) s else EntityRegex.replace(s) { m ->
    val e = m.groupValues[1]
    when {
        e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let(::codePoint)
        e.startsWith("#") -> e.substring(1).toIntOrNull()?.let(::codePoint)
        else -> NamedEntities[e]
    } ?: m.value
}

private fun codePoint(cp: Int): String? =
    if (cp in 1..0x10FFFF && cp !in 0xD800..0xDFFF) String(Character.toChars(cp)) else null

/** Mellomrom etter listemarkøren (en-space, alltid 0,5 em bred). */
private const val MarkerGap = "\u2002"
private const val BulletEm = 0.85f
/** Innrykk før markøren for hvert listenivå. */
private const val LevelEm = 0.9f
private const val QuoteBar = "▍ "
private const val QuoteEm = 0.9f

private fun numberEm(label: String) = 0.56f * (label.length - 1) + 0.75f

private class ListCtx(val ordered: Boolean, var counter: Int) {
    /** Bredden på markøren til gjeldende punkt, brukt som hengende innrykk. */
    var markerEm = BulletEm
}

private class OpenMark(val tag: String, val mark: HtmlMark?, var start: Int)

private class HtmlBuilder {
    val out = StringBuilder()
    val marks = mutableListOf<MarkRange>()
    val indents = mutableListOf<LineIndent>()

    private val open = ArrayList<OpenMark>()
    private val lists = ArrayList<ListCtx>()
    private var liDepth = 0
    private var quoteDepth = 0
    private var preDepth = 0
    private var pendingBreak = 0
    private var pendingSpace = false
    private var pendingMarker: String? = null
    private var atLineStart = true
    private var lineStart = 0
    private var lineFirst = 0f
    private var lineRest = 0f
    private var skipPreNewline = false
    private var skipDepth = 0

    fun text(raw: String) {
        if (skipDepth > 0) return
        var s = decodeEntities(raw)
        if (preDepth > 0) {
            if (skipPreNewline) {
                skipPreNewline = false
                s = s.removePrefix("\r").removePrefix("\n")
            }
            val parts = s.replace("\r\n", "\n").split('\n')
            parts.forEachIndexed { i, part ->
                if (i > 0) {
                    flushBreak()
                    if (atLineStart && out.isNotEmpty()) beginLine()
                    newline()
                }
                if (part.isNotEmpty()) write(part)
            }
            return
        }
        s = s.replace(Whitespace, " ")
        if (s.isEmpty()) return
        if (s.startsWith(" ")) {
            pendingSpace = true
            s = s.substring(1)
        }
        val trailing = s.endsWith(" ")
        if (trailing) s = s.dropLast(1)
        if (s.isNotEmpty()) write(s)
        if (trailing) pendingSpace = true
    }

    fun startTag(tag: String, attrs: Map<String, String>) {
        if (skipDepth > 0) {
            if (tag == "script" || tag == "style") skipDepth++
            return
        }
        when (tag) {
            "script", "style" -> skipDepth++
            "br" -> lineBreak()
            "p", "div" -> requestBreak(blockGap())
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                requestBreak(blockGap())
                push(tag, HtmlMark.Heading(tag[1] - '0'))
            }
            "ul", "ol" -> {
                requestBreak(if (liDepth > 0) 1 else blockGap())
                val start = if (tag == "ol") attrs["start"]?.trim()?.toIntOrNull() ?: 1 else 1
                lists.add(ListCtx(tag == "ol", start))
            }
            "li" -> {
                requestBreak(1)
                val list = lists.lastOrNull()
                val label = when {
                    list == null -> "•"
                    list.ordered -> "${list.counter++}."
                    lists.size % 2 == 0 -> "◦"
                    else -> "•"
                }
                list?.markerEm = if (list?.ordered == true) numberEm(label) else BulletEm
                pendingMarker = label + MarkerGap
                liDepth++
            }
            "blockquote" -> {
                requestBreak(blockGap())
                quoteDepth++
                push(tag, HtmlMark.Quote)
            }
            "pre" -> {
                requestBreak(blockGap())
                preDepth++
                skipPreNewline = true
                push(tag, HtmlMark.CodeBlock)
            }
            "hr" -> {
                requestBreak(blockGap())
                write("──────────", HtmlMark.Marker)
                requestBreak(blockGap())
            }
            "img" -> attrs["alt"]?.takeIf { it.isNotBlank() }?.let { text(it) }
            "a" -> push(tag, attrs["href"]?.let { HtmlMark.Link(decodeEntities(it)) })
            "b", "strong" -> push(tag, HtmlMark.Bold)
            "i", "em", "cite", "var", "dfn" -> push(tag, HtmlMark.Italic)
            "s", "del", "strike" -> push(tag, HtmlMark.Strike)
            "u", "ins" -> push(tag, HtmlMark.Underline)
            "code", "tt", "kbd", "samp" -> push(tag, if (preDepth > 0) null else HtmlMark.Code)
            "sup" -> push(tag, HtmlMark.Sup)
            "sub" -> push(tag, HtmlMark.Sub)
        }
    }

    fun endTag(tag: String) {
        if (skipDepth > 0) {
            if (tag == "script" || tag == "style") skipDepth--
            return
        }
        when (tag) {
            "p", "div" -> requestBreak(blockGap())
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                pop(tag)
                requestBreak(blockGap())
            }
            "ul", "ol" -> {
                if (lists.isNotEmpty()) lists.removeAt(lists.lastIndex)
                pendingMarker = null
                requestBreak(if (liDepth > 0) 1 else blockGap())
            }
            "li" -> {
                if (liDepth > 0) liDepth--
                pendingMarker = null
                requestBreak(1)
            }
            "blockquote" -> {
                pop(tag)
                requestBreak(blockGap())
                if (quoteDepth > 0) quoteDepth--
            }
            "pre" -> {
                pop(tag)
                if (preDepth > 0) preDepth--
                skipPreNewline = false
                requestBreak(blockGap())
            }
            else -> pop(tag)
        }
    }

    fun finish(): ParsedHtml {
        if (!atLineStart) recordLine(out.length)
        while (open.isNotEmpty()) close(open.removeAt(open.lastIndex))
        val text = out.toString().trimEnd()
        val len = text.length
        val m = marks.mapNotNull { r ->
            val e = r.end.coerceAtMost(len)
            if (e > r.start) r.copy(end = e) else null
        }
        val ind = indents.mapNotNull { r ->
            val e = r.end.coerceAtMost(len)
            if (e > r.start) r.copy(end = e) else null
        }
        return ParsedHtml(text, m, ind)
    }

    /** Avstand mellom blokker: tom linje, unntatt inne i lister der det holder med linjeskift. */
    private fun blockGap() = if (liDepth > 0) 1 else 2

    private fun requestBreak(n: Int) {
        pendingSpace = false
        if (n > pendingBreak) pendingBreak = n
    }

    private fun lineBreak() {
        flushBreak()
        if (out.isEmpty() && pendingMarker == null) return
        if (atLineStart) beginLine()
        newline()
    }

    private fun push(tag: String, mark: HtmlMark?) {
        open.add(OpenMark(tag, mark, -1))
    }

    private fun pop(tag: String) {
        val i = open.indexOfLast { it.tag == tag }
        if (i < 0) return
        // Lukker også tagger som ble stående åpne inni (feilnøstet HTML)
        while (open.size > i) close(open.removeAt(open.lastIndex))
    }

    private fun close(m: OpenMark) {
        if (m.mark != null && m.start >= 0 && out.length > m.start) {
            marks.add(MarkRange(m.mark, m.start, out.length))
        }
    }

    private fun flushBreak() {
        if (pendingBreak == 0) return
        if (out.isEmpty()) {
            pendingBreak = 0
            return
        }
        var existing = 0
        while (existing < out.length && out[out.length - 1 - existing] == '\n') existing++
        if (!atLineStart) {
            newline()
            existing = 1
        }
        repeat(pendingBreak - existing) { newline() }
        pendingBreak = 0
    }

    private fun write(s: String, extra: HtmlMark? = null) {
        flushBreak()
        if (atLineStart) beginLine()
        else if (pendingSpace) out.append(' ')
        pendingSpace = false
        for (m in open) if (m.start < 0) m.start = out.length
        val from = out.length
        out.append(s)
        if (extra != null) marks.add(MarkRange(extra, from, out.length))
    }

    /** Starter en ny linje: innrykk for lister, sitatstreker og eventuelt listemarkør. */
    private fun beginLine() {
        atLineStart = false
        pendingSpace = false
        lineStart = out.length
        // Innrykk fra overordnede listepunkter; gjeldende punkt teller bare når linjen ikke har markør
        val ancestors = lists.dropLast(1).sumOf { (LevelEm + it.markerEm).toDouble() }.toFloat()
        val current = lists.lastOrNull()
        var first = ancestors + if (current != null) LevelEm else 0f
        if (current != null && pendingMarker == null && liDepth > 0) first += current.markerEm
        var rest = first
        repeat(quoteDepth) {
            val s = out.length
            out.append(QuoteBar)
            marks.add(MarkRange(HtmlMark.Marker, s, out.length))
            rest += QuoteEm
        }
        pendingMarker?.let {
            val s = out.length
            out.append(it)
            marks.add(MarkRange(HtmlMark.Marker, s, out.length))
            rest += current?.markerEm ?: BulletEm
            pendingMarker = null
        }
        lineFirst = first
        lineRest = rest
    }

    private fun newline() {
        if (!atLineStart) recordLine(out.length)
        out.append('\n')
        atLineStart = true
        pendingSpace = false
    }

    private fun recordLine(end: Int) {
        if (lineFirst > 0f || lineRest > 0f) indents.add(LineIndent(lineStart, end, lineFirst, lineRest))
    }
}

/**
 * Compose lager et eget avsnitt for hvert innrykk (ParagraphStyle), og et linjeskift rett ved en
 * avsnittsgrense gir da en ekstra tom linje. Fjerner linjeskiftene like før og etter hver linje med innrykk.
 */
internal fun ParsedHtml.withParagraphBreaks(): ParsedHtml {
    if (indents.isEmpty()) return this
    val drop = sortedSetOf<Int>()
    for (r in indents) {
        if (r.start > 0 && text[r.start - 1] == '\n') drop.add(r.start - 1)
        if (r.end < text.length && text[r.end] == '\n') drop.add(r.end)
    }
    val removed = drop.toIntArray()
    fun map(i: Int): Int {
        var lo = 0
        var hi = removed.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (removed[mid] < i) lo = mid + 1 else hi = mid
        }
        return i - lo
    }
    val sb = StringBuilder(text.length)
    text.forEachIndexed { i, c -> if (i !in drop) sb.append(c) }
    return ParsedHtml(
        sb.toString(),
        marks.mapNotNull { r -> MarkRange(r.mark, map(r.start), map(r.end)).takeIf { it.end > it.start } },
        indents.map { LineIndent(map(it.start), map(it.end), it.first, it.rest) },
    )
}

internal fun parseHtml(html: String): ParsedHtml {
    val b = HtmlBuilder()
    var last = 0
    for (m in TokenRegex.findAll(html)) {
        if (m.range.first > last) b.text(html.substring(last, m.range.first))
        last = m.range.last + 1
        val name = m.groupValues[2].lowercase()
        if (name.isEmpty()) continue // kommentar
        if (m.groupValues[1] == "/") {
            b.endTag(name)
        } else {
            val attrs = AttrRegex.findAll(m.groupValues[3]).associate { a ->
                a.groupValues[1].lowercase() to (a.groups[2] ?: a.groups[3] ?: a.groups[4])!!.value
            }
            b.startTag(name, attrs)
            if (m.groupValues[3].trimEnd().endsWith("/") && name !in VoidTags) b.endTag(name)
        }
    }
    if (last < html.length) b.text(html.substring(last))
    return b.finish()
}

private val VoidTags = setOf("br", "hr", "img")
