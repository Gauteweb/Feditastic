package app.pleromastisk.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HashtagTest {
    @Test fun mastodonLenke() = assertEquals("NRKno", hashtagFromLink("https://mastodon.social/tags/NRKno", "#NRKno"))
    @Test fun pleromaLenke() = assertEquals("linux", hashtagFromLink("https://social.gaute.space/tag/linux", "#linux"))
    @Test fun pixelfedLenke() = assertEquals("art", hashtagFromLink("https://pixelfed.social/discover/tags/art?x=1", "#art"))
    @Test fun prosentkodetNavn() = assertEquals("høst", hashtagFromLink("https://x.org/tags/h%C3%B8st", "#høst"))
    @Test fun tekstUtenHashErIkkeEmneknagg() = assertNull(hashtagFromLink("https://x.org/tags/foo", "se her"))
    @Test fun vanligLenkeMedHashErIkkeEmneknagg() = assertNull(hashtagFromLink("https://x.org/users/bob", "#bob"))
}

class HashtagsInTest {
    private val html = """<p>Fin dag på <a href="https://x/tag/fjellet" class="mention hashtag">#<span>Fjellet</span></a> og """ +
        """<a href="https://x/tag/tur">#<span>tur</span></a>! Se https://x.no/side#anker og nr #1 &#39;hei&#39;</p>"""

    @org.junit.Test fun iTekstrekkefoelgeMedForfattersSkrivemaate() =
        org.junit.Assert.assertEquals(listOf("Fjellet", "tur"), hashtagsIn(html, listOf("tur", "fjellet")))

    @org.junit.Test fun utenInstansensListeBrukesTeksten() =
        org.junit.Assert.assertEquals(listOf("Fjellet", "tur"), hashtagsIn(html, emptyList()))

    @org.junit.Test fun knaggerBareIListenKommerSist() =
        org.junit.Assert.assertEquals(listOf("Fjellet", "tur", "skjult"), hashtagsIn(html, listOf("fjellet", "tur", "skjult")))
}
