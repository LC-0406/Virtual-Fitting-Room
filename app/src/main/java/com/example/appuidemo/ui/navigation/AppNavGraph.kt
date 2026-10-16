package com.example.appuidemo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.appuidemo.data.model.LibraryType
import com.example.appuidemo.data.repository.LibraryRepository
import com.example.appuidemo.ui.home.HomeScreen
import com.example.appuidemo.ui.library.LibraryScreen
import com.example.appuidemo.ui.select.ImageSelectScreen
import com.example.appuidemo.ui.zoom.ZoomScreen

private fun NavBackStackEntry.arg(name: String): String =
    arguments?.getString(name).orEmpty()

private fun NavBackStackEntry.libraryType(): LibraryType =
    runCatching { LibraryType.valueOf(arg("type")) }.getOrDefault(LibraryType.PERSON)

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        // ------------------------------------------------ 主页面
        composable(Routes.HOME) {
            HomeScreen(
                onOpenPersonLibrary = { navController.navigate(Routes.library(LibraryType.PERSON)) },
                onOpenClothingLibrary = { navController.navigate(Routes.library(LibraryType.CLOTHING)) },
                onOpenHistory = { navController.navigate(Routes.library(LibraryType.HISTORY)) },
                onOpenZoom = { navController.navigate(Routes.zoom(it.type, it.id)) }
            )
        }

        // ------------------------------------------------ 人物库 / 衣柜 / 历史库
        composable("library/{type}") { entry ->
            val type = entry.libraryType()
            LibraryScreen(
                type = type,
                onBack = { navController.popBackStack() },
                onOpenZoom = { navController.navigate(Routes.zoom(it.type, it.id)) },
                onOpenSelect = { navController.navigate(Routes.select(type, it.id)) }
            )
        }

        // ------------------------------------------------ 图片选择页（人物 / 衣服共用）
        composable(Routes.SELECT_PATTERN) { entry ->
            val item = LibraryRepository.findById(entry.arg("itemId"))
            if (item == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ImageSelectScreen(
                    item = item,
                    // 确认后直达使用场景：主页面
                    onConfirm = { navController.popBackStack(Routes.HOME, inclusive = false) },
                    // 取消则回到来源库，库页面自行恢复滚动位置
                    onCancel = { navController.popBackStack() }
                )
            }
        }

        // ------------------------------------------------ 放大图（全屏浮层）
        composable(Routes.ZOOM_PATTERN) { entry ->
            val item = LibraryRepository.findById(entry.arg("itemId"))
            if (item == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ZoomScreen(item = item, onClose = { navController.popBackStack() })
            }
        }
    }
}
