package stellarelite.zxsp

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 桌面版更新器：检测 GitHub Releases 里 tag 以 -desktop 结尾的最新版本，
// 支持程序内下载 zip → 解压到 update_new → 写 update.bat → 启动脚本。
// 调用方在 downloadAndApply 返回 true 后退出主程序，脚本会在主程序退出后替换文件并重启（无需跳浏览器下载页）
object DesktopUpdater {
    const val CURRENT_VERSION = "1.2.46"
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
                    // 优先取 zip 资产的直链（用于程序内下载），找不到再退回 Release 页面链接
                    val zipUrl = obj["assets"]?.jsonArray
                        ?.firstOrNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".zip") == true }
                        ?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
                    return@withContext VersionInfo(
                        versionCode = 0,
                        versionName = ver,
                        apkUrl = zipUrl ?: (obj["html_url"]?.jsonPrimitive?.contentOrNull ?: ""),
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

    // 程序内更新：下载 zip → 解压 → 写替换脚本 → 启动脚本。
    // 返回 true 表示已准备好替换（脚本已启动），调用方应退出主程序让脚本接管。
    suspend fun downloadAndApply(url: String, onProgress: ((Long, Long) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext false
        try {
            val appDir = File(System.getProperty("user.dir"))
            val zipFile = File(appDir, "update.zip")

            // 1. 下载
            if (!download(url, zipFile, onProgress)) return@withContext false

            // 2. 解压到 update_new（去掉 zip 顶层目录）
            val updateDir = File(appDir, "update_new")
            updateDir.deleteRecursively()
            updateDir.mkdirs()
            unzip(zipFile, updateDir)
            zipFile.delete()

            // 3. 写替换脚本
            val bat = File(appDir, "update.bat")
            bat.writeText(buildBatScript())

            // 4. 启动脚本
            Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", "", bat.absolutePath))
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun download(url: String, dest: File, onProgress: ((Long, Long) -> Unit)?): Boolean {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 120000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/octet-stream")
            conn.setRequestProperty("User-Agent", "ZhiXiangFood-Updater")
            if (conn.responseCode != 200) return false
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        onProgress?.invoke(done, total)
                    }
                }
            }
            return dest.exists() && dest.length() > 0
        } catch (e: Exception) {
            return false
        }
    }

    private fun unzip(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                // 去掉 zip 顶层目录（如 ZhiXiangFood\）
                var name = entry.name
                val slash = name.indexOfAny(charArrayOf('\\', '/'))
                if (slash > 0) {
                    name = name.substring(slash + 1)
                }
                if (name.isEmpty()) continue
                val target = File(destDir, name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    private fun buildBatScript(): String = """
        @echo off
        setlocal
        REM ZhiXiangFood auto-updater
        :waitloop
        tasklist /FI "IMAGENAME eq ZhiXiangFood.exe" 2>nul | find /I "ZhiXiangFood.exe" >nul
        if not errorlevel 1 (
            timeout /t 1 /nobreak >nul
            goto waitloop
        )
        xcopy /E /Y /Q "%~dp0update_new\*" "%~dp0" >nul
        start "" "%~dp0ZhiXiangFood.exe"
        rmdir /S /Q "%~dp0update_new"
        del "%~f0"
        endlocal
    """.trimIndent()

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
