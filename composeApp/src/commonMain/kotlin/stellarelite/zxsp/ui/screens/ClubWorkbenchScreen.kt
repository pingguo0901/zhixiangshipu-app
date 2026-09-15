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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import stellarelite.zxsp.data.t
import stellarelite.zxsp.network.CustomerOrder
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.network.TableList
import stellarelite.zxsp.ui.theme.DiningColors

// ============ Topone 工作台（酒吧选座平面图） ============
@Composable
fun ToponeWorkbenchScreen(onBack: () -> Unit) {
    var showNewOrder by remember { mutableStateOf(false) }
    var newOrderTableId by remember { mutableStateOf<Long?>(null) }
    var orderDialogTable by remember { mutableStateOf<TableList?>(null) }
    var addItemsOrder by remember { mutableStateOf<CustomerOrder?>(null) }
    var addItemsTableNo by remember { mutableStateOf<String?>(null) }

    if (showNewOrder) {
        NewOrderScreen(onBack = { showNewOrder = false }, initialTableId = newOrderTableId)
        return
    }
    if (addItemsOrder != null) {
        AddItemsScreen(
            order = addItemsOrder!!,
            tableNo = addItemsTableNo,
            onBack = { addItemsOrder = null },
            onDone = { addItemsOrder = null }
        )
        return
    }

    ToponeBoard(
        onBack = onBack,
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
        TableOrderDialog(
            table = table,
            onDismiss = { orderDialogTable = null },
            onAddItems = { order ->
                orderDialogTable = null
                addItemsOrder = order
                addItemsTableNo = table.table_no
            }
        )
    }
}

// Lunar 工作台：桌台布局待董事长提供，先留入口
@Composable
fun LunarWorkbenchScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiningColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Text(t("‹ 返回", "‹ Back"), color = DiningColors.Primary)
            }
            Text(
                "Lunar 工作台",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 60.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                t("桌台布局待提供", "Floor plan coming soon"),
                color = DiningColors.TextMuted,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun ToponeBoard(onBack: () -> Unit, onNewOrder: () -> Unit, onTableClick: (TableList) -> Unit) {
    val scope = rememberCoroutineScope()
    var tables by remember { mutableStateOf<List<TableList>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun load(silent: Boolean = false) {
        scope.launch {
            if (!silent) loading = true
            runCatching { SupabaseClient.ensureToponeTables() }
            runCatching { SupabaseClient.fetchTables() }
                .onSuccess { tables = it.filter { t -> t.table_no.startsWith("Topone-") } }
            if (!silent) loading = false
        }
    }
    LaunchedEffect(Unit) {
        load()
        while (true) {
            delay(3000)
            load(silent = true)
        }
    }

    val byName = remember(tables) { tables.associateBy { it.table_no.removePrefix("Topone-") } }
    val freeCount = tables.count { it.table_status == "free" }
    val occupiedCount = tables.count { it.table_status == "occupied" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DiningColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部标题 + 新建订单
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(t("‹ 返回", "‹ Back"), color = DiningColors.Primary)
            }
            Text("Topone 工作台", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            Button(
                onClick = onNewOrder,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DiningColors.Primary)
            ) {
                Text(t("＋ 新建订单", "＋ New Order"), color = DiningColors.Surface, fontWeight = FontWeight.Bold)
            }
        }

        // 上方统计卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DiningColors.Primary)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ClubStatItem(Icons.Outlined.Chair, "$freeCount", t("空闲桌", "Free Tables"))
                ClubStatItem(Icons.Outlined.Restaurant, "$occupiedCount", t("占用中", "Occupied"))
                ClubStatItem(Icons.Outlined.TableRestaurant, "${tables.size}", t("总桌台", "Total Tables"))
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DiningColors.Primary)
            }
        } else {
            // ===== Ground Floor 一楼 =====
            Text("Ground Floor", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DiningColors.Card)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 舞台
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(DiningColors.Primary, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("TOP ONE DJ STAGE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DiningColors.Surface, letterSpacing = 2.sp)
                    }

                    // 主桌台集群：左区 / 中区 / 右区
                    Row(verticalAlignment = Alignment.Top) {
                        // 左侧：VIP1 VIP2（竖） | G1 G2（竖）
                        Row(modifier = Modifier.weight(1.0f)) {
                            TableColumn(listOf("VIP1", "VIP2"), byName, onTableClick, Modifier.weight(1f), vertical = true)
                            Spacer(modifier = Modifier.width(4.dp))
                            TableColumn(listOf("G1", "G2"), byName, onTableClick, Modifier.weight(1f), vertical = true)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        // 中间：GA（右→左）/ GB（右→左）/ G3 G4 G5
                        Column(modifier = Modifier.weight(1.6f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TableRow(listOf("GA6", "GA5", "GA4", "GA3", "GA2", "GA1"), byName, onTableClick)
                            TableRow(listOf("GB6", "GB5", "GB4", "GB3", "GB2", "GB1"), byName, onTableClick)
                            TableRow(listOf("G3", "G4", "G5"), byName, onTableClick)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        // 右侧：G8 G7 G6（竖） | VIP7 VIP6 VIP5（竖）
                        Row(modifier = Modifier.weight(1.0f)) {
                            TableColumn(listOf("G8", "G7", "G6"), byName, onTableClick, Modifier.weight(1f), vertical = true)
                            Spacer(modifier = Modifier.width(4.dp))
                            TableColumn(listOf("VIP7", "VIP6", "VIP5"), byName, onTableClick, Modifier.weight(1f), vertical = true)
                        }
                    }

                    // 中间偏下：VIP3（左）VIP4（右）
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FloorTable("VIP3", byName["VIP3"], onTableClick, Modifier.weight(1f))
                        FloorTable("VIP4", byName["VIP4"], onTableClick, Modifier.weight(1f))
                    }
                }
            }

            // ===== Second Floor 二楼 =====
            Text("Second Floor", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DiningColors.TextPrimary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DiningColors.Card)
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
                    // ---- 左侧片区：F + FB + SVIP1-3 ----
                    // F 列（竖的）
                    TableColumn(listOf("F5", "F4", "F3", "F2", "F1"), byName, onTableClick, Modifier.weight(0.7f), vertical = true, short = true)
                    Spacer(modifier = Modifier.width(4.dp))
                    // FB + SVIP1-3 列
                    Column(modifier = Modifier.weight(1.0f), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        FloorTable("FB6", byName["FB6"], onTableClick, Modifier.width(42.dp), vertical = true)
                        FloorTable("FB5", byName["FB5"], onTableClick, Modifier.width(42.dp), vertical = true)
                        FloorTable("FB4", byName["FB4"], onTableClick, Modifier.width(42.dp), vertical = true)
                        FloorTable("FB3", byName["FB3"], onTableClick, Modifier.width(42.dp), vertical = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FloorTable("FB2", byName["FB2"], onTableClick, Modifier.weight(1f))
                            FloorTable("FB1", byName["FB1"], onTableClick, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Spacer(modifier = Modifier.weight(1f))
                            FloorTable("SVIP3", byName["SVIP3"], onTableClick, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FloorTable("SVIP1", byName["SVIP1"], onTableClick, Modifier.weight(1f))
                            FloorTable("SVIP2", byName["SVIP2"], onTableClick, Modifier.weight(1f))
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // ---- 右侧片区：FA + SVIP4-9（3列网格，竖的）----
                    Column(modifier = Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FloorGridRow(null, "FA7", "SVIP7", byName, onTableClick)
                        FloorGridRow(null, "FA6", "SVIP6", byName, onTableClick)
                        FloorGridRow(null, "SVIP9", "SVIP5", byName, onTableClick)
                        FloorGridRow(null, "SVIP8", "SVIP4", byName, onTableClick)
                        FloorGridRow("FA3", "FA5", null, byName, onTableClick, horizontal = true)
                        FloorGridRow("FA2", "FA4", null, byName, onTableClick, horizontal = true)
                        FloorGridRow("FA1", null, null, byName, onTableClick, horizontal = true)
                    }
                }
            }
        }
    }
}

// 桌台按钮（横排里用 weight 撑满，竖排里 fillMaxWidth）
@Composable
private fun FloorTable(label: String, table: TableList?, onClick: (TableList) -> Unit, modifier: Modifier = Modifier, vertical: Boolean = false, short: Boolean = false) {
    val status = table?.table_status ?: "free"
    val bg = when (status) {
        "occupied" -> DiningColors.Primary
        "cleaning" -> DiningColors.Warning
        else -> DiningColors.Surface
    }
    val fg = if (status == "free") DiningColors.TextPrimary else DiningColors.Surface
    Box(
        modifier = modifier
            .height(if (vertical) (if (short) 40.dp else 52.dp) else 38.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .clickable(enabled = table != null) { table?.let(onClick) },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TableRow(names: List<String>, byName: Map<String, TableList>, onClick: (TableList) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        names.forEach { n -> FloorTable(n, byName[n], onClick, Modifier.weight(1f)) }
    }
}

@Composable
private fun TableColumn(names: List<String>, byName: Map<String, TableList>, onClick: (TableList) -> Unit, modifier: Modifier = Modifier, vertical: Boolean = false, short: Boolean = false) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        names.forEach { n -> FloorTable(n, byName[n], onClick, if (vertical) Modifier.width(42.dp) else Modifier.fillMaxWidth(), vertical, short) }
    }
}

@Composable
private fun FloorGridRow(left: String?, mid: String?, right: String?, byName: Map<String, TableList>, onClick: (TableList) -> Unit, horizontal: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(left, mid, right).forEach { label ->
            if (label != null) {
                if (horizontal) {
                    FloorTable(label, byName[label], onClick, Modifier.weight(1f))
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        FloorTable(label, byName[label], onClick, Modifier.width(42.dp), vertical = true)
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ClubStatItem(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = DiningColors.Surface, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DiningColors.Surface)
        Text(label, fontSize = 11.sp, color = DiningColors.Surface.copy(alpha = 0.75f))
    }
}
