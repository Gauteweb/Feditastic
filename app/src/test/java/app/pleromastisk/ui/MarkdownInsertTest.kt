package app.pleromastisk.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownInsertTest {
    @Test fun fetUtenMarkeringSetterMarkoerenMidtI() =
        assertEquals(MdEdit("hei ****", 6, 6), wrapMarkdown("hei ", 4, 4, "**"))

    @Test fun fetOmslutterMarkering() =
        assertEquals(MdEdit("en **to** tre", 5, 7), wrapMarkdown("en to tre", 3, 5, "**"))

    @Test fun kodeMedBaklengsMarkering() =
        assertEquals(MdEdit("`ab`", 1, 3), wrapMarkdown("ab", 2, 0, "`"))

    @Test fun overskriftFoerstPaaLinjen() =
        assertEquals(MdEdit("a\n# tittel", 9, 9), prefixLines("a\ntittel", 7, 7, "# "))

    @Test fun nyttTrykkGirMindreOverskrift() =
        assertEquals(MdEdit("## tittel", 4, 4), prefixLines("# tittel", 3, 3, "# "))

    @Test fun sitatPaaFlereLinjer() =
        assertEquals(MdEdit("> a\n> b\nc", 2, 7), prefixLines("a\nb\nc", 0, 3, "> "))

    @Test fun sitatITomTekst() = assertEquals(MdEdit("> ", 2, 2), prefixLines("", 0, 0, "> "))
}
