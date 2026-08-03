package com.cydoniancitizen.bingee.data.tmdb.search

import com.cydoniancitizen.bingee.data.tmdb.TmdbImageUrlResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbImageUrlResolverTest {
    @Test
    fun resolvesOnlySupportedPathsAtListSize() {
        val url = TmdbImageUrlResolver.listPoster("/poster.webp")

        assertEquals("https://image.tmdb.org/t/p/w342/poster.webp", url)
        assertFalse(requireNotNull(url).contains("/original/"))
        assertNull(TmdbImageUrlResolver.listPoster(null))
        assertNull(TmdbImageUrlResolver.listPoster("https://example.com/poster.jpg"))
        assertNull(TmdbImageUrlResolver.listPoster("/poster.svg"))
        assertNull(TmdbImageUrlResolver.listPoster("/../poster.jpg"))
    }
}
