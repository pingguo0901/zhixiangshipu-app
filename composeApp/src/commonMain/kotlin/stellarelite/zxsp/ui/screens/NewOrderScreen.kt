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
import kotlinx.serialization.json.jsonArray
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
    val cart = remember { mutableStateListOf<CartLine>() }
    var selectedItem by remember { mutableStateOf<MenuItem?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discount by remember { mutableStateOf("") }

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

    // 购物车合计（含外卖费）
    val cartTotal = cart.sumOf { line ->
        line.item.sell_price_myr * (line.qtyNoSpicy + line.qtySpicy + line.qtyExtra)
    } + (if (isTakeaway) 1.0 else 0.0)
    val discountVal = discount.toDoubleOrNull() ?: 0.0
    val finalTotal = (cartTotal - discountVal).coerceAtLeast(0.0)

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

                // 下单详情（购物车）
                item {
                    Text(t("下单详情", "Order Details"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (cart.isEmpty()) {
                        Text(t("暂未添加菜品", "No items yet"), fontSize = 13.sp, color = DiningColors.TextMuted)
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                cart.forEachIndexed { idx, line ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(menuName(line.item), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                                            val flavors = buildList {
                                                if (line.qtyNoSpicy > 0) add("${flavorLabel("不辣")}×${line.qtyNoSpicy}")
                                                if (line.qtySpicy > 0) add("${flavorLabel("香辣")}×${line.qtySpicy}")
                                                if (line.qtyExtra > 0) add("${flavorLabel("加辣")}×${line.qtyExtra}")
                                            }
                                            if (flavors.isNotEmpty()) Text(flavors.joinToString("  "), fontSize = 12.sp, color = DiningColors.TextSecondary)
                                        }
                                        TextButton(onClick = { cart.removeAt(idx) }) {
                                            Text("✕", color = DiningColors.Error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                HorizontalDivider(color = DiningColors.TextMuted.copy(alpha = 0.2f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(t("合计", "Total"), fontSize = 13.sp, color = DiningColors.TextSecondary)
                                    Text("RM%.2f".format(cartTotal), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.Primary)
                                }
                            }
                        }
                    }
                }

                // 折扣输入框（下单详情下方）
                if (cart.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = discount,
                            onValueChange = { discount = it },
                            label = { Text(t("折扣 (RM)", "Discount (RM)")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (discountVal > 0.0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(t("折后应付", "Amount Due"), fontSize = 14.sp, color = DiningColors.TextSecondary)
                                Text("RM%.2f".format(finalTotal), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.Primary)
                            }
                        }
                    }
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

            // 底部确认下单按钮（点击才真正下单 + 自动打印厨房单）
            if (cart.isNotEmpty()) {
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            val itemsJson = buildCartItemsJson(cart.toList(), isTakeaway)
                            val order = CustomerOrder(
                                table_id = tableId,
                                customer_name = null,
                                customer_phone = null,
                                order_items = itemsJson,
                                total_amount_myr = cartTotal,
                                discount = discountVal,
                                notes = null,
                                created_by_staff_id = SupabaseClient.currentStaffId(),
                                order_datetime = currentIso()
                            )
                            val r = SupabaseClient.insertOrder(order)
                            saving = false
                            if (r != null) {
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
                    enabled = !saving && tableId != null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DiningColors.Primary,
                        disabledContainerColor = DiningColors.TextMuted.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        if (saving) t("下单中…", "Placing…") else t("确认下单", "Place Order") + " · RM%.2f".format(finalTotal),
                        color = DiningColors.Surface, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // 菜品弹窗：选口味数量 → 加入购物车（不真正下单）
    selectedItem?.let { item ->
        MenuItemOrderDialog(
            item = item,
            isTakeaway = isTakeaway,
            onConfirm = { noSpicy, spicy, extra, note ->
                selectedItem = null
                val existing = cart.firstOrNull { it.item.id == item.id }
                if (existing != null) {
                    val i = cart.indexOf(existing)
                    cart[i] = existing.copy(
                        qtyNoSpicy = existing.qtyNoSpicy + noSpicy,
                        qtySpicy = existing.qtySpicy + spicy,
                        qtyExtra = existing.qtyExtra + extra,
                        note = if (note.isNotBlank()) note else existing.note
                    )
                } else {
                    cart.add(CartLine(item, noSpicy, spicy, extra, note))
                }
            },
            onDismiss = { selectedItem = null }
        )
    }
}

private val FLAVOR_OPTIONS = listOf("不辣", "香辣", "加辣")
private val FLAVOR_EN = mapOf("不辣" to "No Spicy", "香辣" to "Spicy", "加辣" to "Spicy+")

// 购物车条目
private data class CartLine(
    val item: MenuItem,
    val qtyNoSpicy: Int,
    val qtySpicy: Int,
    val qtyExtra: Int,
    val note: String
)

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

// 菜品点单弹窗：三口味各自数量 + 备注 + 加入购物车
@Composable
private fun MenuItemOrderDialog(
    item: MenuItem,
    isTakeaway: Boolean,
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
                enabled = totalQty > 0,
                onClick = { onConfirm(qtyNoSpicy, qtySpicy, qtyExtra, note) }
            ) {
                Text(
                    t("添加", "Add") + " · RM%.2f".format(total),
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

// 把整个购物车生成 order_items JSON
private fun buildCartItemsJson(cart: List<CartLine>, isTakeaway: Boolean): JsonElement {
    return buildJsonArray {
        cart.forEach { line ->
            listOf(
                Triple("不辣", "No Spicy", line.qtyNoSpicy),
                Triple("香辣", "Spicy", line.qtySpicy),
                Triple("加辣", "Spicy+", line.qtyExtra)
            ).forEach { (flavor, flavorEn, qty) ->
                if (qty > 0) {
                    add(buildJsonObject {
                        put("item_id", JsonPrimitive(line.item.id))
                        put("item_name", JsonPrimitive("${line.item.item_name}（$flavor）"))
                        put("name_en", JsonPrimitive("${line.item.name_en ?: line.item.item_name} ($flavorEn)"))
                        put("quantity", JsonPrimitive(qty))
                        put("unit_price_myr", JsonPrimitive(line.item.sell_price_myr))
                        put("unit", JsonPrimitive(line.item.unit))
                    })
                }
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

// ============ 加单页面（购物车模式，跟新建订单页面一致） ============
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddItemsScreen(order: CustomerOrder, tableNo: String?, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val cart = remember { mutableStateListOf<CartLine>() }
    var selectedItem by remember { mutableStateOf<MenuItem?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var discount by remember { mutableStateOf("") }
    var addOnTextZh by remember { mutableStateOf<String?>(null) }
    var addOnTextEn by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { menuItems = SupabaseClient.fetchMenuItems().filter { it.is_active } }
        loading = false
    }

    // 本次加单新增金额
    val addedAmount = cart.sumOf { line ->
        line.item.sell_price_myr * (line.qtyNoSpicy + line.qtySpicy + line.qtyExtra)
    }
    val discountVal = discount.toDoubleOrNull() ?: 0.0
    val finalAdded = (addedAmount - discountVal).coerceAtLeast(0.0)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(t("‹ 返回", "‹ Back"), color = DiningColors.Primary)
            }
            Text(
                t("加单", "Add Items") + " · ${order.order_no}",
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
                // 下单详情（本次新增）
                item {
                    Text(t("下单详情", "Order Details"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (cart.isEmpty()) {
                        Text(t("暂未添加菜品", "No items yet"), fontSize = 13.sp, color = DiningColors.TextMuted)
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                cart.forEachIndexed { idx, line ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(menuName(line.item), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                                            val flavors = buildList {
                                                if (line.qtyNoSpicy > 0) add("${flavorLabel("不辣")}×${line.qtyNoSpicy}")
                                                if (line.qtySpicy > 0) add("${flavorLabel("香辣")}×${line.qtySpicy}")
                                                if (line.qtyExtra > 0) add("${flavorLabel("加辣")}×${line.qtyExtra}")
                                            }
                                            if (flavors.isNotEmpty()) Text(flavors.joinToString("  "), fontSize = 12.sp, color = DiningColors.TextSecondary)
                                        }
                                        TextButton(onClick = { cart.removeAt(idx) }) {
                                            Text("✕", color = DiningColors.Error, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                HorizontalDivider(color = DiningColors.TextMuted.copy(alpha = 0.2f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(t("本次加单合计", "Add-on Total"), fontSize = 13.sp, color = DiningColors.TextSecondary)
                                    Text("RM%.2f".format(addedAmount), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.Primary)
                                }
                            }
                        }
                    }
                }

                // 折扣输入框（下单详情下方）
                if (cart.isNotEmpty()) {
                    item {
                        OutlinedTextField(
                            value = discount,
                            onValueChange = { discount = it },
                            label = { Text(t("折扣 (RM)", "Discount (RM)")) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (discountVal > 0.0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(t("折后应付", "Amount Due"), fontSize = 14.sp, color = DiningColors.TextSecondary)
                                Text("RM%.2f".format(finalAdded), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.Primary)
                            }
                        }
                    }
                }

                // 选择菜品
                item {
                    Text(t("选择菜品", "Select Items"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    MenuGrid(menuItems, onSelect = { selectedItem = it })
                }

                if (error != null) {
                    item { Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp) }
                }
            }

            // 底部确认加单按钮
            if (cart.isNotEmpty()) {
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            val addedItems = buildCartItemsJson(cart.toList(), false)
                            val merged = buildJsonArray {
                                order.order_items.jsonArray.forEach { add(it) }
                                addedItems.jsonArray.forEach { add(it) }
                            }
                            val newSubTotal = order.total_amount_myr + addedAmount
                            val newDiscount = order.discount + discountVal
                            val ok = SupabaseClient.updateOrderItems(order.id, merged, newSubTotal, newDiscount)
                            saving = false
                            if (ok) {
                                // 厨房追加单：只打印本次新增
                                val addedLinesZh = mutableListOf<KitchenLine>()
                                val addedLinesEn = mutableListOf<KitchenLine>()
                                cart.forEach { line ->
                                    val en = line.item.name_en?.takeIf { it.isNotBlank() } ?: line.item.item_name
                                    if (line.qtyNoSpicy > 0) { addedLinesZh.add(KitchenLine(line.qtyNoSpicy, line.item.item_name, "不辣")); addedLinesEn.add(KitchenLine(line.qtyNoSpicy, en, "No Spicy")) }
                                    if (line.qtySpicy > 0) { addedLinesZh.add(KitchenLine(line.qtySpicy, line.item.item_name, "香辣")); addedLinesEn.add(KitchenLine(line.qtySpicy, en, "Spicy")) }
                                    if (line.qtyExtra > 0) { addedLinesZh.add(KitchenLine(line.qtyExtra, line.item.item_name, "加辣")); addedLinesEn.add(KitchenLine(line.qtyExtra, en, "Spicy+")) }
                                }
                                if (addedLinesZh.isNotEmpty()) {
                                    val time = formatDateTimeMy(currentIso())
                                    val tblZh = tableNo ?: t("外卖", "Takeaway")
                                    val tblEn = if (tableNo == null || tableNo == "外卖") "Takeaway" else tableNo
                                    addOnTextZh = buildKitchenAddOnOrder(orderNo = order.order_no, tableNo = tblZh, time = time, items = addedLinesZh)
                                    addOnTextEn = buildKitchenAddOnOrderEnglish(orderNo = order.order_no, tableNo = tblEn, time = time, items = addedLinesEn)
                                } else {
                                    onDone()
                                }
                            } else error = t("加单失败", "Add items failed")
                        }
                    },
                    enabled = !saving && cart.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DiningColors.Primary,
                        disabledContainerColor = DiningColors.TextMuted.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        if (saving) t("加单中…", "Adding…") else t("确认加单", "Add Items") + " · RM%.2f".format(finalAdded),
                        color = DiningColors.Surface, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // 菜品弹窗：选口味数量 → 加入购物车
    selectedItem?.let { item ->
        MenuItemOrderDialog(
            item = item,
            isTakeaway = false,
            onConfirm = { noSpicy, spicy, extra, note ->
                selectedItem = null
                val existing = cart.firstOrNull { it.item.id == item.id }
                if (existing != null) {
                    val i = cart.indexOf(existing)
                    cart[i] = existing.copy(
                        qtyNoSpicy = existing.qtyNoSpicy + noSpicy,
                        qtySpicy = existing.qtySpicy + spicy,
                        qtyExtra = existing.qtyExtra + extra,
                        note = if (note.isNotBlank()) note else existing.note
                    )
                } else {
                    cart.add(CartLine(item, noSpicy, spicy, extra, note))
                }
            },
            onDismiss = { selectedItem = null }
        )
    }

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
