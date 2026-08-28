package stellarelite.zxsp.util

import kotlin.math.floor

/**
 * 芯烨 Zy808 (80mm / 48列) 收据完美对齐排版工具（KMP 纯 Kotlin 实现）
 * 核心分配策略：Item(24列) + Qty(5列) + Unit(9列) + Amount(10列) = 48列
 */
object ReceiptFormatter {
    const val TOTAL_WIDTH = 48

    // 1. 获取字符串在打印时的实际显示宽度（中文字符占2，英文字符/数字/半角符号占1）
    // 在 KMP 中通过将其转换为 UTF-8 编码的字节数组来精确判定
    fun getPrintWidth(str: String): Int {
        var length = 0
        val bytes = str.encodeToByteArray()
        var i = 0
        while (i < bytes.size) {
            val byte = bytes[i].toInt()
            if (byte in 0..127) {
                length += 1 // 标准 ASCII 字符（英文、数字、半角空格）
                i += 1
            } else {
                // 处理多字节字符（如中文 UTF-8 占 3 字节，但在打印机占 2 个半角字符宽）
                length += 2
                // 根据 UTF-8 编码规则滑过当前多字节字符
                if ((byte and 0xE0) == 0xC0) i += 2
                else if ((byte and 0xF0) == 0xE0) i += 3
                else if ((byte and 0xF8) == 0xF0) i += 4
                else i += 1
            }
        }
        return length
    }

    // 2. 左对齐填充（右侧补空格）
    fun padRight(str: String, width: Int): String {
        val currentLen = getPrintWidth(str)
        if (currentLen >= width) return str
        return str + " ".repeat(width - currentLen)
    }

    // 3. 右对齐填充（左侧补空格）
    fun padLeft(str: String, width: Int): String {
        val currentLen = getPrintWidth(str)
        if (currentLen >= width) return str
        return " ".repeat(width - currentLen) + str
    }

    // 4. 居中对齐填充（两侧补空格）
    fun padCenter(str: String, width: Int): String {
        val currentLen = getPrintWidth(str)
        if (currentLen >= width) return str
        val leftSpaces = floor(((width - currentLen) / 2).toDouble()).toInt()
        val rightSpaces = width - currentLen - leftSpaces
        return " ".repeat(leftSpaces) + str + " ".repeat(rightSpaces)
    }

    // 5. 生成商品明细行（Item占24格，Qty占5格，Unit占9格，Amount占10格）
    // name 为品名（中文/英文皆可，按实际显示宽度对齐）；chineseName 为可选的中文品名辅助行
    fun generateItemRow(name: String, chineseName: String?, qty: String, unit: String, amount: String): String {
        val sb = StringBuilder()

        // 第一行：品名左对齐24格，其余数据全部右对齐
        val col1 = padRight(name, 24)
        val col2 = padLeft(qty, 5)
        val col3 = padLeft(unit, 9)
        val col4 = padLeft(amount, 10)
        sb.append(col1).append(col2).append(col3).append(col4)

        // 第二行：如果存在中文品名，则单独一行输出，且左侧对齐品名，右侧留空防折行错位
        if (!chineseName.isNullOrBlank()) {
            val chCol = padRight(chineseName, 24)
            val emptyCol = " ".repeat(24)
            sb.append("\n").append(chCol).append(emptyCol)
        }

        return sb.toString()
    }

    // 6. 生成底部结算行 (Sub Total、Total Amount、金额通用)
    fun generateTotalRow(label: String, currency: String, amount: String): String {
        val leftPart = padRight(label, 24) // 标签左对齐（24格）
        val midPart = padRight(currency, 14) // 币种靠左（14格）
        val rightPart = padLeft(amount, 10) // 金额数字绝对右对齐（10格），完美对齐上面的 Amount
        return "$leftPart$midPart$rightPart"
    }
}
