package com.yifeng.commissionbook

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份加密（2026-11-17 加，逸风选的 A 方案：只加密**手动导出 / 分享**出去的那一份）。
 *
 * 格式跟 iOS 端 **一个字节都不能差**，两边要能互读：
 *
 * ```
 * {"format":"commissionbook-backup","v":1,"enc":"AES-256-GCM",
 *  "kdf":"PBKDF2-SHA256","iter":200000,
 *  "salt":"<16 字节 base64>","nonce":"<12 字节 base64>",
 *  "data":"<密文 + 16 字节 tag 的 base64>"}
 * ```
 *
 * - 密钥 = PBKDF2-HmacSHA256(密码, salt, 200000 次) → 256 位
 * - 加密 = AES/GCM/NoPadding，IV 12 字节，tag 128 位，**tag 跟在密文末尾**（Conscrypt 默认就是这么给的）
 * - base64 用标准的、带 padding（`android.util.Base64.NO_WRAP` 正好是这个口径，
 *   跟 iOS 的 `Data.base64EncodedString()` 一样）
 *
 * ⚠️ 密码丢了 = 这份文件永久打不开。**没有找回**，界面上必须说清楚。
 * ⚠️ 别拿它去加密「自动备份」—— 自动备份留在本机，逸风选了不动它。
 */
object BackupCrypto {

    /** 迭代次数 —— iOS 那边写死同一个数，改这里就等于把老备份全废掉 */
    private const val ITERATIONS = 200_000
    private const val SALT_LEN = 16
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    /** 这段文本是不是加密信封（不是的话 = 老明文备份，照常导） */
    fun isSealed(text: String): Boolean = runCatching {
        val o = JSONObject(text)
        o.optString("enc").isNotEmpty() && o.optString("salt").isNotEmpty()
    }.getOrDefault(false)

    /**
     * 加密：明文 JSON 文本 → 信封 JSON 文本。
     * @return 失败（密码空 / 环境异常）返回 null
     */
    fun seal(plain: String, password: String): String? = runCatching {
        if (password.isEmpty()) return@runCatching null

        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt, ITERATIONS)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // 不给 IV → 由系统生成一个随机 IV（实测 12 字节）。自己造 IV 再塞进去也没问题，
        // 但**千万别复用**：GCM 下同一个 (key, IV) 用两次，密钥就废了。
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        require(iv.size == 12) { "IV 长度应为 12，实际 ${iv.size}" }

        JSONObject()
            .put("format", "commissionbook-backup")
            .put("v", 1)
            .put("enc", "AES-256-GCM")
            .put("kdf", "PBKDF2-SHA256")
            .put("iter", ITERATIONS)
            .put("salt", b64(salt))
            .put("nonce", b64(iv))
            .put("data", b64(ct))   // ct 末尾自带 tag
            .toString(2)
    }.getOrNull()

    /**
     * 解密：信封 JSON 文本 → 明文 JSON 文本。
     * **密码不对 / 文件坏了都返回 null**（不区分 —— 别给猜密码的人递线索）。
     */
    fun open(envelope: String, password: String): String? = runCatching {
        val o = JSONObject(envelope)
        val iter = o.optInt("iter", 0)
        val salt = unb64(o.getString("salt"))
        val iv = unb64(o.getString("nonce"))
        val blob = unb64(o.getString("data"))
        if (iter <= 0 || iv.size != 12 || blob.size <= 16) return@runCatching null

        val key = deriveKey(password, salt, iter)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(blob), Charsets.UTF_8)
    }.getOrNull()

    /** PBKDF2-HmacSHA256 → AES-256 密钥 */
    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
