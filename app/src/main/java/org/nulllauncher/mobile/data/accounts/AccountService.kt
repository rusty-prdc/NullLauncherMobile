package org.nulllauncher.mobile.data.accounts

import android.content.Context
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.nulllauncher.mobile.core.LauncherException
import org.nulllauncher.mobile.core.Net
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

@Serializable
data class Account(
    val uuid: String,
    val name: String,
    val accessToken: String,
    val refreshToken: String = "",
    val expiresAt: Long = 0,
)

/**
 * Вход через Microsoft — официальный device-code flow (без паролей):
 * 1) GET devicecode → код и ссылка login.microsoftonline.com
 * 2) poll token → user token Microsoft
 * 3) XBL → XSTS → login_with_xbox → токен Minecraft Services
 * 4) profile → имя и uuid игрока
 */
class AccountService(private val ctx: Context) {

    companion object {
        // Публичный client id Minecraft (используется официальным лаунчером и open-source проектами)
        const val CLIENT_ID = "00000000-4058-40d0-800e-d4261674c1ff"
        const val SCOPE = "XboxLive.signin offline_access"
    }

    private val file get() = File(ctx.filesDir, "accounts.json")

    @Serializable
    data class DeviceCode(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_uri") val verificationUri: String,
        @SerialName("expires_in") val expiresIn: Int = 900,
        @SerialName("interval") val interval: Int = 5,
    )

    @Serializable
    data class MsToken(
        @SerialName("access_token") val accessToken: String = "",
        @SerialName("refresh_token") val refreshToken: String = "",
        @SerialName("expires_in") val expiresIn: Int = 0,
    )

    fun accounts(): List<Account> {
        if (!file.exists()) return emptyList()
        return try {
            Net.json.decodeFromString(file.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(all: List<Account>) {
        file.writeText(Net.json.encodeToString(all))
    }

    fun add(a: Account) {
        val all = accounts().filterNot { it.uuid == a.uuid }.toMutableList()
        all.add(a)
        save(all)
    }

    fun remove(uuid: String) = save(accounts().filterNot { it.uuid == uuid })

    /**
     * Офлайн-аккаунт: локальное имя без авторизации (для локальной игры/серверов без пароля).
     * UUID считается как в Minecraft: MD5("OfflinePlayer:<name>") — стабильный.
     */
    fun addOffline(rawName: String): Account {
        val name = rawName.trim().replace(Regex("[^A-Za-z0-9_]"), "").take(16)
        if (name.length < 3) throw LauncherException("Имя слишком короткое", "от 3 символов: A-Z, a-z, 0-9, _")
        val md = java.security.MessageDigest.getInstance("MD5")
        val hash = md.digest("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x30).toByte()  // version 3
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()  // variant
        val uuid = hash.joinToString("") { "%02x".format(it) }
        val acc = Account(uuid = uuid, name = name, accessToken = "", refreshToken = "", expiresAt = 0)
        add(acc)
        return acc
    }

    /** Шаг 1: получить device-code */
    suspend fun requestDeviceCode(): DeviceCode {
        val resp = postForm(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
            mapOf("client_id" to CLIENT_ID, "scope" to SCOPE)
        )
        return Net.json.decodeFromString(resp)
    }

    /** Шаг 2: опрос токена до подтверждения кода */
    suspend fun pollToken(deviceCode: String): MsToken {
        repeat(120) {
            val resp = try {
                postForm(
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                    mapOf(
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                        "client_id" to CLIENT_ID,
                        "device_code" to deviceCode,
                    )
                )
            } catch (e: LauncherException) {
                throw e
            }
            if (resp.contains("access_token")) {
                return Net.json.decodeFromString(resp)
            }
            if (resp.contains("authorization_declined") || resp.contains("authorization_timeout")) {
                throw LauncherException("Вход отменён", resp)
            }
            kotlinx.coroutines.delay(minOf((it + 1) * 1000L, 5000L))
        }
        throw LauncherException("Время входа истекло", "Повтори попытку")
    }

    /** Шаг 3: XBL → XSTS → Minecraft token; Шаг 4: профиль */
    suspend fun loginWithMicrosoft(ms: MsToken): Account {
        val xbl = postJson(
            "https://user.auth.xboxlive.com/user/authenticate",
            """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=${ms.accessToken}"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}"""
        )
        val xblToken = extract(xbl, "Token")
        val userHash = extract(extract(xbl, "DisplayClaims"), "xui").let { Regex("\"uhs\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) ?: "" }

        val xsts = postJson(
            "https://xsts.auth.xboxlive.com/xsts/authorize",
            """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}"""
        )
        val xstsToken = extract(xsts, "Token")

        val mc = postJson(
            "https://api.minecraftservices.com/authentication/login_with_xbox",
            """{"userTokens":["$xstsToken"],"identityToken":"XBL3.0 x=$userHash;$xstsToken"}"""
        )
        val mcToken = extract(mc, "access_token")

        val profile = Net.get(
            "https://api.minecraftservices.com/minecraft/profile",
            mapOf("Authorization" to "Bearer $mcToken", "User-Agent" to "nullLauncher-Mobile/0.1")
        )
        val uuid = Regex("\"id\":\"([^\"]+)\"").find(profile)?.groupValues?.get(1)
            ?: throw LauncherException("Не удалось получить профиль", "нет.minecraft")
        val name = Regex("\"name\":\"([^\"]+)\"").find(profile)?.groupValues?.get(1) ?: "Player"

        return Account(
            uuid = uuid,
            name = name,
            accessToken = mcToken,
            refreshToken = ms.refreshToken,
            expiresAt = System.currentTimeMillis() + ms.expiresIn * 1000L,
        )
    }

    /** Обновление токена Minecraft по refresh token Microsoft */
    suspend fun refresh(acc: Account): Account {
        if (acc.refreshToken.isBlank()) return acc
        val resp = postForm(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
            mapOf(
                "grant_type" to "refresh_token",
                "client_id" to CLIENT_ID,
                "refresh_token" to acc.refreshToken,
                "scope" to SCOPE,
            )
        )
        val ms = Net.json.decodeFromString<MsToken>(resp)
        return loginWithMicrosoft(ms).copy(refreshToken = ms.refreshToken.ifBlank { acc.refreshToken })
    }

    private fun extract(json: String, key: String): String {
        val i = json.indexOf("\"$key\":\"")
        if (i < 0) throw LauncherException("Некорректный ответ авторизации", key)
        val s = i + key.length + 3
        val e = json.indexOf('"', s)
        return json.substring(s, e)
    }

    private suspend fun postForm(url: String, fields: Map<String, String>): String =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val form = fields.entries.joinToString("&") {
                "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
            }.toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val req = okhttp3.Request.Builder().url(url).post(form)
                .header("User-Agent", "nullLauncher-Mobile/0.1").build()
            Net.http.newCall(req).execute().use { r ->
                r.body?.string() ?: throw LauncherException("Пустой ответ", url)
            }
        }

    private suspend fun postJson(url: String, json: String): String =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val req = okhttp3.Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", "nullLauncher-Mobile/0.1").build()
            Net.http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw LauncherException("Ошибка авторизации (HTTP ${r.code})", url)
                r.body?.string() ?: throw LauncherException("Пустой ответ", url)
            }
        }
}
