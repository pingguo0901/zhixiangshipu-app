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

    // 5. 生成商品明细行（中文名在上带数据，英文名在下右侧留空）
    fun generateItemRow(englishName: String, chineseName: String?, qty: String, unit: String, amount: String): String {
        val sb = StringBuilder()

        // 有效主名称：中文名优先，否则英文名兜底
        val primaryName = if (!chineseName.isNullOrBlank()) chineseName else englishName

        // 第一行：中文菜品名左对齐24格 + Qty/Unit/Amount 右对齐（24 + 5 + 9 + 10 = 48）
        val col1 = padRight(primaryName, 24)
        val col2 = padLeft(qty, 5)
        val col3 = padLeft(unit, 9)
        val col4 = padLeft(amount, 10)
        sb.append(col1).append(col2).append(col3).append(col4)

        // 第二行：若中文名存在且英文名非空，英文名左对齐24格 + 右侧留空24格撑满48列
        if (!chineseName.isNullOrBlank() && englishName.isNotBlank()) {
            val enCol = padRight(englishName, 24)
            val emptyCol = " ".repeat(24)
            sb.append("\n").append(enCol).append(emptyCol)
        }

        return sb.toString()
    }

    // 6. 生成底部结算行 (Sub Total、Total Amount、金额通用)（34 + 4 + 10 = 48列）
    fun generateTotalRow(label: String, currency: String, amount: String): String {
        val leftPart = padRight(label, 34) // 左侧标签拓宽到 34 格
        val midPart = padRight(currency, 4) // 货币符号紧凑固定为 4 格 (足够容纳 "RM" 和 2个空格)
        val rightPart = padLeft(amount, 10) // 金额数字绝对右对齐（10格），完美对齐上方的数字
        return "$leftPart$midPart$rightPart"
    }

    // 7. 报表通用数据对齐行（总宽 48 列，24 + 14 + 10）
    fun generateReportRow(label: String, unit: String, value: String): String {
        val leftPart = padRight(label, 24)
        val midPart = padRight(unit, 14)
        val rightPart = padLeft(value, 10)
        return "$leftPart$midPart$rightPart"
    }
}
