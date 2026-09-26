package stellarelite.zxsp.ui.screens

import stellarelite.zxsp.network.SupabaseClient
import stellarelite.zxsp.platform.printReceiptText

// 自动监听新订单并打印厨房单（网页下单 → 店内手机自动出单）
// 已打印游标存服务端 app_state 共享：多台手机共用一个游标，避免各自本地游标导致重复打印。
// 本地内存游标只做本机下单时的同步快速去重（markPrinted 写服务端是异步的，需同步挡一次本机轮询）。
object KitchenAutoPrinter {
    private const val KEY = "lastPrintedOrderId"

    // 本机内存游标（同步更新，Main 线程）
    private var localCursor: Long = 0L

    private suspend fun serverCursor(): Long =
        SupabaseClient.fetchAppState(KEY)?.toLongOrNull() ?: 0L

    private fun bumpLocal(id: Long) {
        if (id > localCursor) localCursor = id
    }

    private suspend fun bumpServer(id: Long) {
        val cur = serverCursor()
        if (id > cur) SupabaseClient.setAppState(KEY, id.toString())
    }

    // 首次启用：优先用服务端游标；服务端为空则从当前最大订单 ID 开始（跳过历史订单）
    suspend fun initBaseline() {
        localCursor = serverCursor()
        if (localCursor > 0L) return
        val all = SupabaseClient.fetchOrders()
        localCursor = all.maxOfOrNull { it.id } ?: 0L
        SupabaseClient.setAppState(KEY, localCursor.toString())
    }

    // 标记订单已打印（APP 自己下单后调用：本机同步挡重复 + 写服务端让其它手机也跳过）
    suspend fun markPrinted(orderId: Long) {
        bumpLocal(orderId)
        bumpServer(orderId)
    }

    // 轮询一轮：打印所有新订单（线上支付单 Online外卖 由 Stripe Webhook 支付成功后出单，这里跳过）
    suspend fun pollOnce() {
        // 以服务端游标为准（跨设备去重），本机游标若落后则追平
        val serverCur = serverCursor()
        if (serverCur > localCursor) localCursor = serverCur
        val cur = localCursor
        val newOrders = SupabaseClient.fetchOrdersAfterId(cur)
        if (newOrders.isEmpty()) return
        val tables = SupabaseClient.fetchTables()
        var high = cur
        for (order in newOrders) {
            val tno = tables.firstOrNull { it.id == order.table_id }?.table_no ?: "外卖"
            // 线上支付单（Online外卖）：支付成功后才由 Webhook 写 print_jobs 出单，这里不重复打印
            if (tno.startsWith("Online外卖")) {
                if (order.id > high) high = order.id
                continue
            }
            val time = formatDateTimeMy(order.order_datetime ?: "")
            val lines = parseOrderLines(order.order_items)
            // 厨房出单统一英文版（已取消中文版）
            val kitchenText = buildKitchenOrderEnglish(
                orderNo = order.order_no,
                tableNo = kitchenTableLabel(tno),
                time = time,
                items = lines.map { line ->
                    val en = line.nameEn.ifBlank { line.name }
                    val (name, remark) = splitItemNameEn(en)
                    KitchenLine(line.qty, name, remark)
                },
                note = order.notes
            )
            printReceiptText(kitchenText)
            if (order.id > high) high = order.id
        }
        if (high > cur) {
            bumpLocal(high)
            bumpServer(high)
        }
    }
}
