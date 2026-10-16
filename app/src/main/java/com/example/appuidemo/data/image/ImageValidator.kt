package com.example.appuidemo.data.image

import android.graphics.BitmapFactory

/**
 * 导入图片的「合格检验」。
 *
 * 规则刻意保持在很轻的一层：能正常解码 + 长宽不低于最小像素。
 * 以后要收紧（如必须全身照 / 固定宽高比），只改 [validate] 一处即可。
 */
object ImageValidator {

    /** 最短边最小像素，小于该值视为画质过低 */
    const val MIN_SIDE_PX = 200

    const val MESSAGE_INVALID = "照片不符合要求，请重拍"

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(bytes: ByteArray): Result {
        if (bytes.isEmpty()) return Result.Invalid("图片内容为空")

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return Result.Invalid("无法识别该图片格式")
        }
        if (minOf(options.outWidth, options.outHeight) < MIN_SIDE_PX) {
            return Result.Invalid("图片分辨率过低（最短边需 ≥ ${MIN_SIDE_PX}px）")
        }
        return Result.Valid
    }
}
