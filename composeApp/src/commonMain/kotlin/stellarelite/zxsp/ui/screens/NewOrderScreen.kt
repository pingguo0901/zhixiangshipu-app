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
import stellarelite.zxsp.network.CustomerOrder
import stellarelite.zxsp.network.MenuItem
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.TableList
import stellarelite.zxsp.ui.theme.DiningColors

@Composable
fun NewOrderScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var menuItems by remember { mutableStateOf<List<MenuItem>>(emptyList()) }
    var tables by remember { mutableStateOf<List<TableList>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    var tableId by remember { mutableStateOf<Long?>(null) }
    var isTakeaway by remember { mutableStateOf(false) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val quantities = remember { mutableStateMapOf<Long, Int>() }
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

    val totalAmount = menuItems.sumOf { it.sell_price_myr * (quantities[it.id] ?: 0) }
    val totalCount = quantities.values.sum()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = DiningColors.Primary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("📝 新建订单", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
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
                    Text("用餐方式", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isTakeaway,
                            onClick = { isTakeaway = false; tableId = tables.firstOrNull()?.id },
                            label = { Text("堂食") }
                        )
                        FilterChip(
                            selected = isTakeaway,
                            onClick = { isTakeaway = true; tableId = null },
                            label = { Text("外卖") }
                        )
                    }
                }

                // 桌台选择（堂食）
                if (!isTakeaway) {
                    item {
                        Text("选择桌台", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (tables.isEmpty()) {
                            Text("暂无空闲桌台", fontSize = 13.sp, color = DiningColors.TextMuted)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                tables.forEach { t ->
                                    FilterChip(
                                        selected = tableId == t.id,
                                        onClick = { tableId = t.id },
                                        label = { Text(t.table_no) }
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
                        label = { Text("顾客姓名（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("顾客电话（可选）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 菜品
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("选择菜品", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                        Text("共 $totalCount 件 · RM %.2f".format(totalAmount), fontSize = 13.sp, color = DiningColors.Primary)
                    }
                }

                items(menuItems, key = { it.id }) { item ->
                    MenuItemRow(
                        item = item,
                        quantity = quantities[item.id] ?: 0,
                        onMinus = {
                            val q = quantities[item.id] ?: 0
                            if (q > 1) quantities[item.id] = q - 1 else quantities.remove(item.id)
                        },
                        onPlus = { quantities[item.id] = (quantities[item.id] ?: 0) + 1 }
                    )
                }

                // 备注
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("备注（少辣、不要葱等）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (error != null) {
                    item { Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp) }
                }

                item {
                    Button(
                        onClick = {
                            scope.launch {
                                saving = true
                                error = null
                                val itemsJson = buildOrderItemsJson(menuItems, quantities)
                                val order = CustomerOrder(
                                    table_id = if (isTakeaway) null else tableId,
                                    customer_name = customerName.trim().ifBlank { null },
                                    customer_phone = customerPhone.trim().ifBlank { null },
                                    order_items = itemsJson,
                                    total_amount_myr = totalAmount,
                                    notes = notes.trim().ifBlank { null },
                                    created_by_staff_id = SessionManager.staffId ?: 0,
                                    order_datetime = currentIso()
                                )
                                val r = SupabaseClient.insertOrder(order)
                                saving = false
                                if (r != null) onBack() else error = "下单失败"
                            }
                        },
                        enabled = totalCount > 0 && !saving,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DiningColors.Primary,
                            disabledContainerColor = DiningColors.TextMuted.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            if (saving) "提交中…" else "确认下单 · RM %.2f".format(totalAmount),
                            color = DiningColors.Surface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

private fun buildOrderItemsJson(items: List<MenuItem>, quantities: Map<Long, Int>): String {
    val sb = StringBuilder("[")
    var first = true
    items.forEach { item ->
        val q = quantities[item.id] ?: 0
        if (q > 0) {
            if (!first) sb.append(",")
            sb.append("{\"item_id\":${item.id},\"item_name\":\"${item.item_name}\",\"quantity\":$q,\"unit_price_myr\":${item.sell_price_myr},\"unit\":\"${item.unit}\"}")
            first = false
        }
    }
    sb.append("]")
    return sb.toString()
}

@Composable
private fun MenuItemRow(item: MenuItem, quantity: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.item_name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                Text("${item.category} · RM%.2f/${item.unit}".format(item.sell_price_myr), fontSize = 12.sp, color = DiningColors.TextMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StepperBtn("−", quantity > 0, onMinus)
                Text("$quantity", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
                StepperBtn("+", true, onPlus)
            }
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
