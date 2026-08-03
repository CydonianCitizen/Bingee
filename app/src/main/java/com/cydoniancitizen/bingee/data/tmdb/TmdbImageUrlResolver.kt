package com.cydoniancitizen.bingee.data.tmdb

internal object TmdbImageUrlResolver {
    private const val BASE_URL = "https://image.tmdb.org/t/p/"
    private const val LIST_POSTER_SIZE = "w342"
    private const val DETAIL_POSTER_SIZE = "w500"
    private const val DETAIL_BACKDROP_SIZE = "w780"
    private val supportedPath = Regex("/[A-Za-z0-9._-]+\\.(?:jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE)

    fun listPoster(path: String?): String? = resolve(path, LIST_POSTER_SIZE)

    fun detailPoster(path: String?): String? = resolve(path, DETAIL_POSTER_SIZE)

    fun detailBackdrop(path: String?): String? = resolve(path, DETAIL_BACKDROP_SIZE)

    private fun resolve(path: String?, size: String): String? {
        val normalized = path?.trim()?.takeIf(supportedPath::matches) ?: return null
        return "$BASE_URL$size$normalized"
    }
}
