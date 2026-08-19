package com.example

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AudioTrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRefEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AudioDatabase : RoomDatabase() {
    abstract fun audioDao(): AudioDao

    companion object {
        @Volatile
        private var INSTANCE: AudioDatabase? = null

        /** Adds the 4 new columns introduced in v6. Uses ALTER TABLE so existing data is preserved. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN lrcLyrics TEXT")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN bpm REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN replayGainDb REAL NOT NULL DEFAULT 0.0")
            }
        }

        /** Adds mood column introduced in v7. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_tracks ADD COLUMN mood TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val baseContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.applicationContext.createAttributionContext("audio_player")
                } else {
                    context.applicationContext
                }
                val instance = Room.databaseBuilder(
                    baseContext,
                    AudioDatabase::class.java,
                    "audio_player_database"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
