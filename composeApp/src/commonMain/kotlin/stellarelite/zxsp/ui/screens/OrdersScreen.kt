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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.painterResource
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.generated.resources.*
import stellarelite.zxsp.network.CustomerOrder
import stellarelite.zxsp.network.MenuItem
import stellarelite.zxsp.network.PaymentRecord
import stellarelite.zxsp.network.ReceiptItem
import stellarelite.zxsp.network.ReceiptMaster
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.TableList
import stellarelite.zxsp.platform.printReceiptText
import stellarelite.zxsp.platform.rememberCamera
import stellarelite.zxsp.platform.toImageBitmap
import stellarelite.zxsp.platform.toJpegBytes
import stellarelite.zxsp.ui.theme.DiningColors
import stellarelite.zxsp.util.ReceiptFormatter
import stellarelite.zxsp.util.decodeJwtSub

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
    var tableMap by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { SupabaseClient.fetchOrders() }
                .onSuccess { orders = it }
                .onFailure { error = it.message ?: "加载失败" }
            runCatching { SupabaseClient.fetchTables() }
                .onSuccess { tableMap = it.associate { t -> t.id to t.table_no } }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = DiningColors.TextPrimary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("订单管理", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { load() }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = DiningColors.Primary) }
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
                    OrderCard(order, tableMap[order.table_id], onClick = { onDetail(order) })
                }
            }
        }
    }
}

@Composable
private fun OrderCard(order: CustomerOrder, tableNo: String?, onClick: () -> Unit) {
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
    // 堂食/外卖判断：有桌台就是堂食，否则才是外卖
    val orderType = if (order.table_id != null) "堂食 · 桌 ${tableNo ?: order.table_id}" else "外卖订单"
    val orderTypeColor = if (order.table_id != null) DiningColors.Primary else DiningColors.TextSecondary
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
                    if (order.table_id != null) orderType else (order.customer_name ?: orderType),
                    fontSize = 13.sp,
                    color = orderTypeColor
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
    var currentOrder by remember { mutableStateOf(order) }
    var showPay by remember { mutableStateOf(false) }
    var showReceipt by remember { mutableStateOf(false) }
    var receiptData by remember { mutableStateOf<ReceiptData?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var tableNo by remember { mutableStateOf<String?>(null) }
    var receipt by remember { mutableStateOf<ReceiptMaster?>(null) }
    var payment by remember { mutableStateOf<PaymentRecord?>(null) }
    var receiptPhoto by remember { mutableStateOf<ImageBitmap?>(null) }
    var showFullImage by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var showKitchen by remember { mutableStateOf(false) }

    LaunchedEffect(currentOrder.table_id, currentOrder.order_no) {
        tableNo = currentOrder.table_id?.let { id ->
            runCatching { SupabaseClient.fetchTables().firstOrNull { it.id == id }?.table_no }.getOrNull()
        }
        receipt = runCatching { SupabaseClient.fetchReceiptByOrderNo(currentOrder.order_no) }.getOrNull()
    }

    // 进入详情页时强制刷新角色 + 查付款记录（含已上传收据）
    LaunchedEffect(currentOrder.id) {
        val uid = SessionManager.authUid ?: decodeJwtSub(SessionManager.accessToken ?: "")
        val staff = uid?.let { runCatching { SupabaseClient.fetchMyStaff(it) }.getOrNull() }
        if (staff != null && staff.is_active) {
            SessionManager.setSession(SessionManager.accessToken, staff.id, staff.staff_name, staff.role, uid)
        }
        val p = runCatching { SupabaseClient.fetchPaymentByOrder(currentOrder.id) }.getOrNull()
        payment = p
        if (p != null && p.pay_method != "cash" && p.receipt_attachment_url != null) {
            receiptPhoto = runCatching {
                SupabaseClient.downloadFile(p.receipt_attachment_url)?.toImageBitmap()
            }.getOrNull()
        }
    }

    val lines = remember(currentOrder.order_items) { parseOrderLines(currentOrder.order_items) }
    val isTakeaway = currentOrder.table_id == null

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // 标题居中，返回在左，编辑按钮（仅 Admin）在右上角
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Text("‹ 返回", color = DiningColors.Primary) }
            Text(
                "订单详情",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DiningColors.TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
            if (SessionManager.isAdmin) {
                TextButton(onClick = { showEdit = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = DiningColors.Primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑", color = DiningColors.Primary)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("订单号", currentOrder.order_no)
                DetailRow("收据号", currentOrder.receipt_no.ifBlank { "—" })
                DetailRow("类型", if (isTakeaway) "外卖" else "堂食")
                DetailRow("顾客", currentOrder.customer_name ?: "—")
                DetailRow("电话", currentOrder.customer_phone ?: "—")
                DetailRow("桌台", if (isTakeaway) "外卖" else (tableNo ?: "桌 #${currentOrder.table_id}"))
                DetailRow("状态", when (currentOrder.payment_status) {
                    "paid" -> "已付清"; "partial" -> "部分付"; else -> "未付"
                })

                // 菜品明细
                if (lines.isNotEmpty()) {
                    HorizontalDivider(color = DiningColors.SurfaceVariant)
                    Text("菜品明细", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextSecondary)
                    lines.forEach { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(line.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                                Text("${line.qty} × RM%.2f".format(line.unitPrice), fontSize = 12.sp, color = DiningColors.TextMuted)
                            }
                            Text("RM%.2f".format(line.amount), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                        }
                    }
                }

                HorizontalDivider(color = DiningColors.SurfaceVariant)
                DetailRow("折扣", "RM%.2f".format(receipt?.discount ?: 0.0))
                DetailRow("付款方式", receipt?.payment_mode?.ifBlank { "未付" } ?: "未付")
                DetailRow("顾客给多少", "RM%.2f".format(receipt?.amount_received ?: 0.0))
                DetailRow("找零", "RM%.2f".format(receipt?.change_given ?: 0.0))
                DetailRow("总金额", "RM%.2f".format(currentOrder.total_amount_myr))
                currentOrder.notes?.takeIf { it.isNotBlank() }?.let { DetailRow("备注", it) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 非现金付款：显示已上传的收据照片（点击放大）
        if (receiptPhoto != null) {
            Text("已上传收据照片", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                bitmap = receiptPhoto!!,
                contentDescription = "收据照片",
                modifier = Modifier.fillMaxWidth().height(180.dp).clickable { showFullImage = true }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 打印厨房单
        OutlinedButton(
            onClick = { showKitchen = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.Print, contentDescription = null, tint = DiningColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("打印厨房单", color = DiningColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 打印收据两个按钮
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { printReceiptText(buildUnpaidReceipt(currentOrder).toReceiptText()) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Print, contentDescription = null, tint = DiningColors.Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("打印收据", color = DiningColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("（未付款）", color = DiningColors.Primary, fontSize = 11.sp)
                }
            }
            OutlinedButton(
                onClick = { printReceiptText(buildPaidReceipt(currentOrder, receipt).toReceiptText()) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Print, contentDescription = null, tint = DiningColors.Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("打印收据", color = DiningColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("（已付款）", color = DiningColors.Primary, fontSize = 11.sp)
                }
            }
        }

        // 删除订单（仅 Admin）
        if (SessionManager.isAdmin) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = DiningColors.Error, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("删除订单", color = DiningColors.Error, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 收款按钮
        if (currentOrder.payment_status != "paid") {
            Button(
                onClick = { showPay = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)
            ) {
                Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = DiningColors.Surface, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("录入收款", color = DiningColors.Surface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showEdit) {
        OrderEditDialog(
            order = currentOrder,
            receipt = receipt,
            payment = payment,
            onDismiss = { showEdit = false },
            onDone = {
                showEdit = false
                scope.launch {
                    SupabaseClient.fetchOrder(currentOrder.id)?.let { currentOrder = it }
                }
            }
        )
    }

    if (showPay) {
        PaymentDialog(
            order = currentOrder,
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

    // 厨房单预览
    if (showKitchen) {
        val kitchenZh = remember(currentOrder, tableNo) {
            buildKitchenOrder(
                orderNo = currentOrder.order_no,
                tableNo = tableNo ?: "外卖",
                time = formatDateTimeMy(currentOrder.order_datetime ?: ""),
                items = lines.map { line ->
                    val (item, remark) = splitItemName(line.name)
                    KitchenLine(line.qty, item, remark)
                },
                note = currentOrder.notes
            )
        }
        val kitchenEn = remember(currentOrder, tableNo) {
            buildKitchenOrderEnglish(
                orderNo = currentOrder.order_no,
                tableNo = tableNo ?: "Takeaway",
                time = formatDateTimeMy(currentOrder.order_datetime ?: ""),
                items = lines.map { line ->
                    val en = line.nameEn.ifBlank { line.name }
                    val (item, remark) = splitItemNameEn(en)
                    KitchenLine(line.qty, item, remark)
                },
                note = currentOrder.notes
            )
        }
        KitchenOrderDialog(
            textZh = kitchenZh,
            textEn = kitchenEn,
            onPrint = { text -> printReceiptText(text) },
            onDone = { showKitchen = false }
        )
    }

    // 收据照片全屏放大
    if (showFullImage && receiptPhoto != null) {
        Dialog(onDismissRequest = { showFullImage = false }) {
            Box(
                modifier = Modifier.fillMaxWidth().clickable { showFullImage = false },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = receiptPhoto!!,
                    contentDescription = "收据照片放大",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 删除订单确认
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DiningColors.Surface,
            shape = RoundedCornerShape(16.dp),
            title = { Text("删除订单", color = DiningColors.Error, fontWeight = FontWeight.SemiBold) },
            text = { Text("确定要删除订单 ${currentOrder.order_no} 吗？此操作不可恢复，会连同付款记录、收据一起删除。", color = DiningColors.TextPrimary) },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    scope.launch {
                        deleting = true
                        val ok = SupabaseClient.deleteOrder(currentOrder.id, currentOrder.order_no)
                        deleting = false
                        showDeleteConfirm = false
                        if (ok) onBack() else {
                            // 删除失败提示
                            showDeleteConfirm = false
                        }
                    }
                }) { Text(if (deleting) "删除中…" else "确定删除", color = DiningColors.Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = DiningColors.TextMuted) } }
        )
    }
}

// ============ 订单编辑弹窗（仅 Admin） ============
@Composable
private fun OrderEditDialog(
    order: CustomerOrder,
    receipt: ReceiptMaster?,
    payment: PaymentRecord?,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var tables by remember { mutableStateOf<List<TableList>>(emptyList()) }
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val quantities = remember { mutableStateMapOf<Long, Int>() }

    // 订单字段
    var isTakeaway by remember { mutableStateOf(order.table_id == null) }
    var tableId by remember { mutableStateOf(order.table_id) }
    var tableExpanded by remember { mutableStateOf(false) }
    var customerName by remember { mutableStateOf(order.customer_name ?: "") }
    var customerPhone by remember { mutableStateOf(order.customer_phone ?: "") }
    var status by remember { mutableStateOf(order.payment_status) }
    var discount by remember { mutableStateOf(if ((receipt?.discount ?: 0.0) > 0) (receipt?.discount ?: 0.0).toString() else "") }
    var payMode by remember { mutableStateOf(receipt?.payment_mode ?: "CASH") }
    var amountReceived by remember { mutableStateOf(if ((receipt?.amount_received ?: 0.0) > 0) (receipt?.amount_received ?: 0.0).toString() else "") }
    var receiptPhoto by remember { mutableStateOf<ImageBitmap?>(null) }
    var showFullImage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            tables = SupabaseClient.fetchTables()
            menuItems = SupabaseClient.fetchMenuItems().filter { it.is_active }
            order.order_items.jsonArray.forEach { el ->
                val obj = el.jsonObject
                val itemId = obj["item_id"]?.jsonPrimitive?.content?.toLongOrNull()
                val qty = obj["quantity"]?.jsonPrimitive?.content?.toIntOrNull()
                if (itemId != null && qty != null) quantities[itemId] = qty
            }
        }
        if (payment != null && payment.pay_method != "cash" && payment.receipt_attachment_url != null) {
            receiptPhoto = runCatching {
                SupabaseClient.downloadFile(payment.receipt_attachment_url)?.toImageBitmap()
            }.getOrNull()
        }
        loading = false
    }

    val totalAmount = menuItems.sumOf { it.sell_price_myr * (quantities[it.id] ?: 0) }
    val discountVal = discount.toDoubleOrNull() ?: 0.0
    val finalTotal = (totalAmount - discountVal).coerceAtLeast(0.0)
    val receivedVal = amountReceived.toDoubleOrNull() ?: 0.0
    val changeVal = (receivedVal - finalTotal).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("编辑订单 · ${order.order_no}", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DiningColors.Primary)
                }
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 类型
                    Text("类型", fontSize = 12.sp, color = DiningColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isTakeaway, onClick = { isTakeaway = false }, label = { Text("堂食") })
                        FilterChip(selected = isTakeaway, onClick = { isTakeaway = true }, label = { Text("外卖") })
                    }
                    // 桌台（独立字段）
                    Text("桌台", fontSize = 12.sp, color = DiningColors.TextSecondary)
                    if (isTakeaway) {
                        OutlinedTextField(
                            value = "外卖",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box {
                            OutlinedButton(onClick = { tableExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        tables.firstOrNull { it.id == tableId }?.table_no ?: "选择桌台",
                                        modifier = Modifier.weight(1f),
                                        color = DiningColors.TextPrimary
                                    )
                                    Text("▾", color = DiningColors.TextMuted)
                                }
                            }
                            DropdownMenu(expanded = tableExpanded, onDismissRequest = { tableExpanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                                tables.forEach { t ->
                                    DropdownMenuItem(
                                        text = { Text(t.table_no) },
                                        onClick = { tableId = t.id; tableExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(value = customerName, onValueChange = { customerName = it }, label = { Text("顾客") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = customerPhone, onValueChange = { customerPhone = it }, label = { Text("电话") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    // 状态
                    Text("状态", fontSize = 12.sp, color = DiningColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("unpaid" to "未付", "partial" to "部分付", "paid" to "已付清").forEach { (v, l) ->
                            FilterChip(selected = status == v, onClick = { status = v }, label = { Text(l) })
                        }
                    }

                    // 折扣 / 付款方式 / 顾客支付
                    OutlinedTextField(value = discount, onValueChange = { discount = it }, label = { Text("折扣 (RM)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Text("付款方式", fontSize = 12.sp, color = DiningColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("CASH" to "现金", "DUITNOW" to "DuitNow", "TNG" to "TNG", "ALIPAY" to "支付宝").forEach { (v, l) ->
                            FilterChip(selected = payMode == v, onClick = { payMode = v }, label = { Text(l) })
                        }
                    }
                    OutlinedTextField(value = amountReceived, onValueChange = { amountReceived = it }, label = { Text("顾客支付 (RM)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

                    // 已上传收据
                    if (receiptPhoto != null) {
                        Text("已上传收据", fontSize = 12.sp, color = DiningColors.TextSecondary)
                        Image(
                            bitmap = receiptPhoto!!,
                            contentDescription = "已上传收据",
                            modifier = Modifier.fillMaxWidth().height(140.dp).clickable { showFullImage = true }
                        )
                    }

                    // 菜品明细
                    HorizontalDivider(color = DiningColors.SurfaceVariant)
                    Text("菜品明细", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextSecondary)
                    menuItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.item_name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                                Text("RM%.2f/${item.unit}".format(item.sell_price_myr), fontSize = 11.sp, color = DiningColors.TextMuted)
                            }
                            TextButton(onClick = {
                                val q = quantities[item.id] ?: 0
                                if (q > 1) quantities[item.id] = q - 1 else quantities.remove(item.id)
                            }) { Text("−", fontSize = 18.sp, color = DiningColors.Primary) }
                            Text("${quantities[item.id] ?: 0}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
                            TextButton(onClick = { quantities[item.id] = (quantities[item.id] ?: 0) + 1 }) {
                                Text("＋", fontSize = 18.sp, color = DiningColors.Primary)
                            }
                        }
                    }

                    Text(
                        "实收合计 RM %.2f · 找零 RM %.2f".format(finalTotal, changeVal),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DiningColors.Primary
                    )
                    if (error != null) Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !loading && !saving, onClick = {
                scope.launch {
                    saving = true; error = null
                    val ok1 = SupabaseClient.updateOrderInfo(
                        order.id,
                        customerName.trim().ifBlank { null },
                        customerPhone.trim().ifBlank { null },
                        if (isTakeaway) null else tableId,
                        status
                    )
                    val itemsJson = buildJsonArray {
                        menuItems.forEach { item ->
                            val q = quantities[item.id] ?: 0
                            if (q > 0) {
                                add(buildJsonObject {
                                    put("item_id", JsonPrimitive(item.id))
                                    put("item_name", JsonPrimitive(item.item_name))
                                    put("name_en", JsonPrimitive(item.name_en ?: ""))
                                    put("quantity", JsonPrimitive(q))
                                    put("unit_price_myr", JsonPrimitive(item.sell_price_myr))
                                    put("unit", JsonPrimitive(item.unit))
                                })
                            }
                        }
                    }
                    val ok2 = SupabaseClient.updateOrderItems(order.id, itemsJson, totalAmount)
                    var ok3 = true
                    if (receipt != null) {
                        ok3 = SupabaseClient.updateReceiptByNo(
                            receipt.receipt_no, totalAmount, discountVal, finalTotal,
                            payMode, receivedVal, changeVal
                        )
                    }
                    saving = false
                    if (ok1 && ok2 && ok3) onDone() else error = "保存失败"
                }
            }) { Text("保存", color = if (!loading && !saving) DiningColors.Primary else DiningColors.TextMuted, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) } }
    )

    if (showFullImage && receiptPhoto != null) {
        Dialog(onDismissRequest = { showFullImage = false }) {
            Box(
                modifier = Modifier.fillMaxWidth().clickable { showFullImage = false },
                contentAlignment = Alignment.Center
            ) {
                Image(bitmap = receiptPhoto!!, contentDescription = "收据照片放大", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// 未付款收据（用订单数据构造，无付款信息）
private fun buildUnpaidReceipt(order: CustomerOrder): ReceiptData {
    val lines = parseOrderLines(order.order_items)
    return ReceiptData(
        receiptNo = order.receipt_no.ifBlank { order.order_no },
        transDatetime = order.order_datetime ?: "",
        items = lines,
        subTotal = order.total_amount_myr,
        discount = 0.0,
        total = order.total_amount_myr,
        paymentMode = "UNPAID",
        amountReceived = 0.0,
        changeGiven = 0.0
    )
}

// 已付款收据（优先用 receipt_master 数据）
private fun buildPaidReceipt(order: CustomerOrder, receipt: ReceiptMaster?): ReceiptData {
    val lines = parseOrderLines(order.order_items)
    return ReceiptData(
        receiptNo = receipt?.receipt_no ?: order.receipt_no,
        transDatetime = receipt?.trans_datetime ?: order.order_datetime ?: "",
        items = lines,
        subTotal = receipt?.sub_total ?: order.total_amount_myr,
        discount = receipt?.discount ?: 0.0,
        total = receipt?.total_amount ?: order.total_amount_myr,
        paymentMode = receipt?.payment_mode ?: "UNPAID",
        amountReceived = receipt?.amount_received ?: 0.0,
        changeGiven = receipt?.change_given ?: 0.0
    )
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
    val nameEn: String = "",
    val qty: Int,
    val unitPrice: Double,
    val amount: Double
)

// 厨房单明细行
private data class KitchenLine(
    val qty: Int,
    val item: String,
    val remark: String
)

// 把菜品名拆成「菜品」+「口味备注」，如「五花肉串（香辣）」→ 五花肉串 + 香辣
private fun splitItemName(name: String): Pair<String, String> {
    val idx = name.indexOf('（')
    if (idx > 0) {
        val item = name.substring(0, idx)
        val remark = name.substring(idx + 1).removeSuffix("）")
        return item to remark
    }
    return name to ""
}

// 英文名拆「菜品」+「口味」，如 "Pork Belly Skewer (Spicy)" → Pork Belly Skewer + Spicy
private fun splitItemNameEn(nameEn: String): Pair<String, String> {
    val idx = nameEn.indexOf('(')
    if (idx > 0) {
        val item = nameEn.substring(0, idx).trim()
        val remark = nameEn.substring(idx + 1).removeSuffix(")").trim()
        return item to remark
    }
    return nameEn.trim() to ""
}

// 生成厨房出单文本（48 列）
private fun buildKitchenOrder(orderNo: String, tableNo: String, time: String, items: List<KitchenLine>, note: String?): String {
    val W = ReceiptFormatter.TOTAL_WIDTH
    val r = mutableListOf<String>()

    r.add("=".repeat(W))
    r.add(ReceiptFormatter.padCenter("KITCHEN ORDER", W))
    r.add(ReceiptFormatter.padCenter("厨房出单", W))
    r.add("")
    r.add(ReceiptFormatter.padRight("Order No: $orderNo", W))
    r.add(ReceiptFormatter.padRight("Table No: $tableNo", W))
    r.add(ReceiptFormatter.padRight("Time: $time", W))
    r.add("=".repeat(W))

    r.add(ReceiptFormatter.padRight("QTY", 6) + ReceiptFormatter.padRight("ITEM", 22) + ReceiptFormatter.padRight("REMARK", 20))
    r.add("-".repeat(W))

    items.forEach { line ->
        r.add(ReceiptFormatter.generateKitchenRow(line.qty.toString(), line.item, line.remark))
    }
    r.add("-".repeat(W))

    r.add("【特殊指令】")
    r.add(note?.takeIf { it.isNotBlank() } ?: "无")
    r.add("=".repeat(W))

    r.add(ReceiptFormatter.padRight("份数：1份", W))
    r.add(ReceiptFormatter.padRight("打印:$time", W))
    r.add("=".repeat(W))
    r.add("\n\n\n")

    return r.joinToString("\n")
}

// 生成厨房出单英文版文本（48 列）
private fun buildKitchenOrderEnglish(orderNo: String, tableNo: String, time: String, items: List<KitchenLine>, note: String?): String {
    val W = ReceiptFormatter.TOTAL_WIDTH
    val r = mutableListOf<String>()

    r.add("=".repeat(W))
    r.add(ReceiptFormatter.padCenter("KITCHEN ORDER", W))
    r.add("")
    r.add(ReceiptFormatter.padRight("Order No: $orderNo", W))
    r.add(ReceiptFormatter.padRight("Table No: $tableNo", W))
    r.add(ReceiptFormatter.padRight("Time: $time", W))
    r.add("=".repeat(W))

    r.add(ReceiptFormatter.padRight("QTY", 6) + ReceiptFormatter.padRight("ITEM", 22) + ReceiptFormatter.padRight("REMARK", 20))
    r.add("-".repeat(W))

    items.forEach { line ->
        r.add(ReceiptFormatter.generateKitchenRow(line.qty.toString(), line.item, line.remark))
    }
    r.add("-".repeat(W))

    r.add("[SPECIAL INSTRUCTIONS]")
    r.add(note?.takeIf { it.isNotBlank() } ?: "N/A")
    r.add("=".repeat(W))

    r.add(ReceiptFormatter.padRight("Copies: 1 of 1", W))
    r.add(ReceiptFormatter.padRight("Printed: $time", W))
    r.add("=".repeat(W))
    r.add("\n\n\n")

    return r.joinToString("\n")
}

@Composable
private fun KitchenOrderDialog(textZh: String, textEn: String, onPrint: (String) -> Unit, onDone: () -> Unit) {
    var lang by remember { mutableStateOf("zh") }
    val text = if (lang == "zh") textZh else textEn
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(16.dp),
        title = { Text(if (lang == "zh") "厨房出单" else "Kitchen Order", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = lang == "zh", onClick = { lang = "zh" }, label = { Text("中文版") })
                    FilterChip(selected = lang == "en", onClick = { lang = "en" }, label = { Text("英文版") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DiningColors.TextPrimary,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Row {
                OutlinedButton(onClick = { onPrint(text) }) {
                    Icon(Icons.Outlined.Print, contentDescription = null, tint = DiningColors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (lang == "zh") "打印厨房单" else "Print", color = DiningColors.Primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDone) { Text(if (lang == "zh") "完成" else "Done", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
            }
        }
    )
}

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
            r.add(ReceiptFormatter.generateItemRow(line.nameEn, line.name, line.qty.toString(), "%.2f".format(line.unitPrice), "%.2f".format(line.amount)))
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
        val nameEn = obj["name_en"]?.jsonPrimitive?.content ?: ""
        val qty = obj["quantity"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val price = obj["unit_price_myr"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        ReceiptLine(name, nameEn, qty, price, qty * price)
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
                OutlinedButton(onClick = onPrint) {
                    Icon(Icons.Outlined.Print, contentDescription = null, tint = DiningColors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("打印收据", color = DiningColors.Primary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDone) { Text("完成", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
            }
        }
    )
}
