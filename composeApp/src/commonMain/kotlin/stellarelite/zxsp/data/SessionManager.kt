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

    val isLoggedIn: Boolean get() = accessToken != null
    val isAdmin: Boolean get() = role == "admin"

    fun setSession(token: String?, staffId: Long?, staffName: String, role: String, authUid: String? = null) {
        accessToken = token
        this.staffId = staffId
        this.staffName = staffName
        this.role = role
        if (authUid != null) this.authUid = authUid
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
        authUid = null
        SessionStorage.remove("token")
        SessionStorage.remove("staffId")
        SessionStorage.remove("staffName")
        SessionStorage.remove("role")
        SessionStorage.remove("authUid")
    }

    // 登录成功后持久化，重开 APP 免登录
    private fun save() {
        SessionStorage.put("token", accessToken ?: "")
        SessionStorage.put("staffId", staffId?.toString() ?: "")
        SessionStorage.put("staffName", staffName)
        SessionStorage.put("role", role)
        SessionStorage.put("authUid", authUid ?: "")
    }

    // APP 启动时恢复会话
    fun load() {
        val token = SessionStorage.get("token")
        if (!token.isNullOrBlank()) {
            accessToken = token
            staffId = SessionStorage.get("staffId")?.toLongOrNull()
            staffName = SessionStorage.get("staffName") ?: ""
            role = SessionStorage.get("role") ?: ""
            authUid = SessionStorage.get("authUid")?.takeIf { it.isNotBlank() }
        }
    }
}
