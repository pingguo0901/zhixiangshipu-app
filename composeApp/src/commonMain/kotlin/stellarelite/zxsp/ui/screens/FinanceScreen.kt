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
import stellarelite.zxsp.network.DailySales
import stellarelite.zxsp.network.ExpenseRecord
import stellarelite.zxsp.network.SupabaseClient
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
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DiningColors.Surface)) {
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
}

private fun expenseTypeLabel(t: String): String = when (t) {
    "stock" -> "进货"; "utility" -> "杂费"; "logistics" -> "运费"; "maintenance" -> "维修"; else -> t
}

@Composable
private fun ExpenseAddDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("utility") }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("cash") }
    var ref by remember { mutableStateOf("") }
    var isPersonal by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val amt = amount.toDoubleOrNull()
    val canSave = title.isNotBlank() && amt != null && amt > 0 && ref.isNotBlank() && !saving

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DiningColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("记一笔开销", fontWeight = FontWeight.SemiBold, color = DiningColors.TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("开销名称（瓦斯、竹签…）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("stock" to "进货", "utility" to "杂费", "logistics" to "运费", "maintenance" to "维修").forEach { (v, l) ->
                        FilterChip(selected = type == v, onClick = { type = v }, label = { Text(l) })
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("金额 (RM)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("cash" to "现金", "duitnow" to "DuitNow", "tng_ewallet" to "TNG", "alipay" to "支付宝").forEach { (v, l) ->
                        FilterChip(selected = method == v, onClick = { method = v }, label = { Text(l) })
                    }
                }
                OutlinedTextField(value = ref, onValueChange = { ref = it }, label = { Text(if (method == "cash") "现金编号 CASH-EXP-日期-序号" else "交易号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isPersonal, onCheckedChange = { isPersonal = it })
                    Text("私人消费（报税不抵扣）", fontSize = 13.sp, color = DiningColors.TextSecondary)
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("业务用途说明（LHDN）") }, modifier = Modifier.fillMaxWidth())
                if (error != null) Text("⚠️ $error", color = DiningColors.Error, fontSize = 13.sp)
                if (saving) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DiningColors.Primary)
            }
        },
        confirmButton = {
            TextButton(enabled = canSave, onClick = {
                scope.launch {
                    saving = true; error = null
                    val rec = ExpenseRecord(
                        expense_title = title.trim(), expense_type = type, amount_myr = amt!!,
                        pay_method = method, transaction_ref = ref.trim(), is_personal = isPersonal,
                        notes = notes.trim().ifBlank { null },
                        operate_staff_id = SessionManager.staffId ?: 0,
                        transaction_datetime = currentIso()
                    )
                    val r = SupabaseClient.insertExpense(rec)
                    saving = false
                    if (r != null) onDone() else error = "保存失败"
                }
            }) { Text("保存", color = if (canSave) DiningColors.Primary else DiningColors.TextMuted, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = DiningColors.TextMuted) } }
    )
}

// ============ 报表（仅老板） ============
@Composable
private fun ReportScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf(todayDate()) }
    var end by remember { mutableStateOf(todayDate()) }
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
            OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("开始 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("结束 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.weight(1f))
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
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = DiningColors.Surface.copy(alpha = 0.8f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DiningColors.Surface)
    }
}
