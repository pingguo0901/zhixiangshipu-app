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

    val isLoggedIn: Boolean get() = accessToken != null
    val isAdmin: Boolean get() = role == "admin"

    fun setSession(token: String?, staffId: Long?, staffName: String, role: String) {
        accessToken = token
        this.staffId = staffId
        this.staffName = staffName
        this.role = role
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
        SessionStorage.remove("token")
        SessionStorage.remove("staffId")
        SessionStorage.remove("staffName")
        SessionStorage.remove("role")
    }

    // 登录成功后持久化，重开 APP 免登录
    private fun save() {
        SessionStorage.put("token", accessToken ?: "")
        SessionStorage.put("staffId", staffId?.toString() ?: "")
        SessionStorage.put("staffName", staffName)
        SessionStorage.put("role", role)
    }

    // APP 启动时恢复会话
    fun load() {
        val token = SessionStorage.get("token")
        if (!token.isNullOrBlank()) {
            accessToken = token
            staffId = SessionStorage.get("staffId")?.toLongOrNull()
            staffName = SessionStorage.get("staffName") ?: ""
            role = SessionStorage.get("role") ?: ""
        }
    }
}
