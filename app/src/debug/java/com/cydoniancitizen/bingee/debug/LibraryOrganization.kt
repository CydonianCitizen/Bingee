package com.cydoniancitizen.bingee.debug

import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.LibraryQuery
import com.cydoniancitizen.bingee.core.model.applyLibraryStateAndSort
import com.cydoniancitizen.bingee.core.model.normalizeLibrarySearch
import java.util.Locale

/** In-memory search for the debug repository; production filters through Room. */
internal fun organizeLibraryEntries(entries: List<LibraryEntry>, query: LibraryQuery): List<LibraryEntry> {
    val locallyMatched = entries.filter {
        (query.mediaFilter.mediaType == null || it.mediaType == query.mediaFilter.mediaType) &&
            it.matchesLibrarySearch(query.searchQuery)
    }
    return applyLibraryStateAndSort(locallyMatched, query)
}

private fun LibraryEntry.matchesLibrarySearch(query: String): Boolean {
    val normalized = normalizeLibrarySearch(query)
    if (normalized.isEmpty()) return true
    return title.lowercase(Locale.ROOT).contains(normalized) ||
        originalTitle?.lowercase(Locale.ROOT)?.contains(normalized) == true
}
