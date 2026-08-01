package com.cydoniancitizen.bingee.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaSearchQueryTest {
    @Test
    fun normalizationTrimsEdgesAndPreservesInternalInput() {
        val query =
            MediaSearchQuery.from(
                "  Star   Wars  ",
                MediaSearchCategory.MOVIES
            )

        assertEquals("Star   Wars", query?.query)
        assertEquals("en-US", query?.language)
        assertEquals(1, query?.page)
        assertNull(MediaSearchQuery.from("   ", MediaSearchCategory.TV_SERIES))
    }

    @Test
    fun documentedPageBoundsAreEnforced() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaSearchQuery("fixed", MediaSearchCategory.MOVIES, page = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MediaSearchQuery("fixed", MediaSearchCategory.MOVIES, page = 501)
        }
    }
}
