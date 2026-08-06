package com.cydoniancitizen.bingee.domain.equivalence

import java.text.Normalizer
import java.util.Locale

object MediaEquivalenceNormalizer {
    private val PUNCTUATION_PATTERN = Regex("""[^\p{L}\p{N}\s]""")
    private val WHITESPACE_PATTERN = Regex("""\s+""")

    fun normalizeTitle(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val normalizedNfkc = Normalizer.normalize(input, Normalizer.Form.NFKC)
        val lowercased = normalizedNfkc.lowercase(Locale.ROOT)
        val withoutAccents = Normalizer.normalize(lowercased, Normalizer.Form.NFD)
            .replace(Regex("""\p{M}"""), "")
        val withoutPunctuation = PUNCTUATION_PATTERN.replace(withoutAccents, " ")
        return WHITESPACE_PATTERN.replace(withoutPunctuation, " ").trim()
    }
}
