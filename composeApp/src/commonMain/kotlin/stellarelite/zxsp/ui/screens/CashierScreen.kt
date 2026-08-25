package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import stellarelite.zxsp.generated.resources.Res
import stellarelite.zxsp.generated.resources.alipay_qr
import stellarelite.zxsp.generated.resources.duitnow_mybqr
import stellarelite.zxsp.generated.resources.tng_qr
import stellarelite.zxsp.platform.rememberCamera
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
    TableBill(13, emptyList()),
    TableBill(14, emptyList()),
    TableBill(15, emptyList()),
    TableBill(16, emptyList()),
)

@Composable
fun CashierScreen() {
    var tables by remember { mutableStateOf(initialTables) }
    var selectedTableNo by remember { mutableStateOf(1) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var qrMethod by remember { mutableStateOf<PaymentMethod?>(null) }
    var showCashDialog by remember { mutableStateOf(false) }
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

        // 桌台选择 - 4x4 可视化网格（1~16 从左到右、从上到下）
        Text(
            "选择桌台",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = DiningColors.TextPrimary
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (row in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0 until 4) {
                        val tableNo = row * 4 + col + 1
                        val table = tables[tableNo - 1]
                        TableCell(
                            table = table,
                            selected = tableNo == selectedTableNo,
                            onClick = { selectedTableNo = tableNo },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
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
                                    if (method == PaymentMethod.Cash) {
                                        showCashDialog = true
                                    } else {
                                        qrMethod = method
                                    }
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

    // 扫码支付弹窗（TNG / DuitNow / 支付宝）
    qrMethod?.let { method ->
        QrPaymentDialog(
            method = method,
            amount = total,
            onDismiss = { qrMethod = null },
            onComplete = {
                qrMethod = null
                tables = tables.map {
                    if (it.tableNo == selectedTableNo) it.copy(items = emptyList()) else it
                }
                completedMethod = method
            }
        )
    }

    // 现金支付弹窗
    if (showCashDialog) {
        CashPaymentDialog(
            amount = total,
            onDismiss = { showCashDialog = false },
            onComplete = {
                showCashDialog = false
                tables = tables.map {
                    if (it.tableNo == selectedTableNo) it.copy(items = emptyList()) else it
                }
                completedMethod = PaymentMethod.Cash
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
private fun QrPaymentDialog(
    method: PaymentMethod,
    amount: Double,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    var receipt by remember { mutableStateOf<ImageBitmap?>(null) }
    val takePhoto = rememberCamera { receipt = it }

    val qrPainter = when (method) {
        PaymentMethod.TNG -> painterResource(Res.drawable.tng_qr)
        PaymentMethod.DUITNOW -> painterResource(Res.drawable.duitnow_mybqr)
        PaymentMethod.Alipay -> painterResource(Res.drawable.alipay_qr)
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(
                    "${method.emoji} ${method.label}",
                    color = DiningColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "请顾客扫码支付 RM %.2f".format(amount),
                    fontSize = 14.sp,
                    color = DiningColors.TextSecondary
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 付款二维码
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DiningColors.SurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrPainter != null) {
                        Image(
                            painter = qrPainter,
                            contentDescription = "付款二维码",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            "付款二维码",
                            fontSize = 14.sp,
                            color = DiningColors.TextSecondary
                        )
                    }
                }

                // 收据拍照
                if (receipt != null) {
                    Image(
                        bitmap = receipt!!,
                        contentDescription = "收据",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Button(
                    onClick = takePhoto,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DiningColors.SurfaceVariant)
                ) {
                    Text(
                        "📷 ${if (receipt == null) "拍照收据" else "重新拍照"}",
                        color = DiningColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onComplete,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)
            ) {
                Text("完成", color = DiningColors.Surface, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) }
        }
    )
}

@Composable
private fun CashPaymentDialog(
    amount: Double,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    var cashText by remember { mutableStateOf("") }
    val cashGiven = cashText.toDoubleOrNull() ?: 0.0
    val change = cashGiven - amount

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "💵 现金收款",
                color = DiningColors.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 消费金额
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("消费金额", fontSize = 15.sp, color = DiningColors.TextSecondary)
                    Text(
                        "RM %.2f".format(amount),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DiningColors.TextPrimary
                    )
                }

                // 顾客给多少现金
                OutlinedTextField(
                    value = cashText,
                    onValueChange = { cashText = it },
                    label = { Text("顾客给多少现金 (RM)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                // 找零
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("找零", fontSize = 15.sp, color = DiningColors.TextSecondary)
                    Text(
                        if (change >= 0) "RM %.2f".format(change) else "现金不足",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (change >= 0) DiningColors.Success else DiningColors.Error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onComplete,
                enabled = cashGiven >= amount && amount > 0,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)
            ) {
                Text("完成", color = DiningColors.Surface, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) }
        }
    )
}

@Composable
private fun TableCell(table: TableBill, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val hasOrder = table.total > 0
    Column(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DiningColors.Primary else DiningColors.Surface)
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
                fontSize = 10.sp,
                color = if (selected) DiningColors.Surface.copy(alpha = 0.9f) else DiningColors.Primary
            )
        } else {
            Text(
                "空桌",
                fontSize = 10.sp,
                color = if (selected) DiningColors.Surface.copy(alpha = 0.7f) else DiningColors.TextMuted
            )
        }
    }
}
