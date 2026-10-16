package com.example.appuidemo.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.appuidemo.data.generation.GenerationService
import com.example.appuidemo.data.generation.MockGenerationService
import com.example.appuidemo.data.image.ImageValidator
import com.example.appuidemo.data.image.decodeBytesOriented
import com.example.appuidemo.data.persistence.LibraryIndexStore
import com.example.appuidemo.data.persistence.LibrarySnapshot
import com.example.appuidemo.data.model.LibraryItem
import com.example.appuidemo.data.model.LibraryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 图片仓库：应用内唯一的数据源。
 *
 * 存储位置（真机与模拟器一致）：
 * ```
 * filesDir/library/person/<uuid>.jpg
 * filesDir/library/clothing/<uuid>.jpg
 * filesDir/library/history/<uuid>.jpg
 * filesDir/library/index.json        ← 元数据 + 当前选中项
 * ```
 * 「预览」不单独存文件，它就是历史列表里最新的一条。
 */
object LibraryRepository {

    private lateinit var appContext: Context
    private lateinit var rootDir: File
    private lateinit var store: LibraryIndexStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /** 换真实后端时只改这一行 */
    private val generationService: GenerationService = MockGenerationService()

    private val _personItems = MutableStateFlow<List<LibraryItem>>(emptyList())
    private val _clothingItems = MutableStateFlow<List<LibraryItem>>(emptyList())
    private val _historyItems = MutableStateFlow<List<LibraryItem>>(emptyList())
    private val _selectedPerson = MutableStateFlow<LibraryItem?>(null)
    private val _selectedClothing = MutableStateFlow<LibraryItem?>(null)
    private val _ready = MutableStateFlow(false)

    val personItems: StateFlow<List<LibraryItem>> = _personItems.asStateFlow()
    val clothingItems: StateFlow<List<LibraryItem>> = _clothingItems.asStateFlow()
    val historyItems: StateFlow<List<LibraryItem>> = _historyItems.asStateFlow()
    val selectedPerson: StateFlow<LibraryItem?> = _selectedPerson.asStateFlow()
    val selectedClothing: StateFlow<LibraryItem?> = _selectedClothing.asStateFlow()

    /** 首屏数据是否就绪；为 false 时库页面显示骨架屏 */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        rootDir = File(appContext.filesDir, LIBRARY_DIR)
        store = LibraryIndexStore(rootDir)
        scope.launch { loadFromDisk() }
    }

    private suspend fun loadFromDisk() = withContext(Dispatchers.IO) {
        val snapshot = store.read()
        val byType = snapshot.items.groupBy { it.type }
        _personItems.value = byType[LibraryType.PERSON].orEmpty().sortedByDescending { it.createdAt }
        _clothingItems.value = byType[LibraryType.CLOTHING].orEmpty().sortedByDescending { it.createdAt }
        _historyItems.value = byType[LibraryType.HISTORY].orEmpty().sortedByDescending { it.createdAt }
        _selectedPerson.value = snapshot.items.firstOrNull { it.id == snapshot.selectedPersonId }
        _selectedClothing.value = snapshot.items.firstOrNull { it.id == snapshot.selectedClothingId }

        // 让骨架屏在首屏真的能被看到（本地读盘几乎瞬时）
        delay(SKELETON_MIN_VISIBLE_MS)
        _ready.value = true
    }

    // ---------------------------------------------------------------- 查询

    fun itemsOf(type: LibraryType): StateFlow<List<LibraryItem>> = when (type) {
        LibraryType.PERSON -> personItems
        LibraryType.CLOTHING -> clothingItems
        LibraryType.HISTORY -> historyItems
    }

    fun findById(id: String): LibraryItem? =
        (_personItems.value + _clothingItems.value + _historyItems.value).firstOrNull { it.id == id }

    /** 当前「预览」图：历史里最新一条 */
    fun latestHistory(): LibraryItem? = _historyItems.value.firstOrNull()

    // ---------------------------------------------------------------- 导入

    sealed interface ImportResult {
        data class Success(val item: LibraryItem) : ImportResult
        data class Invalid(val reason: String) : ImportResult
        data class Failed(val message: String) : ImportResult
    }

    /** 从系统相册 / 相机返回的 Uri 导入一张图，含合格检验 */
    suspend fun importImage(type: LibraryType, uri: Uri): ImportResult {
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        } ?: return ImportResult.Failed("读取图片失败，请重试")

        return when (val check = ImageValidator.validate(bytes)) {
            is ImageValidator.Result.Invalid -> ImportResult.Invalid(check.reason)
            ImageValidator.Result.Valid -> addBytes(type, bytes)
        }
    }

    /** 写入图片文件 + 更新列表 + 落盘 */
    private suspend fun addBytes(type: LibraryType, bytes: ByteArray): ImportResult {
        val item = withContext(Dispatchers.IO) {
            runCatching {
                // 统一重编码为 JPEG，避免各来源格式（HEIC/PNG/WebP）后续读不一致。
                // 解码时降采样并摆正 EXIF 方向，也顺带避免大图 OOM。
                val bitmap = decodeBytesOriented(bytes) ?: error("图片解码失败")
                val dir = File(rootDir, type.dirName).apply { mkdirs() }
                val id = UUID.randomUUID().toString()
                val file = File(dir, "$id.jpg")
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                bitmap.recycle()
                LibraryItem(
                    id = id,
                    type = type,
                    filePath = file.absolutePath,
                    createdAt = System.currentTimeMillis()
                )
            }
        }.getOrElse { return ImportResult.Failed("保存图片失败：${it.message ?: "未知错误"}") }

        mutex.withLock {
            mutate(type) { listOf(item) + it }
            persist()
        }
        return ImportResult.Success(item)
    }

    // ---------------------------------------------------------------- 生成

    sealed interface GenerateResult {
        data class Success(val item: LibraryItem) : GenerateResult
        data object MissingSelection : GenerateResult
        data class Failed(val message: String) : GenerateResult
    }

    /**
     * 「确认生成」：校验人物 + 衣服已选 → 调服务端（当前为本地模拟）
     * → 结果存入历史库（同时也就成了主页「预览」）。
     */
    suspend fun generate(): GenerateResult {
        val person = _selectedPerson.value ?: return GenerateResult.MissingSelection
        val clothing = _selectedClothing.value ?: return GenerateResult.MissingSelection

        val outputDir = File(rootDir, LibraryType.HISTORY.dirName).apply { mkdirs() }
        val outputFile = File(outputDir, "${UUID.randomUUID()}.jpg")

        val result = generationService.generate(
            personFile = File(person.filePath),
            clothingFile = File(clothing.filePath),
            outputFile = outputFile
        )

        return result.fold(
            onSuccess = { file ->
                val item = LibraryItem(
                    id = file.nameWithoutExtension,
                    type = LibraryType.HISTORY,
                    filePath = file.absolutePath,
                    createdAt = System.currentTimeMillis()
                )
                mutex.withLock {
                    mutate(LibraryType.HISTORY) { listOf(item) + it }
                    persist()
                }
                GenerateResult.Success(item)
            },
            onFailure = { error ->
                outputFile.delete()
                GenerateResult.Failed(error.message ?: "生成失败，请重试")
            }
        )
    }

    // ---------------------------------------------------------------- 选择

    /** 覆盖式存储当前人物 / 衣服 */
    suspend fun select(type: LibraryType, item: LibraryItem) {
        when (type) {
            LibraryType.PERSON -> _selectedPerson.value = item
            LibraryType.CLOTHING -> _selectedClothing.value = item
            LibraryType.HISTORY -> return
        }
        mutex.withLock { persist() }
    }

    // ---------------------------------------------------------------- 删除

    /** 删除指定项；同时清理它们占用的人物 / 衣服选中位 */
    suspend fun deleteItems(type: LibraryType, ids: Set<String>): List<LibraryItem> {
        if (ids.isEmpty()) return emptyList()
        val removed = itemsOf(type).value.filter { it.id in ids }
        if (removed.isEmpty()) return emptyList()

        mutex.withLock {
            mutate(type) { list -> list.filterNot { it.id in ids } }
            if (_selectedPerson.value?.id in ids) _selectedPerson.value = null
            if (_selectedClothing.value?.id in ids) _selectedClothing.value = null
            persist()
        }

        withContext(Dispatchers.IO) {
            removed.forEach { runCatching { File(it.filePath).delete() } }
        }
        return removed
    }

    // ---------------------------------------------------------------- 内部

    private inline fun mutate(type: LibraryType, transform: (List<LibraryItem>) -> List<LibraryItem>) {
        when (type) {
            LibraryType.PERSON -> _personItems.value = transform(_personItems.value)
            LibraryType.CLOTHING -> _clothingItems.value = transform(_clothingItems.value)
            LibraryType.HISTORY -> _historyItems.value = transform(_historyItems.value)
        }
    }

    private fun persist() {
        store.write(
            LibrarySnapshot(
                items = _personItems.value + _clothingItems.value + _historyItems.value,
                selectedPersonId = _selectedPerson.value?.id,
                selectedClothingId = _selectedClothing.value?.id
            )
        )
    }

    private const val LIBRARY_DIR = "library"

    /** 骨架屏最短展示时间，纯为演示可感知 */
    private const val SKELETON_MIN_VISIBLE_MS = 450L
}
