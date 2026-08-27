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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import stellarelite.zxsp.network.CustomerOrder
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.ui.theme.DiningColors

private sealed class OrdersNav {
    object List : OrdersNav()
    object NewOrder : OrdersNav()
    data class Detail(val order: CustomerOrder) : OrdersNav()
}

@Composable
fun OrdersScreen() {
    var nav by remember { mutableStateOf<OrdersNav>(OrdersNav.List) }

    when (val n = nav) {
        is OrdersNav.List -> OrderListView(
            onNew = { nav = OrdersNav.NewOrder },
            onDetail = { nav = OrdersNav.Detail(it) }
        )
        is OrdersNav.NewOrder -> NewOrderScreen(onBack = { nav = OrdersNav.List })
        is OrdersNav.Detail -> OrderDetailScreen(order = n.order, onBack = { nav = OrdersNav.List })
    }
}

@Composable
private fun OrderListView(onNew: () -> Unit, onDetail: (CustomerOrder) -> Unit) {
    val scope = rememberCoroutineScope()
    var orders by remember { mutableStateOf<List<CustomerOrder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { SupabaseClient.fetchOrders() }
                .onSuccess { orders = it }
                .onFailure { error = it.message ?: "加载失败" }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🧾 订单管理", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { load() }) { Text("🔄", fontSize = 18.sp) }
            Button(onClick = onNew, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)) {
                Text("＋ 新建", color = DiningColors.Surface, fontWeight = FontWeight.Bold)
            }
        }

        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DiningColors.Primary)
            }
            error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️ $error", color = DiningColors.Error, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { load() }) { Text("重试") }
                }
            }
            orders.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无订单", color = DiningColors.TextMuted, fontSize = 14.sp)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(orders, key = { it.id }) { order ->
                    OrderCard(order, onClick = { onDetail(order) })
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: CustomerOrder, onClick: () -> Unit) {
    val statusLabel = when (order.payment_status) {
        "paid" -> "已付清"
        "partial" -> "部分付"
        else -> "未付"
    }
    val statusColor = when (order.payment_status) {
        "paid" -> DiningColors.Success
        "partial" -> DiningColors.Warning
        else -> DiningColors.Error
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(order.order_no, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
                Text(
                    order.customer_name ?: "外卖订单",
                    fontSize = 13.sp,
                    color = DiningColors.TextSecondary
                )
                Text(
                    order.order_datetime?.take(16)?.replace("T", " ") ?: "",
                    fontSize = 11.sp,
                    color = DiningColors.TextMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("RM%.2f".format(order.total_amount_myr), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.Primary)
                Text(statusLabel, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OrderDetailScreen(order: CustomerOrder, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var showPay by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = DiningColors.Primary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("订单详情", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
        }
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("订单号", order.order_no)
                DetailRow("收据号", order.receipt_no)
                DetailRow("顾客", order.customer_name ?: "—")
                DetailRow("电话", order.customer_phone ?: "—")
                DetailRow("桌台", if (order.table_id != null) "桌 #${order.table_id}" else "外卖")
                DetailRow("状态", when (order.payment_status) {
                    "paid" -> "已付清"; "partial" -> "部分付"; else -> "未付"
                })
                DetailRow("总金额", "RM%.2f".format(order.total_amount_myr))
                order.notes?.takeIf { it.isNotBlank() }?.let { DetailRow("备注", it) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 收款按钮
        if (order.payment_status != "paid") {
            Button(
                onClick = { showPay = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)
            ) {
                Text("💳 录入收款", color = DiningColors.Surface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showPay) {
        PaymentDialog(order = order, onDismiss = { showPay = false }, onDone = { onBack() })
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = DiningColors.TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
    }
}

@Composable
private fun PaymentDialog(order: CustomerOrder, onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(order.total_amount_myr.toString()) }
    var method by remember { mutableStateOf("cash") }
    var ref by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val payAmount = amount.toDoubleOrNull()
    val canSave = payAmount != null && payAmount > 0 && ref.isNotBlank() && !saving

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("录入收款", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("实收金额 (RM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // 付款方式
                Text("付款方式", fontSize = 12.sp, color = DiningColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("cash" to "现金", "duitnow" to "DuitNow", "tng_ewallet" to "TNG", "alipay" to "支付宝").forEach { (v, l) ->
                        FilterChip(
                            selected = method == v,
                            onClick = { method = v },
                            label = { Text(l) }
                        )
                    }
                }
                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text(if (method == "cash") "现金编号 CASH-YYYYMMDD-序号" else "交易号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error!!, color = DiningColors.Error, fontSize = 13.sp)
                if (saving) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DiningColors.Primary)
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        val p = stellarelite.zxsp.network.PaymentRecord(
                            order_id = order.id,
                            pay_amount_myr = payAmount!!,
                            pay_method = method,
                            transaction_ref = ref.trim(),
                            received_by_staff_id = stellarelite.zxsp.data.SessionManager.staffId ?: 0,
                            transaction_datetime = currentIso()
                        )
                        val r = SupabaseClient.insertPayment(p)
                        saving = false
                        if (r != null) onDone() else error = "收款失败"
                    }
                }
            ) {
                Text("保存", color = if (canSave) DiningColors.Primary else DiningColors.TextMuted, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) } }
    )
}
