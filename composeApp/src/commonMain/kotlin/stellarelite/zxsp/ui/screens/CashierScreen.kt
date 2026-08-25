package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stellarelite.zxsp.ui.theme.DiningColors

data class OrderItem(
    val name: String,
    val price: Double,
    var quantity: Int,
    val emoji: String
)

data class TableBill(
    val tableNo: Int,
    val items: List<OrderItem>
) {
    val total: Double get() = items.sumOf { it.price * it.quantity }
    val itemCount: Int get() = items.sumOf { it.quantity }
}

enum class PaymentMethod(val label: String, val emoji: String) {
    TNG("TNG eWallet", "📱"),
    DUITNOW("DuitNow", "🏦"),
    Cash("现金", "💵"),
    Alipay("支付宝", "🔷")
}

private val initialTables = listOf(
    TableBill(1, listOf(
        OrderItem("红烧排骨", 38.0, 2, "🍖"),
        OrderItem("蛋炒饭", 16.0, 1, "🍚"),
        OrderItem("酸梅汤", 10.0, 3, "🍹")
    )),
    TableBill(2, listOf(
        OrderItem("宫保鸡丁", 32.0, 1, "🍗"),
        OrderItem("白米饭", 3.0, 2, "🍚")
    )),
    TableBill(3, listOf(
        OrderItem("清蒸鲈鱼", 58.0, 1, "🐟"),
        OrderItem("蒜蓉时蔬", 18.0, 1, "🥬"),
        OrderItem("菊花茶", 8.0, 2, "🍵")
    )),
    TableBill(4, listOf(
        OrderItem("麻辣香锅", 68.0, 1, "🌶️")
    )),
    TableBill(5, listOf(
        OrderItem("番茄牛腩", 42.0, 1, "🍅"),
        OrderItem("米饭", 3.0, 1, "🍚")
    )),
    TableBill(6, emptyList()),
    TableBill(7, emptyList()),
    TableBill(8, emptyList()),
    TableBill(9, emptyList()),
    TableBill(10, emptyList()),
    TableBill(11, emptyList()),
    TableBill(12, emptyList()),
)

@Composable
fun CashierScreen() {
    var tables by remember { mutableStateOf(initialTables) }
    var selectedTableNo by remember { mutableStateOf(1) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var completedMethod by remember { mutableStateOf<PaymentMethod?>(null) }

    val selectedTable = tables.firstOrNull { it.tableNo == selectedTableNo } ?: tables.first()
    val total = selectedTable.total

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Column {
            Text(
                "💰 收银台",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = DiningColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "选择桌台进行收银",
                fontSize = 14.sp,
                color = DiningColors.TextSecondary
            )
        }

        // 桌台选择
        Text(
            "选择桌台",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = DiningColors.TextPrimary
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(tables) { table ->
                TableChip(
                    table = table,
                    selected = table.tableNo == selectedTableNo,
                    onClick = { selectedTableNo = table.tableNo }
                )
            }
        }

        // 消费金额卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DiningColors.Primary)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "桌台 $selectedTableNo · 消费金额",
                    fontSize = 14.sp,
                    color = DiningColors.Surface.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "RM %.2f".format(total),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = DiningColors.Surface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "共 ${selectedTable.itemCount} 件商品",
                    fontSize = 13.sp,
                    color = DiningColors.Surface.copy(alpha = 0.8f)
                )
            }
        }

        // 订单明细
        if (total > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "订单明细",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = DiningColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    selectedTable.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.emoji, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(item.name, fontSize = 15.sp, color = DiningColors.TextPrimary)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("x${item.quantity}", fontSize = 13.sp, color = DiningColors.TextMuted)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    "RM%.2f".format(item.price * item.quantity),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DiningColors.TextPrimary
                                )
                            }
                        }
                        if (item != selectedTable.items.last()) {
                            HorizontalDivider(color = DiningColors.SurfaceVariant, thickness = 0.5.dp)
                        }
                    }
                }
            }
        } else {
            // 空桌提示
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
            ) {
                Text(
                    "该桌台暂无消费记录",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    color = DiningColors.TextMuted
                )
            }
        }

        // 收银按钮
        Button(
            onClick = { showPaymentDialog = true },
            enabled = total > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DiningColors.Primary,
                disabledContainerColor = DiningColors.TextMuted.copy(alpha = 0.3f),
                disabledContentColor = DiningColors.Surface.copy(alpha = 0.6f)
            )
        ) {
            Text("💳 收银", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }

    // 付款方式弹窗
    if (showPaymentDialog) {
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            containerColor = DiningColors.Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "选择付款方式",
                    color = DiningColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        "桌台 $selectedTableNo · RM %.2f".format(total),
                        fontSize = 14.sp,
                        color = DiningColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PaymentMethod.entries.forEach { method ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showPaymentDialog = false
                                    completedMethod = method
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(method.emoji, fontSize = 22.sp)
                            Text(
                                method.label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = DiningColors.TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text("取消", color = DiningColors.TextMuted)
                }
            }
        )
    }

    // 收款成功弹窗
    completedMethod?.let { method ->
        AlertDialog(
            onDismissRequest = { completedMethod = null },
            containerColor = DiningColors.Surface,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "✅ 收款成功",
                    color = DiningColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("桌台 $selectedTableNo", fontSize = 14.sp, color = DiningColors.TextPrimary)
                    Text(
                        "金额 RM %.2f".format(total),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = DiningColors.Primary
                    )
                    Text(
                        "付款方式 ${method.emoji} ${method.label}",
                        fontSize = 14.sp,
                        color = DiningColors.TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tables = tables.map {
                            if (it.tableNo == selectedTableNo) it.copy(items = emptyList()) else it
                        }
                        completedMethod = null
                    }
                ) {
                    Text("确定", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
private fun TableChip(table: TableBill, selected: Boolean, onClick: () -> Unit) {
    val hasOrder = table.total > 0
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DiningColors.Primary else DiningColors.Surface)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "桌 ${table.tableNo}",
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) DiningColors.Surface else DiningColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        if (hasOrder) {
            Text(
                "RM%.2f".format(table.total),
                fontSize = 11.sp,
                color = if (selected) DiningColors.Surface.copy(alpha = 0.9f) else DiningColors.Primary
            )
        } else {
            Text(
                "空桌",
                fontSize = 11.sp,
                color = if (selected) DiningColors.Surface.copy(alpha = 0.7f) else DiningColors.TextMuted
            )
        }
    }
}
