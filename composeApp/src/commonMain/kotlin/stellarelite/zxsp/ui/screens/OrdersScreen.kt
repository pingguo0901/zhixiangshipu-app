package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.painterResource
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.generated.resources.*
import stellarelite.zxsp.network.CustomerOrder
import stellarelite.zxsp.network.PaymentRecord
import stellarelite.zxsp.network.ReceiptItem
import stellarelite.zxsp.network.ReceiptMaster
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.platform.printReceiptText
import stellarelite.zxsp.platform.rememberCamera
import stellarelite.zxsp.platform.toJpegBytes
import stellarelite.zxsp.ui.theme.DiningColors
import stellarelite.zxsp.util.ReceiptFormatter

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
    var showReceipt by remember { mutableStateOf(false) }
    var receiptData by remember { mutableStateOf<ReceiptData?>(null) }
    var tableNo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(order.table_id) {
        tableNo = order.table_id?.let { id ->
            runCatching { SupabaseClient.fetchTables().firstOrNull { it.id == id }?.table_no }.getOrNull()
        }
    }

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
                DetailRow("桌台", tableNo ?: (if (order.table_id != null) "桌 #${order.table_id}" else "外卖"))
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
        PaymentDialog(
            order = order,
            onDismiss = { showPay = false },
            onPaid = { data ->
                showPay = false
                receiptData = data
                showReceipt = true
            }
        )
    }

    if (showReceipt && receiptData != null) {
        ReceiptDialog(
            data = receiptData!!,
            onPrint = { printReceiptText(receiptData!!.toReceiptText()) },
            onDone = { showReceipt = false; onBack() }
        )
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
fun PaymentDialog(order: CustomerOrder, onDismiss: () -> Unit, onPaid: (ReceiptData) -> Unit) {
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf("cash") }
    var cashReceived by remember { mutableStateOf("") }
    var receiptBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val total = order.total_amount_myr
    val received = cashReceived.toDoubleOrNull() ?: 0.0
    val change = (received - total).coerceAtLeast(0.0)
    val canSave = !saving && when (method) {
        "cash" -> received >= total
        else -> true
    }

    val takePhoto = rememberCamera { bitmap -> receiptBitmap = bitmap }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("结账", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 付款方式
                Text("付款方式", fontSize = 12.sp, color = DiningColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("cash" to "现金", "duitnow" to "DuitNow", "tng_ewallet" to "TNG", "alipay" to "支付宝").forEach { (v, l) ->
                        FilterChip(selected = method == v, onClick = { method = v }, label = { Text(l) })
                    }
                }

                if (method == "cash") {
                    Text("消费金额：RM%.2f".format(total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
                    OutlinedTextField(
                        value = cashReceived,
                        onValueChange = { cashReceived = it },
                        label = { Text("客户给多少 (RM)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (received >= total) "找零：RM%.2f".format(change) else "还需收 RM%.2f".format(total - received),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (received >= total) DiningColors.Success else DiningColors.Error
                    )
                } else {
                    val qr = when (method) {
                        "duitnow" -> Res.drawable.duitnow_tng_qr
                        "tng_ewallet" -> Res.drawable.duitnow_tng_qr
                        else -> Res.drawable.alipay_qr
                    }
                    Image(
                        painter = painterResource(qr),
                        contentDescription = "付款二维码",
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                    OutlinedButton(onClick = { takePhoto() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (receiptBitmap == null) "📷 拍收据" else "📷 重拍收据", color = DiningColors.Primary)
                    }
                    receiptBitmap?.let {
                        Image(it, contentDescription = "收据", modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                }

                if (error != null) Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp)
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
                        var receiptUrl: String? = null
                        if (method != "cash" && receiptBitmap != null) {
                            val bytes = receiptBitmap!!.toJpegBytes()
                            if (bytes != null) {
                                val path = "receipt_${order.id}_${Clock.System.now().toEpochMilliseconds()}.jpg"
                                receiptUrl = SupabaseClient.uploadFile("receipts", path, bytes)
                            }
                        }
                        val payMode = mapPayMode(method)
                        val now = currentIso()
                        val lines = parseOrderLines(order.order_items)
                        val amountReceived = if (method == "cash") received else total
                        val changeGiven = if (method == "cash") change else 0.0

                        val master = ReceiptMaster(
                            trans_datetime = now,
                            sub_total = total,
                            discount = 0.0,
                            total_amount = total,
                            payment_mode = payMode,
                            amount_received = amountReceived,
                            change_given = changeGiven,
                            operator = SessionManager.staffName,
                            remark = order.order_no
                        )
                        val m = SupabaseClient.insertReceiptMaster(master)
                        if (m == null) {
                            saving = false
                            error = "收款失败：${SupabaseClient.lastError ?: "未知原因"}"
                            return@launch
                        }
                        val receiptNo = m.receipt_no

                        lines.forEach { line ->
                            SupabaseClient.insertReceiptItem(
                                ReceiptItem(
                                    receipt_no = receiptNo,
                                    item_name = line.name,
                                    qty = line.qty,
                                    unit_price = line.unitPrice,
                                    item_amount = line.amount
                                )
                            )
                        }

                        val p = PaymentRecord(
                            order_id = order.id,
                            pay_amount_myr = total,
                            pay_method = method,
                            transaction_ref = "",
                            receipt_attachment_url = receiptUrl,
                            received_by_staff_id = SupabaseClient.currentStaffId(),
                            transaction_datetime = now
                        )
                        val r = SupabaseClient.insertPayment(p)
                        saving = false
                        if (r != null) {
                            if (order.table_id != null) {
                                SupabaseClient.setTableStatus(order.table_id!!, "free")
                            }
                            onPaid(
                                ReceiptData(
                                    receiptNo = receiptNo,
                                    transDatetime = now,
                                    items = lines,
                                    subTotal = total,
                                    discount = 0.0,
                                    total = total,
                                    paymentMode = payMode,
                                    amountReceived = amountReceived,
                                    changeGiven = changeGiven
                                )
                            )
                        } else {
                            error = "收款失败：${SupabaseClient.lastError ?: "未知原因"}"
                        }
                    }
                }
            ) {
                Text("结账", color = if (canSave) DiningColors.Primary else DiningColors.TextMuted, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) } }
    )
}

data class ReceiptLine(
    val name: String,
    val qty: Int,
    val unitPrice: Double,
    val amount: Double
)

data class ReceiptData(
    val receiptNo: String,
    val transDatetime: String,
    val items: List<ReceiptLine>,
    val subTotal: Double,
    val discount: Double,
    val total: Double,
    val paymentMode: String,
    val amountReceived: Double,
    val changeGiven: Double
) {
    fun toReceiptText(): String {
        val W = ReceiptFormatter.TOTAL_WIDTH
        val r = mutableListOf<String>()

        // 头部
        r.add("=".repeat(W))
        r.add(ReceiptFormatter.padCenter("OFFICIAL SALES RECEIPT", W))
        r.add("")
        r.add(ReceiptFormatter.padCenter("ZHI XIANG FOOD ENTERPRISE", W))
        r.add(ReceiptFormatter.padCenter("(Trade Name: 炙巷食铺)", W))
        r.add(ReceiptFormatter.padCenter("SSM BRN: 【12位BRN NUMBER】", W))
        r.add(ReceiptFormatter.padCenter("2313, Jalan Dato Sulaiman,", W))
        r.add(ReceiptFormatter.padCenter("Taman Abad, 80250 Johor Bahru,", W))
        r.add(ReceiptFormatter.padCenter("Johor Darul Ta'zim", W))
        r.add(ReceiptFormatter.padCenter("WHATSAPP: +852 5140 3695", W))
        r.add(ReceiptFormatter.padCenter("Business Hour: 6PM-6AM", W))
        r.add("=".repeat(W))

        // 单号与时间
        r.add(ReceiptFormatter.padRight("Receipt No.: $receiptNo", W))
        r.add(ReceiptFormatter.padRight("Date/Time: ${formatDateTimeMy(transDatetime)}", W))
        r.add("=".repeat(W))

        // 表头 (Item左对齐24，其余右对齐 5, 9, 10)
        r.add(ReceiptFormatter.padRight("Item", 24) + ReceiptFormatter.padLeft("Qty", 5) + ReceiptFormatter.padLeft("Unit", 9) + ReceiptFormatter.padLeft("Amount", 10))
        r.add("-".repeat(W))

        // 商品明细注入
        items.forEach { line ->
            r.add(ReceiptFormatter.generateItemRow(line.name, null, line.qty.toString(), "%.2f".format(line.unitPrice), "%.2f".format(line.amount)))
        }
        r.add("-".repeat(W))

        // 财务结算（完美垂直靠右对齐）
        r.add(ReceiptFormatter.generateTotalRow("Sub Total", "RM", "%.2f".format(subTotal)))
        r.add(ReceiptFormatter.generateTotalRow("Discount", "RM", "%.2f".format(discount)))
        r.add("-".repeat(W))
        r.add(ReceiptFormatter.generateTotalRow("TOTAL AMOUNT", "RM", "%.2f".format(total)))
        r.add("")

        // 支付与找零
        r.add(ReceiptFormatter.padRight("Payment Mode: $paymentMode", W))
        r.add(ReceiptFormatter.generateTotalRow("Amount Received", "RM", "%.2f".format(amountReceived)))
        r.add(ReceiptFormatter.generateTotalRow("Change Given", "RM", "%.2f".format(changeGiven)))
        r.add("=".repeat(W))

        // 页脚
        r.add(ReceiptFormatter.padRight("Currency: MYR (Ringgit Malaysia)", W))
        r.add("")
        r.add(ReceiptFormatter.padCenter("* Goods Sold Are Non-Refundable", W))
        r.add(ReceiptFormatter.padCenter("Thank You For Your Patronage", W))
        r.add("=".repeat(W))
        r.add("\n\n\n") // 留出撕纸空白

        return r.joinToString("\n")
    }
}

// 日期转马来西亚格式 dd/MM/yyyy HH:mm
private fun formatDateTimeMy(iso: String): String {
    val datePart = iso.take(10)
    val timePart = if (iso.length >= 16) iso.substring(11, 16) else ""
    val parts = datePart.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]} $timePart" else iso
}

private fun mapPayMode(method: String): String = when (method) {
    "cash" -> "CASH"
    "duitnow" -> "DUITNOW"
    "tng_ewallet" -> "TNG"
    "alipay" -> "ALIPAY"
    else -> "CASH"
}

private fun parseOrderLines(items: JsonElement): List<ReceiptLine> {
    return items.jsonArray.mapNotNull { el ->
        val obj = el.jsonObject
        val name = obj["item_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val qty = obj["quantity"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val price = obj["unit_price_myr"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        ReceiptLine(name, qty, price, qty * price)
    }
}

@Composable
fun ReceiptDialog(data: ReceiptData, onPrint: () -> Unit, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(16.dp),
        title = { Text("收据", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())
            ) {
                Text(
                    data.toReceiptText(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DiningColors.TextPrimary,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Row {
                OutlinedButton(onClick = onPrint) { Text("🖨 打印收据", color = DiningColors.Primary) }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDone) { Text("完成", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
            }
        }
    )
}
