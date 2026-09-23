package com.sunpos.backend.domain.sync

import com.sunpos.backend.common.TestFixtureFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * ADR 0034 / spec 0037 (ticket 05): cloud key backup + flow กู้กุญแจ
 *
 * ยืนยันผ่าน controller seam จริง (method security บังคับ @PreAuthorize):
 *   1. เก็บกุญแจแล้วดึงกลับได้ตรงค่า (และ re-key ทับค่าเก่าหาย)
 *   2. สิทธิ์ HQ เท่านั้น — สิทธิ์อื่นเข้าไม่ได้ (AccessDenied)
 *   3. ดึงเครื่องที่ไม่มี backup → คืน null (fail-closed)
 *   4. service wrap กุญแจฝั่ง server — ใน DB ไม่มีกุญแจเปล่า, unwrap กลับได้ตรงค่า
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeviceKeyBackupTest {

    @Autowired private lateinit var deviceKeyBackupController: DeviceKeyBackupController
    @Autowired private lateinit var keyBackupService: DeviceKeyBackupService
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory
    @Autowired private lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureCompany()
    }

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun `store then fetch returns same wrapped key - rekey replaces old value`() {
        val stored = deviceKeyBackupController.storeKey(
            DeviceKeyBackupRequest(deviceId = "dev-pos-1", wrappedKey = "wrapped-secret-abc123", keyVersion = 1)
        )
        assertTrue(stored.success)

        // round-trip: ดึงกลับแล้ว unwrap ต้องได้ค่าเดิมที่ส่งมา
        val fetched = deviceKeyBackupController.getKey("dev-pos-1")
        assertEquals("wrapped-secret-abc123", keyBackupService.unwrapForTest("dev-pos-1"))
        assertEquals(1, fetched.data?.keyVersion)

        // re-key: เก็บทับ → ค่าเก่าหาย
        deviceKeyBackupController.storeKey(
            DeviceKeyBackupRequest(deviceId = "dev-pos-1", wrappedKey = "wrapped-v2", keyVersion = 2)
        )
        val fetched2 = deviceKeyBackupController.getKey("dev-pos-1")
        assertEquals("wrapped-v2", keyBackupService.unwrapForTest("dev-pos-1"))
        assertEquals(2, fetched2.data?.keyVersion)
    }

    @Test
    @WithMockUser(roles = ["CASHIER"])
    fun `non-HQ role is denied store and fetch`() {
        assertThrows(AccessDeniedException::class.java) {
            deviceKeyBackupController.storeKey(
                DeviceKeyBackupRequest(deviceId = "dev-cashier", wrappedKey = "x", keyVersion = 1)
            )
        }
        assertThrows(AccessDeniedException::class.java) {
            deviceKeyBackupController.getKey("dev-pos-1")
        }
    }

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun `missing device returns no key - fail closed`() {
        val fetched = deviceKeyBackupController.getKey("no-such-device")
        assertNull(fetched.data, "ไม่มีข้อมูลต้องไม่คืนกุญแจ (fail-closed)")
    }

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun `service wraps key server-side - db never stores raw key`() {
        val deviceId = "dev-wrap-check"
        val raw = "my-raw-device-key"
        keyBackupService.storeWrappedKey(deviceId, raw, keyVersion = 1)

        val fromDb = jdbc.queryForList(
            "SELECT wrapped_key FROM device_key_backups WHERE device_id = ?",
            deviceId
        )
        assertEquals(1, fromDb.size)
        val stored = fromDb[0]["wrapped_key"] as String
        assertNotEquals(raw, stored, "cloud ต้องไม่เห็นกุญแจเปล่า — ต้องถูก wrap ก่อนเก็บ")

        // unwrap กลับได้ตรงค่าเดิม
        assertEquals(raw, keyBackupService.unwrapForTest(deviceId))
    }
}
