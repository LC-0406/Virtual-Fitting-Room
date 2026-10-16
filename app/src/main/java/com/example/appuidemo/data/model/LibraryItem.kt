package com.example.appuidemo.data.model

/** 三个图库 + 历史库的类型标记 */
enum class LibraryType {
    PERSON,
    CLOTHING,
    HISTORY;

    /** 内部存储目录名 */
    val dirName: String
        get() = when (this) {
            PERSON -> "person"
            CLOTHING -> "clothing"
            HISTORY -> "history"
        }

    /** 缩略图展示比例（宽 : 高） */
    val aspectRatio: Float
        get() = when (this) {
            CLOTHING -> 1f          // 衣服 1:1
            PERSON, HISTORY -> 3f / 4f  // 人物 / 历史 3:4
        }
}

/**
 * 图库中的一张图片。图片本体是 [filePath] 指向的应用私有文件，
 * 路径不会失效，因为应用卸载前该目录一直存在。
 */
data class LibraryItem(
    val id: String,
    val type: LibraryType,
    val filePath: String,
    val createdAt: Long
)
