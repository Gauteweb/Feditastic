package app.pleromastisk.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MentionCompleteTest {
    @Test fun starterMedAt() = assertEquals(MentionQuery(0, 4, "gau"), mentionQueryAt("@gau", 4))
    @Test fun midtISetning() = assertEquals(MentionQuery(4, 8, "bob"), mentionQueryAt("Hei @bob takk", 8))
    @Test fun medDomene() = assertEquals(MentionQuery(0, 9, "bob@mast"), mentionQueryAt("@bob@mast", 9))
    @Test fun markoerMidtIOrdet() = assertEquals(MentionQuery(0, 6, "bo"), mentionQueryAt("@bobby", 3))
    @Test fun etterParentes() = assertEquals(MentionQuery(1, 4, "al"), mentionQueryAt("(@al", 4))
    @Test fun bareAtGirIngenting() = assertNull(mentionQueryAt("Hei @", 5))
    @Test fun epostGirIngenting() = assertNull(mentionQueryAt("meg@eksempel", 12))
    @Test fun etterMellomromGirIngenting() = assertNull(mentionQueryAt("@bob ", 5))
    @Test fun markertTekstGirIngenting() = assertNull(mentionQueryAt("@bob", 2, 4))

    @Test fun setterInnAcct() {
        val v = TextFieldValue("Hei @bo", TextRange(7))
        val r = insertMention(v, mentionQueryAt(v.text, 7)!!, "bob@mastodon.social")
        assertEquals("Hei @bob@mastodon.social ", r.text)
        assertEquals(TextRange(r.text.length), r.selection)
    }

    @Test fun erstatterHeleOrdetOgGjenbrukerMellomrom() {
        val v = TextFieldValue("@bo takk", TextRange(2))
        val r = insertMention(v, mentionQueryAt(v.text, 2)!!, "bob")
        assertEquals("@bob takk", r.text)
        assertEquals(TextRange(5), r.selection)
    }
}

class ReplyDraftTest {
    @Test fun utenKnaggerMarkoerPaaLinjenUnder() {
        val d = replyDraft("bob alice@x.org", null)
        assertEquals("@bob @alice@x.org\n", d.text)
        assertEquals(TextRange(d.text.length), d.selection)
    }

    @Test fun medKnaggerMarkoerPaaLinjenUnder() {
        val d = replyDraft("bob", "Fjellet tur")
        assertEquals("@bob\n\n\n#Fjellet #tur", d.text)
        assertEquals(TextRange(5), d.selection)
    }

    @Test fun ingentingGirTomTekst() {
        val d = replyDraft(null, null)
        assertEquals("", d.text)
        assertEquals(TextRange(0), d.selection)
    }

    @Test fun bareKnagger() {
        val d = replyDraft("", "tur")
        assertEquals("\n\n#tur", d.text)
        assertEquals(TextRange(0), d.selection)
    }
}
