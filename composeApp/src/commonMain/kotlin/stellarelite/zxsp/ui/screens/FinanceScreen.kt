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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Print
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import stellarelite.zxsp.data.SessionManager
import stellarelite.zxsp.network.DailySales
import stellarelite.zxsp.network.ExpenseRecord
import stellarelite.zxsp.network.StockInLog
import stellarelite.zxsp.network.Supplier
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.WarehouseItem
import stellarelite.zxsp.platform.rememberCamera
import stellarelite.zxsp.platform.printReceiptText
import stellarelite.zxsp.platform.toImageBitmap
import stellarelite.zxsp.platform.toJpegBytes
import stellarelite.zxsp.ui.theme.DiningColors
import stellarelite.zxsp.util.ReceiptFormatter
import stellarelite.zxsp.util.decodeJwtSub

private sealed class FinanceNav {
    object Expense : FinanceNav()
    object Report : FinanceNav()
}

@Composable
fun FinanceScreen() {
    var nav by remember { mutableStateOf<FinanceNav>(FinanceNav.Expense) }
    // 进入页面时刷新角色，修复旧会话 role 缓存导致 Admin 按钮消失
    LaunchedEffect(Unit) {
        val uid = SessionManager.authUid ?: decodeJwtSub(SessionManager.accessToken ?: "")
        val staff = uid?.let { runCatching { SupabaseClient.fetchMyStaff(it) }.getOrNull() }
        if (staff != null && staff.is_active) {
            SessionManager.setSession(SessionManager.accessToken, staff.id, staff.staff_name, staff.role, uid)
        }
    }
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
    var actionRecord by remember { mutableStateOf<ExpenseRecord?>(null) }
    var viewingDetail by remember { mutableStateOf<ExpenseRecord?>(null) }

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
            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = DiningColors.TextPrimary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("开销记账", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            if (SessionManager.isAdmin) {
                TextButton(onClick = onReport) {
                    Icon(Icons.Outlined.Assessment, contentDescription = null, tint = DiningColors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("报表", color = DiningColors.Primary)
                }
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
            else -> {
                val grouped = expenses.groupBy { it.transaction_datetime?.take(10) ?: "" }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    grouped.forEach { (date, list) ->
                        item(key = "date-$date") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = DiningColors.TextPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (date.isBlank()) "未标注日期" else fmtMyDate(date),
                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "RM%.2f".format(list.sumOf { it.amount_myr }),
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DiningColors.Primary
                                )
                            }
                        }
                        items(list, key = { it.id }) { e ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { actionRecord = e },
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
                                            "${expenseTypeLabel(e.expense_type)}" +
                                                (e.notes?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                                                (if (e.is_personal) " · 私人" else ""),
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
    actionRecord?.let { e ->
        ExpenseActionDialog(
            record = e,
            onViewDetail = { actionRecord = null; viewingDetail = e },
            onEdit = { actionRecord = null; editing = e },
            onDismiss = { actionRecord = null }
        )
    }
    viewingDetail?.let { e ->
        ExpenseDetailDialog(record = e, onDismiss = { viewingDetail = null })
    }
}

private fun expenseTypeLabel(t: String): String = when (t) {
    "stock" -> "进货"; "utility" -> "杂费"; "logistics" -> "运费"; "maintenance" -> "维修"; else -> t
}

private fun payMethodLabel(m: String): String = when (m) {
    "cash" -> "现金"; "duitnow" -> "DuitNow"; "tng_ewallet" -> "TNG"; "alipay" -> "支付宝"; else -> m
}

@Composable
private fun ExpenseActionDialog(
    record: ExpenseRecord,
    onViewDetail: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text(expenseTypeLabel(record.expense_type), fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onViewDetail, modifier = Modifier.fillMaxWidth()) {
                    Text("查看开销详情", color = DiningColors.Primary)
                }
                if (SessionManager.isAdmin) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                        Text("编辑", color = DiningColors.Primary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) } }
    )
}

@Composable
private fun ExpenseDetailDialog(record: ExpenseRecord, onDismiss: () -> Unit) {
    var transferBmp by remember { mutableStateOf<ImageBitmap?>(null) }
    var supplierBmp by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(record) {
        record.attachment_url?.let { url ->
            runCatching { SupabaseClient.downloadFile(url)?.toImageBitmap() }
                .onSuccess { transferBmp = it }
        }
        record.receipt_invoice_no?.let { url ->
            runCatching { SupabaseClient.downloadFile(url)?.toImageBitmap() }
                .onSuccess { supplierBmp = it }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("开销详情", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailLine("物品", expenseTypeLabel(record.expense_type))
                DetailLine("批发商", record.expense_title.ifBlank { "—" })
                record.notes?.takeIf { it.isNotBlank() }?.let { DetailLine("重量", it) }
                DetailLine("金额", "RM%.2f".format(record.amount_myr))
                DetailLine("付款方式", payMethodLabel(record.pay_method))
                DetailLine("日期", fmtMyTime(record.transaction_datetime ?: ""))
                if (transferBmp != null) {
                    Text("转账收据", fontSize = 12.sp, color = DiningColors.TextSecondary)
                    Image(transferBmp!!, contentDescription = "转账收据", modifier = Modifier.fillMaxWidth().height(140.dp))
                }
                if (supplierBmp != null) {
                    Text("批发商收据", fontSize = 12.sp, color = DiningColors.TextSecondary)
                    Image(supplierBmp!!, contentDescription = "批发商收据", modifier = Modifier.fillMaxWidth().height(140.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭", color = DiningColors.TextMuted) } }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = DiningColors.TextSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DiningColors.TextPrimary)
    }
}

// 开销物品：费用项固定（员工、租金不入库），食材项动态从仓库读取
private val EXPENSE_FEE_OPTIONS = listOf("员工", "租金")

@OptIn(ExperimentalMaterial3Api::class)
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
    // 日期（临时，录入历史数据用）
    var date by remember(initial) { mutableStateOf(initial?.transaction_datetime?.take(10) ?: todayDate()) }
    var showDatePicker by remember { mutableStateOf(false) }

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
    // 物品选项 = 费用项（员工/租金）+ 仓库食材（动态）
    val itemOptions = remember(warehouseItems) { EXPENSE_FEE_OPTIONS + warehouseItems.map { it.item_name } }
    val isStockItem = itemName.isNotBlank() && itemName !in EXPENSE_FEE_OPTIONS
    val weightVal = weight.toDoubleOrNull() ?: 0.0
    val needTransferReceipt = method != "cash"
    // 编辑模式：已有收据不强制重拍
    val hasExistingReceipts = initial != null && initial.attachment_url != null
    val canSave = itemName.isNotBlank() && supplierId != null && amt != null && amt > 0 &&
        (!isStockItem || weightVal > 0) &&
        (hasExistingReceipts || (if (needTransferReceipt) receipt1 != null && receipt2 != null else receipt2 != null)) && !saving

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
                // 日期（临时，录入历史数据用）
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = DiningColors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("日期：$date", color = DiningColors.TextPrimary)
                }
                // 物品（单选弹出式）
                Box {
                    OutlinedButton(onClick = { itemExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(itemName.ifBlank { "选择物品" }, modifier = Modifier.weight(1f), color = DiningColors.TextPrimary)
                            Text("▾", color = DiningColors.TextMuted)
                        }
                    }
                    DropdownMenu(expanded = itemExpanded, onDismissRequest = { itemExpanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                        itemOptions.forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { itemName = name; itemExpanded = false }
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
                    DropdownMenu(expanded = supplierExpanded, onDismissRequest = { supplierExpanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
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

                if (needTransferReceipt) {
                    Text("收据1（转账收据）", fontSize = 13.sp, color = DiningColors.TextSecondary)
                    OutlinedButton(onClick = { takePhoto1() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (receipt1 == null) "📷 拍照上传" else "📷 重拍", color = DiningColors.Primary)
                    }
                    receipt1?.let {
                        Image(it, contentDescription = "转账收据", modifier = Modifier.fillMaxWidth().height(120.dp))
                    }
                }
                Text("收据2（批发商收据）", fontSize = 13.sp, color = DiningColors.TextSecondary)
                OutlinedButton(onClick = { takePhoto2() }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (receipt2 == null) "📷 拍照上传" else "📷 重拍", color = DiningColors.Primary)
                }
                receipt2?.let {
                    Image(it, contentDescription = "批发商收据", modifier = Modifier.fillMaxWidth().height(120.dp))
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
                    if (needTransferReceipt && receipt1 != null) {
                        url1 = receipt1!!.toJpegBytes()?.let { bytes ->
                            SupabaseClient.uploadFile("receipts", "expense_transfer_${Clock.System.now().toEpochMilliseconds()}.jpg", bytes)
                        }
                    }
                    if (receipt2 != null) {
                        url2 = receipt2!!.toJpegBytes()?.let { bytes ->
                            SupabaseClient.uploadFile("receipts", "expense_supplier_${Clock.System.now().toEpochMilliseconds()}.jpg", bytes)
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
                        transaction_datetime = "${date}T${currentIso().substringAfter('T')}"
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
                    if (initial == null && isStockItem && weightVal > 0) {
                        val wh = warehouseItems.firstOrNull { it.item_name == itemName }
                        if (wh != null) {
                            // 存原始值 + 原始单位，库存累加由触发器按 unit 换算（G → KG）
                            val inItemsJson = buildJsonArray {
                                add(buildJsonObject {
                                    put("warehouse_item_id", JsonPrimitive(wh.id))
                                    put("qty", JsonPrimitive(weightVal))
                                    put("unit", JsonPrimitive(weightUnit))
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
                                    transaction_datetime = "${date}T${currentIso().substringAfter('T')}"
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
    var showPrintDialog by remember { mutableStateOf(false) }
    var printMode by remember { mutableStateOf("daily") } // daily / monthly
    var printLang by remember { mutableStateOf("zh") } // zh / en

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
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Text("‹ 返回", color = DiningColors.Primary) }
            Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Assessment, contentDescription = null, tint = DiningColors.TextPrimary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("报表统计", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            }
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

        Spacer(modifier = Modifier.height(8.dp))

        // 打印日账 / 打印月账
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { printMode = "daily"; showPrintDialog = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Print, contentDescription = null, tint = DiningColors.Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("打印日账", color = DiningColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { printMode = "monthly"; showPrintDialog = true },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Print, contentDescription = null, tint = DiningColors.Primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("打印月账", color = DiningColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
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

    // 打印日账/月账弹窗（选择语言）
    if (showPrintDialog) {
        AlertDialog(
            onDismissRequest = { showPrintDialog = false },
            containerColor = DiningColors.Surface,
            shape = RoundedCornerShape(20.dp),
            title = { Text(if (printMode == "daily") "打印日账" else "打印月账", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("选择版本", fontSize = 12.sp, color = DiningColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = printLang == "zh", onClick = { printLang = "zh" }, label = { Text("中文版") })
                        FilterChip(selected = printLang == "en", onClick = { printLang = "en" }, label = { Text("英文版") })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val report = SupabaseClient.fetchDailyReport(start, end)
                        if (report != null) {
                            val text = when {
                                printMode == "monthly" && printLang == "zh" -> buildMonthlyReportZh(report, start)
                                printMode == "monthly" && printLang == "en" -> buildMonthlyReportEn(report, start)
                                else -> buildReportText(false, printLang == "en", report, start)
                            }
                            printReceiptText(text)
                        }
                        showPrintDialog = false
                    }
                }) { Text("打印", color = DiningColors.Primary, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showPrintDialog = false }) { Text("取消", color = DiningColors.TextMuted) }
                    TextButton(onClick = { showPrintDialog = false }) { Text("完成", color = DiningColors.Primary) }
                }
            }
        )
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = DiningColors.Surface.copy(alpha = 0.8f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DiningColors.Surface)
    }
}

// 日期 "YYYY-MM-DD" -> "DD/MM/YYYY"
private fun fmtMyDate(date: String): String {
    val parts = date.take(10).split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else date
}

// ISO "YYYY-MM-DDTHH:MM:SS" -> "DD/MM/YYYY HH:MM"
private fun fmtMyTime(iso: String): String {
    val datePart = iso.take(10)
    val timePart = if (iso.length >= 16) iso.substring(11, 16) else ""
    return "${fmtMyDate(datePart)} $timePart".trim()
}

// 支出类型英文映射
private fun expenseLabelEn(type: String): String = when (type) {
    "员工" -> "Staff Salary"
    "租金" -> "Shop Rental"
    "炭火" -> "Charcoal"
    "炭火耗材" -> "Charcoal & Consumables"
    else -> type
}

// 生成报表打印文本（日账/月账，中/英），report 为 get_daily_report 返回的 JSON
private fun buildReportText(isMonthly: Boolean, isEnglish: Boolean, report: JsonElement, start: String): String {
    val W = ReceiptFormatter.TOTAL_WIDTH
    val r = mutableListOf<String>()
    val obj = report.jsonObject

    val totalOrders = obj["total_orders"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    val paidOrders = obj["paid_orders"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    val cancelled = (totalOrders - paidOrders).coerceAtLeast(0)
    val totalSales = obj["total_sales"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val totalDiscount = obj["total_discount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val actualRevenue = totalSales - totalDiscount
    val cash = obj["cash"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val duitnow = obj["duitnow"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val tng = obj["tng"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val alipay = obj["alipay"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val totalExpense = obj["total_expense"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val grossProfit = actualRevenue - totalExpense
    val skewersArr = obj["skewers"]?.jsonArray
    val expenseObj = obj["expense_breakdown"]?.jsonObject

    fun money(v: Double) = "%.2f".format(v)
    // 对齐行：英文版用 26+12+10，中文版用 24+14+10
    fun row(label: String, unit: String, value: String): String =
        if (isEnglish) ReceiptFormatter.generateEnglishReportRow(label, unit, value)
        else ReceiptFormatter.generateReportRow(label, unit, value)

    // 头部
    r.add("=".repeat(W))
    if (isEnglish) {
        r.add(ReceiptFormatter.padCenter(if (isMonthly) "MONTHLY BUSINESS REPORT" else "DAILY BUSINESS REPORT", W))
        r.add("")
        r.add(ReceiptFormatter.padCenter("ZHI XIANG FOOD ENTERPRISE", W))
        r.add(ReceiptFormatter.padCenter("(Trade Name: 炙巷食铺)", W))
        r.add(ReceiptFormatter.padCenter("SSM BRN: [ENTER BRN NUMBER]", W))
        r.add(ReceiptFormatter.padRight((if (isMonthly) "Report Period: " else "Report Date: ") + (if (isMonthly) start.substring(0, 7) else fmtMyDate(start)), W))
    } else {
        r.add(ReceiptFormatter.padCenter(if (isMonthly) "MONTHLY BUSINESS REPORT" else "DAILY BUSINESS REPORT", W))
        r.add(ReceiptFormatter.padCenter(if (isMonthly) "月营业报表" else "日营业报表", W))
        r.add("")
        r.add(ReceiptFormatter.padCenter("ZHI XIANG FOOD ENTERPRISE", W))
        r.add(ReceiptFormatter.padCenter("(Trade Name: 炙巷食铺)", W))
        r.add(ReceiptFormatter.padCenter("SSM BRN: 【填写BRN】", W))
        r.add(ReceiptFormatter.padRight((if (isMonthly) "统计月份: " else "统计日期: ") + (if (isMonthly) start.substring(0, 7) else fmtMyDate(start)), W))
    }
    r.add("=".repeat(W))

    // 订单统计
    r.add(if (isEnglish) "[ORDER STATISTICS]" else "【订单统计】")
    r.add(row(if (isEnglish) "Total Orders Placed:" else "总开单数量:", if (isEnglish) "Bills" else "单", totalOrders.toString()))
    r.add(row(if (isEnglish) "Total Completed Orders:" else "有效成交单:", if (isEnglish) "Bills" else "单", paidOrders.toString()))
    r.add(row(if (isEnglish) "Total Void/Cancelled:" else "作废/取消单:", if (isEnglish) "Bills" else "单", cancelled.toString()))
    r.add("")

    // 营业额汇总
    r.add(if (isEnglish) "[REVENUE SUMMARY]" else "【营业额汇总】")
    r.add(row(if (isEnglish) "Total Sales Amount:" else "总销售金额:", "RM", money(totalSales)))
    r.add(row(if (isEnglish) "Total Discount Amount:" else "总折扣金额:", "RM", money(totalDiscount)))
    r.add(row(if (isEnglish) "Actual Net Revenue:" else "实际营收:", "RM", money(actualRevenue)))
    r.add("")

    // 付款方式统计
    r.add(if (isEnglish) "[PAYMENT MODE STATISTICS]" else "【付款方式统计】")
    r.add(row(if (isEnglish) "CASH:" else "CASH现金:", "RM", money(cash)))
    r.add(row("DUITNOW:", "RM", money(duitnow)))
    r.add(row(if (isEnglish) "TNG E-Wallet:" else "TNG E-Wallet:", "RM", money(tng)))
    r.add(row("ALIPAY:", "RM", money(alipay)))
    r.add("-".repeat(W))
    r.add(row(if (isEnglish) "Total Collected:" else "收款合计:", "RM", money(cash + duitnow + tng + alipay)))
    r.add("")

    // 实物销售串数
    r.add(if (isEnglish) "[PRODUCT SALES - SKEWER COUNTS]" else "【实物销售-串数统计】")
    if (skewersArr != null && skewersArr.isNotEmpty()) {
        skewersArr.forEach { el ->
            val o = el.jsonObject
            val name = o["name"]?.jsonPrimitive?.content ?: ""
            val nameEn = o["name_en"]?.jsonPrimitive?.content ?: name
            val q = o["qty"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            r.add(row("${if (isEnglish) nameEn else name}:", if (isEnglish) "Pcs" else "串", q.toString()))
        }
    } else {
        r.add(row(if (isEnglish) "(none)" else "（无）", "", ""))
    }
    r.add("")

    // 当日支出
    r.add(if (isEnglish) "[DAILY EXPENSES]" else "【当日支出】")
    if (expenseObj != null && expenseObj.isNotEmpty()) {
        expenseObj.forEach { (type, amt) ->
            val a = amt.jsonPrimitive.content.toDoubleOrNull() ?: 0.0
            r.add(row("${if (isEnglish) expenseLabelEn(type) else type}:", "RM", money(a)))
        }
    } else {
        r.add(row(if (isEnglish) "(none)" else "（无）", "", ""))
    }
    r.add("-".repeat(W))
    r.add(row(if (isEnglish) "Total Daily Expenses:" else "当日总支出:", "RM", money(totalExpense)))
    r.add("")

    // 毛利
    r.add(if (isEnglish) "[DAILY GROSS PROFIT ESTIMATION]" else "【当日毛利粗算】")
    r.add(row(if (isEnglish) "Estimated Gross Profit:" else "当日毛利:", "RM", money(grossProfit)))
    r.add("=".repeat(W))

    // 尾部
    r.add(ReceiptFormatter.padRight((if (isEnglish) "Operator: " else "操作员: ") + SessionManager.staffName, W))
    r.add(ReceiptFormatter.padRight((if (isEnglish) "Print Time: " else "打印时间: ") + fmtMyTime(currentIso()), W))
    r.add("=".repeat(W))
    r.add("\n\n\n")

    return r.joinToString("\n")
}

// 生成月账中文版打印文本（接真实数据库统计）
private fun buildMonthlyReportZh(report: JsonElement, start: String): String {
    val W = ReceiptFormatter.TOTAL_WIDTH
    val r = mutableListOf<String>()
    val obj = report.jsonObject

    val totalOrders = obj["total_orders"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    val paidOrders = obj["paid_orders"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    val cancelled = (totalOrders - paidOrders).coerceAtLeast(0)
    val totalSales = obj["total_sales"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val totalDiscount = obj["total_discount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val actualRevenue = totalSales - totalDiscount
    val cash = obj["cash"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val duitnow = obj["duitnow"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val tng = obj["tng"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val alipay = obj["alipay"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val totalExpense = obj["total_expense"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val netProfit = actualRevenue - totalExpense
    val skewersArr = obj["skewers"]?.jsonArray
    val expenseObj = obj["expense_breakdown"]?.jsonObject

    fun money(v: Double) = "%.2f".format(v)
    fun row(label: String, unit: String, value: String) = ReceiptFormatter.generateReportRow(label, unit, value)

    // 月份 "YYYY-MM" -> "MM/YYYY"
    val monthLabel = if (start.length >= 7) "${start.substring(5, 7)}/${start.substring(0, 4)}" else start

    // 头部
    r.add("=".repeat(W))
    r.add(ReceiptFormatter.padCenter("MONTHLY BUSINESS REPORT", W))
    r.add(ReceiptFormatter.padCenter("月度营业报表", W))
    r.add("")
    r.add(ReceiptFormatter.padCenter("ZHI XIANG FOOD ENTERPRISE", W))
    r.add(ReceiptFormatter.padCenter("(Trade Name: 炙巷食铺)", W))
    r.add(ReceiptFormatter.padCenter("SSM BRN: 【填写BRN】", W))
    r.add(ReceiptFormatter.padRight("统计月份: $monthLabel", W))
    r.add("=".repeat(W))

    // 订单汇总
    r.add("【订单汇总】")
    r.add(row("本月总开单:", "单", totalOrders.toString()))
    r.add(row("成交有效单:", "单", paidOrders.toString()))
    r.add(row("作废取消单:", "单", cancelled.toString()))
    r.add("")

    // 营收汇总
    r.add("【营收汇总】")
    r.add(row("销售总金额:", "RM", money(totalSales)))
    r.add(row("总折扣金额:", "RM", money(totalDiscount)))
    r.add(row("本月实际营收:", "RM", money(actualRevenue)))
    r.add("")

    // 各付款方式合计
    r.add("【各付款方式合计】")
    r.add(row("CASH现金:", "RM", money(cash)))
    r.add(row("DUITNOW:", "RM", money(duitnow)))
    r.add(row("TNG E-Wallet:", "RM", money(tng)))
    r.add(row("ALIPAY:", "RM", money(alipay)))
    r.add("")

    // 商品月度销量
    r.add("【商品月度销量】")
    if (skewersArr != null && skewersArr.isNotEmpty()) {
        skewersArr.forEach { el ->
            val o = el.jsonObject
            val name = o["name"]?.jsonPrimitive?.content ?: ""
            val q = o["qty"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            r.add(row("$name:", "串", q.toString()))
        }
    } else {
        r.add(row("（无）", "", ""))
    }
    r.add("")

    // 月度总开销（按 5 大类聚合）
    r.add("【月度总开销】")
    val catOrder = listOf("店租合计", "员工薪资合计", "炭火耗材合计", "采购食材成本", "其他杂项")
    val catMap = linkedMapOf<String, Double>()
    if (expenseObj != null && expenseObj.isNotEmpty()) {
        expenseObj.forEach { (type, amt) ->
            val a = amt.jsonPrimitive.content.toDoubleOrNull() ?: 0.0
            val cat = monthlyExpenseCategory(type)
            catMap[cat] = (catMap[cat] ?: 0.0) + a
        }
    }
    var printedAny = false
    catOrder.forEach { cat ->
        val v = catMap[cat] ?: 0.0
        if (v > 0.0) {
            r.add(row("$cat:", "RM", money(v)))
            printedAny = true
        }
    }
    if (!printedAny) r.add(row("（无）", "", ""))
    r.add("-".repeat(W))
    r.add(row("月度总支出:", "RM", money(totalExpense)))
    r.add("")

    // 月度粗算利润
    r.add("【月度粗算利润】")
    r.add(row("月度结余:", "RM", money(netProfit)))
    r.add("=".repeat(W))

    // 尾部
    r.add(ReceiptFormatter.padRight("打印时间: ${fmtMyTime(currentIso())}", W))
    r.add(ReceiptFormatter.padCenter("*完整明细请导出Excel存档", W))
    r.add(ReceiptFormatter.padCenter("LHDN要求记录保存7年", W))
    r.add("=".repeat(W))
    r.add("\n\n\n")

    return r.joinToString("\n")
}

// 月账开销分类：把 expense_type 归入 5 大类
private fun monthlyExpenseCategory(type: String): String {
    return when {
        type.contains("租") -> "店租合计"
        type.contains("员工") || type.contains("薪") -> "员工薪资合计"
        type.contains("炭") || type.contains("耗材") -> "炭火耗材合计"
        else -> "采购食材成本"
    }
}

// 月账开销分类英文标签
private fun monthlyExpenseCategoryEn(type: String): String = when (type) {
    "店租合计" -> "Total Shop Rental"
    "员工薪资合计" -> "Total Staff Salary"
    "炭火耗材合计" -> "Charcoal & Consumables"
    "采购食材成本" -> "Ingredients Procurement"
    else -> "Other Miscellaneous"
}

// 生成月账英文版打印文本（接真实数据库统计）
private fun buildMonthlyReportEn(report: JsonElement, start: String): String {
    val W = ReceiptFormatter.TOTAL_WIDTH
    val r = mutableListOf<String>()
    val obj = report.jsonObject

    val totalOrders = obj["total_orders"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    val paidOrders = obj["paid_orders"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
    val cancelled = (totalOrders - paidOrders).coerceAtLeast(0)
    val totalSales = obj["total_sales"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val totalDiscount = obj["total_discount"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val actualRevenue = totalSales - totalDiscount
    val cash = obj["cash"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val duitnow = obj["duitnow"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val tng = obj["tng"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val alipay = obj["alipay"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val totalExpense = obj["total_expense"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
    val netProfit = actualRevenue - totalExpense
    val skewersArr = obj["skewers"]?.jsonArray
    val expenseObj = obj["expense_breakdown"]?.jsonObject

    fun money(v: Double) = "%.2f".format(v)
    fun row(label: String, unit: String, value: String) = ReceiptFormatter.generateEnglishReportRow(label, unit, value)

    // 月份 "YYYY-MM" -> "MM/YYYY"
    val monthLabel = if (start.length >= 7) "${start.substring(5, 7)}/${start.substring(0, 4)}" else start

    // Header
    r.add("=".repeat(W))
    r.add(ReceiptFormatter.padCenter("MONTHLY BUSINESS REPORT", W))
    r.add("")
    r.add(ReceiptFormatter.padCenter("ZHI XIANG FOOD ENTERPRISE", W))
    r.add(ReceiptFormatter.padCenter("(Trade Name: 炙巷食铺)", W))
    r.add(ReceiptFormatter.padCenter("SSM BRN: [ENTER BRN NUMBER]", W))
    r.add(ReceiptFormatter.padRight("Report Month: $monthLabel", W))
    r.add("=".repeat(W))

    // Order Summary
    r.add("[ORDER SUMMARY]")
    r.add(row("Total Monthly Orders:", "Bills", totalOrders.toString()))
    r.add(row("Total Completed Orders:", "Bills", paidOrders.toString()))
    r.add(row("Total Void/Cancelled:", "Bills", cancelled.toString()))
    r.add("")

    // Revenue Summary
    r.add("[REVENUE SUMMARY]")
    r.add(row("Total Sales Amount:", "RM", money(totalSales)))
    r.add(row("Total Discount Amount:", "RM", money(totalDiscount)))
    r.add(row("Actual Net Revenue:", "RM", money(actualRevenue)))
    r.add("")

    // Payment Mode Total
    r.add("[PAYMENT MODE TOTAL]")
    r.add(row("CASH:", "RM", money(cash)))
    r.add(row("DUITNOW:", "RM", money(duitnow)))
    r.add(row("TNG E-Wallet:", "RM", money(tng)))
    r.add(row("ALIPAY:", "RM", money(alipay)))
    r.add("")

    // Monthly Product Sales
    r.add("[MONTHLY PRODUCT SALES]")
    if (skewersArr != null && skewersArr.isNotEmpty()) {
        skewersArr.forEach { el ->
            val o = el.jsonObject
            val nameEn = o["name_en"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: o["name"]?.jsonPrimitive?.content ?: ""
            val q = o["qty"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            r.add(row("$nameEn:", "Pcs", q.toString()))
        }
    } else {
        r.add(row("(none)", "", ""))
    }
    r.add("")

    // Monthly Total Expenses
    r.add("[MONTHLY TOTAL EXPENSES]")
    val catOrder = listOf("店租合计", "员工薪资合计", "炭火耗材合计", "采购食材成本", "其他杂项")
    val catMap = linkedMapOf<String, Double>()
    if (expenseObj != null && expenseObj.isNotEmpty()) {
        expenseObj.forEach { (type, amt) ->
            val a = amt.jsonPrimitive.content.toDoubleOrNull() ?: 0.0
            val cat = monthlyExpenseCategory(type)
            catMap[cat] = (catMap[cat] ?: 0.0) + a
        }
    }
    var printedAny = false
    catOrder.forEach { cat ->
        val v = catMap[cat] ?: 0.0
        if (v > 0.0) {
            r.add(row("${monthlyExpenseCategoryEn(cat)}:", "RM", money(v)))
            printedAny = true
        }
    }
    if (!printedAny) r.add(row("(none)", "", ""))
    r.add("-".repeat(W))
    r.add(row("Total Monthly Expenses:", "RM", money(totalExpense)))
    r.add("")

    // Monthly Estimated Profit
    r.add("[MONTHLY ESTIMATED PROFIT]")
    r.add(row("Monthly Balance / Profit:", "RM", money(netProfit)))
    r.add("=".repeat(W))

    // Footer
    r.add(ReceiptFormatter.padRight("Print Time: ${fmtMyTime(currentIso())}", W))
    r.add(ReceiptFormatter.padCenter("* Please export Excel for full details", W))
    r.add(ReceiptFormatter.padCenter("LHDN Requirement: Keep records for 7 years", W))
    r.add("=".repeat(W))
    r.add("\n\n\n")

    return r.joinToString("\n")
}
