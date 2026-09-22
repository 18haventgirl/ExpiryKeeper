package com.expirykeeper.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Item::class, ChangeLogEntry::class, ItemEvent::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun changeLogDao(): ChangeLogDao
    abstract fun eventDao(): EventDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN emoji TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN openedAtEpochDay INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN shelfLifeDays INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN handledAtEpochDay INTEGER")
                db.execSQL("ALTER TABLE items ADD COLUMN handledStatus TEXT")
                db.execSQL("ALTER TABLE items ADD COLUMN snoozedUntilEpochDay INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `itemId` TEXT NOT NULL, `kind` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }
    }
}
