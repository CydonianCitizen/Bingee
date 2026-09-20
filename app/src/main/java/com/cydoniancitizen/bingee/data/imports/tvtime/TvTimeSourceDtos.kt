package com.cydoniancitizen.bingee.data.imports.tvtime

internal data class TvTimeListDto(val items: List<TvTimeListItemDto>)

internal data class TvTimeListItemDto(val type: String, val tvdbId: Long?, val uuid: String?)

internal data class TvTimeSourceIdsDto(val imdb: String?, val tvdb: Long?)
