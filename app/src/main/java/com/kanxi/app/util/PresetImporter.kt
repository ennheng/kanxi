package com.kanxi.app.util

import android.content.Context
import org.json.JSONObject

/** One item inside a built-in preset pack. */
data class PresetItem(
    val bvid: String,
    val part: Int,
    val title: String,
)

/** A built-in preset pack identified by a pinyin code. */
data class PresetPack(
    val name: String,
    val categoryName: String,
    val items: List<PresetItem>,
)

/** Result of importing a preset pack. */
data class PresetImportResult(
    val createdCount: Int,
    val skippedCount: Int,
    val collectionId: Long,
)

object PresetImporter {
    private const val PRESETS_ASSET = "presets.json"

    fun loadPacks(context: Context): Result<Map<String, PresetPack>> = runCatching {
        val json = context.assets.open(PRESETS_ASSET).use { it.reader().readText() }
        parsePacks(json)
    }

    internal fun parsePacks(json: String): Map<String, PresetPack> {
        val root = JSONObject(json)
        val result = mutableMapOf<String, PresetPack>()
        root.keys().forEach { code ->
            val packObject = root.getJSONObject(code)
            val itemsArray = packObject.getJSONArray("items")
            val items = mutableListOf<PresetItem>()
            for (index in 0 until itemsArray.length()) {
                val itemObject = itemsArray.getJSONObject(index)
                items += PresetItem(
                    bvid = itemObject.getString("bvid"),
                    part = itemObject.getInt("part"),
                    title = itemObject.getString("title"),
                )
            }
            result[code.lowercase()] = PresetPack(
                name = packObject.getString("name"),
                categoryName = packObject.getString("categoryName"),
                items = items,
            )
        }
        return result
    }
}
