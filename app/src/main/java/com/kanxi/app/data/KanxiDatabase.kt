package com.kanxi.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        OperaCategory::class,
        OperaItem::class,
        Favorite::class,
        ViewHistory::class,
        OperaCollection::class,
        CollectionItem::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class KanxiDatabase : RoomDatabase() {
    abstract fun operaDao(): OperaDao

    companion object {
        const val DATABASE_NAME = "kanxi.db"

        @Volatile
        private var instance: KanxiDatabase? = null

        fun create(context: Context): KanxiDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                KanxiDatabase::class.java,
                DATABASE_NAME,
            )
                .addCallback(SeedCategoriesCallback)
                .addMigrations(Migration1To2)
                .build()
                .also { instance = it }
        }

        internal val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS opera_collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        sort_order INTEGER NOT NULL DEFAULT -1
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_opera_collections_name
                    ON opera_collections(name)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collection_items (
                        collection_id INTEGER NOT NULL,
                        item_id INTEGER NOT NULL,
                        sort_order INTEGER NOT NULL DEFAULT -1,
                        PRIMARY KEY(collection_id, item_id),
                        FOREIGN KEY(collection_id) REFERENCES opera_collections(id)
                            ON UPDATE CASCADE ON DELETE CASCADE,
                        FOREIGN KEY(item_id) REFERENCES opera_items(id)
                            ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_collection_items_collection_id
                    ON collection_items(collection_id)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_collection_items_item_id
                    ON collection_items(item_id)
                    """.trimIndent(),
                )
            }
        }

        internal fun clearInstanceForTests() {
            instance = null
        }

        private object SeedCategoriesCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                DefaultOperaCategories.names.forEachIndexed { index, name ->
                    db.execSQL(
                        """
                        INSERT OR IGNORE INTO opera_categories(name, sort_order, is_built_in)
                        VALUES (?, ?, 1)
                        """.trimIndent(),
                        arrayOf<Any>(name, index),
                    )
                }
            }
        }
    }
}
