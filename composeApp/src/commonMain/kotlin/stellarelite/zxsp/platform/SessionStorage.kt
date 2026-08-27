package stellarelite.zxsp.platform

// 跨平台本地键值存储（Android SharedPreferences / iOS NSUserDefaults）
expect object SessionStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}
