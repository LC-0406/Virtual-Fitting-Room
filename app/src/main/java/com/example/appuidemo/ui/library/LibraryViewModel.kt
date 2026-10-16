package com.example.appuidemo.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.appuidemo.data.model.LibraryItem
import com.example.appuidemo.data.model.LibraryType
import com.example.appuidemo.data.repository.LibraryRepository
import com.example.appuidemo.ui.navigation.LibraryAction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 分页每页条数 */
private const val PAGE_SIZE = 12

class LibraryViewModel(val type: LibraryType) : ViewModel() {

    private val repository = LibraryRepository

    val items: StateFlow<List<LibraryItem>> = repository.itemsOf(type)
    val ready: StateFlow<Boolean> = repository.ready

    /** 当前已渲染到第几条；触底后递增 */
    var visibleCount by mutableIntStateOf(PAGE_SIZE)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    // ---- 多选模式 ----
    var multiSelectMode by mutableStateOf(false)
        private set

    var action by mutableStateOf(LibraryAction.DELETE)
        private set

    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var pendingDeleteCount by mutableIntStateOf(0)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun hasMore(total: Int): Boolean = visibleCount < total

    // ------------------------------------------------------------ 加载

    /** 触底加载下一页；内存分页，末尾停在「已经到底啦」 */
    fun loadMore() {
        if (isLoadingMore) return
        isLoadingMore = true
        viewModelScope.launch {
            delay(LOAD_MORE_DELAY_MS)
            visibleCount += PAGE_SIZE
            isLoadingMore = false
        }
    }

    /** 下拉刷新：回到第一页并重新读取仓库最新数据 */
    fun refresh() {
        if (isRefreshing) return
        isRefreshing = true
        viewModelScope.launch {
            delay(REFRESH_DELAY_MS)
            visibleCount = PAGE_SIZE
            isRefreshing = false
            _messages.trySend("已刷新")
        }
    }

    // ------------------------------------------------------------ 多选

    fun enterMultiSelect(action: LibraryAction) {
        this.action = action
        selectedIds = emptySet()
        multiSelectMode = true
    }

    fun exitMultiSelect() {
        multiSelectMode = false
        selectedIds = emptySet()
    }

    fun toggleSelection(id: String) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun selectAll(ids: Collection<String>) {
        selectedIds = ids.toSet()
    }

    fun clearSelection() {
        selectedIds = emptySet()
    }

    /** 删除前先弹二次确认，这里只负责发起 */
    fun requestDelete() {
        if (selectedIds.isEmpty()) return
        pendingDeleteCount = selectedIds.size
    }

    fun cancelDelete() {
        pendingDeleteCount = 0
    }

    fun confirmDelete() {
        val ids = selectedIds
        pendingDeleteCount = 0
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteItems(type, ids)
            exitMultiSelect()
            _messages.trySend("已删除 ${ids.size} 项")
        }
    }

    /** 导出由界面层调用相册写入，完成后回到这里收尾 */
    fun onExported(count: Int) {
        exitMultiSelect()
        _messages.trySend("已导出 $count 张到相册")
    }

    fun notifyImportSuccess() {
        // 新项按时间倒序在最前，列表回到顶部即可看到
        visibleCount = maxOf(visibleCount, PAGE_SIZE)
        _messages.trySend("导入成功")
    }

    fun notifyImportFailed(message: String) {
        _messages.trySend(message)
    }

    companion object {
        private const val LOAD_MORE_DELAY_MS = 450L
        private const val REFRESH_DELAY_MS = 700L

        fun factory(type: LibraryType): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(type) }
        }
    }
}
