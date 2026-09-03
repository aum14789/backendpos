package com.sunpos.backend.domain.organization

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class Company(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var taxId: String? = null,
    val createdAt: Instant = Instant.now(),
    var createdBy: String? = null,
    var updatedAt: Instant = Instant.now(),
    var updatedBy: String? = null,
    var version: Long = 0
)

class Brand(
    val id: String = UUID.randomUUID().toString(),
    var companyId: String = "",
    var name: String = "",
    var code: String = "",
    var logoUrl: String? = null,
    var description: String? = null,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    var createdBy: String? = null,
    var updatedAt: Instant = Instant.now(),
    var updatedBy: String? = null,
    var version: Long = 0
)

class Branch(
    val id: String = UUID.randomUUID().toString(),
    var companyId: String = "",
    var brandId: String? = null,
    var name: String = "",
    var code: String = "",
    var address: String? = null,
    var phone: String? = null,
    var openTime: String = "10:00",
    var closeTime: String = "22:00",
    var businessDayCloseTime: String = "02:00",
    var taxRate: BigDecimal = BigDecimal("7.00"),
    var serviceChargeRate: BigDecimal = BigDecimal("10.00"),
    var ipAddress: String? = null,
    var dynDnsHost: String? = null,
    var allowedIpSubnets: List<String> = emptyList(),
    var activationCode: String? = null,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    var createdBy: String? = null,
    var updatedAt: Instant = Instant.now(),
    var updatedBy: String? = null,
    var version: Long = 0
)

class Device(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var deviceName: String = "",
    var deviceCode: String = "",
    var deviceType: String = "POS_MAIN", // POS_MAIN, POS_TABLET, KITCHEN_DISPLAY
    var appVersion: String? = null,
    var lastSyncTime: Instant? = null,
    var status: String = "ACTIVE",
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    var createdBy: String? = null,
    var updatedAt: Instant = Instant.now(),
    var updatedBy: String? = null,
    var version: Long = 0
)

data class CompanyCreateDto(
    val name: String = "",
    val taxId: String? = null
)

data class BrandCreateDto(
    val companyId: String = "",
    val name: String = "",
    val code: String = "",
    val logoUrl: String? = null,
    val description: String? = null
)

data class BranchCreateDto(
    val companyId: String = "",
    val brandId: String? = null,
    val name: String = "",
    val code: String = "",
    val address: String? = null,
    val phone: String? = null,
    val openTime: String = "10:00",
    val closeTime: String = "22:00",
    val businessDayCloseTime: String = "02:00",
    val taxRate: BigDecimal = BigDecimal("7.00"),
    val serviceChargeRate: BigDecimal = BigDecimal("10.00"),
    val ipAddress: String? = null,
    val dynDnsHost: String? = null,
    val allowedIpSubnets: List<String> = emptyList(),
    val activationCode: String? = null,
    val isActive: Boolean = true
)

data class DeviceRegisterDto(
    val branchId: String = "",
    val deviceName: String = "",
    val deviceCode: String = "",
    val deviceType: String = "POS_MAIN",
    val appVersion: String? = "1.0.0"
)

// ── Device Capabilities ──

enum class DeviceCapability {
    OPEN_TABLE,
    TAKE_ORDER,
    PAY,
    OPEN_SHIFT,
    CLOSE_SHIFT,
    PRINT_RECEIPT,
    CLOSE_BUSINESS_DAY,
    STOCK_ADJUST;

    companion object {
        val EXCLUSIVE_PER_BRANCH = setOf(PAY, CLOSE_BUSINESS_DAY)
    }
}

class DeviceCapabilityEntity(
    val id: String = UUID.randomUUID().toString(),
    var deviceId: String = "",
    var branchId: String = "",
    var capability: DeviceCapability = DeviceCapability.OPEN_TABLE,
    var isActive: Boolean = true,
    var assignedBy: String? = null,
    var assignedAt: Instant = Instant.now()
)

data class AssignCapabilityDto(
    val capabilities: List<DeviceCapability> = emptyList(),
    val assignedBy: String? = null
)

data class RevokeCapabilityDto(
    val capability: DeviceCapability = DeviceCapability.OPEN_TABLE
)

data class DeviceCapabilityResponseDto(
    val id: String,
    val deviceId: String,
    val branchId: String,
    val capability: String,
    val isActive: Boolean,
    val assignedBy: String?,
    val assignedAt: Instant
)

data class BranchCapabilityMapDto(
    val branchId: String,
    val devices: List<DeviceWithCapabilitiesDto>
)

data class DeviceWithCapabilitiesDto(
    val deviceId: String,
    val deviceName: String,
    val deviceCode: String,
    val deviceType: String? = null,
    val status: String? = "ACTIVE",
    val capabilities: List<DeviceCapabilityResponseDto>
)

class DeviceCapabilityAuditLog(
    val id: String = UUID.randomUUID().toString(),
    var deviceId: String = "",
    var branchId: String = "",
    var action: String = "", // ASSIGNED, REVOKED, TRANSFERRED, REPLACED
    var previousCapabilities: String? = null,
    var newCapabilities: String = "",
    var changedBy: String? = null,
    var reason: String? = null,
    var createdAt: Instant = Instant.now()
)

class NavigationSetting(
    val id: String = "default_nav_setting",
    var companyId: String? = null,
    var disabledGroupIds: List<String> = emptyList(),
    var disabledItemPaths: List<String> = emptyList(),
    var updatedAt: Instant = Instant.now(),
    var updatedBy: String? = null
)

data class NavigationSettingUpdateDto(
    val companyId: String? = null,
    val disabledGroupIds: List<String> = emptyList(),
    val disabledItemPaths: List<String> = emptyList()
)

// ── Multi-Branch POS Activation Code Engine ──

class ActivationCode(
    val id: String = UUID.randomUUID().toString(),
    var code: String = "",
    var branchId: String = "",
    var branchName: String = "",
    var branchCode: String = "",
    var deviceCode: String = "POS-01",
    var deviceName: String = "",
    var companyId: String = "",
    var companyName: String = "",
    var status: String = "UNUSED", // UNUSED, ACTIVATED, EXPIRED
    val createdAt: Instant = Instant.now(),
    var expiresAt: Instant = Instant.now().plusSeconds(72 * 3600),
    var activatedAt: Instant? = null,
    var activatedDeviceId: String? = null,
    var createdBy: String? = null
)

data class GenerateActivationCodeDto(
    val branchId: String = "branch-001",
    val branchName: String? = null,
    val branchCode: String? = null,
    val deviceCode: String? = "POS-01",
    val deviceName: String? = null,
    val expiresInHours: Int? = 72
)

data class ActivateDeviceRequestDto(
    val activationCode: String = "",
    val cloudApiUrl: String? = null,
    val appVersion: String? = null
)

data class DeviceIdentityDto(
    val deviceId: String,
    val branchId: String,
    val branchName: String,
    val branchCode: String,
    val companyId: String,
    val companyName: String,
    val deviceName: String,
    val deviceCode: String,
    val activatedAt: Long,
    val activationCode: String,
    val cloudApiUrl: String? = null
)

