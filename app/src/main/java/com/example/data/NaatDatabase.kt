package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [NaatEntity::class], version = 1, exportSchema = false)
abstract class NaatDatabase : RoomDatabase() {
    abstract fun naatDao(): NaatDao

    companion object {
        @Volatile
        private var INSTANCE: NaatDatabase? = null

        fun getDatabase(context: Context): NaatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NaatDatabase::class.java,
                    "naat_notebook_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
