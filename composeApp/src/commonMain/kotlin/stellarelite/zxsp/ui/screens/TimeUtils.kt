package stellarelite.zxsp.ui.screens

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// 当前时间 ISO 字符串（Asia/Kuala_Lumpur，带 +08:00 时区标记，避免存库时被当 UTC）
fun currentIso(): String {
    val tz = TimeZone.of("Asia/Kuala_Lumpur")
    val now = Clock.System.now().toLocalDateTime(tz)
    return "%04d-%02d-%02dT%02d:%02d:%02d+08:00".format(
        now.year, now.monthNumber, now.dayOfMonth,
        now.hour, now.minute, now.second
    )
}

// 今天日期 YYYY-MM-DD
fun todayDate(): String {
    val tz = TimeZone.of("Asia/Kuala_Lumpur")
    val now = Clock.System.now().toLocalDateTime(tz)
    return "%04d-%02d-%02d".format(now.year, now.monthNumber, now.dayOfMonth)
}

// 时间戳转 YYYY-MM-DD
fun millisToDate(millis: Long): String {
    val tz = TimeZone.of("Asia/Kuala_Lumpur")
    val dt = Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)
    return "%04d-%02d-%02d".format(dt.year, dt.monthNumber, dt.dayOfMonth)
}

// 带时区的 ISO 字符串（如 2026-09-02T22:33:48+00:00）转 Asia/Kuala_Lumpur 本地时间 "YYYY-MM-DD HH:mm"
fun isoToKlDateTime(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching {
        val dt = Instant.parse(iso).toLocalDateTime(TimeZone.of("Asia/Kuala_Lumpur"))
        "%04d-%02d-%02d %02d:%02d".format(dt.year, dt.monthNumber, dt.dayOfMonth, dt.hour, dt.minute)
    }.getOrElse { iso.take(16).replace("T", " ") }
}

// 带时区的 ISO 字符串转 Asia/Kuala_Lumpur 本地日期 "YYYY-MM-DD"
fun isoToKlDate(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching {
        val dt = Instant.parse(iso).toLocalDateTime(TimeZone.of("Asia/Kuala_Lumpur"))
        "%04d-%02d-%02d".format(dt.year, dt.monthNumber, dt.dayOfMonth)
    }.getOrElse { iso.take(10) }
}

// 带时区 ISO → "dd/MM/yyyy HH:mm"（厨房单/收据打印时间等使用）
fun isoToKlDateTimeSlash(iso: String): String {
    if (iso.isBlank()) return iso
    return runCatching {
        val dt = Instant.parse(iso).toLocalDateTime(TimeZone.of("Asia/Kuala_Lumpur"))
        "%02d/%02d/%04d %02d:%02d".format(dt.dayOfMonth, dt.monthNumber, dt.year, dt.hour, dt.minute)
    }.getOrElse {
        val datePart = iso.take(10)
        val timePart = if (iso.length >= 16) iso.substring(11, 16) else ""
        val parts = datePart.split("-")
        if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]} $timePart" else iso
    }
}
