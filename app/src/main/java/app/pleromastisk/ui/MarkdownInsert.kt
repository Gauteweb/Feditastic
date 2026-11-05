package app.pleromastisk.ui

/** Tekst og markør/markering etter at en Markdown-knapp er trykket. */
internal data class MdEdit(val text: String, val selStart: Int, val selEnd: Int)

/**
 * Omslutter markeringen med [marker] (f.eks. ** for fet skrift). Uten markering settes
 * markøren mellom et par tomme markører, klar til å skrive.
 */
internal fun wrapMarkdown(text: String, selStart: Int, selEnd: Int, marker: String): MdEdit {
    val start = minOf(selStart, selEnd).coerceIn(0, text.length)
    val end = maxOf(selStart, selEnd).coerceIn(0, text.length)
    val out = text.substring(0, start) + marker + text.substring(start, end) + marker + text.substring(end)
    // Med markering: behold den (nå innenfor markørene); uten: markøren mellom markørene
    return MdEdit(out, start + marker.length, end + marker.length)
}

/**
 * Setter [prefix] (f.eks. «> ») først på linjen(e) markøren eller markeringen står på.
 * For overskrift øker et nytt trykk nivået: «# » blir «## ».
 */
internal fun prefixLines(text: String, selStart: Int, selEnd: Int, prefix: String): MdEdit {
    val start = minOf(selStart, selEnd).coerceIn(0, text.length)
    val end = maxOf(selStart, selEnd).coerceIn(0, text.length)
    val heading = prefix.trim() == "#"
    // Startindeksene til alle linjene markeringen berører
    val lineStarts = mutableListOf(text.lastIndexOf('\n', start - 1) + 1)
    var i = text.indexOf('\n', lineStarts[0])
    while (i >= 0 && i + 1 < end) {
        lineStarts.add(i + 1)
        i = text.indexOf('\n', i + 1)
    }
    val sb = StringBuilder(text)
    var newStart = start
    var newEnd = end
    for (ls in lineStarts.asReversed()) {
        val insert = if (heading && text.startsWith("#", ls)) "#" else prefix
        sb.insert(ls, insert)
        if (ls <= newStart) newStart += insert.length
        if (ls <= newEnd) newEnd += insert.length
    }
    return MdEdit(sb.toString(), newStart, newEnd)
}
