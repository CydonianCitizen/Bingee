package com.cydoniancitizen.bingee.data.library.local

import androidx.room.TypeConverter
import com.cydoniancitizen.bingee.core.model.AnimeCompletionOrigin
import com.cydoniancitizen.bingee.core.model.AnimeFormat
import com.cydoniancitizen.bingee.core.model.AnimeStatus
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.model.MediaType
import java.time.Instant
import java.time.LocalDate

internal class RoomConverters {
    @TypeConverter
    fun mediaSourceToString(value: MediaSource): String = value.name

    @TypeConverter
    fun stringToMediaSource(value: String): MediaSource = MediaSource.valueOf(value)

    @TypeConverter
    fun mediaTypeToString(value: MediaType): String = value.name

    @TypeConverter
    fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter
    fun animeFormatToString(value: AnimeFormat): String = value.name

    @TypeConverter
    fun stringToAnimeFormat(value: String): AnimeFormat = AnimeFormat.valueOf(value)

    @TypeConverter
    fun animeStatusToString(value: AnimeStatus): String = value.name

    @TypeConverter
    fun stringToAnimeStatus(value: String): AnimeStatus = AnimeStatus.valueOf(value)

    @TypeConverter
    fun completionOriginToString(value: AnimeCompletionOrigin?): String? = value?.name

    @TypeConverter
    fun stringToCompletionOrigin(value: String?): AnimeCompletionOrigin? = value?.let(AnimeCompletionOrigin::valueOf)

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun instantToString(value: Instant): String = value.toString()

    @TypeConverter
    fun stringToInstant(value: String): Instant = Instant.parse(value)
}
