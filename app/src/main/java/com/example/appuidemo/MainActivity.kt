package com.example.appuidemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.appuidemo.data.repository.LibraryRepository
import com.example.appuidemo.ui.navigation.AppNavGraph
import com.example.appuidemo.ui.theme.AppUIdemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 图库数据源只初始化一次，之后从 filesDir/library 读取
        LibraryRepository.init(applicationContext)

        enableEdgeToEdge()
        setContent {
            AppUIdemoTheme {
                AppNavGraph()
            }
        }
    }
}
