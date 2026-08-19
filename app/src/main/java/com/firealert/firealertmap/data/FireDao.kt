package com.firealert.firealertmap.data

import androidx.room.*

@Dao
interface FireDao {
    @Query("SELECT * FROM fire_reports ORDER BY timestamp DESC")
    suspend fun getAll(): List<FireReportLocal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: FireReportLocal)

    @Delete
    suspend fun delete(report: FireReportLocal)
}