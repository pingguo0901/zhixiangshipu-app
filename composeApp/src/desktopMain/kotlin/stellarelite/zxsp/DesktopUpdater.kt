package stellarelite.zxsp

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 桌面版更新器：检测 GitHub Releases 里 tag 以 -desktop 结尾的最新版本，
// 支持程序内下载 zip → 解压 → 写替换脚本 → 退出由脚本替换并重启（无需跳浏览器下载页）
object DesktopUpdater {
    const val CURRENT_VERSION = "1.2.86"
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
    // 返回 null 表示成功（脚本已启动，调用方应退出主程序让脚本接管）；返回非 null 是错误信息。
    suspend fun downloadAndApply(url: String, onProgress: ((Long, Long) -> Unit)? = null): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext "更新地址无效"
        try {
            // 程序目录（exe 所在目录，比 user.dir 更可靠，避免快捷方式/工作目录不可写）
            val appDir = currentAppDir()

            // 临时目录（系统临时目录总是可写）
            val tmpDir = File(System.getProperty("java.io.tmpdir"), "zxsp_update")
            if (!tmpDir.exists()) tmpDir.mkdirs()
            val zipFile = File(tmpDir, "update.zip")

            // 1. 下载
            if (!download(url, zipFile, onProgress)) {
                return@withContext "下载失败（网络中断或写入受限），请重试"
            }

            // 2. 解压到 new 子目录（去掉 zip 顶层目录）
            val newDir = File(tmpDir, "new")
            newDir.deleteRecursively()
            newDir.mkdirs()
            unzip(zipFile, newDir)
            zipFile.delete()

            // 3. 写替换脚本（硬编码目标目录与新文件目录）
            val bat = File(tmpDir, "update.bat")
            bat.writeText(buildBatScript(appDir.absolutePath, newDir.absolutePath), Charset.forName("GBK"))

            // 4. 启动脚本
            Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", "", "\"" + bat.absolutePath + "\""))
            null
        } catch (e: Exception) {
            "更新出错：" + (e.message ?: "未知错误")
        }
    }

    private fun currentAppDir(): File {
        // 关键：不信任任何单一来源，而是收集多个候选目录，
        // 校验目录里确实存在 ZhiXiangFood.exe 才采用（比硬编码/单属性可靠得多）。
        val candidates = mutableListOf<File>()

        // 1. jpackage.app-path：打包后指向启动器 exe（若 jpackage 注入了该属性）
        System.getProperty("jpackage.app-path")?.let { p ->
            File(p).parentFile?.let { candidates.add(it) }
        }

        // 2. java.home 的父目录：jpackage/Compose Desktop 打包后 JRE 固定放在 <app>\runtime，
        //    所以 java.home = <app>\runtime，父目录就是程序目录。这是最可靠的信号。
        System.getProperty("java.home")?.let { h ->
            File(h).parentFile?.let { candidates.add(it) }
        }

        // 3. ProcessHandle command：运行中的 JVM 是 <app>\runtime\bin\java.exe，往上 3 层是程序目录
        runCatching { ProcessHandle.current().info().command().orElse(null) }
            .getOrNull()?.let { cmd ->
                var f = File(cmd)
                repeat(3) { f = f.parentFile ?: return@let }
                candidates.add(f)
            }

        // 4. 硬编码兜底 + 5. user.dir 兜底
        candidates.add(File("C:\\Users\\pingg\\OneDrive\\zxsp-desktop-windows\\ZhiXiangFood"))
        candidates.add(File(System.getProperty("user.dir")))

        // 返回第一个「确实含 exe」的目录；都没有就取第一个存在的目录；再退到第一个候选
        return candidates.firstOrNull { c ->
            c.isDirectory && c.exists() && File(c, "ZhiXiangFood.exe").exists()
        } ?: candidates.firstOrNull { it.isDirectory && it.exists() } ?: candidates.first()
    }

    private fun download(url: String, dest: File, onProgress: ((Long, Long) -> Unit)?): Boolean {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 300000
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

    private fun buildBatScript(appDir: String, newDir: String): String = """
        @echo off
        setlocal
        set "APP_DIR=$appDir"
        set "NEW_DIR=$newDir"
        :waitloop
        tasklist /FI "IMAGENAME eq ZhiXiangFood.exe" 2>nul | find /I "ZhiXiangFood.exe" >nul
        if not errorlevel 1 (
            timeout /t 1 /nobreak >nul
            goto waitloop
        )
        xcopy /E /Y /Q "%NEW_DIR%\*" "%APP_DIR%" >nul
        start "" "%APP_DIR%\ZhiXiangFood.exe"
        rmdir /S /Q "%NEW_DIR%"
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
