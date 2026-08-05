package com.cydoniancitizen.bingee.data.jikan

import java.time.LocalDate

internal fun String?.normalizedJikanText(stripHtml: Boolean = false): String? {
    val normalized = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return if (stripHtml) {
        normalized.replace(HTML_TAGS, " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(WHITESPACE, " ").trim()
    } else {
        normalized
    }
}

internal fun String?.jikanDate(): LocalDate? =
    normalizedJikanText()?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private val HTML_TAGS = Regex("<[^>]*>")
private val WHITESPACE = Regex("\\s+")
