package app.pleromastisk.ui

import java.net.URLDecoder

private val TagPath = Regex("""/(?:tags?|hashtag)/([^/?#]+)""", RegexOption.IGNORE_CASE)

/**
 * Hvis lenken er en emneknagg-lenke (teksten starter med «#» og adressen har /tag/ eller /tags/),
 * returneres navnet på emneknaggen – ellers null. Fungerer for Pleroma, Mastodon, Pixelfed m.fl.
 */
fun hashtagFromLink(url: String, label: String): String? {
    if (!label.trimStart().startsWith("#")) return null
    val match = TagPath.findAll(url).lastOrNull() ?: return null
    return runCatching { URLDecoder.decode(match.groupValues[1], "UTF-8") }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

private val HtmlTag = Regex("<[^>]+>")
// «#» som ikke står inni et ord, en adresse eller en HTML-entitet (&#39;)
private val HashtagInText = Regex("""(?<![\p{L}\p{N}_/&#])#([\p{L}\p{N}_]+)""")

/**
 * Emneknaggene i et innlegg, i rekkefølgen de står i teksten og med skrivemåten forfatteren brukte.
 * [known] er instansens liste (`tags`); knagger der som ikke ble funnet i teksten legges til sist.
 * Hvis instansen oppgir knagger, tas bare de med (så vi ikke plukker opp f.eks. «#1» fra teksten).
 */
fun hashtagsIn(html: String, known: List<String>): List<String> {
    val text = HtmlTag.replace(html, "")
    val knownLower = known.map { it.lowercase() }.toSet()
    val found = HashtagInText.findAll(text).map { it.groupValues[1] }
        .filter { name -> name.any { !it.isDigit() } }
        .filter { knownLower.isEmpty() || it.lowercase() in knownLower }
        .toList()
    return (found + known.filter { it.isNotBlank() })
        .distinctBy { it.lowercase() }
}
