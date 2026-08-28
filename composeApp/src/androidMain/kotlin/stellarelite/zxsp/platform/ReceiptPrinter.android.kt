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
    // 前置指令（二进制下发）：复位 + Font-A 等宽字体 + 正常字号 + 默认左对齐
    out.write(byteArrayOf(0x1B, 0x40))       // ESC @ 复位初始化
    out.write(byteArrayOf(0x1B, 0x4D, 0x00)) // ESC M 0 强制 Font-A（12x24 等宽）
    out.write(byteArrayOf(0x1B, 0x21, 0x00)) // ESC ! 0 正常字号
    out.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC A 0 默认全局左对齐

    // 全部左对齐（每行已在文本里带 1 空格左边距）
    for (line in text.split("\n")) {
        out.write(line.toByteArray(gbk))
        out.write(0x0A)
    }
    // 走纸 3 行 + 切纸
    out.write(byteArrayOf(0x1B, 0x64, 0x03))
    out.write(byteArrayOf(0x1D, 0x56, 0x00))
    return out.toByteArray()
}
