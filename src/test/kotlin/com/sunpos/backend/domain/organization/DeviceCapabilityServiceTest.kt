package com.sunpos.backend.domain.organization

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeviceCapabilityServiceTest {

    @Autowired
    private lateinit var deviceCapabilityService: DeviceCapabilityService

    @Autowired
    private lateinit var deviceRepository: DeviceRepository

    @Autowired
    private lateinit var deviceCapabilityRepository: DeviceCapabilityRepository

    @Autowired
    private lateinit var auditLogRepository: DeviceCapabilityAuditLogRepository

    private lateinit var branchId: String
    private lateinit var deviceA: Device
    private lateinit var deviceB: Device

    @BeforeEach
    fun setUp() {
        branchId = "test-branch-" + UUID.randomUUID().toString().take(8)

        // Seed Device A (Main Cashier Station)
        deviceA = deviceRepository.save(
            Device(
                branchId = branchId,
                deviceCode = "POS-TEST-01",
                deviceName = "Station A (Main Cashier)",
                deviceType = "POS_MAIN",
                appVersion = "v1.10.0"
            )
        )

        // Seed Device B (Waiter Tablet)
        deviceB = deviceRepository.save(
            Device(
                branchId = branchId,
                deviceCode = "TAB-TEST-02",
                deviceName = "Station B (Waiter Tablet)",
                deviceType = "POS_TABLET",
                appVersion = "v1.10.0"
            )
        )

        // Initial capabilities:
        // Device A: PAY, OPEN_SHIFT, CLOSE_SHIFT, PRINT_RECEIPT, TAKE_ORDER
        deviceCapabilityService.replaceCapabilities(
            deviceA.id,
            AssignCapabilityDto(
                capabilities = listOf(
                    DeviceCapability.PAY,
                    DeviceCapability.OPEN_SHIFT,
                    DeviceCapability.CLOSE_SHIFT,
                    DeviceCapability.PRINT_RECEIPT,
                    DeviceCapability.TAKE_ORDER
                ),
                assignedBy = "admin"
            )
        )

        // Device B: OPEN_TABLE, TAKE_ORDER
        deviceCapabilityService.replaceCapabilities(
            deviceB.id,
            AssignCapabilityDto(
                capabilities = listOf(
                    DeviceCapability.OPEN_TABLE,
                    DeviceCapability.TAKE_ORDER
                ),
                assignedBy = "admin"
            )
        )
    }

    @Test
    fun `test initial seeded capabilities`() {
        val capsA = deviceCapabilityService.getDeviceCapabilities(deviceA.id).map { it.capability }
        val capsB = deviceCapabilityService.getDeviceCapabilities(deviceB.id).map { it.capability }

        assertTrue(capsA.containsAll(listOf("PAY", "OPEN_SHIFT", "CLOSE_SHIFT", "PRINT_RECEIPT", "TAKE_ORDER")))
        assertFalse(capsA.contains("OPEN_TABLE"))

        assertTrue(capsB.containsAll(listOf("OPEN_TABLE", "TAKE_ORDER")))
        assertFalse(capsB.contains("PAY"))
    }

    @Test
    fun `test transferring PAY capability automatically deactivates PAY from previous device in same transaction`() {
        // Verify Device A initially has PAY
        val initialCapsA = deviceCapabilityService.getDeviceCapabilities(deviceA.id).map { it.capability }
        assertTrue(initialCapsA.contains("PAY"))

        // Assign PAY to Device B (e.g. Manager moves cashier responsibility to Tablet while Station A is in maintenance)
        val updatedCapsB = deviceCapabilityService.replaceCapabilities(
            deviceB.id,
            AssignCapabilityDto(
                capabilities = listOf(
                    DeviceCapability.OPEN_TABLE,
                    DeviceCapability.TAKE_ORDER,
                    DeviceCapability.PAY,
                    DeviceCapability.PRINT_RECEIPT
                ),
                assignedBy = "manager-01"
            )
        ).map { it.capability }

        // Assert Device B now HAS PAY
        assertTrue(updatedCapsB.contains("PAY"))
        assertTrue(updatedCapsB.contains("OPEN_TABLE"))

        // Assert Device A NO LONGER HAS PAY (atomic deactivation & transfer)
        val afterCapsA = deviceCapabilityService.getDeviceCapabilities(deviceA.id).map { it.capability }
        assertFalse(afterCapsA.contains("PAY"), "Device A must not have PAY after transfer to Device B")
        // Other non-exclusive capabilities on Device A remain intact
        assertTrue(afterCapsA.contains("OPEN_SHIFT"))
        assertTrue(afterCapsA.contains("TAKE_ORDER"))

        // Verify only 1 device in branch has PAY
        val branchPayDevices = deviceCapabilityRepository.findByBranchIdAndCapabilityAndIsActiveTrue(branchId, DeviceCapability.PAY)
        assertEquals(1, branchPayDevices.size)
        assertEquals(deviceB.id, branchPayDevices.first().deviceId)

        // Verify Audit Logs recorded for both devices
        val auditLogs = auditLogRepository.findByBranchIdOrderByCreatedAtDesc(branchId)
        val transferLogs = auditLogs.filter { it.action == "TRANSFERRED" }
        val replaceLogs = auditLogs.filter { it.action == "REPLACED" }

        assertTrue(transferLogs.any { it.deviceId == deviceA.id && it.previousCapabilities?.contains("PAY") == true })
        assertTrue(replaceLogs.any { it.deviceId == deviceB.id && it.newCapabilities.contains("PAY") })
    }

    @Test
    fun `test transferring CLOSE_BUSINESS_DAY exclusive capability`() {
        // Device A gets CLOSE_BUSINESS_DAY
        deviceCapabilityService.replaceCapabilities(
            deviceA.id,
            AssignCapabilityDto(
                capabilities = listOf(DeviceCapability.CLOSE_BUSINESS_DAY, DeviceCapability.TAKE_ORDER),
                assignedBy = "admin"
            )
        )
        assertTrue(deviceCapabilityService.getDeviceCapabilities(deviceA.id).map { it.capability }.contains("CLOSE_BUSINESS_DAY"))

        // Device B receives CLOSE_BUSINESS_DAY
        deviceCapabilityService.replaceCapabilities(
            deviceB.id,
            AssignCapabilityDto(
                capabilities = listOf(DeviceCapability.CLOSE_BUSINESS_DAY, DeviceCapability.OPEN_TABLE),
                assignedBy = "manager"
            )
        )

        // Assert exclusivity
        assertFalse(deviceCapabilityService.getDeviceCapabilities(deviceA.id).map { it.capability }.contains("CLOSE_BUSINESS_DAY"))
        assertTrue(deviceCapabilityService.getDeviceCapabilities(deviceB.id).map { it.capability }.contains("CLOSE_BUSINESS_DAY"))
    }

    @Test
    fun `test revoke capability`() {
        deviceCapabilityService.revokeCapability(deviceA.id, DeviceCapability.PRINT_RECEIPT, revokedBy = "admin")

        val capsA = deviceCapabilityService.getDeviceCapabilities(deviceA.id).map { it.capability }
        assertFalse(capsA.contains("PRINT_RECEIPT"))

        val auditLogs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(deviceA.id)
        assertTrue(auditLogs.any { it.action == "REVOKED" && it.previousCapabilities?.contains("PRINT_RECEIPT") == true })
    }
}
