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
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.WarehouseItem
import stellarelite.zxsp.ui.theme.DiningColors

private sealed class WarehouseNav {
    object Stock : WarehouseNav()
    object StockIn : WarehouseNav()
    object Fridge : WarehouseNav()
    object MeatProcess : WarehouseNav()
}

@Composable
fun WarehouseScreen() {
    var nav by remember { mutableStateOf<WarehouseNav>(WarehouseNav.Stock) }
    when (val n = nav) {
        is WarehouseNav.Stock -> StockListView(
            onStockIn = { nav = WarehouseNav.StockIn },
            onFridge = { nav = WarehouseNav.Fridge },
            onMeat = { nav = WarehouseNav.MeatProcess }
        )
        is WarehouseNav.StockIn -> StockInScreen(onBack = { nav = WarehouseNav.Stock })
        is WarehouseNav.Fridge -> FridgeScreen(onBack = { nav = WarehouseNav.Stock })
        is WarehouseNav.MeatProcess -> MeatProcessScreen(onBack = { nav = WarehouseNav.Stock })
    }
}

@Composable
private fun StockListView(onStockIn: () -> Unit, onFridge: () -> Unit, onMeat: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<WarehouseItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { SupabaseClient.fetchWarehouseItems() }
                .onSuccess { items = it }
                .onFailure { error = it.message ?: "加载失败" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    val lowCount = items.count { it.stock_qty < it.warning_qty }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📦 仓库库存", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { load() }) { Text("🔄", fontSize = 18.sp) }
            Button(onClick = { showAdd = true }, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)) {
                Text("＋ 新增物料", color = DiningColors.Surface)
            }
        }

        // 快捷入口
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickBtn("📥", "进货入库", Modifier.weight(1f), onStockIn)
            QuickBtn("❄️", "冰箱操作", Modifier.weight(1f), onFridge)
            QuickBtn("🍖", "肉品加工", Modifier.weight(1f), onMeat)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("⚠️ $lowCount 项低库存预警", modifier = Modifier.padding(horizontal = 16.dp),
            fontSize = 13.sp, color = if (lowCount > 0) DiningColors.Error else DiningColors.Success)

        Spacer(modifier = Modifier.height(8.dp))

        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DiningColors.Primary)
            }
            error != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️ $error", color = DiningColors.Error, fontSize = 14.sp)
                    Button(onClick = { load() }) { Text("重试") }
                }
            }
            items.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无物料", color = DiningColors.TextMuted, fontSize = 14.sp)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val low = item.stock_qty < item.warning_qty
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (low) DiningColors.Error.copy(alpha = 0.06f) else DiningColors.Surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(item.item_name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                                Text("预警值 ${item.warning_qty} ${item.unit}", fontSize = 12.sp, color = DiningColors.TextMuted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${item.stock_qty} ${item.unit}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (low) DiningColors.Error else DiningColors.TextPrimary
                                )
                                if (low) Text("需补货", fontSize = 11.sp, color = DiningColors.Error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddMaterialDialog(onDismiss = { showAdd = false }, onDone = { showAdd = false; load() })
    }
}

@Composable
private fun QuickBtn(emoji: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
        }
    }
}

@Composable
private fun AddMaterialDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var warningQty by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("新增物料", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("物料名称（如：五花肉、牛肉）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("单位（如：kg、串、瓶）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = warningQty, onValueChange = { warningQty = it }, label = { Text("预警库存值") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth())
                if (error != null) Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && unit.isNotBlank() && !saving, onClick = {
                scope.launch {
                    saving = true; error = null
                    val wq = warningQty.trim().toDoubleOrNull() ?: 0.0
                    val r = SupabaseClient.insertWarehouseItem(WarehouseItem(
                        item_name = name.trim(), unit = unit.trim(),
                        stock_qty = 0.0, warning_qty = wq,
                        notes = notes.trim().ifBlank { null }
                    ))
                    saving = false
                    if (r != null) onDone() else error = "保存失败"
                }
            }) { Text("保存", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) } }
    )
}
