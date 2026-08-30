package stellarelite.zxsp.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import stellarelite.zxsp.platform.SessionStorage

// 全局语言状态：zh（中文）/ en（英文）
object LanguageManager {
    var lang by mutableStateOf("en")
        private set

    val isEnglish: Boolean get() = lang == "en"

    fun setLanguage(l: String) {
        lang = if (l == "zh") "zh" else "en"
        SessionStorage.put("lang", lang)
    }

    fun toggle() {
        setLanguage(if (isEnglish) "zh" else "en")
    }

    // APP 启动时恢复上次语言选择，默认英文
    fun load() {
        lang = SessionStorage.get("lang") ?: "en"
    }
}

// 文案双语辅助：t("中文", "English")，读取语言状态（在 Composable 中调用会自动追踪重组）
fun t(zh: String, en: String): String = if (LanguageManager.isEnglish) en else zh
