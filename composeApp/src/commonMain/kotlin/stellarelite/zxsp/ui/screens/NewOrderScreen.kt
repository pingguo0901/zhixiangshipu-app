package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import stellarelite.zxsp.data.LanguageManager
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.data.t
import stellarelite.zxsp.network.CustomerOrder
import stellarelite.zxsp.network.MenuItem
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.TableList
import stellarelite.zxsp.platform.printReceiptText
import stellarelite.zxsp.ui.theme.DiningColors

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(onBack: () -> Unit, initialTableId: Long? = null) {
    val scope = rememberCoroutineScope()
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var tables by remember { mutableStateOf<List<TableList>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var tableId by remember { mutableStateOf<Long?>(null) }
    var isTakeaway by remember { mutableStateOf(false) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<MenuItem?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching {
            val m = SupabaseClient.fetchMenuItems().filter { it.is_active }
            val t = SupabaseClient.fetchTables().filter { it.table_status == "free" }
            menuItems = m
            tables = t
        }
        loading = false
    }

    // 从桌台看板点击空闲桌台进入时，预选该桌台/外卖号
    LaunchedEffect(initialTableId, tables) {
        if (initialTableId != null) {
            val t = tables.firstOrNull { it.id == initialTableId }
            if (t != null) {
                tableId = t.id
                isTakeaway = t.table_no.startsWith("外卖")
            }
        }
    }

    val dineInTables = tables.filter { !it.table_no.startsWith("外卖") }
    val takeawayTables = tables.filter { it.table_no.startsWith("外卖") }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(t("‹ 返回", "‹ Back"), color = DiningColors.Primary)
            }
            Text(
                t("新建订单", "New Order"),
                modifier = Modifier.align(Alignment.Center),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DiningColors.Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 堂食/外卖
                item {
                    Text(t("用餐方式", "Dining Mode"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isTakeaway,
                            onClick = { isTakeaway = false; tableId = dineInTables.firstOrNull()?.id },
                            label = { Text(t("堂食", "Dine-in")) }
                        )
                        FilterChip(
                            selected = isTakeaway,
                            onClick = { isTakeaway = true; tableId = takeawayTables.firstOrNull()?.id },
                            label = { Text(t("外卖", "Takeaway")) }
                        )
                    }
                }

                // 桌台选择（堂食）
                if (!isTakeaway) {
                    item {
                        Text(t("选择桌台", "Select Table"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (dineInTables.isEmpty()) {
                            Text(t("暂无空闲桌台", "No free tables"), fontSize = 13.sp, color = DiningColors.TextMuted)
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                dineInTables.forEach { t ->
                                    FilterChip(
                                        selected = tableId == t.id,
                                        onClick = { tableId = t.id },
                                        label = { Text(displayTableNo(t.table_no)) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 外卖号选择（外卖，自动分配可手动改）
                if (isTakeaway) {
                    item {
                        Text(t("外卖号（自动分配，可手动改）", "Takeaway No. (auto-assigned)"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (takeawayTables.isEmpty()) {
                            Text(t("暂无空闲外卖号", "No free takeaway no."), fontSize = 13.sp, color = DiningColors.TextMuted)
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                takeawayTables.forEach { t ->
                                    FilterChip(
                                        selected = tableId == t.id,
                                        onClick = { tableId = t.id },
                                        label = { Text(displayTableNo(t.table_no)) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 顾客信息
                item {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text(t("顾客姓名（可选）", "Customer Name (optional)")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text(t("顾客电话（可选）", "Customer Phone (optional)")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 选择菜品：2排4个方形按钮
                item {
                    Text(t("选择菜品", "Select Items"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    MenuGrid(menuItems, onSelect = { selectedItem = it })
                }

                if (error != null) {
                    item { Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp) }
                }
            }
        }
    }

    // 菜品弹窗：选口味数量 → 查看订单详情 → 确认下单自动打印
    selectedItem?.let { item ->
        MenuItemOrderDialog(
            item = item,
            isTakeaway = isTakeaway,
            saving = saving,
            onConfirm = { noSpicy, spicy, extra, note ->
                scope.launch {
                    saving = true
                    error = null
                    val itemsJson = buildOrderItemsJson(item, noSpicy, spicy, extra, isTakeaway)
                    val totalQty = noSpicy + spicy + extra
                    val takeawayFee = if (isTakeaway) 1.0 else 0.0
                    val totalAmount = item.sell_price_myr * totalQty + takeawayFee
                    val order = CustomerOrder(
                        table_id = tableId,
                        customer_name = customerName.trim().ifBlank { null },
                        customer_phone = customerPhone.trim().ifBlank { null },
                        order_items = itemsJson,
                        total_amount_myr = totalAmount,
                        notes = note.trim().ifBlank { null },
                        created_by_staff_id = SupabaseClient.currentStaffId(),
                        order_datetime = currentIso()
                    )
                    val r = SupabaseClient.insertOrder(order)
                    saving = false
                    if (r != null) {
                        selectedItem = null
                        // 自动打印厨房出单（按当前语言）
                        val tno = tables.firstOrNull { it.id == r.table_id }?.table_no ?: "外卖"
                        val time = formatDateTimeMy(r.order_datetime ?: "")
                        val lines = parseOrderLines(r.order_items)
                        val kitchenText = if (LanguageManager.isEnglish) {
                            buildKitchenOrderEnglish(
                                orderNo = r.order_no,
                                tableNo = if (tno == "外卖") "Takeaway" else tno,
                                time = time,
                                items = lines.map { line ->
                                    val en = line.nameEn.ifBlank { line.name }
                                    val (name, remark) = splitItemNameEn(en)
                                    KitchenLine(line.qty, name, remark)
                                },
                                note = r.notes
                            )
                        } else {
                            buildKitchenOrder(
                                orderNo = r.order_no,
                                tableNo = tno,
                                time = time,
                                items = lines.map { line ->
                                    val (name, remark) = splitItemName(line.name)
                                    KitchenLine(line.qty, name, remark)
                                },
                                note = r.notes
                            )
                        }
                        printReceiptText(kitchenText)
                        onBack()
                    } else {
                        error = t("下单失败", "Order failed") + "：" + (SupabaseClient.lastError ?: "")
                    }
                }
            },
            onDismiss = { selectedItem = null }
        )
    }
}

private val FLAVOR_OPTIONS = listOf("不辣", "香辣", "加辣")
private val FLAVOR_EN = mapOf("不辣" to "No Spicy", "香辣" to "Spicy", "加辣" to "Spicy+")

// 口味标签显示（英文界面转英文）
private fun flavorLabel(flavor: String): String =
    if (LanguageManager.isEnglish) FLAVOR_EN[flavor] ?: flavor else flavor

// 菜品名显示（英文界面用 name_en）
private fun menuName(item: MenuItem): String =
    if (LanguageManager.isEnglish) item.name_en?.takeIf { it.isNotBlank() } ?: item.item_name else item.item_name

// 单位显示（英文界面转英文）
private fun unitLabel(unit: String): String =
    if (LanguageManager.isEnglish) when (unit) {
        "串" -> "pc"; "份" -> "Serving"; "瓶" -> "Bottle"; else -> unit
    } else unit

// 桌台号显示：英文界面「外卖XX」转「TA-XX」
private fun displayTableNo(tableNo: String): String =
    if (LanguageManager.isEnglish && tableNo.startsWith("外卖")) "TA-" + tableNo.removePrefix("外卖") else tableNo

// 2排4个方形按钮（最多8格，多余的先空着）
@Composable
private fun MenuGrid(items: List<MenuItem>, onSelect: (MenuItem) -> Unit) {
    val gridItems = items.take(8)
    val rows = gridItems.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) { rowIdx ->
            val row = rows.getOrNull(rowIdx) ?: emptyList()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) {
                        MenuGridButton(item, onClick = { onSelect(item) })
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
private fun MenuGridButton(item: MenuItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(DiningColors.Surface, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(menuName(item), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text("RM%.2f".format(item.sell_price_myr), fontSize = 12.sp, color = DiningColors.Primary)
    }
}

// 菜品点单弹窗：三口味各自数量 + 订单详情 + 确认下单
@Composable
private fun MenuItemOrderDialog(
    item: MenuItem,
    isTakeaway: Boolean,
    saving: Boolean,
    onConfirm: (noSpicy: Int, spicy: Int, extra: Int, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var qtyNoSpicy by remember { mutableStateOf(0) }
    var qtySpicy by remember { mutableStateOf(0) }
    var qtyExtra by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }

    val totalQty = qtyNoSpicy + qtySpicy + qtyExtra
    val subTotal = item.sell_price_myr * totalQty
    val takeawayFee = if (isTakeaway) 1.0 else 0.0
    val total = subTotal + takeawayFee

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(menuName(item), fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                Text("RM%.2f/${unitLabel(item.unit)}".format(item.sell_price_myr), fontSize = 13.sp, color = DiningColors.TextMuted)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlavorQtyRow(flavorLabel("不辣"), qtyNoSpicy, { qtyNoSpicy = it })
                FlavorQtyRow(flavorLabel("香辣"), qtySpicy, { qtySpicy = it })
                FlavorQtyRow(flavorLabel("加辣"), qtyExtra, { qtyExtra = it })

                // 备注
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(t("备注（少辣、不要葱等）", "Note (less spicy, no scallion)")) },
                    modifier = Modifier.fillMaxWidth()
                )

                // 订单详情（让员工确认）
                if (totalQty > 0) {
                    HorizontalDivider(color = DiningColors.TextMuted.copy(alpha = 0.2f))
                    Text(t("订单详情", "Order Details"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                    if (qtyNoSpicy > 0) Text("${flavorLabel("不辣")} × $qtyNoSpicy", fontSize = 13.sp, color = DiningColors.TextSecondary)
                    if (qtySpicy > 0) Text("${flavorLabel("香辣")} × $qtySpicy", fontSize = 13.sp, color = DiningColors.TextSecondary)
                    if (qtyExtra > 0) Text("${flavorLabel("加辣")} × $qtyExtra", fontSize = 13.sp, color = DiningColors.TextSecondary)
                    if (isTakeaway) Text(t("外带费", "Takeaway Fee") + " +RM1.00", fontSize = 13.sp, color = DiningColors.TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(t("合计", "Total") + " RM%.2f".format(total), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DiningColors.Primary)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = totalQty > 0 && !saving,
                onClick = { onConfirm(qtyNoSpicy, qtySpicy, qtyExtra, note) }
            ) {
                Text(
                    if (saving) t("下单中…", "Placing…") else t("确认下单", "Place Order") + " · RM%.2f".format(total),
                    color = if (totalQty > 0) DiningColors.Primary else DiningColors.TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("取消", "Cancel"), color = DiningColors.TextMuted) }
        }
    )
}

@Composable
private fun FlavorQtyRow(label: String, qty: Int, onQtyChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepperBtn("−", qty > 0) { if (qty > 0) onQtyChange(qty - 1) }
            Text("$qty", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            StepperBtn("+", true) { onQtyChange(qty + 1) }
        }
    }
}

private fun buildOrderItemsJson(
    item: MenuItem,
    qtyNoSpicy: Int,
    qtySpicy: Int,
    qtyExtra: Int,
    isTakeaway: Boolean
): JsonElement {
    return buildJsonArray {
        listOf(
            Triple("不辣", "No Spicy", qtyNoSpicy),
            Triple("香辣", "Spicy", qtySpicy),
            Triple("加辣", "Spicy+", qtyExtra)
        ).forEach { (flavor, flavorEn, qty) ->
            if (qty > 0) {
                add(buildJsonObject {
                    put("item_id", JsonPrimitive(item.id))
                    put("item_name", JsonPrimitive("${item.item_name}（$flavor）"))
                    put("name_en", JsonPrimitive("${item.name_en ?: item.item_name} ($flavorEn)"))
                    put("quantity", JsonPrimitive(qty))
                    put("unit_price_myr", JsonPrimitive(item.sell_price_myr))
                    put("unit", JsonPrimitive(item.unit))
                })
            }
        }
        if (isTakeaway) {
            add(buildJsonObject {
                put("item_id", JsonPrimitive(0))
                put("item_name", JsonPrimitive("外带"))
                put("name_en", JsonPrimitive("Take away"))
                put("quantity", JsonPrimitive(1))
                put("unit_price_myr", JsonPrimitive(1.0))
                put("unit", JsonPrimitive("份"))
            })
        }
    }
}

@Composable
private fun StepperBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(
                if (enabled) DiningColors.SurfaceVariant else DiningColors.SurfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            color = if (enabled) DiningColors.Primary else DiningColors.TextMuted)
    }
}
