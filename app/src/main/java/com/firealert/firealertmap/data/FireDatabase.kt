package com.firealert.firealertmap.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FireReportLocal::class], version = 1, exportSchema = false)
abstract class FireDatabase : RoomDatabase() {
    abstract fun fireDao(): FireDao
}