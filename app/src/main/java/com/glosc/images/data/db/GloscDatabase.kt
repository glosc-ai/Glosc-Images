package com.glosc.images.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ImageAssetEntity::class,
        GenerationTaskEntity::class,
        ApiProviderEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        TagEntity::class,
        CategoryEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class GloscDatabase : RoomDatabase() {
    abstract fun dao(): GloscDao

    companion object {
        @Volatile private var instance: GloscDatabase? = null

        fun get(context: Context): GloscDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GloscDatabase::class.java,
                    "glosc-images.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE api_providers ADD COLUMN imageModels TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
