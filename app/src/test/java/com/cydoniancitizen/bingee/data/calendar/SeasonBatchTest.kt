package com.cydoniancitizen.bingee.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonBatchTest {
    @Test
    fun rotationHandlesChangedCatalogDuplicatesAndEmptySeasons() {
        assertEquals(listOf(0, 1), rotateSeasonBatch(listOf(3, 1, 0, 1), null))
        assertEquals(listOf(3, 0), rotateSeasonBatch(listOf(3, 1, 0), 2))
        assertEquals(listOf(0, 1), rotateSeasonBatch(listOf(0, 1), 99))
        assertEquals(listOf(0), rotateSeasonBatch(listOf(0), 0))
        assertEquals(emptyList<Int>(), rotateSeasonBatch(emptyList(), 4))
        assertEquals(listOf(9, 0), rotateSeasonBatch((0..9).toList(), null, 9))
        assertEquals(listOf(9, 1), rotateSeasonBatch((0..9).toList(), 0, 9))
        assertEquals(listOf(9), rotateSeasonBatch(listOf(9), 0, 9))
        assertEquals(listOf(0, 1), rotateSeasonBatch(listOf(0, 1), null, 9))
    }
}
