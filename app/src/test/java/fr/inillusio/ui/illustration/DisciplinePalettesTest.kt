package fr.inillusio.ui.illustration

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DisciplinePalettesTest {
    @Test fun canonicalStylesheetDefinesAllOfficialPairs() {
        val css = File("../data/discipline-palettes.css").readText()
        val palettes = DisciplinePalettes.parseCss(css)
        val expected = mapOf(
            "sociologie" to (0xFFFF6347.toInt() to 0xFF00A995.toInt()),
            "anthropologie" to (0xFFC16E4B.toInt() to 0xFF745574.toInt()),
            "psychologie_sociale" to (0xFFC65385.toInt() to 0xFF628CB5.toInt()),
            "histoire" to (0xFFA17BFF.toInt() to 0xFF253A94.toInt()),
            "linguistique" to (0xFF426FC7.toInt() to 0xFFFFE000.toInt()),
            "economie" to (0xFFB2DC25.toInt() to 0xFFDB4939.toInt()),
            "philosophie" to (0xFF583D65.toInt() to 0xFFD8BE91.toInt()),
            "geographie_humaine" to (0xFF3E7859.toInt() to 0xFFF29AB1.toInt()),
            "ethologie" to (0xFF79513E.toInt() to 0xFF76A46C.toInt()),
            "archeologie" to (0xFFE58A3C.toInt() to 0xFF654433.toInt()),
            "science_politique" to (0xFF193F60.toInt() to 0xFFD36A53.toInt()),
            "psychologie_cognitive" to (0xFFC43F78.toInt() to 0xFF275844.toInt()),
            "demographie" to (0xFF2583BA.toInt() to 0xFF79B77A.toInt()),
            "histoire_art" to (0xFF953F53.toInt() to 0xFFD2A65A.toInt())
        )

        assertEquals(expected.keys, palettes.keys)
        expected.forEach { (category, pair) ->
            assertEquals("$category primary", pair.first, palettes.getValue(category).primary)
            assertEquals("$category secondary", pair.second, palettes.getValue(category).secondary)
        }
    }

    @Test fun incompletePaletteIsIgnored() {
        assertEquals(emptyMap<String, DisciplinePalette>(), DisciplinePalettes.parseCss(":root { --discipline-histoire-primary: #C49435; }"))
    }
}
