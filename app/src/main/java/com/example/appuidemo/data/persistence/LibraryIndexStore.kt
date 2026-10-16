package com.example.appuidemo.data.persistence

import com.example.appuidemo.data.model.LibraryItem
import com.example.appuidemo.data.model.LibraryType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 落盘时只保存文件名，根目录变化（如换设备）时仍可解析 */
private data class PersistedItem(
    val id: String,
    val type: LibraryType,
    val fileName: String,
    val createdAt: Long
)

data class LibrarySnapshot(
    val items: List<LibraryItem> = emptyList(),
    val selectedPersonId: String? = null,
    val selectedClothingId: String? = null
)

/**
 * 用内置 org.json 读写 `filesDir/library/index.json`。
 * 不引入序列化插件，保持依赖最小。
 */
class LibraryIndexStore(private val rootDir: File) {

    private val indexFile = File(rootDir, "index.json")

    fun read(): LibrarySnapshot {
        if (!indexFile.exists()) return LibrarySnapshot()
        return runCatching {
            val json = JSONObject(indexFile.readText())
            val array = json.optJSONArray(KEY_ITEMS) ?: JSONArray()
            val items = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val item = obj.toItem() ?: continue
                    add(item)
                }
            }
            LibrarySnapshot(
                items = items,
                selectedPersonId = json.optStringOrNull(KEY_SELECTED_PERSON),
                selectedClothingId = json.optStringOrNull(KEY_SELECTED_CLOTHING)
            )
        }.getOrElse { LibrarySnapshot() }
    }

    fun write(snapshot: LibrarySnapshot) {
        runCatching {
            rootDir.mkdirs()
            val array = JSONArray()
            snapshot.items.forEach { array.put(it.toJson()) }
            val json = JSONObject().apply {
                put(KEY_ITEMS, array)
                put(KEY_SELECTED_PERSON, snapshot.selectedPersonId ?: JSONObject.NULL)
                put(KEY_SELECTED_CLOTHING, snapshot.selectedClothingId ?: JSONObject.NULL)
            }
            indexFile.writeText(json.toString())
        }
    }

    private fun JSONObject.toItem(): LibraryItem? {
        val id = optStringOrNull("id") ?: return null
        val typeName = optStringOrNull("type") ?: return null
        val fileName = optStringOrNull("fileName") ?: return null
        val type = runCatching { LibraryType.valueOf(typeName) }.getOrNull() ?: return null
        return LibraryItem(
            id = id,
            type = type,
            filePath = File(File(rootDir, type.dirName), fileName).absolutePath,
            createdAt = optLong("createdAt", 0L)
        )
    }

    private fun LibraryItem.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("fileName", File(filePath).name)
        put("createdAt", createdAt)
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotEmpty() && it != "null" }
    }

    private companion object {
        const val KEY_ITEMS = "items"
        const val KEY_SELECTED_PERSON = "selectedPersonId"
        const val KEY_SELECTED_CLOTHING = "selectedClothingId"
    }
}
