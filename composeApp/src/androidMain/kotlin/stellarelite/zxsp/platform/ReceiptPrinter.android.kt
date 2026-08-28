package stellarelite.zxsp.platform

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.UUID

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

// ESC/POS 蓝牙热敏打印（Zy808，80mm，GBK 中文）
actual fun printReceiptText(text: String) {
    Thread {
        val msg = try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                "此设备不支持蓝牙"
            } else {
                val devices = bondedDevices(adapter)
                val printer = pickPrinter(devices)
                if (printer == null) {
                    "未找到已配对的蓝牙打印机，请先在系统蓝牙里配对 Zy808"
                } else {
                    printToDevice(printer, text)
                    "已发送到打印机：${printer.name ?: "未知"}"
                }
            }
        } catch (e: Exception) {
            "打印失败：${e.message}"
        }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(AppContext.context, msg, Toast.LENGTH_LONG).show()
        }
    }.start()
}

private fun bondedDevices(adapter: BluetoothAdapter): Set<BluetoothDevice> {
    return if (Build.VERSION.SDK_INT >= 31) {
        try { adapter.bondedDevices } catch (e: SecurityException) { emptySet() }
    } else {
        adapter.bondedDevices
    }
}

private fun pickPrinter(devices: Set<BluetoothDevice>): BluetoothDevice? {
    val list = devices.toList()
    if (list.isEmpty()) return null
    return list.firstOrNull {
        val n = it.name?.uppercase() ?: ""
        n.contains("ZY") || n.contains("PRINT") || n.contains("808")
    } ?: list.first()
}

private fun printToDevice(device: BluetoothDevice, text: String) {
    val adapter = BluetoothAdapter.getDefaultAdapter()
    if (adapter != null && adapter.isDiscovering) adapter.cancelDiscovery()
    val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
    try {
        socket.connect()
        val os = socket.outputStream
        os.write(buildEscPos(text))
        os.flush()
        os.close()
    } finally {
        try { socket.close() } catch (_: Exception) { }
    }
}

private fun buildEscPos(text: String): ByteArray {
    val out = ByteArrayOutputStream()
    val gbk = Charset.forName("GBK")
    // 前置指令：初始化 + Font-A 等宽点阵字体 + 正常字号（不设 Font-A 补空格会失效）
    out.write(byteArrayOf(0x1B, 0x40))       // ESC @ 初始化
    out.write(byteArrayOf(0x1B, 0x4D, 0x00)) // ESC M 0 选 Font-A（12x24 等宽）
    out.write(byteArrayOf(0x1B, 0x21, 0x00)) // ESC ! 0 正常字号

    for (line in text.split("\n")) {
        val t = line.trim()
        when {
            t == "OFFICIAL SALES RECEIPT" -> {
                // 标题硬件居中
                out.write(byteArrayOf(0x1B, 0x61, 0x01))
                out.write(line.toByteArray(gbk))
                out.write(0x0A)
                out.write(byteArrayOf(0x1B, 0x61, 0x00))
            }
            isAmountLine(t) -> {
                // 金额行：文字左 + RM 右（硬件右对齐）
                val idx = t.indexOf("RM")
                if (idx > 0) {
                    val label = t.substring(0, idx).trimEnd()
                    val amount = t.substring(idx).trim()
                    out.write(label.toByteArray(gbk))
                    out.write(byteArrayOf(0x1B, 0x61, 0x02))
                    out.write(amount.toByteArray(gbk))
                    out.write(0x0A)
                    out.write(byteArrayOf(0x1B, 0x61, 0x00))
                } else {
                    out.write(line.toByteArray(gbk))
                    out.write(0x0A)
                }
            }
            else -> {
                out.write(line.toByteArray(gbk))
                out.write(0x0A)
            }
        }
    }
    // 走纸 3 行 + 切纸
    out.write(byteArrayOf(0x1B, 0x64, 0x03))
    out.write(byteArrayOf(0x1D, 0x56, 0x00))
    return out.toByteArray()
}

private fun isAmountLine(t: String): Boolean {
    return t.startsWith("Sub Total") || t.startsWith("Discount") ||
        t.startsWith("TOTAL AMOUNT") || t.startsWith("Amount Received") ||
        t.startsWith("Change Given")
}
