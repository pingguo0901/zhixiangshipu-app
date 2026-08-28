package stellarelite.zxsp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.network.FridgeLog
import stellarelite.zxsp.network.MeatProcessLog
import stellarelite.zxsp.network.StockInLog
import stellarelite.zxsp.network.Supplier
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.WarehouseItem
import stellarelite.zxsp.ui.theme.DiningColors

// ============ 进货入库 ============
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StockInScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var items by remember { mutableStateOf<List<WarehouseItem>>(emptyList()) }
    var supplierId by remember { mutableStateOf<Long?>(null) }
    val entries = remember { mutableStateListOf<StockInEntry>() }
    var payMethod by remember { mutableStateOf("cash") }
    var ref by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayDate()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddItem by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching {
            suppliers = SupabaseClient.fetchSuppliers()
            items = SupabaseClient.fetchWarehouseItems()
            supplierId = suppliers.firstOrNull()?.id
        }
        loading = false
    }

    val totalCost = entries.sumOf { (it.qty.toDoubleOrNull() ?: 0.0) * (it.price.toDoubleOrNull() ?: 0.0) }
    val hasItems = entries.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = DiningColors.Primary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("📥 进货入库", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DiningColors.Primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("选择供应商", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suppliers.forEach { s ->
                            FilterChip(selected = supplierId == s.id, onClick = { supplierId = s.id }, label = { Text(s.supplier_name) })
                        }
                    }
                }

                item {
                    Text("进货日期", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp)) {
                        Text("📅 $date", color = DiningColors.TextPrimary, fontWeight = FontWeight.Medium)
                    }
                }

                item {
                    Text("进货物料", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (entries.isEmpty()) {
                        Text("还没有添加物料，点下方按钮添加", fontSize = 13.sp, color = DiningColors.TextMuted)
                    }
                }

                items(entries) { e ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(e.itemName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                                Text("数量 ${e.qty} ${e.unit} · 单价 RM${e.price}", fontSize = 12.sp, color = DiningColors.TextMuted)
                            }
                            TextButton(onClick = { entries.remove(e) }) { Text("删除", color = DiningColors.Error) }
                        }
                    }
                }

                item {
                    OutlinedButton(onClick = { showAddItem = true }, modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)) {
                        Text("＋ 添加物料", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold)
                    }
                }

                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("cash" to "现金", "duitnow" to "DuitNow", "tng_ewallet" to "TNG", "alipay" to "支付宝").forEach { (v, l) ->
                            FilterChip(selected = payMethod == v, onClick = { payMethod = v }, label = { Text(l) })
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ref,
                        onValueChange = { ref = it },
                        label = { Text(if (payMethod == "cash") "现金编号 CASH-PAY-日期-序号" else "交易号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (error != null) item { Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp) }

                item {
                    Button(
                        onClick = {
                            scope.launch {
                                saving = true
                                error = null
                                val inItemsJson = buildStockInJson(entries)
                                val log = StockInLog(
                                    supplier_id = supplierId ?: 0,
                                    in_items = inItemsJson,
                                    total_cost_myr = totalCost,
                                    pay_method = payMethod,
                                    transaction_ref = ref.trim(),
                                    operate_staff_id = SupabaseClient.currentStaffId(),
                                    transaction_datetime = if (date.isNotBlank()) "${date.trim()}T${currentIso().substringAfter('T')}" else currentIso()
                                )
                                val r = SupabaseClient.insertStockIn(log)
                                saving = false
                                if (r != null) onBack() else error = "入库失败：" + (SupabaseClient.lastError ?: "")
                            }
                        },
                        enabled = hasItems && supplierId != null && ref.isNotBlank() && !saving,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DiningColors.Primary,
                            disabledContainerColor = DiningColors.TextMuted.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            if (saving) "保存中…" else "保存进货 · 总成本 RM %.2f".format(totalCost),
                            color = DiningColors.Surface, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { date = millisToDate(it) }
                    showDatePicker = false
                }) { Text("确定", color = DiningColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消", color = DiningColors.TextMuted) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showAddItem) {
        AddStockItemDialog(
            items = items,
            onAdd = { entries.add(it) },
            onDismiss = { showAddItem = false }
        )
    }
}

private data class StockInEntry(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val qty: String,
    val price: String
)

@Composable
private fun AddStockItemDialog(items: List<WarehouseItem>, onAdd: (StockInEntry) -> Unit, onDismiss: () -> Unit) {
    var itemId by remember { mutableStateOf<Long?>(null) }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val selectedItem = items.firstOrNull { it.id == itemId }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("添加进货物料", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedItem?.item_name ?: "选择物料类型", modifier = Modifier.weight(1f), color = DiningColors.TextPrimary)
                            Text("▾", color = DiningColors.TextMuted)
                        }
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        items.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.item_name) },
                                onClick = { itemId = item.id; expanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("数量 (${selectedItem?.unit ?: ""})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("单价 (RM)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = itemId != null && (qty.toDoubleOrNull() ?: 0.0) > 0 && !price.isBlank(),
                onClick = {
                    val s = selectedItem
                    if (s != null) {
                        onAdd(StockInEntry(itemId = s.id, itemName = s.item_name, unit = s.unit, qty = qty.trim(), price = price.trim()))
                    }
                }
            ) { Text("添加", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) } }
    )
}

private fun buildStockInJson(entries: List<StockInEntry>): JsonElement {
    return buildJsonArray {
        entries.forEach { e ->
            val q = e.qty.toDoubleOrNull() ?: 0.0
            val p = e.price.toDoubleOrNull() ?: 0.0
            add(buildJsonObject {
                put("warehouse_item_id", JsonPrimitive(e.itemId))
                put("qty", JsonPrimitive(q))
                put("unit_price", JsonPrimitive(p))
            })
        }
    }
}

// ============ 冰箱操作 ============
@Composable
fun FridgeScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<WarehouseItem>>(emptyList()) }
    var itemId by remember { mutableStateOf<Long?>(null) }
    var takeQty by remember { mutableStateOf("") }
    var returnQty by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching { items = SupabaseClient.fetchWarehouseItems() }
        loading = false
    }

    val take = takeQty.toDoubleOrNull() ?: 0.0
    val ret = returnQty.toDoubleOrNull() ?: 0.0
    val used = take - ret

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = DiningColors.Primary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("❄️ 冰箱操作", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("选择物料", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                FilterChip(
                    selected = itemId == item.id,
                    onClick = { itemId = item.id },
                    label = { Text("${item.item_name}（库存 ${item.stock_qty} ${item.unit}）") }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = takeQty, onValueChange = { takeQty = it },
            label = { Text("取出数量") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = returnQty, onValueChange = { returnQty = it },
            label = { Text("放回数量（没有则填 0）") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("实际消耗：$used（后端自动计算）", fontSize = 13.sp, color = DiningColors.TextSecondary)

        if (error != null) Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    saving = true
                    error = null
                    val log = FridgeLog(
                        warehouse_item_id = itemId ?: 0,
                        take_qty = take,
                        return_qty = ret,
                        used_qty = 0.0, // 后端计算
                        operate_staff_id = SessionManager.staffId ?: 0,
                        log_time = currentIso()
                    )
                    val r = SupabaseClient.insertFridgeLog(log)
                    saving = false
                    if (r != null) onBack() else error = "保存失败"
                }
            },
            enabled = itemId != null && take > 0 && !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DiningColors.Primary,
                disabledContainerColor = DiningColors.TextMuted.copy(alpha = 0.3f)
            )
        ) {
            Text(if (saving) "保存中…" else "保存日志", color = DiningColors.Surface, fontWeight = FontWeight.Bold)
        }
    }
}

// ============ 肉品加工 ============
@Composable
fun MeatProcessScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<WarehouseItem>>(emptyList()) }
    var itemId by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf("raw") }
    var qty by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching { items = SupabaseClient.fetchWarehouseItems() }
        loading = false
    }

    val processQty = qty.toDoubleOrNull() ?: 0.0

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = DiningColors.Primary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("🍖 肉品加工", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("选择物料", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                FilterChip(
                    selected = itemId == item.id,
                    onClick = { itemId = item.id },
                    label = { Text(item.item_name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("加工状态", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("raw" to "未腌制", "marinated" to "已腌制", "unskewered" to "未串好", "skewered" to "已串好").forEach { (v, l) ->
                FilterChip(selected = status == v, onClick = { status = v }, label = { Text(l) })
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = qty, onValueChange = { qty = it },
            label = { Text("处理数量") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        if (error != null) Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp)

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    saving = true
                    error = null
                    val log = MeatProcessLog(
                        warehouse_item_id = itemId ?: 0,
                        process_status = status,
                        process_qty = processQty,
                        operate_staff_id = SessionManager.staffId ?: 0,
                        process_time = currentIso()
                    )
                    val r = SupabaseClient.insertMeatProcessLog(log)
                    saving = false
                    if (r != null) onBack() else error = "保存失败"
                }
            },
            enabled = itemId != null && processQty > 0 && !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DiningColors.Primary,
                disabledContainerColor = DiningColors.TextMuted.copy(alpha = 0.3f)
            )
        ) {
            Text(if (saving) "保存中…" else "保存日志", color = DiningColors.Surface, fontWeight = FontWeight.Bold)
        }
    }
}
