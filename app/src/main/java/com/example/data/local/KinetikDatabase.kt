package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KinetikDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: KinetikDatabase? = null

        fun getInstance(context: Context): KinetikDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KinetikDatabase::class.java,
                    "kinetik_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
