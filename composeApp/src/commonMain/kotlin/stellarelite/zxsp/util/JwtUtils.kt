package stellarelite.zxsp.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// 从 JWT access_token 解析出 sub（即 auth_uid）
@OptIn(ExperimentalEncodingApi::class)
fun decodeJwtSub(token: String): String? = runCatching {
    val parts = token.split(".")
    if (parts.size < 2) return null
    val payload = parts[1]
    // 补齐 base64url 的 padding
    val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
    val json = Base64.UrlSafe.decode(padded).decodeToString()
    Json.parseToJsonElement(json).jsonObject["sub"]?.jsonPrimitive?.content
}.getOrNull()

// 从 JWT access_token 解析出 exp（过期时间，Unix 秒）
@OptIn(ExperimentalEncodingApi::class)
fun decodeJwtExp(token: String): Long? = runCatching {
    val parts = token.split(".")
    if (parts.size < 2) return null
    val payload = parts[1]
    val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
    val json = Base64.UrlSafe.decode(padded).decodeToString()
    Json.parseToJsonElement(json).jsonObject["exp"]?.jsonPrimitive?.content?.toLongOrNull()
}.getOrNull()
