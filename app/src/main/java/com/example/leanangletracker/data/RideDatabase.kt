package com.example.leanangletracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RideEntity::class, TrackPointEntity::class], version = 3, exportSchema = false)
abstract class RideDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao

    companion object {
        @Volatile
        private var INSTANCE: RideDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN isFinished INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN accumulatedTimeMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rides ADD COLUMN trackLengthMeters REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rides ADD COLUMN maxLeftDeg REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rides ADD COLUMN maxRightDeg REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rides ADD COLUMN sumSpeedKmh REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rides ADD COLUMN sumAbsLeanDeg REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rides ADD COLUMN pointCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): RideDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RideDatabase::class.java,
                    "ride_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
