package com.cydoniancitizen.bingee.feature.library

import com.cydoniancitizen.bingee.core.model.LibraryEntry
import com.cydoniancitizen.bingee.core.model.MediaLinkGroup

sealed interface LibraryPresentationItem {
    val keyString: String

    data class Standalone(val entry: LibraryEntry) : LibraryPresentationItem {
        override val keyString: String = "standalone_${entry.mediaRef.source.name}_${entry.mediaRef.externalId}"
    }

    data class LinkedGroup(val group: MediaLinkGroup, val displayEntry: LibraryEntry, val members: List<LibraryEntry>) :
        LibraryPresentationItem {
        override val keyString: String = "group_${group.groupId.value}"
    }
}
