package com.example.appuidemo.ui.library

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.appuidemo.data.image.MediaStoreHelper
import com.example.appuidemo.data.model.LibraryItem
import com.example.appuidemo.data.model.LibraryType
import com.example.appuidemo.ui.components.BackToTopButton
import com.example.appuidemo.ui.components.ConfirmDialog
import com.example.appuidemo.ui.components.EmptyState
import com.example.appuidemo.ui.components.LibraryThumbnail
import com.example.appuidemo.ui.components.ListFooter
import com.example.appuidemo.ui.components.SkeletonGrid
import com.example.appuidemo.ui.import.ImportSheet
import com.example.appuidemo.ui.multiselect.MultiSelectBottomBar
import com.example.appuidemo.ui.multiselect.MultiSelectTopBar
import com.example.appuidemo.ui.navigation.LibraryAction
import com.example.appuidemo.ui.util.needsLegacyStoragePermission
import com.example.appuidemo.ui.util.openAppSettings
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

/** 截取到本地时间当天零点，用于历史库分组 */
private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun historyBucket(createdAt: Long, now: Long): String {
    val dayMs = 24 * 60 * 60 * 1000L
    val days = (startOfDay(now) - startOfDay(createdAt)) / dayMs
    return when {
        days <= 0 -> "今天"
        days < 7 -> "本周"
        else -> "更早"
    }
}

private sealed interface GridEntry {
    data class Header(val text: String) : GridEntry
    data class Photo(val item: LibraryItem) : GridEntry
}

private fun buildEntries(type: LibraryType, visible: List<LibraryItem>): List<GridEntry> {
    if (type != LibraryType.HISTORY) return visible.map { GridEntry.Photo(it) }

    val now = System.currentTimeMillis()
    val result = mutableListOf<GridEntry>()
    var currentBucket: String? = null
    visible.forEach { item ->
        val bucket = historyBucket(item.createdAt, now)
        if (bucket != currentBucket) {
            result += GridEntry.Header(bucket)
            currentBucket = bucket
        }
        result += GridEntry.Photo(item)
    }
    return result
}

/**
 * 人物库 / 衣柜库 / 历史库三页共用的滚动网格骨架。
 *
 * 2 列固定比例网格 + 触底加载 + 下拉刷新 + 滚动位置记忆 + 回到顶部；
 * 删除与导出复用同一套多选模式，只是底部主操作按钮不同。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    type: LibraryType,
    onBack: () -> Unit,
    onOpenZoom: (LibraryItem) -> Unit,
    onOpenSelect: (LibraryItem) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(type))
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val items by viewModel.items.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()

    val gridState = rememberLazyGridState()
    val visibleItems = remember(items, viewModel.visibleCount) {
        items.take(viewModel.visibleCount)
    }
    val entries = remember(visibleItems, type) { buildEntries(type, visibleItems) }
    val hasMore = viewModel.hasMore(items.size)

    var showImportSheet by rememberSaveable { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    // 多选模式下的删除二次确认
    if (viewModel.pendingDeleteCount > 0) {
        ConfirmDialog(
            title = "删除确认",
            message = "确定删除选中的 ${viewModel.pendingDeleteCount} 项？此操作不可恢复。",
            confirmLabel = "删除",
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() }
        )
    }

    // Toast 反馈统一走这里
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 触底加载：最后一个可见项接近已渲染内容末尾时加载下一页。
    // hasMore 必须进 key —— 否则图片增删但已渲染条数不变时，这里会一直用旧的判断结果。
    LaunchedEffect(gridState, entries.size, viewModel.visibleCount, hasMore) {
        if (!hasMore) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastIndex ->
                if (lastIndex >= 0 && lastIndex >= entries.size - 4) {
                    viewModel.loadMore()
                }
            }
    }

    // 导出到相册。Android 9 及以下先补一个写存储权限
    fun startExport() {
        val selected = items.filter { it.id in viewModel.selectedIds }
        if (selected.isEmpty()) return
        isExporting = true
        scope.launch {
            val ok = selected.count { item ->
                MediaStoreHelper.saveToAlbum(
                    context = context,
                    source = File(item.filePath),
                    displayName = "VFR_${item.id}.jpg"
                )
            }
            isExporting = false
            if (ok == 0) {
                Toast.makeText(context, "导出失败，请检查存储权限", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.onExported(ok)
            }
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startExport()
        else Toast.makeText(context, "没有存储权限，无法导出到相册", Toast.LENGTH_SHORT).show()
    }

    fun handlePrimaryAction() {
        when (viewModel.action) {
            LibraryAction.DELETE -> viewModel.requestDelete()
            LibraryAction.EXPORT -> {
                if (context.needsLegacyStoragePermission()) {
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    startExport()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            if (viewModel.multiSelectMode) {
                MultiSelectTopBar(
                    selectedCount = viewModel.selectedIds.size,
                    onSelectAll = { viewModel.selectAll(items.map { it.id }) },
                    onClearSelection = { viewModel.clearSelection() },
                    onExit = { viewModel.exitMultiSelect() }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = when (type) {
                                LibraryType.PERSON -> "人物库"
                                LibraryType.CLOTHING -> "衣柜"
                                LibraryType.HISTORY -> "历史"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 历史库没有"导入"，改成"导出"
                        if (type == LibraryType.HISTORY) {
                            IconButton(onClick = { viewModel.enterMultiSelect(LibraryAction.EXPORT) }) {
                                Icon(Icons.Filled.FileDownload, contentDescription = "导出")
                            }
                        } else {
                            IconButton(onClick = { showImportSheet = true }) {
                                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "导入")
                            }
                        }
                        IconButton(onClick = { viewModel.enterMultiSelect(LibraryAction.DELETE) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (viewModel.multiSelectMode) {
                MultiSelectBottomBar(
                    action = viewModel.action,
                    selectedCount = viewModel.selectedIds.size,
                    loading = isExporting,
                    onAction = { handlePrimaryAction() }
                )
            }
        },
        floatingActionButton = {
            BackToTopButton(
                visible = gridState.firstVisibleItemIndex > 0 && !viewModel.multiSelectMode,
                onClick = {
                    scope.launch { gridState.animateScrollToItem(0) }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                !ready -> SkeletonGrid(aspectRatio = type.aspectRatio)

                items.isEmpty() -> EmptyState(
                    icon = when (type) {
                        LibraryType.PERSON -> Icons.Outlined.AddPhotoAlternate
                        LibraryType.CLOTHING -> Icons.Outlined.Checkroom
                        LibraryType.HISTORY -> Icons.Outlined.History
                    },
                    title = when (type) {
                        LibraryType.PERSON -> "暂无人物照片"
                        LibraryType.CLOTHING -> "暂无衣服照片"
                        LibraryType.HISTORY -> "暂无生成记录"
                    },
                    description = when (type) {
                        LibraryType.HISTORY -> "在主页选择人物和衣服后点击「确认生成」"
                        else -> "点击导入添加一张照片"
                    },
                    actionLabel = if (type == LibraryType.HISTORY) null else "导入照片",
                    onAction = if (type == LibraryType.HISTORY) null else ({ showImportSheet = true })
                )

                else -> {
                    val grid: @Composable () -> Unit = {
                        LibraryGrid(
                            entries = entries,
                            type = type,
                            gridState = gridState,
                            selectionMode = viewModel.multiSelectMode,
                            selectedIds = viewModel.selectedIds,
                            isLoadingMore = viewModel.isLoadingMore,
                            isEnd = !hasMore,
                            onItemClick = { item ->
                                when {
                                    viewModel.multiSelectMode -> viewModel.toggleSelection(item.id)
                                    type == LibraryType.HISTORY -> onOpenZoom(item)
                                    else -> onOpenSelect(item)
                                }
                            },
                            onItemLongClick = { item ->
                                if (!viewModel.multiSelectMode) {
                                    viewModel.enterMultiSelect(LibraryAction.DELETE)
                                    viewModel.toggleSelection(item.id)
                                }
                            }
                        )
                    }

                    // 多选模式下禁用下拉刷新，避免误触
                    if (viewModel.multiSelectMode) {
                        grid()
                    } else {
                        PullToRefreshBox(
                            isRefreshing = viewModel.isRefreshing,
                            onRefresh = { viewModel.refresh() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            grid()
                        }
                    }
                }
            }
        }
    }

    if (showImportSheet && type != LibraryType.HISTORY) {
        ImportSheet(
            type = type,
            onDismiss = { showImportSheet = false },
            onImported = {
                showImportSheet = false
                viewModel.notifyImportSuccess()
                // 新项按时间倒序排在首位，滚回顶部就能看到
                scope.launch { gridState.animateScrollToItem(0) }
            }
        )
    }
}

@Composable
private fun LibraryGrid(
    entries: List<GridEntry>,
    type: LibraryType,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    isLoadingMore: Boolean,
    isEnd: Boolean,
    onItemClick: (LibraryItem) -> Unit,
    onItemLongClick: (LibraryItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        entries.forEach { entry ->
            when (entry) {
                is GridEntry.Header -> item(
                    key = "header_${entry.text}",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }

                is GridEntry.Photo -> item(key = entry.item.id) {
                    LibraryThumbnail(
                        filePath = entry.item.filePath,
                        aspectRatio = type.aspectRatio,
                        selectionMode = selectionMode,
                        selected = entry.item.id in selectedIds,
                        onClick = { onItemClick(entry.item) },
                        onLongClick = { onItemLongClick(entry.item) }
                    )
                }
            }
        }

        item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
            ListFooter(isLoading = isLoadingMore, isEnd = isEnd && entries.isNotEmpty())
        }
    }
}
