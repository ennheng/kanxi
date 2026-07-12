package com.kanxi.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

const val OTHER_CATEGORY_NAME = "其他"

object DefaultOperaCategories {
    val names: List<String> = listOf(
        "京剧",
        "越剧",
        // Keep the permanent migration target last so every custom category can be removed safely.
        OTHER_CATEGORY_NAME,
    )
}

@Entity(
    tableName = "opera_categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class OperaCategory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = UNSORTED,
    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean = false,
) {
    companion object {
        const val UNSORTED = -1
    }
}

@Entity(
    tableName = "opera_items",
    foreignKeys = [
        ForeignKey(
            entity = OperaCategory::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["bvid", "part"], unique = true),
    ],
)
data class OperaItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    val actors: String = "",
    val troupe: String = "",
    val aliases: String = "",
    val notes: String = "",
    val bvid: String,
    val part: Int = 1,
    /** Absolute path of a cover copied into the app's private storage. */
    @ColumnInfo(name = "cover_path")
    val coverPath: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = UNSORTED,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = createdAt,
) {
    companion object {
        const val UNSORTED = -1
    }
}

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = OperaItem::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["favorited_at"])],
)
data class Favorite(
    @PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "favorited_at")
    val favoritedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "view_history",
    foreignKeys = [
        ForeignKey(
            entity = OperaItem::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["last_opened_at"])],
)
data class ViewHistory(
    @PrimaryKey
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAt: Long,
    @ColumnInfo(name = "open_count")
    val openCount: Int,
)

@Entity(
    tableName = "opera_collections",
    indices = [Index(value = ["name"], unique = true)],
)
data class OperaCollection(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = UNSORTED,
) {
    companion object {
        const val UNSORTED = -1
    }
}

@Entity(
    tableName = "collection_items",
    primaryKeys = ["collection_id", "item_id"],
    foreignKeys = [
        ForeignKey(
            entity = OperaCollection::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OperaItem::class,
            parentColumns = ["id"],
            childColumns = ["item_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["collection_id"]),
        Index(value = ["item_id"]),
    ],
)
data class CollectionItem(
    @ColumnInfo(name = "collection_id")
    val collectionId: Long,
    @ColumnInfo(name = "item_id")
    val itemId: Long,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = UNSORTED,
) {
    companion object {
        const val UNSORTED = -1
    }
}

/** A collection together with its member operas, used by the home and detail screens. */
data class CollectionWithItems(
    @Embedded
    val collection: OperaCollection,
    @ColumnInfo(name = "item_count")
    val itemCount: Int,
)

/** Projection used by home, category, search, detail and favorites screens. */
data class OperaWithCategory(
    @Embedded
    val item: OperaItem,
    @ColumnInfo(name = "category_name")
    val categoryName: String,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean,
)

/** Projection for the "recently opened" section. This is not playback progress. */
data class RecentOpera(
    @Embedded
    val item: OperaItem,
    @ColumnInfo(name = "category_name")
    val categoryName: String,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean,
    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAt: Long,
    @ColumnInfo(name = "open_count")
    val openCount: Int,
)

sealed interface SaveOperaResult {
    val itemId: Long

    data class Created(override val itemId: Long) : SaveOperaResult

    data class Updated(override val itemId: Long) : SaveOperaResult

    /** A newly shared link matched an existing BVID + part and edited that row. */
    data class UpdatedExistingDuplicate(override val itemId: Long) : SaveOperaResult
}

sealed interface DeleteCategoryResult {
    data object NotFound : DeleteCategoryResult

    /** "其他" is the permanent fallback and cannot itself be deleted. */
    data object ProtectedFallback : DeleteCategoryResult

    data class Deleted(val movedItemCount: Int) : DeleteCategoryResult
}

/** Result of importing multiple parts at once from a multi-part video. */
data class BulkSaveResult(
    val createdCount: Int,
    val skippedCount: Int,
)
