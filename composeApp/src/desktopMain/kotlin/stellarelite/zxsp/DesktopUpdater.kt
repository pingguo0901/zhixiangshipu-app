package stellarelite.zxsp

import java.awt.Desktop
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 桌面版更新检测（查 GitHub Releases 里 tag 以 -desktop 结尾的最新版本）
object DesktopUpdater {
    const val CURRENT_VERSION = "1.2.42"
    private const val RELEASES_URL = "https://api.github.com/repos/pingguo0901/zhixiangshipu-app/releases"

    suspend fun checkForUpdate(): VersionInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return@withContext null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = Json.parseToJsonElement(text).jsonArray
            for (el in arr) {
                val obj = el.jsonObject
                val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: continue
                if (!tag.endsWith("-desktop")) continue
                val ver = tag.removeSuffix("-desktop").removePrefix("v")
                if (compareVersion(ver, CURRENT_VERSION) > 0) {
                    return@withContext VersionInfo(
                        versionCode = 0,
                        versionName = ver,
                        apkUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: "",
                        changelog = obj["body"]?.jsonPrimitive?.contentOrNull ?: ""
                    )
                } else {
                    return@withContext null
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun openDownload(url: String) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        } catch (_: Exception) { }
    }

    private fun compareVersion(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}
