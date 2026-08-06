package com.cydoniancitizen.bingee.domain.equivalence

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaEquivalenceNormalizerTest {

    @Test
    fun normalizeTitle_basicCaseAndWhitespace() {
        assertEquals("spirited away", MediaEquivalenceNormalizer.normalizeTitle("  Spirited   Away  "))
    }

    @Test
    fun normalizeTitle_punctuationStripping() {
        assertEquals(
            "attack on titan final season",
            MediaEquivalenceNormalizer.normalizeTitle("Attack on Titan: Final Season!")
        )
    }

    @Test
    fun normalizeTitle_unicodeAndAccents() {
        assertEquals(
            "pokemom the movie",
            MediaEquivalenceNormalizer.normalizeTitle("Pokémom: The Movie!")
        )
    }

    @Test
    fun normalizeTitle_japaneseCharacters() {
        assertEquals(
            "千と千尋の神隠し",
            MediaEquivalenceNormalizer.normalizeTitle("千と千尋の神隠し")
        )
    }

    @Test
    fun normalizeTitle_romanNumeralsAndDigitsPreserved() {
        assertEquals(
            "my hero academia 2 heroes",
            MediaEquivalenceNormalizer.normalizeTitle("My Hero Academia: 2 Heroes")
        )
        assertEquals(
            "part ii",
            MediaEquivalenceNormalizer.normalizeTitle("Part II")
        )
    }

    @Test
    fun normalizeTitle_seasonNumbersNotStripped() {
        assertEquals(
            "season 2",
            MediaEquivalenceNormalizer.normalizeTitle("Season 2")
        )
    }

    @Test
    fun normalizeTitle_emptyOrPunctuationOnly() {
        assertEquals("", MediaEquivalenceNormalizer.normalizeTitle(null))
        assertEquals("", MediaEquivalenceNormalizer.normalizeTitle(""))
        assertEquals("", MediaEquivalenceNormalizer.normalizeTitle("   "))
        assertEquals("", MediaEquivalenceNormalizer.normalizeTitle("!?:;---"))
    }

    @Test
    fun normalizeTitle_deterministicOutput() {
        val input = "Steins;Gate 0"
        val first = MediaEquivalenceNormalizer.normalizeTitle(input)
        val second = MediaEquivalenceNormalizer.normalizeTitle(input)
        assertEquals(first, second)
        assertEquals("steins gate 0", first)
    }
}
