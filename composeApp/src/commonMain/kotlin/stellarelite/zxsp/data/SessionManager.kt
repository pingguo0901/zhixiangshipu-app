package stellarelite.zxsp.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
    }

    fun clear() {
        accessToken = null
        staffId = null
        staffName = ""
        role = ""
    }
}
