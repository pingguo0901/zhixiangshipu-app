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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.network.DailySales
import stellarelite.zxsp.network.ExpenseRecord
import stellarelite.zxsp.network.StockInLog
import stellarelite.zxsp.network.Supplier
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.WarehouseItem
import stellarelite.zxsp.platform.rememberCamera
import stellarelite.zxsp.platform.toJpegBytes
import stellarelite.zxsp.ui.theme.DiningColors

private sealed class FinanceNav {
    object Expense : FinanceNav()
    object Report : FinanceNav()
}

@Composable
fun FinanceScreen() {
    var nav by remember { mutableStateOf<FinanceNav>(FinanceNav.Expense) }
    when (val n = nav) {
        is FinanceNav.Expense -> ExpenseListView(onReport = { nav = FinanceNav.Report })
        is FinanceNav.Report -> ReportScreen(onBack = { nav = FinanceNav.Expense })
    }
}

// ============ 开销记账 ============
@Composable
private fun ExpenseListView(onReport: () -> Unit) {
    val scope = rememberCoroutineScope()
    var expenses by remember { mutableStateOf<List<ExpenseRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseRecord?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { SupabaseClient.fetchExpenses() }
                .onSuccess { expenses = it }
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
            Text("💸 开销记账", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            if (SessionManager.isAdmin) {
                TextButton(onClick = onReport) { Text("📈 报表", color = DiningColors.Primary) }
            }
            Button(onClick = { showAdd = true }, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)) {
                Text("＋ 记一笔", color = DiningColors.Surface, fontWeight = FontWeight.Bold)
            }
        }

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
            expenses.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无开销记录", color = DiningColors.TextMuted, fontSize = 14.sp)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(expenses, key = { it.id }) { e ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = SessionManager.isAdmin) { editing = e },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(e.expense_title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
                                Text(
                                    "${expenseTypeLabel(e.expense_type)}${if (e.is_personal) " · 私人" else ""}",
                                    fontSize = 12.sp, color = DiningColors.TextMuted
                                )
                            }
                            Text("RM%.2f".format(e.amount_myr), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.Error)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        ExpenseAddDialog(onDismiss = { showAdd = false }, onDone = { showAdd = false; load() })
    }
    editing?.let { e ->
        ExpenseAddDialog(
            initial = e,
            onDismiss = { editing = null },
            onDone = { editing = null; load() }
        )
    }
}

private fun expenseTypeLabel(t: String): String = when (t) {
    "stock" -> "进货"; "utility" -> "杂费"; "logistics" -> "运费"; "maintenance" -> "维修"; else -> t
}

// 开销物品固定清单（员工、租金为费用项不入库；其余为食材，保存后自动入库）
private data class ExpenseItemOption(val name: String, val isStock: Boolean)

private val EXPENSE_ITEM_OPTIONS = listOf(
    ExpenseItemOption("员工", false),
    ExpenseItemOption("租金", false),
    ExpenseItemOption("五花肉", true),
    ExpenseItemOption("鸡腿肉", true),
    ExpenseItemOption("牛上脑", true),
    ExpenseItemOption("羊肩肉", true),
    ExpenseItemOption("生抽", true),
    ExpenseItemOption("蚝油", true),
    ExpenseItemOption("花雕酒", true),
    ExpenseItemOption("糖", true),
    ExpenseItemOption("白胡椒粉", true),
    ExpenseItemOption("孜然粉", true),
    ExpenseItemOption("生姜", true),
    ExpenseItemOption("食用油", true),
    ExpenseItemOption("烧烤酱", true),
    ExpenseItemOption("辣椒粉", true),
    ExpenseItemOption("烧烤撒料（孜然味）", true),
    ExpenseItemOption("烧烤撒料（香辣味）", true),
)

@Composable
private fun ExpenseAddDialog(onDismiss: () -> Unit, onDone: () -> Unit, initial: ExpenseRecord? = null) {
    val scope = rememberCoroutineScope()
    var suppliers by remember { mutableStateOf<List<Supplier>>(emptyList()) }
    var warehouseItems by remember { mutableStateOf<List<WarehouseItem>>(emptyList()) }
    // 编辑模式：从 notes 解析重量（格式 "数量 单位"，如 "2 KG"）
    val initWeight = remember(initial) {
        val n = initial?.notes
        if (n.isNullOrBlank()) "" else n.substringBefore(' ')
    }
    val initWeightUnit = remember(initial) {
        val n = initial?.notes
        if (n.isNullOrBlank()) "KG" else n.substringAfter(' ', "KG")
    }
    var itemName by remember(initial) { mutableStateOf(initial?.expense_type ?: "") }
    var itemExpanded by remember { mutableStateOf(false) }
    var supplierId by remember { mutableStateOf<Long?>(null) }
    var supplierExpanded by remember { mutableStateOf(false) }
    var weight by remember(initial) { mutableStateOf(initWeight) }
    var weightUnit by remember(initial) { mutableStateOf(initWeightUnit) }
    var amount by remember(initial) { mutableStateOf(if (initial != null && initial.amount_myr > 0) initial.amount_myr.toString() else "") }
    var method by remember(initial) { mutableStateOf(initial?.pay_method ?: "cash") }
    var receipt1 by remember { mutableStateOf<ImageBitmap?>(null) }
    var receipt2 by remember { mutableStateOf<ImageBitmap?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val takePhoto1 = rememberCamera { bitmap -> receipt1 = bitmap }
    val takePhoto2 = rememberCamera { bitmap -> receipt2 = bitmap }

    LaunchedEffect(Unit) {
        runCatching { suppliers = SupabaseClient.fetchSuppliers() }
        runCatching { warehouseItems = SupabaseClient.fetchWarehouseItems() }
        // 编辑模式：按 expense_title（批发商名）匹配 supplierId
        supplierId = if (initial != null) {
            suppliers.firstOrNull { it.supplier_name == initial.expense_title }?.id
                ?: suppliers.firstOrNull()?.id
        } else {
            suppliers.firstOrNull()?.id
        }
    }

    val amt = amount.toDoubleOrNull()
    val selectedItem = EXPENSE_ITEM_OPTIONS.firstOrNull { it.name == itemName }
    val weightVal = weight.toDoubleOrNull() ?: 0.0
    val needReceipts = method != "cash"
    // 编辑模式：已有收据不强制重拍
    val hasExistingReceipts = initial != null && initial.attachment_url != null
    val canSave = itemName.isNotBlank() && supplierId != null && amt != null && amt > 0 &&
        (selectedItem?.isStock != true || weightVal > 0) &&
        (!needReceipts || hasExistingReceipts || (receipt1 != null && receipt2 != null)) && !saving

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text(if (initial == null) "记一笔开销" else "编辑开销", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 物品（单选弹出式）
                Box {
                    OutlinedButton(onClick = { itemExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(itemName.ifBlank { "选择物品" }, modifier = Modifier.weight(1f), color = DiningColors.TextPrimary)
                            Text("▾", color = DiningColors.TextMuted)
                        }
                    }
                    DropdownMenu(expanded = itemExpanded, onDismissRequest = { itemExpanded = false }) {
                        EXPENSE_ITEM_OPTIONS.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.name) },
                                onClick = { itemName = opt.name; itemExpanded = false }
                            )
                        }
                    }
                }

                // G/KG 输入框
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("重量") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    listOf("KG" to "KG", "G" to "G").forEach { (v, l) ->
                        FilterChip(selected = weightUnit == v, onClick = { weightUnit = v }, label = { Text(l) })
                    }
                }

                // 批发商（弹出式选项框）
                Box {
                    OutlinedButton(onClick = { supplierExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                suppliers.firstOrNull { it.id == supplierId }?.supplier_name ?: "选择批发商",
                                modifier = Modifier.weight(1f),
                                color = DiningColors.TextPrimary
                            )
                            Text("▾", color = DiningColors.TextMuted)
                        }
                    }
                    DropdownMenu(expanded = supplierExpanded, onDismissRequest = { supplierExpanded = false }) {
                        suppliers.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.supplier_name) },
                                onClick = { supplierId = s.id; supplierExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额 (RM)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("cash" to "现金", "duitnow" to "DuitNow", "tng_ewallet" to "TNG", "alipay" to "支付宝").forEach { (v, l) ->
                        FilterChip(selected = method == v, onClick = { method = v }, label = { Text(l) })
                    }
                }

                if (needReceipts) {
                    Text("收据1（转账收据）", fontSize = 13.sp, color = DiningColors.TextSecondary)
                    OutlinedButton(onClick = { takePhoto1() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (receipt1 == null) "📷 拍照上传" else "📷 重拍", color = DiningColors.Primary)
                    }
                    receipt1?.let {
                        Image(it, contentDescription = "转账收据", modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                    Text("收据2（批发商收据）", fontSize = 13.sp, color = DiningColors.TextSecondary)
                    OutlinedButton(onClick = { takePhoto2() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (receipt2 == null) "📷 拍照上传" else "📷 重拍", color = DiningColors.Primary)
                    }
                    receipt2?.let {
                        Image(it, contentDescription = "批发商收据", modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                }

                if (error != null) Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp)
                if (saving) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DiningColors.Primary)
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                scope.launch {
                    saving = true; error = null
                    var url1: String? = initial?.attachment_url
                    var url2: String? = initial?.receipt_invoice_no
                    if (needReceipts) {
                        if (receipt1 != null) {
                            url1 = receipt1!!.toJpegBytes()?.let { bytes ->
                                SupabaseClient.uploadFile("receipts", "expense_transfer_${Clock.System.now().toEpochMilliseconds()}.jpg", bytes)
                            }
                        }
                        if (receipt2 != null) {
                            url2 = receipt2!!.toJpegBytes()?.let { bytes ->
                                SupabaseClient.uploadFile("receipts", "expense_supplier_${Clock.System.now().toEpochMilliseconds()}.jpg", bytes)
                            }
                        }
                    }
                    val supplierName = suppliers.firstOrNull { it.id == supplierId }?.supplier_name ?: ""
                    val rec = ExpenseRecord(
                        expense_title = supplierName,
                        expense_type = itemName,
                        amount_myr = amt!!,
                        pay_method = method,
                        transaction_ref = "",
                        receipt_invoice_no = url2,
                        attachment_url = url1,
                        is_personal = false,
                        notes = if (weightVal > 0) "${weight.trim()} $weightUnit" else null,
                        operate_staff_id = SupabaseClient.currentStaffId(),
                        transaction_datetime = currentIso()
                    )
                    val ok = if (initial == null) {
                        SupabaseClient.insertExpense(rec) != null
                    } else {
                        SupabaseClient.updateExpense(initial.id, rec)
                    }
                    if (!ok) {
                        saving = false
                        error = "保存失败"
                        return@launch
                    }

                    // 食材类：仓库自动入库（仅新增时入库，编辑不重复入库）
                    if (initial == null && selectedItem?.isStock == true && weightVal > 0) {
                        val wh = warehouseItems.firstOrNull { it.item_name == itemName }
                        if (wh != null) {
                            // G 换算成 KG（仓库单位为 KG）
                            val qtyKg = if (weightUnit == "G") weightVal / 1000.0 else weightVal
                            val inItemsJson = buildJsonArray {
                                add(buildJsonObject {
                                    put("warehouse_item_id", JsonPrimitive(wh.id))
                                    put("qty", JsonPrimitive(qtyKg))
                                    put("unit_price", JsonPrimitive(amt))
                                })
                            }
                            SupabaseClient.insertStockIn(
                                StockInLog(
                                    supplier_id = supplierId ?: 0,
                                    in_items = inItemsJson,
                                    total_cost_myr = amt,
                                    pay_method = method,
                                    transaction_ref = "",
                                    operate_staff_id = SupabaseClient.currentStaffId(),
                                    transaction_datetime = currentIso()
                                )
                            )
                        }
                    }
                    saving = false
                    onDone()
                }
            }) { Text("保存", color = if (canSave) DiningColors.Primary else DiningColors.TextMuted, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (initial != null) {
                    TextButton(onClick = {
                        scope.launch {
                            val ok = SupabaseClient.deleteExpense(initial.id)
                            if (ok) onDone()
                        }
                    }) { Text("删除", color = DiningColors.Error) }
                }
                TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) }
            }
        }
    )
}

// ============ 报表（仅老板） ============
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf(todayDate()) }
    var end by remember { mutableStateOf(todayDate()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<DailySales>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true; error = null
            runCatching { SupabaseClient.fetchDailySales(start, end) }
                .onSuccess { rows = it }
                .onFailure { error = it.message ?: "加载失败" }
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回", color = DiningColors.Primary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("📈 报表统计", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp)) {
                Text("📅 $start", color = DiningColors.TextPrimary, fontWeight = FontWeight.Medium)
            }
            OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(12.dp)) {
                Text("📅 $end", color = DiningColors.TextPrimary, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { load() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)) {
            Text("查询", color = DiningColors.Surface, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DiningColors.Primary) }
        } else if (error != null) {
            Text("⚠️ $error", color = DiningColors.Error, fontSize = 14.sp)
        } else {
            // 汇总
            val totalSales = rows.sumOf { it.total_sales_myr }
            val totalCost = rows.sumOf { it.total_stock_cost_myr }
            val totalExpense = rows.sumOf { it.total_expense_myr }
            val totalProfit = rows.sumOf { it.gross_profit_myr }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DiningColors.Primary)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReportRow("总收入", "RM %.2f".format(totalSales))
                    ReportRow("进货成本", "RM %.2f".format(totalCost))
                    ReportRow("业务开销", "RM %.2f".format(totalExpense))
                    HorizontalDivider(color = DiningColors.Surface.copy(alpha = 0.3f))
                    ReportRow("毛利", "RM %.2f".format(totalProfit))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.period_date }) { r ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(r.period_date, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary)
                            Text("营收 RM%.2f · 成本 RM%.2f · 开销 RM%.2f".format(r.total_sales_myr, r.total_stock_cost_myr, r.total_expense_myr), fontSize = 12.sp, color = DiningColors.TextSecondary)
                            Text("毛利 RM%.2f".format(r.gross_profit_myr), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DiningColors.Primary)
                        }
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { start = millisToDate(it) }
                    showStartPicker = false
                }) { Text("确定", color = DiningColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("取消", color = DiningColors.TextMuted) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndPicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { end = millisToDate(it) }
                    showEndPicker = false
                }) { Text("确定", color = DiningColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("取消", color = DiningColors.TextMuted) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = DiningColors.Surface.copy(alpha = 0.8f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DiningColors.Surface)
    }
}
