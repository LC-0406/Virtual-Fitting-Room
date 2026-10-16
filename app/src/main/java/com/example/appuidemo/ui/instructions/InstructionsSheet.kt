package com.example.appuidemo.ui.instructions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val STEPS = listOf(
    "1. 导入照片" to "进入「人物库」或「衣柜」，点右上角导入按钮，从相册选择或直接拍摄。" +
        "系统会做一次合格检验，不合格会提示重拍。",
    "2. 选中人物和衣服" to "在库里点任意一张缩略图进入大图页，点「确认」即把它设为主页当前的" +
        "人物或衣服。再次选择会覆盖原来那张。",
    "3. 确认生成" to "人物和衣服都选好后，主页「确认生成」会亮起。点击后图片会上传生成，" +
        "期间按钮显示进度并禁用，避免重复提交。",
    "4. 查看与导出" to "生成完成后会自动切到「预览」并存入历史库。点「导出」可以把结果图" +
        "存进系统相册，导出的副本在卸载应用后仍然保留。",
    "5. 管理图库" to "长按任意缩略图可进入多选模式，支持全选、批量删除；历史库还支持批量" +
        "导出。删除前会二次确认。",
    "6. 数据存放位置" to "人物、衣服、历史图片都保存在应用私有目录中，卸载应用时会被系统" +
        "一并清除；只有你主动「导出」到相册的副本才会留在手机相册里。"
)

/**
 * 使用说明（页面 9）：底部抽屉，可滚动，下滑 / 点遮罩 / 关闭按钮均可退出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "使用说明",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))

            STEPS.forEach { (title, body) ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
