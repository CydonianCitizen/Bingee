package com.cydoniancitizen.bingee.data

import android.database.sqlite.SQLiteConstraintException
import com.cydoniancitizen.bingee.core.result.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

class PersistenceErrorTest {
    @Test
    fun persistenceFailuresKeepTheirSharedClassification() {
        assertEquals(AppError.CorruptedData, IllegalArgumentException().toPersistenceError())
        assertEquals(AppError.CorruptedData, IllegalStateException().toPersistenceError())
        assertEquals(AppError.LocalStorageFailure, SQLiteConstraintException().toPersistenceError())
        assertEquals(AppError.Unknown, RuntimeException().toPersistenceError())
    }
}
