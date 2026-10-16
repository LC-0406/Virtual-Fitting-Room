package com.example.appuidemo.data.generation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.example.appuidemo.data.image.decodeBitmapOriented
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「确认生成」的服务端接口。
 *
 * 当前接入的是 [MockGenerationService]（纯本地模拟）。等拿到真实后端协议后，
 * 只需新增一个实现类并在 [com.example.appuidemo.data.repository.LibraryRepository]
 * 里替换掉默认实现，UI 与数据层都不用改。
 */
interface GenerationService {
    /**
     * 把人物图和衣服图合成一张试衣结果图。
     * 成功时 [outputFile] 已写入，返回该文件；失败返回 [Result.failure]。
     */
    suspend fun generate(personFile: File, clothingFile: File, outputFile: File): Result<File>
}

/**
 * 本地模拟实现：不联网，用 Canvas 把衣服图叠到人物图的上身区域，
 * 产出一张真实可看的 JPEG，以便「生成 → 预览 → 存历史」全流程可跑通。
 */
class MockGenerationService : GenerationService {

    override suspend fun generate(
        personFile: File,
        clothingFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.Default) {
        // 模拟一次网络往返 + 服务端推理的耗时，让 loading 态可见
        delay(SIMULATED_LATENCY_MS)

        if (SIMULATE_FAILURE) {
            return@withContext Result.failure(
                IllegalStateException("模拟的生成失败（SIMULATE_FAILURE=true）")
            )
        }

        runCatching {
            val person = decodeBitmapOriented(personFile) ?: error("无法解码人物图")
            val clothing = decodeBitmapOriented(clothingFile) ?: error("无法解码衣服图")

            val output = combine(person, clothing)
            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { out ->
                output.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            person.recycle()
            clothing.recycle()
            output.recycle()
            outputFile
        }
    }

    private fun combine(person: Bitmap, clothing: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(person.width, person.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        canvas.drawBitmap(
            person,
            null,
            RectF(0f, 0f, person.width.toFloat(), person.height.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )

        // 衣服按人物宽度的 46% 缩放，居中摆在躯干位置
        val clothingWidth = person.width * 0.46f
        val clothingHeight = clothingWidth * clothing.height / clothing.width
        val left = (person.width - clothingWidth) / 2f
        val top = person.height * 0.40f
        val dest = RectF(left, top, left + clothingWidth, top + clothingHeight)

        // 用 Multiply 叠加：衣服照片里的浅色背景乘上底图后基本消失，
        // 不会像直接覆盖那样在人物身上盖出一个白方块。
        // 用 PorterDuff 而非 BlendMode —— 后者需要 API 29，而本应用 minSdk 是 24。
        canvas.drawBitmap(
            clothing,
            null,
            dest,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                @Suppress("DEPRECATION")
                xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
        )

        drawFooter(canvas, person.width, person.height)
        return output
    }

    private fun drawFooter(canvas: Canvas, width: Int, height: Int) {
        val barHeight = height * 0.075f
        val barTop = height - barHeight

        canvas.drawRect(
            0f, barTop, width.toFloat(), height.toFloat(),
            Paint().apply { color = Color.argb(150, 20, 18, 24) }
        )

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = barHeight * 0.34f
        }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val label = "虚拟试衣间 · 合成结果  $stamp"
        val textY = barTop + barHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, width * 0.04f, textY, textPaint)
    }

    companion object {
        /** 模拟服务端耗时 */
        private const val SIMULATED_LATENCY_MS = 1800L

        /** 改成 true 可验证「生成失败 → Toast + 按钮恢复」这条分支 */
        private const val SIMULATE_FAILURE = false
    }
}
