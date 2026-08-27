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
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.network.FridgeLog
import stellarelite.zxsp.network.MeatProcessLog
import stellarelite.zxsp.network.StockInLog
import stellarelite.zxsp.network.Supplier
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.WarehouseItem
import stellarelite.zxsp.ui.theme.DiningColors

// ============ 进货入库 ============
@Composable
fun StockInScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var items by remember { mutableStateOf<List<WarehouseItem>>(emptyList()) }
    var supplierId by remember { mutableStateOf<Long?>(null) }
    val inQuantities = remember { mutableStateMapOf<Long, String>() }
    val inPrices = remember { mutableStateMapOf<Long, String>() }
    var payMethod by remember { mutableStateOf("cash") }
    var ref by remember { mutableStateOf("") }
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

    val totalCost = items.sumOf { it -> (inQuantities[it.id]?.toDoubleOrNull() ?: 0.0) * (inPrices[it.id]?.toDoubleOrNull() ?: 0.0) }
    val hasItems = items.any { (inQuantities[it.id]?.toDoubleOrNull() ?: 0.0) > 0 }

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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suppliers.forEach { s ->
                            FilterChip(selected = supplierId == s.id, onClick = { supplierId = s.id }, label = { Text(s.supplier_name) })
                        }
                    }
                }

                items(items, key = { it.id }) { item ->
                    StockInRow(item, inQuantities[item.id] ?: "", inPrices[item.id] ?: "",
                        onQty = { inQuantities[item.id] = it }, onPrice = { inPrices[item.id] = it })
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                val inItemsJson = buildStockInJson(items, inQuantities, inPrices)
                                val log = StockInLog(
                                    supplier_id = supplierId ?: 0,
                                    in_items = inItemsJson,
                                    total_cost_myr = totalCost,
                                    pay_method = payMethod,
                                    transaction_ref = ref.trim(),
                                    operate_staff_id = SessionManager.staffId ?: 0,
                                    transaction_datetime = currentIso()
                                )
                                val r = SupabaseClient.insertStockIn(log)
                                saving = false
                                if (r != null) onBack() else error = "入库失败（交易号不能为空）"
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
}

@Composable
private fun StockInRow(item: WarehouseItem, qty: String, price: String, onQty: (String) -> Unit, onPrice: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.item_name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qty, onValueChange = onQty,
                    label = { Text("数量 (${item.unit})") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = price, onValueChange = onPrice,
                    label = { Text("单价 (RM)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun buildStockInJson(items: List<WarehouseItem>, quantities: Map<Long, String>, prices: Map<Long, String>): String {
    val sb = StringBuilder("[")
    var first = true
    items.forEach { item ->
        val q = quantities[item.id]?.toDoubleOrNull() ?: 0.0
        if (q > 0) {
            if (!first) sb.append(",")
            val p = prices[item.id]?.toDoubleOrNull() ?: 0.0
            sb.append("{\"warehouse_item_id\":${item.id},\"qty\":$q,\"unit_price\":$p}")
            first = false
        }
    }
    sb.append("]")
    return sb.toString()
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
