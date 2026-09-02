package stellarelite.zxsp.platform

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.print.DocFlavor
import javax.print.PrintServiceLookup
import javax.print.SimpleDoc
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

// 桌面端：USB 热敏打印机（ESC/POS 字节流），通过 Java Print Service 下发
actual fun printReceiptText(text: String) {
    Thread {
        val msg = try {
            val bytes = buildEscPos(text)
            sendToPrinter(bytes)
        } catch (e: Exception) {
            "打印失败：${e.message}"
        }
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(null, msg, "打印", JOptionPane.INFORMATION_MESSAGE)
        }
    }.apply { isDaemon = true }.start()
}

actual fun printTableQrSticker(tableNo: String, qrUrl: String) {
    Thread {
        val msg = try {
            val bytes = buildTableQrStickerBytesEnglish(tableNo, qrUrl)
            sendToPrinter(bytes)
        } catch (e: Exception) {
            "打印失败：${e.message}"
        }
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(null, msg, "打印", JOptionPane.INFORMATION_MESSAGE)
        }
    }.apply { isDaemon = true }.start()
}

// 通过 Java Print Service 把字节流发给热敏打印机
private fun sendToPrinter(bytes: ByteArray): String {
    val services = PrintServiceLookup.lookupPrintServices(null, null)
    if (services.isEmpty()) return "未找到打印机，请确认打印机已连接并安装驱动"

    val service = services.firstOrNull {
        val n = it.name.uppercase()
        n.contains("ZY") || n.contains("PRINT") || n.contains("808") || n.contains("RECEIPT")
    } ?: PrintServiceLookup.lookupDefaultPrintService() ?: services.first()

    val job = service.createPrintJob()
    val doc = SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null)
    job.print(doc, null)
    return "已发送到打印机：${service.name}"
}

// 文本 → GBK ESC/POS 字节流（与 Android 版一致）
private fun buildEscPos(text: String): ByteArray {
    val out = ByteArrayOutputStream()
    val gbk = Charset.forName("GBK")
    out.write(byteArrayOf(0x1B, 0x40))       // ESC @ 复位初始化
    out.write(byteArrayOf(0x1B, 0x4D, 0x00)) // ESC M 0 强制 Font-A（12x24 等宽）
    out.write(byteArrayOf(0x1B, 0x21, 0x00)) // ESC ! 0 正常字号
    out.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC A 0 默认全局左对齐
    for (line in text.split("\n")) {
        out.write(line.toByteArray(gbk))
        out.write(0x0A)
    }
    out.write(byteArrayOf(0x1B, 0x64, 0x03)) // 走纸 3 行
    out.write(byteArrayOf(0x1D, 0x56, 0x00)) // 切纸
    return out.toByteArray()
}

// 英文桌台下单二维码贴纸字节流（含硬件 QR 指令）
private fun buildTableQrStickerBytesEnglish(tableNo: String, qrUrl: String): ByteArray {
    val bos = ByteArrayOutputStream()

    fun writeTextLine(text: String) {
        val formatted = text + "\n"
        bos.write(formatted.encodeToByteArray())
    }

    val ESC = 0x1B.toByte()
    val GS = 0x1D.toByte()
    val CMD_ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0x00)
    val CMD_ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01)
    val CMD_FONT_NORMAL = byteArrayOf(ESC, 0x21, 0x00)
    val CMD_FONT_DOUBLE = byteArrayOf(ESC, 0x21, 0x30)

    bos.write(byteArrayOf(ESC, 0x40))
    bos.write(CMD_ALIGN_CENTER)
    writeTextLine("ZHI XIANG")
    writeTextLine("ZHI XIANG SKEWER HOUSE")
    writeTextLine("")
    writeTextLine("📱 SCAN TO ORDER BY YOURSELF")
    writeTextLine("")

    bos.write(CMD_FONT_DOUBLE)
    writeTextLine("$tableNo")
    bos.write(CMD_FONT_NORMAL)
    writeTextLine("================================================")
    writeTextLine("")

    bos.write(CMD_ALIGN_CENTER)
    val urlBytes = qrUrl.encodeToByteArray()
    val numBytes = urlBytes.size + 3
    val pL = (numBytes % 256).toByte()
    val pH = (numBytes / 256).toByte()
    bos.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x08))
    bos.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x44, 0x31))
    bos.write(byteArrayOf(GS, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
    bos.write(urlBytes)
    bos.write(byteArrayOf(GS, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
    writeTextLine("")
    writeTextLine("")

    bos.write(CMD_ALIGN_LEFT)
    writeTextLine("⚠️ FRIENDLY REMINDERS")
    writeTextLine("1. Scan QR to order. Sent to kitchen automatically.")
    writeTextLine("2. To add items, simply scan the same QR again.")
    writeTextLine("3. For takeaway, please select the 'Takeaway' option.")
    writeTextLine("4. Need assistance? Please call our friendly staff.")
    writeTextLine("")

    bos.write(CMD_ALIGN_CENTER)
    writeTextLine("================================================")
    writeTextLine("ZHI XIANG FOOD ENTERPRISE")
    writeTextLine("2313, Jalan Dato Sulaiman, Taman Abad")
    writeTextLine("80250 Johor Bahru")
    writeTextLine("================================================")

    writeTextLine("\n\n\n\n")
    bos.write(byteArrayOf(GS, 0x56, 0x42, 0x00))

    return bos.toByteArray()
}
