package com.cydoniancitizen.bingee.data.imports.tvtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvTimeArchiveSafetyTest {
    @Test
    fun acceptsRelativeDocumentEntry() {
        assertNull(validateTvTimeArchiveEntryName("data/movies.json"))
    }

    @Test
    fun rejectsTraversalAbsoluteDriveUncAndNullPaths() {
        assertEquals(TvTimeArchiveFailureKind.PATH_TRAVERSAL, validateTvTimeArchiveEntryName("../movies.json"))
        assertEquals(TvTimeArchiveFailureKind.ABSOLUTE_PATH, validateTvTimeArchiveEntryName("/movies.json"))
        assertEquals(TvTimeArchiveFailureKind.DRIVE_PATH, validateTvTimeArchiveEntryName("C:/movies.json"))
        assertEquals(TvTimeArchiveFailureKind.DRIVE_PATH, validateTvTimeArchiveEntryName("C:movies.json"))
        assertEquals(
            TvTimeArchiveFailureKind.UNC_PATH,
            validateTvTimeArchiveEntryName("\\\\server\\share\\movies.json")
        )
        assertEquals(TvTimeArchiveFailureKind.NULL_BYTE_PATH, validateTvTimeArchiveEntryName("movies\u0000.json"))
    }
}
