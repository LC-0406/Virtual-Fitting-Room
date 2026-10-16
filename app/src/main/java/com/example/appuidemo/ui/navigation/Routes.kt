package com.example.appuidemo.ui.navigation

import com.example.appuidemo.data.model.LibraryType

/**
 * 导航路由。
 *
 * 只有真正"整页切换"的场景才进导航栈：三个库页面、图片选择页、放大图页。
 * 导入抽屉 / 使用说明 / 多选模式都是页面内浮层或模式，不占路由。
 */
object Routes {
    const val HOME = "home"
    const val PERSON_LIBRARY = "library/PERSON"
    const val CLOTHING_LIBRARY = "library/CLOTHING"
    const val HISTORY_LIBRARY = "library/HISTORY"

    const val SELECT_PATTERN = "select/{type}/{itemId}"
    const val ZOOM_PATTERN = "zoom/{type}/{itemId}"

    fun library(type: LibraryType) = "library/${type.name}"
    fun select(type: LibraryType, itemId: String) = "select/${type.name}/$itemId"
    fun zoom(type: LibraryType, itemId: String) = "zoom/${type.name}/$itemId"
}

/** 库页面的两个入口动作，复用于多选模式 */
enum class LibraryAction { DELETE, EXPORT }

/** 图片选择页的上下文，决定确认后写入人物位还是衣服位 */
val LibraryType.pickerTitle: String
    get() = when (this) {
        com.example.appuidemo.data.model.LibraryType.PERSON -> "选择人物照片"
        com.example.appuidemo.data.model.LibraryType.CLOTHING -> "选择衣服照片"
        com.example.appuidemo.data.model.LibraryType.HISTORY -> "查看"
    }
