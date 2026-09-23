package com.sunpos.backend.domain.sync

import com.sunpos.backend.common.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** ข้อมูลสำเนากุญแจของเครื่อง POS หนึ่งเครื่อง */
data class DeviceKeyBackup(
    val deviceId: String,
    val wrappedKey: String,
    val keyVersion: Int,
)

/** request body ของ POST /api/v1/sync/device-keys */
data class DeviceKeyBackupRequest(
    val deviceId: String = "",
    val wrappedKey: String = "",
    val keyVersion: Int = 1,
)

/**
 * ADR 0034 / spec 0037 (ticket 05): สำรอง/กู้กุญแจเข้ารหัสฐานข้อมูลออฟไลน์
 *
 * หลักการ:
 *  - แอปส่งกุญแจของตัวเองมาเก็บ — service **wrap ด้วย master key ฝั่ง server**
 *    (AES-256-GCM) ก่อนลง DB จึงไม่มีกุญแจเปล่าอยู่บน cloud
 *  - ดึงกลับ → unwrap ให้ผู้มีสิทธิ์ HQ เท่านั้น (บังคับที่ controller)
 *  - เก็บทับได้ (re-key): ค่าเก่าหาย กุญแจเก่าเปิดไฟล์ใหม่ไม่ได้ตามธรรมชาติ
 */
@Service
class DeviceKeyBackupService(
    private val jdbcTemplate: JdbcTemplate,
    @Value("\${sunpos.key-backup.master-key:sunpos-device-key-backup-master-v1}") private val masterKey: String,
) {
    private val log = LoggerFactory.getLogger(DeviceKeyBackupService::class.java)

    private val salt = "sunpos-key-backup-salt-v1".toByteArray()
    private val aesKey: SecretKeySpec by lazy {
        val spec = PBEKeySpec(masterKey.toCharArray(), salt, 120_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    /** เก็บ/อัปเดตสำเนากุญแจของเครื่อง — wrap ก่อนเก็บเสมอ */
    fun storeWrappedKey(deviceId: String, rawKey: String, keyVersion: Int) {
        require(deviceId.isNotBlank()) { "deviceId is required" }
        require(rawKey.isNotBlank()) { "key is required" }
        val wrapped = wrap(rawKey)
        jdbcTemplate.update(
            """
            INSERT INTO device_key_backups (device_id, wrapped_key, key_version)
            VALUES (?, ?, ?)
            ON CONFLICT (device_id)
            DO UPDATE SET wrapped_key = EXCLUDED.wrapped_key,
                          key_version = EXCLUDED.key_version,
                          updated_at = NOW()
            """.trimIndent(),
            deviceId, wrapped, keyVersion,
        )
        log.info("Stored encrypted DB key backup for device {} (v{})", deviceId, keyVersion)
    }

    /** คืน (wrappedKey, keyVersion) หรือ null ถ้าไม่มีสำเนา (fail-closed ฝั่ง caller) */
    fun getWrappedKey(deviceId: String): Pair<String, Int>? {
        val rows = jdbcTemplate.queryForList(
            "SELECT wrapped_key, key_version FROM device_key_backups WHERE device_id = ?",
            deviceId,
        )
        if (rows.isEmpty()) return null
        return (rows[0]["wrapped_key"] as String) to (rows[0]["key_version"] as Int)
    }

    /** ดึงและ unwrap คืนกุญแจเปล่า — เฉพาะ flow กู้กุญแจของผู้มีสิทธิ์ HQ */
    fun unwrap(deviceId: String): String? {
        val entry = getWrappedKey(deviceId) ?: return null
        return unwrapWithAes(entry.first)
    }

    /** ยืนยันรอบ wrap→unwrap ในเทสต์ */
    fun unwrapForTest(deviceId: String): String? = unwrap(deviceId)

    private fun wrap(plaintext: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return "v1:" + Base64.getEncoder().encodeToString(iv + ct)
    }

    private fun unwrapWithAes(wrapped: String): String {
        require(wrapped.startsWith("v1:")) { "Unknown wrapped key format" }
        val bytes = Base64.getDecoder().decode(wrapped.removePrefix("v1:"))
        val iv = bytes.copyOfRange(0, 12)
        val ct = bytes.copyOfRange(12, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}

/**
 * Endpoints สำรอง/กู้กุญแจ — บังคับสิทธิ์ HQ เท่านั้น
 * (แนวเดียวกับ OrganizationController: ORGANIZATION_MANAGE หรือ SUPER_ADMIN)
 */
@RestController
@RequestMapping("/api/v1/sync")
class DeviceKeyBackupController(
    private val keyBackupService: DeviceKeyBackupService,
) {
    @PostMapping("/device-keys")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun storeKey(@RequestBody request: DeviceKeyBackupRequest): ApiResponse<Boolean> {
        keyBackupService.storeWrappedKey(request.deviceId, request.wrappedKey, request.keyVersion)
        return ApiResponse.success(true, "Device key backup stored")
    }

    @GetMapping("/device-keys/{deviceId}")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun getKey(@PathVariable deviceId: String): ApiResponse<DeviceKeyBackup?> {
        val entry = keyBackupService.getWrappedKey(deviceId)
            ?: return ApiResponse.success(null, "No key backup for device")
        return ApiResponse.success(
            DeviceKeyBackup(deviceId = deviceId, wrappedKey = entry.first, keyVersion = entry.second),
        )
    }
}
