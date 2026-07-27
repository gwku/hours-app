package com.gerwinkuijntjes.hours.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Client::class, Visit::class], version = 1, exportSchema = false)
abstract class HoursDatabase : RoomDatabase() {
    abstract fun hoursDao(): HoursDao

    companion object {
        @Volatile
        private var INSTANCE: HoursDatabase? = null

        fun getDatabase(context: Context): HoursDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HoursDatabase::class.java,
                    "hours_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
