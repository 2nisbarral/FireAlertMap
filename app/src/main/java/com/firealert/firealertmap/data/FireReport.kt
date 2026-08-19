package com.firealert.firealertmap.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fire_reports")
data class FireReportLocal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)