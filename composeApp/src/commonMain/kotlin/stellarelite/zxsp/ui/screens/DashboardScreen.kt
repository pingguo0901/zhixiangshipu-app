package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.TableRestaurant
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import stellarelite.zxsp.data.LanguageManager
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.data.t
import stellarelite.zxsp.network.CustomerOrder
import stellarelite.zxsp.network.MenuItem
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.TableList
import stellarelite.zxsp.platform.printReceiptText
import stellarelite.zxsp.ui.theme.DiningColors

@Composable
fun DashboardScreen() {
    var showNewOrder by remember { mutableStateOf(false) }
    var newOrderTableId by remember { mutableStateOf<Long?>(null) }
    var orderDialogTable by remember { mutableStateOf<TableList?>(null) }

    if (showNewOrder) {
        NewOrderScreen(onBack = { showNewOrder = false }, initialTableId = newOrderTableId)
        return
    }
    DashboardView(
        onNewOrder = { newOrderTableId = null; showNewOrder = true },
        onTableClick = { table ->
            if (table.table_status == "occupied") {
                orderDialogTable = table
            } else {
                newOrderTableId = table.id
                showNewOrder = true
            }
        }
    )

    orderDialogTable?.let { table ->
        TableOrderDialog(table = table, onDismiss = { orderDialogTable = null })
    }
}

@Composable
private fun DashboardView(onNewOrder: () -> Unit, onTableClick: (TableList) -> Unit) {
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
                .onFailure { error = it.message ?: t("加载失败", "Load failed") }
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
                Text(t("工作台", "Dashboard"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
                Text(t("你好", "Hello") + "，" + SessionManager.staffName, fontSize = 14.sp, color = DiningColors.TextSecondary)
            }
            Button(
                onClick = onNewOrder,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)
            ) {
                Text(t("＋ 新建订单", "＋ New Order"), color = DiningColors.Surface, fontWeight = FontWeight.Bold)
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
                StatItem(Icons.Outlined.Chair, "$freeCount", t("空闲桌", "Free Tables"))
                StatItem(Icons.Outlined.Restaurant, "$occupiedCount", t("占用中", "Occupied"))
                StatItem(Icons.Outlined.TableRestaurant, "${dineInTables.size}", t("总桌台", "Total Tables"))
                StatItem(Icons.Outlined.DeliveryDining, "${takeawayTables.size}", t("外卖号", "Takeaway"))
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
                    Button(onClick = { load() }) { Text(t("重试", "Retry")) }
                }
            }
            else -> {
                Text(t("堂食桌台", "Dine-in Tables") + "（${dineInTables.size}）", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                if (dineInTables.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(t("暂无桌台，请老板先添加", "No tables yet"), color = DiningColors.TextMuted, fontSize = 14.sp)
                    }
                } else {
                    TableGrid(dineInTables, onTableClick)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(t("外卖", "Takeaway") + "（${takeawayTables.size}）", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                if (takeawayTables.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(t("暂无外卖号", "No takeaway"), color = DiningColors.TextMuted, fontSize = 14.sp)
                    }
                } else {
                    TableGrid(takeawayTables, onTableClick)
                }
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = DiningColors.Surface, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.Surface)
        Text(label, fontSize = 11.sp, color = DiningColors.Surface.copy(alpha = 0.75f))
    }
}

@Composable
private fun TableGrid(tables: List<TableList>, onTableClick: (TableList) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tables.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { table ->
                    Box(modifier = Modifier.weight(1f)) {
                        TableBadge(table, onClick = { onTableClick(table) })
                    }
                }
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// 桌台号显示：英文界面下「外卖XX」转成「TA-XX」
private fun displayTableNo(tableNo: String): String {
    return if (LanguageManager.isEnglish && tableNo.startsWith("外卖")) {
        "TA-" + tableNo.removePrefix("外卖")
    } else tableNo
}

@Composable
private fun TableBadge(table: TableList, onClick: () -> Unit) {
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
        "occupied" -> t("占用", "Occupied")
        "cleaning" -> t("清理", "Cleaning")
        else -> t("空闲", "Free")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(displayTableNo(table.table_no), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
        Text(label, fontSize = 10.sp, color = fg.copy(alpha = 0.8f))
    }
}

// ============ 桌台订单弹窗 ============
@Composable
private fun TableOrderDialog(table: TableList, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf<CustomerOrder?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showPayment by remember { mutableStateOf(false) }
    var showAddItems by remember { mutableStateOf(false) }
    var showReceipt by remember { mutableStateOf(false) }
    var receiptData by remember { mutableStateOf<ReceiptData?>(null) }

    fun loadOrder() {
        scope.launch {
            loading = true
            order = runCatching { SupabaseClient.fetchActiveOrderByTable(table.id) }.getOrNull()
            loading = false
        }
    }
    LaunchedEffect(Unit) { loadOrder() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("${displayTableNo(table.table_no)}（${if (table.table_no.startsWith("外卖")) t("外卖", "Takeaway") else t("堂食", "Dine-in")}）", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            when {
                loading -> Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DiningColors.Primary)
                }
                order == null -> Text(t("该桌台暂无未结账订单", "No active order on this table"), color = DiningColors.TextMuted, fontSize = 14.sp)
                else -> {
                    val o = order!!
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OrderInfoRow(t("订单号", "Order No."), o.order_no)
                        OrderInfoRow(t("收据号", "Receipt No."), o.receipt_no)
                        OrderInfoRow(t("顾客", "Customer"), o.customer_name ?: "—")
                        OrderInfoRow(t("电话", "Phone"), o.customer_phone ?: "—")
                        OrderInfoRow(t("桌台", "Table"), displayTableNo(table.table_no))
                        OrderInfoRow(t("状态", "Status"), when (o.payment_status) {
                            "paid" -> t("已付清", "Paid"); "partial" -> t("部分付", "Partial"); else -> t("未付", "Unpaid")
                        })
                        HorizontalDivider(color = DiningColors.TextMuted.copy(alpha = 0.2f))
                        Text(t("订单明细", "Items"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                        parseOrderItems(o.order_items).forEach { line ->
                            Text("• $line", fontSize = 12.sp, color = DiningColors.TextSecondary)
                        }
                        HorizontalDivider(color = DiningColors.TextMuted.copy(alpha = 0.2f))
                        OrderInfoRow(t("总金额", "Total"), "RM%.2f".format(o.total_amount_myr))
                    }
                }
            }
        },
        confirmButton = {
            if (order != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showAddItems = true }) { Text(t("加单", "Add Items"), color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = { showPayment = true }) { Text(t("结账", "Checkout"), color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("关闭", "Close"), color = DiningColors.TextMuted) } }
    )

    if (showPayment && order != null) {
        PaymentDialog(
            order = order!!,
            onDismiss = { showPayment = false },
            onPaid = { data ->
                showPayment = false
                receiptData = data
                showReceipt = true
            }
        )
    }
    if (showReceipt && receiptData != null) {
        ReceiptDialog(
            data = receiptData!!,
            onPrint = { printReceiptText(receiptData!!.toReceiptText()) },
            onDone = { showReceipt = false; onDismiss() }
        )
    }
    if (showAddItems && order != null) {
        AddItemsDialog(order = order!!, onDismiss = { showAddItems = false }, onDone = { showAddItems = false; loadOrder() }, tableNo = table.table_no)
    }
}

@Composable
private fun OrderInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = DiningColors.TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
    }
}

private fun parseOrderItems(items: JsonElement): List<String> {
    val english = LanguageManager.isEnglish
    return items.jsonArray.mapNotNull { el ->
        val obj = el.jsonObject
        val zh = obj["item_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val en = obj["name_en"]?.jsonPrimitive?.content ?: ""
        val name = if (english) en.ifBlank { zh } else zh
        val qty = obj["quantity"]?.jsonPrimitive?.content ?: ""
        val price = obj["unit_price_myr"]?.jsonPrimitive?.content ?: ""
        "$name × $qty  RM$price"
    }
}

// ============ 加单弹窗 ============
@Composable
internal fun AddItemsDialog(order: CustomerOrder, onDismiss: () -> Unit, onDone: () -> Unit, title: String = "加单", tableNo: String? = null) {
    val scope = rememberCoroutineScope()
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val qtyNoSpicy = remember { mutableStateMapOf<Long, Int>() }
    val qtySpicy = remember { mutableStateMapOf<Long, Int>() }
    val qtyExtra = remember { mutableStateMapOf<Long, Int>() }
    var addOnTextZh by remember { mutableStateOf<String?>(null) }
    var addOnTextEn by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { menuItems = SupabaseClient.fetchMenuItems().filter { it.is_active } }
        loading = false
    }

    // 本次加单新增金额（三种口味合计）
    val addedAmount = menuItems.sumOf { item ->
        item.sell_price_myr * ((qtyNoSpicy[item.id] ?: 0) + (qtySpicy[item.id] ?: 0) + (qtyExtra[item.id] ?: 0))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("$title · ${order.order_no}", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DiningColors.Primary)
                }
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    menuItems.forEach { item ->
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(addOnItemName(item), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                                Text("RM%.2f/${item.unit}".format(item.sell_price_myr), fontSize = 11.sp, color = DiningColors.TextMuted)
                                Spacer(modifier = Modifier.height(2.dp))
                                FlavorQtyRow(t("不辣", "No Spicy"), qtyNoSpicy[item.id] ?: 0) { qtyNoSpicy[item.id] = it }
                                FlavorQtyRow(t("香辣", "Spicy"), qtySpicy[item.id] ?: 0) { qtySpicy[item.id] = it }
                                FlavorQtyRow(t("加辣", "Spicy+"), qtyExtra[item.id] ?: 0) { qtyExtra[item.id] = it }
                            }
                        }
                    }
                    if (error != null) Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !loading && !saving && addedAmount > 0, onClick = {
                scope.launch {
                    saving = true; error = null
                    // 本次新增菜品（按口味拆分）
                    val addedItems = buildJsonArray {
                        menuItems.forEach { item ->
                            val qn = qtyNoSpicy[item.id] ?: 0
                            val qs = qtySpicy[item.id] ?: 0
                            val qe = qtyExtra[item.id] ?: 0
                            if (qn > 0) add(flavorItemJson(item, "不辣", "No Spicy", qn))
                            if (qs > 0) add(flavorItemJson(item, "香辣", "Spicy", qs))
                            if (qe > 0) add(flavorItemJson(item, "加辣", "Spicy+", qe))
                        }
                    }
                    // 合并：原订单明细 + 本次新增
                    val merged = buildJsonArray {
                        order.order_items.jsonArray.forEach { add(it) }
                        addedItems.jsonArray.forEach { add(it) }
                    }
                    val newTotal = order.total_amount_myr + addedAmount
                    val ok = SupabaseClient.updateOrderItems(order.id, merged, newTotal)
                    saving = false
                    if (ok) {
                        // 厨房追加单：只打印本次新增
                        val addedLinesZh = mutableListOf<KitchenLine>()
                        val addedLinesEn = mutableListOf<KitchenLine>()
                        menuItems.forEach { item ->
                            val en = item.name_en?.takeIf { it.isNotBlank() } ?: item.item_name
                            val qn = qtyNoSpicy[item.id] ?: 0
                            val qs = qtySpicy[item.id] ?: 0
                            val qe = qtyExtra[item.id] ?: 0
                            if (qn > 0) { addedLinesZh.add(KitchenLine(qn, item.item_name, "不辣")); addedLinesEn.add(KitchenLine(qn, en, "No Spicy")) }
                            if (qs > 0) { addedLinesZh.add(KitchenLine(qs, item.item_name, "香辣")); addedLinesEn.add(KitchenLine(qs, en, "Spicy")) }
                            if (qe > 0) { addedLinesZh.add(KitchenLine(qe, item.item_name, "加辣")); addedLinesEn.add(KitchenLine(qe, en, "Spicy+")) }
                        }
                        if (addedLinesZh.isNotEmpty()) {
                            val time = formatDateTimeMy(currentIso())
                            val tblZh = tableNo ?: t("外卖", "Takeaway")
                            val tblEn = if (tableNo == null || tableNo == "外卖") "Takeaway" else tableNo
                            addOnTextZh = buildKitchenAddOnOrder(
                                orderNo = order.order_no,
                                tableNo = tblZh,
                                time = time,
                                items = addedLinesZh
                            )
                            addOnTextEn = buildKitchenAddOnOrderEnglish(
                                orderNo = order.order_no,
                                tableNo = tblEn,
                                time = time,
                                items = addedLinesEn
                            )
                        } else {
                            onDone()
                        }
                    } else error = t("加单失败", "Add items failed")
                }
            }) { Text(t("保存", "Save") + title + " · RM %.2f".format(addedAmount), color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("取消", "Cancel"), color = DiningColors.TextMuted) } }
    )

    // 加单成功后弹厨房追加单
    addOnTextZh?.let { zh ->
        KitchenAddOnDialog(
            textZh = zh,
            textEn = addOnTextEn ?: zh,
            onPrint = { printReceiptText(it) },
            onDone = { addOnTextZh = null; addOnTextEn = null; onDone() }
        )
    }
}

// 加单页菜品名显示（英文界面用 name_en）
private fun addOnItemName(item: MenuItem): String =
    if (LanguageManager.isEnglish) item.name_en?.takeIf { it.isNotBlank() } ?: item.item_name else item.item_name

// 单个口味行：不辣/香辣/加辣 各带 − 数量 +
@Composable
private fun FlavorQtyRow(label: String, qty: Int, onQtyChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { if (qty > 0) onQtyChange(qty - 1) }) { Text("−", fontSize = 18.sp, color = DiningColors.Primary) }
            Text("$qty", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            TextButton(onClick = { onQtyChange(qty + 1) }) { Text("＋", fontSize = 18.sp, color = DiningColors.Primary) }
        }
    }
}

// 生成单个口味明细条目（用于追加到订单）
private fun flavorItemJson(item: MenuItem, flavor: String, flavorEn: String, qty: Int): JsonElement = buildJsonObject {
    put("item_id", JsonPrimitive(item.id))
    put("item_name", JsonPrimitive("${item.item_name}（$flavor）"))
    put("name_en", JsonPrimitive("${item.name_en ?: item.item_name} ($flavorEn)"))
    put("quantity", JsonPrimitive(qty))
    put("unit_price_myr", JsonPrimitive(item.sell_price_myr))
    put("unit", JsonPrimitive(item.unit))
}
