package com.cydoniancitizen.bingee.core.model

enum class MediaSearchCategory {
    MOVIES,
    TV_SERIES,
    ANIME
}

data class MediaSearchQuery(
    val query: String,
    val category: MediaSearchCategory,
    val page: Int = FIRST_PAGE,
    val language: String = DEFAULT_LANGUAGE
) {
    init {
        require(query.isNotBlank() && query == query.trim()) {
            "Search query must be non-blank and normalized"
        }
        require(page in FIRST_PAGE..MAX_PAGE) { "Search page must be between 1 and 500" }
        require(LANGUAGE_TAG.matches(language)) { "Search language must be a language tag" }
    }

    companion object {
        const val FIRST_PAGE = 1
        const val MAX_PAGE = 500
        const val DEFAULT_LANGUAGE = "en-US"

        private val LANGUAGE_TAG = Regex("[A-Za-z]{2,3}(?:-[A-Za-z]{2}|-[0-9]{3})?")

        fun from(
            input: String,
            category: MediaSearchCategory,
            page: Int = FIRST_PAGE,
            language: String = DEFAULT_LANGUAGE
        ): MediaSearchQuery? {
            val normalized = input.trim()
            return if (normalized.isEmpty()) null else MediaSearchQuery(normalized, category, page, language)
        }
    }
}

data class MediaSearchPage(
    val results: List<MediaSearchResult>,
    val page: Int,
    val totalPages: Int,
    val totalResults: Int
) {
    init {
        require(page in MediaSearchQuery.FIRST_PAGE..MediaSearchQuery.MAX_PAGE)
        require(totalPages in page..MediaSearchQuery.MAX_PAGE)
        require(totalResults >= 0)
    }
}
