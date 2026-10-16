package com.example.appuidemo.ui.select

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.appuidemo.data.model.LibraryItem
import com.example.appuidemo.data.model.LibraryType
import com.example.appuidemo.data.repository.LibraryRepository
import com.example.appuidemo.ui.components.ZoomableImage
import com.example.appuidemo.ui.components.rememberImageModel
import kotlinx.coroutines.launch

/**
 * 图片选择页（页面 6，人物 / 衣服共用）。
 *
 * 大图只用于预览、可缩放；「确认」才把这张图覆盖式写入主页面当前人物 / 衣服。
 * 「取消」直接返回来源库，滚动位置由库页面自己恢复。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSelectScreen(
    item: LibraryItem,
    onConfirm: (LibraryItem) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val isPerson = item.type == LibraryType.PERSON

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isPerson) "选择人物照片" else "选择衣服照片",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "确认后将替换主页面当前${if (isPerson) "人物" else "衣服"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) { Text("取消") }
                        Button(
                            onClick = {
                                scope.launch {
                                    LibraryRepository.select(item.type, item)
                                    onConfirm(item)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) { Text("确认") }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            ZoomableImage(
                model = rememberImageModel(item.filePath),
                contentDescription = "预览图",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
