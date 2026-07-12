package com.kanxi.app.data

class DuplicateOperaIdentityException(val existingItemId: Long) :
    IllegalArgumentException("Another opera already uses this BVID and part (item $existingItemId)")

sealed interface SaveTarget {
    data object Insert : SaveTarget

    data class Update(val itemId: Long, val wasDuplicateShare: Boolean) : SaveTarget
}

sealed interface CategoryDeletionPlan {
    data object ProtectFallback : CategoryDeletionPlan

    data class MigrateAndDelete(val targetId: Long, val fallbackId: Long) : CategoryDeletionPlan
}

/** Pure, JVM-testable rules shared by Room transactions and the UI-facing repository. */
object OperaDataRules {
    private val bvidRegex = Regex("^BV[0-9A-Za-z]{10}$")

    fun normalizeBvid(raw: String): String {
        val trimmed = raw.trim()
        val normalized = if (trimmed.startsWith("bv", ignoreCase = true)) {
            "BV${trimmed.drop(2)}"
        } else {
            trimmed
        }
        require(bvidRegex.matches(normalized)) { "Invalid Bilibili BVID" }
        return normalized
    }

    fun normalizePart(part: Int): Int {
        require(part >= 1) { "Bilibili part must be at least 1" }
        return part
    }

    fun normalizeItem(item: OperaItem): OperaItem {
        val title = item.title.trim()
        require(title.isNotEmpty()) { "Opera title cannot be blank" }
        require(item.categoryId > 0) { "A valid category is required" }
        return item.copy(
            title = title,
            actors = item.actors.trim(),
            troupe = item.troupe.trim(),
            aliases = item.aliases.trim(),
            notes = item.notes.trim(),
            bvid = normalizeBvid(item.bvid),
            part = normalizePart(item.part),
            coverPath = item.coverPath?.trim()?.ifEmpty { null },
        )
    }

    fun resolveSaveTarget(incomingId: Long, existingIdentityId: Long?): SaveTarget = when {
        incomingId == 0L && existingIdentityId == null -> SaveTarget.Insert
        incomingId == 0L -> SaveTarget.Update(
            itemId = requireNotNull(existingIdentityId),
            wasDuplicateShare = true,
        )

        existingIdentityId == null || incomingId == existingIdentityId -> SaveTarget.Update(
            itemId = incomingId,
            wasDuplicateShare = false,
        )

        else -> throw DuplicateOperaIdentityException(existingIdentityId)
    }

    fun planCategoryDeletion(targetId: Long, fallbackId: Long): CategoryDeletionPlan =
        if (targetId == fallbackId) {
            CategoryDeletionPlan.ProtectFallback
        } else {
            CategoryDeletionPlan.MigrateAndDelete(targetId, fallbackId)
        }

    fun nextFavoriteState(currentlyFavorite: Boolean): Boolean = !currentlyFavorite

    fun nextHistory(existing: ViewHistory?, itemId: Long, openedAt: Long): ViewHistory {
        require(itemId > 0) { "A persisted opera item is required" }
        return ViewHistory(
            itemId = itemId,
            lastOpenedAt = openedAt,
            openCount = (existing?.openCount ?: 0).coerceAtLeast(0) + 1,
        )
    }

    /** Produces a literal-substring LIKE pattern instead of treating user input as SQL wildcards. */
    fun toLikePattern(query: String): String = buildString {
        append('%')
        query.trim().forEach { character ->
            if (character == '\\' || character == '%' || character == '_') append('\\')
            append(character)
        }
        append('%')
    }

    fun requireExactOrder(actualIds: List<Long>, orderedIds: List<Long>) {
        require(actualIds.size == orderedIds.size && actualIds.toSet() == orderedIds.toSet()) {
            "Reordering must include every item exactly once"
        }
    }
}

/** Mirrors the Room search semantics for fast local previews and JVM tests. */
object SearchMatcher {
    fun matches(opera: OperaWithCategory, query: String): Boolean {
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return listOf(
            opera.item.title,
            opera.categoryName,
            opera.item.actors,
            opera.item.troupe,
            opera.item.aliases,
        ).any { it.contains(needle, ignoreCase = true) }
    }
}
