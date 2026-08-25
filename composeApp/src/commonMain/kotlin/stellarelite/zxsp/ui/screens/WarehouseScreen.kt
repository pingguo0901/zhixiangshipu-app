package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.ui.theme.DiningColors

// ===== 数据模型 =====

data class StockRecord(
    val date: String,
    val baseStock: Double,
    val currentStock: Double,
    val unit: String
) {
    val status: String get() = if (currentStock <= baseStock * 0.3) "需补货" else "充足"
}

data class MeatPart(
    val name: String,
    val emoji: String,
    val unit: String,
    val records: List<StockRecord>
)

data class WarehouseItem(
    val name: String,
    val emoji: String,
    val parts: List<MeatPart>
)

// ===== 导航状态 =====

private sealed class NavState {
    object CategoryList : NavState()
    data class PartList(val item: WarehouseItem) : NavState()
    data class StockTable(val item: WarehouseItem, val part: MeatPart) : NavState()
}

// ===== 示例数据 =====

private val warehouseItems = listOf(
    WarehouseItem("猪肉", "🥩", listOf(
        MeatPart("五花肉", "🥓", "kg", listOf(
            StockRecord("07-21", 15.0, 12.0, "kg"),
            StockRecord("07-20", 15.0, 11.5, "kg"),
            StockRecord("07-19", 15.0, 5.0, "kg"),
            StockRecord("07-18", 15.0, 14.0, "kg"),
            StockRecord("07-17", 15.0, 13.5, "kg"),
        )),
        MeatPart("里脊肉", "🍖", "kg", listOf(
            StockRecord("07-21", 10.0, 8.0, "kg"),
            StockRecord("07-20", 10.0, 9.0, "kg"),
            StockRecord("07-19", 10.0, 7.5, "kg"),
            StockRecord("07-18", 10.0, 2.5, "kg"),
            StockRecord("07-17", 10.0, 8.8, "kg"),
        )),
        MeatPart("排骨", "🦴", "kg", listOf(
            StockRecord("07-21", 20.0, 18.0, "kg"),
            StockRecord("07-20", 20.0, 15.0, "kg"),
            StockRecord("07-19", 20.0, 16.5, "kg"),
            StockRecord("07-18", 20.0, 19.0, "kg"),
            StockRecord("07-17", 20.0, 17.2, "kg"),
        )),
        MeatPart("猪蹄", "🐷", "kg", listOf(
            StockRecord("07-21", 8.0, 7.0, "kg"),
            StockRecord("07-20", 8.0, 6.5, "kg"),
            StockRecord("07-19", 8.0, 5.0, "kg"),
            StockRecord("07-18", 8.0, 7.5, "kg"),
            StockRecord("07-17", 8.0, 2.0, "kg"),
        )),
        MeatPart("猪肝", "🫁", "kg", listOf(
            StockRecord("07-21", 5.0, 4.5, "kg"),
            StockRecord("07-20", 5.0, 1.2, "kg"),
            StockRecord("07-19", 5.0, 4.8, "kg"),
            StockRecord("07-18", 5.0, 3.0, "kg"),
            StockRecord("07-17", 5.0, 4.0, "kg"),
        )),
    )),
    WarehouseItem("牛肉", "🐂", listOf(
        MeatPart("牛腱子", "🥩", "kg", listOf(
            StockRecord("07-21", 12.0, 10.0, "kg"),
            StockRecord("07-20", 12.0, 9.5, "kg"),
            StockRecord("07-19", 12.0, 3.0, "kg"),
            StockRecord("07-18", 12.0, 11.0, "kg"),
            StockRecord("07-17", 12.0, 8.5, "kg"),
        )),
        MeatPart("牛腩", "🍲", "kg", listOf(
            StockRecord("07-21", 10.0, 8.0, "kg"),
            StockRecord("07-20", 10.0, 9.0, "kg"),
            StockRecord("07-19", 10.0, 7.5, "kg"),
            StockRecord("07-18", 10.0, 6.0, "kg"),
            StockRecord("07-17", 10.0, 8.2, "kg"),
        )),
        MeatPart("牛柳", "🔪", "kg", listOf(
            StockRecord("07-21", 6.0, 5.0, "kg"),
            StockRecord("07-20", 6.0, 4.5, "kg"),
            StockRecord("07-19", 6.0, 5.8, "kg"),
            StockRecord("07-18", 6.0, 3.0, "kg"),
            StockRecord("07-17", 6.0, 5.0, "kg"),
        )),
    )),
    WarehouseItem("鸡肉", "🐔", listOf(
        MeatPart("鸡胸肉", "🍗", "kg", listOf(
            StockRecord("07-21", 8.0, 7.0, "kg"),
            StockRecord("07-20", 8.0, 6.0, "kg"),
            StockRecord("07-19", 8.0, 2.0, "kg"),
            StockRecord("07-18", 8.0, 7.5, "kg"),
            StockRecord("07-17", 8.0, 6.5, "kg"),
        )),
        MeatPart("鸡腿", "🦵", "kg", listOf(
            StockRecord("07-21", 10.0, 9.0, "kg"),
            StockRecord("07-20", 10.0, 8.0, "kg"),
            StockRecord("07-19", 10.0, 9.5, "kg"),
            StockRecord("07-18", 10.0, 5.0, "kg"),
            StockRecord("07-17", 10.0, 8.8, "kg"),
        )),
        MeatPart("鸡翅", "🪽", "kg", listOf(
            StockRecord("07-21", 5.0, 4.0, "kg"),
            StockRecord("07-20", 5.0, 3.5, "kg"),
            StockRecord("07-19", 5.0, 4.8, "kg"),
            StockRecord("07-18", 5.0, 1.2, "kg"),
            StockRecord("07-17", 5.0, 4.5, "kg"),
        )),
    )),
    WarehouseItem("大米", "🍚", listOf(
        MeatPart("东北大米", "🌾", "kg", listOf(
            StockRecord("07-21", 50.0, 45.0, "kg"),
            StockRecord("07-20", 50.0, 48.0, "kg"),
            StockRecord("07-19", 50.0, 40.0, "kg"),
        )),
        MeatPart("泰国香米", "🍙", "kg", listOf(
            StockRecord("07-21", 30.0, 28.0, "kg"),
            StockRecord("07-20", 30.0, 25.0, "kg"),
            StockRecord("07-19", 30.0, 27.0, "kg"),
        )),
    )),
    WarehouseItem("食用油", "🫗", listOf(
        MeatPart("花生油", "🥜", "L", listOf(
            StockRecord("07-21", 20.0, 18.0, "L"),
            StockRecord("07-20", 20.0, 16.0, "L"),
            StockRecord("07-19", 20.0, 19.0, "L"),
        )),
        MeatPart("菜籽油", "🌻", "L", listOf(
            StockRecord("07-21", 15.0, 14.0, "L"),
            StockRecord("07-20", 15.0, 12.0, "L"),
            StockRecord("07-19", 15.0, 13.5, "L"),
        )),
        MeatPart("橄榄油", "🫒", "L", listOf(
            StockRecord("07-21", 5.0, 4.0, "L"),
            StockRecord("07-20", 5.0, 3.5, "L"),
            StockRecord("07-19", 5.0, 1.2, "L"),
        )),
    )),
    WarehouseItem("调料", "🧂", listOf(
        MeatPart("酱油", "🍾", "瓶", listOf(
            StockRecord("07-21", 30.0, 28.0, "瓶"),
            StockRecord("07-20", 30.0, 26.0, "瓶"),
            StockRecord("07-19", 30.0, 25.0, "瓶"),
        )),
        MeatPart("豆瓣酱", "🫙", "瓶", listOf(
            StockRecord("07-21", 20.0, 18.0, "瓶"),
            StockRecord("07-20", 20.0, 6.0, "瓶"),
            StockRecord("07-19", 20.0, 17.0, "瓶"),
        )),
        MeatPart("蚝油", "🦪", "瓶", listOf(
            StockRecord("07-21", 15.0, 13.0, "瓶"),
            StockRecord("07-20", 15.0, 12.0, "瓶"),
            StockRecord("07-19", 15.0, 14.0, "瓶"),
        )),
    )),
)

@Composable
fun WarehouseScreen() {
    var navStack by remember { mutableStateOf(listOf<NavState>(NavState.CategoryList)) }
    val currentNav = navStack.last()

    when (currentNav) {
        is NavState.CategoryList -> {
            CategoryListView(
                items = warehouseItems,
                onItemClick = { item -> navStack = navStack + NavState.PartList(item) }
            )
        }
        is NavState.PartList -> {
            PartListView(
                item = currentNav.item,
                onBack = { navStack = navStack.dropLast(1) },
                onPartClick = { part -> navStack = navStack + NavState.StockTable(currentNav.item, part) }
            )
        }
        is NavState.StockTable -> {
            StockTableView(
                item = currentNav.item,
                part = currentNav.part,
                onBack = { navStack = navStack.dropLast(1) }
            )
        }
    }
}

// ===== 一级：品类列表 =====

@Composable
private fun CategoryListView(
    items: List<WarehouseItem>,
    onItemClick: (WarehouseItem) -> Unit
) {
    val needRestock = items.sumOf { item ->
        item.parts.count { part -> part.records.lastOrNull()?.status == "需补货" }
    }
    val totalParts = items.sumOf { it.parts.size }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("📦 仓库管理", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBox("📦", "$totalParts", "总品类")
                    StatBox("⚠️", "$needRestock", "需补货")
                    StatBox("✅", "${totalParts - needRestock}", "库存正常")
                }
            }
        }

        item {
            Text("库存清单", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
        }

        items(items) { item ->
            val partsNeedRestock = item.parts.count { it.records.lastOrNull()?.status == "需补货" }
            val hasWarning = partsNeedRestock > 0
            val bgColor = if (hasWarning) DiningColors.Warning.copy(alpha = 0.08f) else DiningColors.Surface

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(item) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.emoji, fontSize = 32.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(item.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
                            Text(
                                "${item.parts.size} 种部位",
                                fontSize = 13.sp,
                                color = DiningColors.TextSecondary
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (hasWarning) {
                            Text("⚠️$partsNeedRestock", fontSize = 13.sp, color = DiningColors.Warning, fontWeight = FontWeight.SemiBold)
                        }
                        Text("›", fontSize = 22.sp, color = DiningColors.TextMuted)
                    }
                }
            }
        }
    }
}

// ===== 二级：部位列表 =====

@Composable
private fun PartListView(
    item: WarehouseItem,
    onBack: () -> Unit,
    onPartClick: (MeatPart) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹ 返回",
                    fontSize = 16.sp,
                    color = DiningColors.Primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onBack() }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.emoji, fontSize = 36.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "${item.name} · 部位清单",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DiningColors.TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "共 ${item.parts.size} 种部位",
                fontSize = 14.sp,
                color = DiningColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(item.parts) { part ->
            val lastRecord = part.records.lastOrNull()
            val needRestock = lastRecord?.status == "需补货"
            val bgColor = if (needRestock) DiningColors.Error.copy(alpha = 0.06f) else DiningColors.Surface
            val borderColor = if (needRestock) DiningColors.Error.copy(alpha = 0.2f) else DiningColors.SurfaceVariant

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPartClick(part) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(part.emoji, fontSize = 28.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(part.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                            if (lastRecord != null) {
                                Text(
                                    "当前库存：%.1f %s | 基础：%.1f %s".format(lastRecord.currentStock, part.unit, lastRecord.baseStock, part.unit),
                                    fontSize = 12.sp,
                                    color = DiningColors.TextSecondary
                                )
                            }
                        }
                    }
                    if (lastRecord != null) {
                        val statusText = if (needRestock) "⚠️ 需补货" else "✅ 充足"
                        val statusColor = if (needRestock) DiningColors.Error else DiningColors.Success
                        Column(horizontalAlignment = Alignment.End) {
                            Text(statusText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = statusColor)
                            Text(
                                "%.1f %s".format(lastRecord.currentStock, part.unit),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = DiningColors.TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ===== 三级：库存明细表格 =====

@Composable
private fun StockTableView(
    item: WarehouseItem,
    part: MeatPart,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        // 顶部返回
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ 返回部位",
                fontSize = 16.sp,
                color = DiningColors.Primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onBack() }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.emoji, fontSize = 36.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(part.emoji, fontSize = 30.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "${item.name} · ${part.name}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DiningColors.TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "库存基础（固定）：%.1f %s".format(part.records.firstOrNull()?.baseStock ?: 0.0, part.unit),
            fontSize = 14.sp,
            color = DiningColors.TextSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 表格
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
        ) {
            Column {
                // 表头
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DiningColors.Primary.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TableHeader("日期", 0.18f)
                    TableHeader("库存基础", 0.22f)
                    TableHeader("当前库存", 0.22f)
                    TableHeader("需补货", 0.2f)
                    TableHeader("状态", 0.18f)
                }

                HorizontalDivider(color = DiningColors.Primary.copy(alpha = 0.3f), thickness = 1.dp)

                // 数据行
                part.records.forEach { record ->
                    val needRestock = record.status == "需补货"
                    val rowBg = if (needRestock) DiningColors.Error.copy(alpha = 0.04f) else DiningColors.Surface
                    val rowCount = part.records.size

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell(record.date, 0.18f, DiningColors.TextPrimary, 13.sp)
                        TableCell("%.1f %s".format(record.baseStock, record.unit), 0.22f, DiningColors.TextSecondary, 12.sp)
                        TableCell("%.1f %s".format(record.currentStock, record.unit), 0.22f, DiningColors.TextPrimary, 13.sp, bold = true)

                        // 需补货：显示缺口数量
                        val gap = record.baseStock - record.currentStock
                        if (gap > 0) {
                            TableCell("%.1f %s".format(gap, record.unit), 0.2f, DiningColors.Error, 12.sp, bold = true)
                        } else {
                            TableCell("—", 0.2f, DiningColors.TextMuted, 12.sp)
                        }

                        val statusText = if (needRestock) "⚠️ 需补货" else "✅ 充足"
                        val statusColor = if (needRestock) DiningColors.Error else DiningColors.Success
                        TableCell(statusText, 0.18f, statusColor, 12.sp, bold = true)
                    }

                    if (record != part.records.last()) {
                        HorizontalDivider(color = DiningColors.SurfaceVariant, thickness = 0.5.dp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 底部汇总
        val lastRecord = part.records.lastOrNull()
        if (lastRecord != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (lastRecord.status == "需补货") DiningColors.Error.copy(alpha = 0.08f)
                    else DiningColors.Success.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("最新库存", fontSize = 14.sp, color = DiningColors.TextSecondary)
                    Text(
                        "当前 %.1f / 基础 %.1f %s".format(lastRecord.currentStock, lastRecord.baseStock, part.unit),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = DiningColors.TextPrimary
                    )
                    val summaryText = if (lastRecord.status == "需补货") {
                        "⚠️ 需补 ${"%.1f".format(lastRecord.baseStock - lastRecord.currentStock)} ${part.unit}"
                    } else {
                        "✅ 库存充足"
                    }
                    Text(
                        summaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (lastRecord.status == "需补货") DiningColors.Error else DiningColors.Success
                    )
                }
            }
        }
    }
}

// ===== 共用组件 =====

@Composable
private fun StatBox(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 26.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.Primary)
        Text(label, fontSize = 12.sp, color = DiningColors.TextMuted)
    }
}

@Composable
private fun TableHeader(text: String, weight: Float) {
    Box(modifier = Modifier.fillMaxWidth(weight)) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = DiningColors.Primary,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun TableCell(text: String, weight: Float, color: androidx.compose.ui.graphics.Color, fontSize: androidx.compose.ui.unit.TextUnit, bold: Boolean = false) {
    Box(modifier = Modifier.fillMaxWidth(weight)) {
        Text(
            text,
            fontSize = fontSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
            textAlign = TextAlign.Start,
            maxLines = 1
        )
    }
}
