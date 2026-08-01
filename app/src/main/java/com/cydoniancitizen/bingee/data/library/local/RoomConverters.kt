package com.cydoniancitizen.bingee.data.library.local

import androidx.room.TypeConverter
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
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun instantToString(value: Instant): String = value.toString()

    @TypeConverter
    fun stringToInstant(value: String): Instant = Instant.parse(value)
}
