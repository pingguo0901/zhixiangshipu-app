package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.ui.theme.DiningColors

@Composable
fun SettingsScreen(
    onAdminLogin: (id: String, password: String) -> Unit = { _, _ -> }
) {
    var adminTapCount by remember { mutableStateOf(0) }
    var showAdminLogin by remember { mutableStateOf(false) }

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
            // 版本号：连点 7 次进入管理员登录
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        adminTapCount++
                        if (adminTapCount >= 7) {
                            adminTapCount = 0
                            showAdminLogin = true
                        }
                    }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("版本号", fontSize = 15.sp, color = DiningColors.TextPrimary)
                Text("v1.0.11", fontSize = 14.sp, color = DiningColors.TextSecondary)
            }
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

    // 管理员登录弹窗
    if (showAdminLogin) {
        AdminLoginDialog(
            onDismiss = { showAdminLogin = false },
            onLogin = { id, password ->
                showAdminLogin = false
                onAdminLogin(id, password)
            }
        )
    }
}

@Composable
private fun AdminLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    var id by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "👤 管理员登录",
                color = DiningColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("管理员 ID") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onLogin(id.trim(), password) },
                enabled = id.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DiningColors.Primary,
                    disabledContainerColor = DiningColors.TextMuted.copy(alpha = 0.3f)
                )
            ) {
                Text("登录", color = DiningColors.Surface, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) }
        }
    )
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
