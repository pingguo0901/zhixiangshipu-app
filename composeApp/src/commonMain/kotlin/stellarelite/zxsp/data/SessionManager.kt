package stellarelite.zxsp.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import stellarelite.zxsp.platform.SessionStorage

// 全局登录会话状态
object SessionManager {
    var accessToken by mutableStateOf<String?>(null)
        private set
    var staffId by mutableStateOf<Long?>(null)
        private set
    var staffName by mutableStateOf("")
        private set
    var role by mutableStateOf("")
        private set
    var authUid by mutableStateOf<String?>(null)
        private set
    var refreshToken by mutableStateOf<String?>(null)
        private set
    var canPrintDaily by mutableStateOf(false)
        private set
    var canPrintQr by mutableStateOf(false)
        private set

    val isLoggedIn: Boolean get() = accessToken != null
    val isAdmin: Boolean get() = role == "admin"

    fun setSession(token: String?, staffId: Long?, staffName: String, role: String, authUid: String? = null, refreshToken: String? = null, canPrintDaily: Boolean = false, canPrintQr: Boolean = false) {
        accessToken = token
        this.staffId = staffId
        this.staffName = staffName
        this.role = role
        this.canPrintDaily = canPrintDaily
        this.canPrintQr = canPrintQr
        if (authUid != null) this.authUid = authUid
        if (refreshToken != null) this.refreshToken = refreshToken
        save()
    }

    // 刷新 token 后更新（保留员工信息）
    fun updateTokens(accessToken: String, refreshToken: String?) {
        this.accessToken = accessToken
        if (refreshToken != null) this.refreshToken = refreshToken
        save()
    }

    fun setToken(token: String) {
        accessToken = token
    }

    fun clear() {
        accessToken = null
        staffId = null
        staffName = ""
        role = ""
        canPrintDaily = false
        canPrintQr = false
        authUid = null
        refreshToken = null
        SessionStorage.remove("token")
        SessionStorage.remove("staffId")
        SessionStorage.remove("staffName")
        SessionStorage.remove("role")
        SessionStorage.remove("canPrintDaily")
        SessionStorage.remove("canPrintQr")
        SessionStorage.remove("authUid")
        SessionStorage.remove("refreshToken")
    }

    // 登录成功后持久化，重开 APP 免登录
    private fun save() {
        SessionStorage.put("token", accessToken ?: "")
        SessionStorage.put("staffId", staffId?.toString() ?: "")
        SessionStorage.put("staffName", staffName)
        SessionStorage.put("role", role)
        SessionStorage.put("canPrintDaily", canPrintDaily.toString())
        SessionStorage.put("canPrintQr", canPrintQr.toString())
        SessionStorage.put("authUid", authUid ?: "")
        SessionStorage.put("refreshToken", refreshToken ?: "")
    }

    // APP 启动时恢复会话
    fun load() {
        val token = SessionStorage.get("token")
        if (!token.isNullOrBlank()) {
            accessToken = token
            staffId = SessionStorage.get("staffId")?.toLongOrNull()
            staffName = SessionStorage.get("staffName") ?: ""
            role = SessionStorage.get("role") ?: ""
            canPrintDaily = SessionStorage.get("canPrintDaily")?.toBooleanStrictOrNull() ?: false
            canPrintQr = SessionStorage.get("canPrintQr")?.toBooleanStrictOrNull() ?: false
            authUid = SessionStorage.get("authUid")?.takeIf { it.isNotBlank() }
            refreshToken = SessionStorage.get("refreshToken")?.takeIf { it.isNotBlank() }
        }
    }
}
