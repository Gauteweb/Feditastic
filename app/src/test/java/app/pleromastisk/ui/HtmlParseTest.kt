package app.pleromastisk.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlParseTest {
    private fun text(html: String) = parseHtml(html).text
    private fun marked(html: String, mark: HtmlMark): List<String> {
        val p = parseHtml(html)
        return p.marks.filter { it.mark == mark }.map { p.text.substring(it.start, it.end) }
    }

    @Test fun avsnittFaarTomLinje() = assertEquals("a\n\nb", text("<p>a</p><p>b</p>"))

    @Test fun linjeskift() = assertEquals("a\nb", text("<p>a<br>b</p>"))

    @Test fun mellomromSlaasSammen() = assertEquals("a b c", text("<p>a \n  b\n<b>c</b></p>"))

    @Test fun punktliste() = assertEquals(
        "før\n\n•\u2002en\n•\u2002to\n\netter",
        text("<p>før</p><ul><li>en</li><li>to</li></ul><p>etter</p>"),
    )

    @Test fun punktlisteMedLinjeskiftIHtml() = assertEquals(
        "•\u2002en\n•\u2002to",
        text("<ul>\n<li>en</li>\n<li>to</li>\n</ul>"),
    )

    @Test fun nummerertListe() = assertEquals("1.\u2002en\n2.\u2002to\n3.\u2002tre", text("<ol><li>en</li><li>to</li><li>tre</li></ol>"))

    @Test fun nummerertListeMedStart() = assertEquals("4.\u2002en\n5.\u2002to", text("<ol start=\"4\"><li>en</li><li>to</li></ol>"))

    @Test fun nostetListe() = assertEquals(
        "•\u2002a\n◦\u2002b\n◦\u2002c\n•\u2002d",
        text("<ul><li>a<ul><li>b</li><li>c</li></ul></li><li>d</li></ul>"),
    )

    @Test fun nostetListeFaarInnrykk() {
        val p = parseHtml("<ul><li>a<ul><li>b</li></ul></li></ul>")
        val b = p.indents.single { p.text.substring(it.start).startsWith("◦") }
        assertTrue(b.first > 0f)
        assertTrue(b.rest > b.first)
    }

    @Test fun løsListeMedAvsnitt() = assertEquals(
        "•\u2002en\n•\u2002to",
        text("<ul><li><p>en</p></li><li><p>to</p></li></ul>"),
    )

    @Test fun kodeIAvsnitt() {
        val html = "<p>kjør <code>ls -la</code> nå</p>"
        assertEquals("kjør ls -la nå", text(html))
        assertEquals(listOf("ls -la"), marked(html, HtmlMark.Code))
    }

    @Test fun kodeblokkBeholderLinjerOgMellomrom() {
        val html = "<p>se:</p><pre><code>fun a() {\n    return 1\n}\n</code></pre><p>slutt</p>"
        assertEquals("se:\n\nfun a() {\n    return 1\n}\n\nslutt", text(html))
        assertEquals(listOf("fun a() {\n    return 1\n}"), marked(html, HtmlMark.CodeBlock).map { it.trimEnd() })
        assertTrue(marked(html, HtmlMark.Code).isEmpty())
    }

    @Test fun kodeblokkDekoderEntiteter() = assertEquals("a < b && c", text("<pre><code>a &lt; b &amp;&amp; c</code></pre>"))

    @Test fun sitat() {
        val html = "<blockquote><p>sitert</p></blockquote><p>svar</p>"
        assertEquals("▍ sitert\n\nsvar", text(html))
        assertEquals(listOf("sitert"), marked(html, HtmlMark.Quote))
    }

    @Test fun overskrift() {
        val html = "<h2>Tittel</h2><p>tekst</p>"
        assertEquals("Tittel\n\ntekst", text(html))
        assertEquals(listOf("Tittel"), marked(html, HtmlMark.Heading(2)))
    }

    @Test fun fetKursivGjennomstreket() {
        val html = "<p><strong>f</strong> <em>k</em> <del>g</del></p>"
        assertEquals("f k g", text(html))
        assertEquals(listOf("f"), marked(html, HtmlMark.Bold))
        assertEquals(listOf("k"), marked(html, HtmlMark.Italic))
        assertEquals(listOf("g"), marked(html, HtmlMark.Strike))
    }

    @Test fun lenkeOgOmtale() {
        val html = """<p><span class="h-card"><a href="https://x.no/@ola" class="u-url mention">@<span>ola</span></a></span> se <a href="https://a.no/?a=1&amp;b=2">her</a></p>"""
        val p = parseHtml(html)
        assertEquals("@ola se her", p.text)
        val links = p.marks.filter { it.mark is HtmlMark.Link }
        assertEquals(listOf("@ola", "her"), links.map { p.text.substring(it.start, it.end) })
        assertEquals("https://a.no/?a=1&b=2", (links[1].mark as HtmlMark.Link).url)
    }

    @Test fun mellomromHoererIkkeTilKoden() =
        assertEquals(listOf("x"), marked("<p>a <code>x</code></p>", HtmlMark.Code))

    @Test fun entiteter() = assertEquals("«a» & 'b' — é", text("<p>&laquo;a&raquo; &amp; &#39;b&#39; &mdash; &#xe9;</p>"))

    @Test fun ukjentEntitetBeholdes() = assertEquals("&foo; a", text("&foo; a"))

    @Test fun rentTekstUtenTagger() = assertEquals("hei der", text("hei der"))

    @Test fun ingenTomLinjeFoerst() = assertEquals("a", text("<p></p><br><p>a</p>"))

    @Test fun tomLinjeMedDobbelBr() = assertEquals("a\n\nb", text("<p>a<br><br>b</p>"))

    @Test fun bildeGirAlternativTekst() = assertEquals("se bilde", text("<p>se <img src=\"x.png\" alt=\"bilde\"></p>"))

    @Test fun avsnittsgrenserErstatterLinjeskift() {
        val p = parseHtml("<p>før</p><ul><li>en</li><li><b>to</b></li></ul><p>etter</p>").withParagraphBreaks()
        assertEquals("før\n•\u2002en•\u2002to\netter", p.text)
        assertEquals(listOf("•\u2002en", "•\u2002to"), p.indents.map { p.text.substring(it.start, it.end) })
        assertEquals(listOf("to"), p.marks.filter { it.mark == HtmlMark.Bold }.map { p.text.substring(it.start, it.end) })
    }

    @Test fun toppnivaaPunktFaarInnrykk() {
        val p = parseHtml("<ul><li>a</li></ul>")
        assertTrue(p.indents.single().first > 0f)
    }
}
