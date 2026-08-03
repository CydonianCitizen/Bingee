package com.cydoniancitizen.bingee.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchPatternTest {
    @Test
    fun trimsNormalizesAndEscapesSqlLikeWildcards() {
        assertEquals("%", "   ".toSqlLikePattern())
        assertEquals("%arrival%", " Arrival ".toSqlLikePattern())
        assertEquals("%100\\% real\\_story%", "100% Real_Story".toSqlLikePattern())
        assertEquals("%a\\\\b%", "a\\b".toSqlLikePattern())
        assertEquals("%director's%", "Director's".toSqlLikePattern())
    }
}
