package com.example.appuidemo.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.appuidemo.data.model.LibraryItem
import com.example.appuidemo.data.repository.LibraryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 中央预览图当前展示的是哪一路 */
enum class PreviewTab { PERSON, CLOTHING, PREVIEW }

class HomeViewModel : ViewModel() {

    private val repository = LibraryRepository

    val selectedPerson: StateFlow<LibraryItem?> = repository.selectedPerson
    val selectedClothing: StateFlow<LibraryItem?> = repository.selectedClothing
    val historyItems: StateFlow<List<LibraryItem>> = repository.historyItems

    var previewTab by mutableStateOf(PreviewTab.PERSON)
        private set

    var generating by mutableStateOf(false)
        private set

    var exporting by mutableStateOf(false)
        private set

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun selectTab(tab: PreviewTab) {
        previewTab = tab
    }

    fun notify(message: String) {
        _messages.trySend(message)
    }

    /**
     * 「确认生成」：人物 + 衣服都已选择才放行，
     * 传输期间按钮进入 loading 并禁用，完成后自动切到「预览」。
     */
    fun generate() {
        if (generating) return

        if (repository.selectedPerson.value == null && repository.selectedClothing.value == null) {
            _messages.trySend("请先选择人物和衣服")
            return
        }
        if (repository.selectedPerson.value == null) {
            _messages.trySend("请先选择人物")
            return
        }
        if (repository.selectedClothing.value == null) {
            _messages.trySend("请先选择衣服")
            return
        }

        generating = true
        viewModelScope.launch {
            when (val result = repository.generate()) {
                is LibraryRepository.GenerateResult.Success -> {
                    previewTab = PreviewTab.PREVIEW
                    _messages.trySend("生成成功")
                }

                LibraryRepository.GenerateResult.MissingSelection ->
                    _messages.trySend("请先选择人物和衣服")

                is LibraryRepository.GenerateResult.Failed ->
                    _messages.trySend(result.message)
            }
            generating = false
        }
    }

    fun beginExport() {
        exporting = true
    }

    fun endExport(success: Boolean) {
        exporting = false
        _messages.trySend(if (success) "已存入相册" else "导出失败，请检查存储权限")
    }
}
