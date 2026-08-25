package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.ui.theme.DiningColors

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "⚙️ 设置",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = DiningColors.TextPrimary
        )

        // 餐厅信息
        SettingsGroup("餐厅信息") {
            SettingsRow("餐厅名称", "炙巷食谱")
            SettingsRow("联系电话", "010-88886666")
            SettingsRow("营业时间", "10:00 - 22:00")
        }

        // 功能设置
        SettingsGroup("功能设置") {
            SettingsSwitch("自动打印小票", true)
            SettingsSwitch("语音播报订单", true)
            SettingsSwitch("堂食/外卖双模式", false)
            SettingsRow("打印机设置", "未连接")
        }

        // 数据管理
        SettingsGroup("数据管理") {
            SettingsRow("菜品数据", "12 个分类 · 156 道菜")
            SettingsRow("订单记录", "共 3,285 条")
            SettingsRow("仓库数据", "共 45 种物料")
        }

        // 系统
        SettingsGroup("系统") {
            SettingsRow("版本号", "v1.0.0")
            SettingsRow("检查更新", "已是最新")
        }

        // 退出按钮
        Button(
            onClick = { /* 退出登录 */ },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Error.copy(alpha = 0.1f))
        ) {
            Text("退出登录", fontSize = 16.sp, color = DiningColors.Error)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = DiningColors.Primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = DiningColors.TextPrimary)
        Text(value, fontSize = 14.sp, color = DiningColors.TextSecondary)
    }
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean) {
    var isChecked by remember { mutableStateOf(checked) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = DiningColors.TextPrimary)
        Switch(
            checked = isChecked,
            onCheckedChange = { isChecked = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = DiningColors.Surface,
                checkedTrackColor = DiningColors.Primary,
                uncheckedThumbColor = DiningColors.Surface,
                uncheckedTrackColor = DiningColors.SurfaceVariant
            )
        )
    }
}
