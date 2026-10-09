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
<<<<<<< HEAD
    version = 8,
=======
    version = 7,
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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

<<<<<<< HEAD
        /**
         * v8: playlist add-order, and repair YouTube identity URIs where the video id is known.
         * Duplicate / unrepairable rows are cleaned in [AudioRepository.cleanInvalidData].
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE playlist_track_cross_ref ADD COLUMN position INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    UPDATE playlist_track_cross_ref SET position = (
                        SELECT COUNT(*) - 1 FROM playlist_track_cross_ref AS other
                        WHERE other.playlistId = playlist_track_cross_ref.playlistId
                          AND other.rowid <= playlist_track_cross_ref.rowid
                    )
                    """.trimIndent()
                )
                // Unique uri collisions are skipped by SQLite; remaining rows are repaired in Kotlin.
                db.execSQL(
                    """
                    UPDATE audio_tracks
                    SET uri = 'youtube://' || substr(category, 4)
                    WHERE category LIKE 'yt:%'
                      AND length(substr(category, 4)) >= 8
                      AND (uri IS NULL OR uri = '' OR uri NOT LIKE 'youtube://%')
                      AND NOT EXISTS (
                          SELECT 1 FROM audio_tracks AS existing
                          WHERE existing.uri = 'youtube://' || substr(audio_tracks.category, 4)
                            AND existing.id != audio_tracks.id
                      )
                    """.trimIndent()
                )
            }
        }

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
=======
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
