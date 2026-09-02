package stellarelite.zxsp.ui.screens

import stellarelite.zxsp.data.LanguageManager
import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.platform.SessionStorage
import stellarelite.zxsp.platform.printReceiptText

// 自动监听新订单并打印厨房单（网页下单 → 店内手机自动出单）
object KitchenAutoPrinter {
    private const val KEY = "lastPrintedOrderId"

    // 已打印的最大订单 ID（持久化，避免重启后重复打印历史订单）
    var lastPrintedId: Long
        get() = SessionStorage.get(KEY)?.toLongOrNull() ?: 0L
        private set(value) { SessionStorage.put(KEY, value.toString()) }

    // 首次启用：跳过历史订单，从当前最大订单 ID 开始
    suspend fun initBaseline() {
        if (SessionStorage.get(KEY) != null) return
        val all = SupabaseClient.fetchOrders()
        lastPrintedId = all.maxOfOrNull { it.id } ?: 0L
    }

    // 标记订单已打印（APP 自己下单后调用，避免轮询重复打印）
    fun markPrinted(orderId: Long) {
        if (orderId > lastPrintedId) lastPrintedId = orderId
    }

    // 轮询一轮：打印所有新订单
    suspend fun pollOnce() {
        val newOrders = SupabaseClient.fetchOrdersAfterId(lastPrintedId)
        if (newOrders.isEmpty()) return
        val tables = SupabaseClient.fetchTables()
        for (order in newOrders) {
            val tno = tables.firstOrNull { it.id == order.table_id }?.table_no ?: "外卖"
            val time = formatDateTimeMy(order.order_datetime ?: "")
            val lines = parseOrderLines(order.order_items)
            // 顾客网页下单（created_by_staff_id == 0）固定打印英文版厨房单
            val isWebOrder = order.created_by_staff_id == 0L
            val kitchenText = if (isWebOrder || LanguageManager.isEnglish) {
                buildKitchenOrderEnglish(
                    orderNo = order.order_no,
                    tableNo = if (tno.startsWith("外卖")) "Takeaway" else tno,
                    time = time,
                    items = lines.map { line ->
                        val en = line.nameEn.ifBlank { line.name }
                        val (name, remark) = splitItemNameEn(en)
                        KitchenLine(line.qty, name, remark)
                    },
                    note = order.notes
                )
            } else {
                buildKitchenOrder(
                    orderNo = order.order_no,
                    tableNo = tno,
                    time = time,
                    items = lines.map { line ->
                        val (name, remark) = splitItemName(line.name)
                        KitchenLine(line.qty, name, remark)
                    },
                    note = order.notes
                )
            }
            printReceiptText(kitchenText)
            lastPrintedId = order.id
        }
    }
}
