package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.TableList
import stellarelite.zxsp.ui.theme.DiningColors

@Composable
fun DashboardScreen(onNewOrder: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var tables by remember { mutableStateOf<List<TableList>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { SupabaseClient.fetchTables() }
                .onSuccess { tables = it }
                .onFailure { error = it.message ?: "加载失败" }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val dineInTables = tables.filter { !it.table_no.startsWith("外卖") }
    val takeawayTables = tables.filter { it.table_no.startsWith("外卖") }
    val occupiedCount = dineInTables.count { it.table_status == "occupied" }
    val freeCount = dineInTables.count { it.table_status == "free" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("🏠 工作台", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
                Text("你好，${SessionManager.staffName}", fontSize = 14.sp, color = DiningColors.TextSecondary)
            }
            Button(
                onClick = onNewOrder,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)
            ) {
                Text("＋ 新建订单", color = DiningColors.Surface, fontWeight = FontWeight.Bold)
            }
        }

        // 统计卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DiningColors.Primary)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("🪑", "$freeCount", "空闲桌")
                StatItem("🍽️", "$occupiedCount", "占用中")
                StatItem("🧾", "${dineInTables.size}", "总桌台")
                StatItem("🛵", "${takeawayTables.size}", "外卖号")
            }
        }

        // 桌台看板
        when {
            loading -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DiningColors.Primary)
            }
            error != null -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️ $error", color = DiningColors.Error, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { load() }) { Text("重试") }
                }
            }
            else -> {
                Text("🪑 堂食桌台（${dineInTables.size}）", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                if (dineInTables.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("暂无桌台，请老板先添加", color = DiningColors.TextMuted, fontSize = 14.sp)
                    }
                } else {
                    TableGrid(dineInTables)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("🛵 外卖（${takeawayTables.size}）", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                if (takeawayTables.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("暂无外卖号", color = DiningColors.TextMuted, fontSize = 14.sp)
                    }
                } else {
                    TableGrid(takeawayTables)
                }
            }
        }
    }
}

@Composable
private fun StatItem(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.Surface)
        Text(label, fontSize = 11.sp, color = DiningColors.Surface.copy(alpha = 0.75f))
    }
}

@Composable
private fun TableGrid(tables: List<TableList>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tables.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { table ->
                    Box(modifier = Modifier.weight(1f)) {
                        TableBadge(table)
                    }
                }
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TableBadge(table: TableList) {
    val bg = when (table.table_status) {
        "occupied" -> DiningColors.Primary
        "cleaning" -> DiningColors.Warning
        else -> DiningColors.Surface
    }
    val fg = when (table.table_status) {
        "free" -> DiningColors.TextPrimary
        else -> DiningColors.Surface
    }
    val label = when (table.table_status) {
        "occupied" -> "占用"
        "cleaning" -> "清理"
        else -> "空闲"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(table.table_no, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
        Text(label, fontSize = 10.sp, color = fg.copy(alpha = 0.8f))
    }
}
