package com.cydoniancitizen.bingee.data.library.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MediaEntity::class, ExternalRefEntity::class, LibraryMembershipEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
internal abstract class BingeeDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val DATABASE_NAME = "bingee.db"
    }
}
