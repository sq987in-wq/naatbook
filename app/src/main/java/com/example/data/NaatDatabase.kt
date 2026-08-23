package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NaatEntity::class], version = 2, exportSchema = false)
abstract class NaatDatabase : RoomDatabase() {

    abstract fun naatDao(): NaatDao

    companion object {
        @Volatile
        private var INSTANCE: NaatDatabase? = null

        /**
         * v1 -> v2: category/taxonomy restructure. The schema is untouched
         * (category is a plain String column); only the stored values are
         * remapped onto the new taxonomy and the "Audio Only" pseudo-folder
         * disappears. Registering this Migration explicitly is what prevents
         * fallbackToDestructiveMigration from wiping user data on upgrade.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE naats SET category = 'Naat' WHERE category = 'Hamd-o-Naat'")
                db.execSQL("UPDATE naats SET category = 'Salam' WHERE category = 'Salam & Qasida'")
                db.execSQL("UPDATE naats SET category = 'My Kalam' WHERE category = 'My Own Poetry'")
                db.execSQL("UPDATE naats SET category = 'My Kalam' WHERE category = 'Audio Only'")
                // Safety net: any other stale/unknown label lands in Others.
                db.execSQL(
                    "UPDATE naats SET category = 'Others' WHERE category NOT IN " +
                        "('Naat', 'Hamd', 'Manqabat', 'Salam', 'Qasida', 'Nasheed', 'My Kalam', 'Others')"
                )
            }
        }

        fun getDatabase(context: Context): NaatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NaatDatabase::class.java,
                    "naat_notebook_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
