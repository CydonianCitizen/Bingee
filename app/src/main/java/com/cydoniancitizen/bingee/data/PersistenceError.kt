package com.cydoniancitizen.bingee.data

import android.database.sqlite.SQLiteException
import com.cydoniancitizen.bingee.core.result.AppError

/** Maps a failure from a local Room write or read to the error the UI reports. */
internal fun Throwable.toPersistenceError(): AppError = when (this) {
    is IllegalArgumentException,
    is IllegalStateException -> AppError.CorruptedData
    is SQLiteException -> AppError.LocalStorageFailure
    else -> AppError.Unknown
}
