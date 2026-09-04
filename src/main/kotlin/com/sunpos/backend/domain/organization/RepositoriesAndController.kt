package com.sunpos.backend.domain.organization

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.Optional

@Repository
class CompanyRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Company>(jdbcTemplate, "companies", Company::class.java)

@Repository
class BrandRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Brand>(jdbcTemplate, "brands", Brand::class.java) {
    fun findByCompanyId(companyId: String): List<Brand> = findByField("companyId", companyId)
    fun findByCode(code: String): Optional<Brand> = findOneByField("code", code)
}

@Repository
class BranchRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Branch>(jdbcTemplate, "branches", Branch::class.java) {
    fun findByCompanyId(companyId: String): List<Branch> = findByField("companyId", companyId)
    fun findByBrandId(brandId: String): List<Branch> = findByField("brandId", brandId)
    fun findByCode(code: String): Optional<Branch> = findOneByField("code", code)
}

@Repository
class DeviceRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Device>(jdbcTemplate, "devices", Device::class.java) {
    fun findByBranchId(branchId: String): List<Device> = findByField("branchId", branchId)
    fun findByDeviceCode(deviceCode: String): Optional<Device> = findOneByField("deviceCode", deviceCode)
}

@Repository
class ActivationCodeRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<ActivationCode>(jdbcTemplate, "activation_codes", ActivationCode::class.java) {
    fun findByCode(code: String): Optional<ActivationCode> = findOneByField("code", code)
    fun findByBranchId(branchId: String): List<ActivationCode> = findByField("branchId", branchId)
}

@Repository
class NavigationSettingRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<NavigationSetting>(jdbcTemplate, "navigation_settings", NavigationSetting::class.java)

@RestController
@RequestMapping("/api/v1/organization")
class OrganizationController(
    private val companyRepository: CompanyRepository,
    private val brandRepository: BrandRepository,
    private val branchRepository: BranchRepository,
    private val deviceRepository: DeviceRepository,
    private val activationCodeRepository: ActivationCodeRepository,
    private val dataSeeder: com.sunpos.backend.config.DataSeeder? = null
) {

    @PostMapping("/seed-mock-data")
    fun triggerSeedData(@RequestParam(defaultValue = "false") force: Boolean): ApiResponse<String> {
        dataSeeder?.seedMasterDataIfEmpty(force)
        return ApiResponse.success("Seed completed", "Database mockup seed requested")
    }

    @GetMapping("/companies")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun getAllCompanies(): ApiResponse<List<Company>> {
        return ApiResponse.success(companyRepository.findAll())
    }

    @PostMapping("/companies")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createCompany(@RequestBody company: Company): ApiResponse<Company> {
        return ApiResponse.success(companyRepository.save(company), "Company created successfully")
    }

    // ── Brands ──

    @GetMapping("/brands")
    fun getBrands(@RequestParam(required = false) companyId: String?): ApiResponse<List<Brand>> {
        val brands = if (!companyId.isNullOrBlank()) brandRepository.findByCompanyId(companyId) else brandRepository.findAll()
        return ApiResponse.success(brands)
    }

    @PostMapping("/brands")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createBrand(@RequestBody dto: BrandCreateDto): ApiResponse<Brand> {
        if (brandRepository.findByCode(dto.code).isPresent) {
            throw IllegalArgumentException("Brand code '${dto.code}' already exists")
        }

        val brand = Brand(
            companyId = dto.companyId,
            name = dto.name,
            code = dto.code,
            logoUrl = dto.logoUrl,
            description = dto.description
        )
        return ApiResponse.success(brandRepository.save(brand), "Brand created successfully")
    }

    @PutMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun updateBrand(@PathVariable id: String, @RequestBody dto: BrandCreateDto): ApiResponse<Brand> {
        val brand = brandRepository.findById(id).orElseThrow { IllegalArgumentException("Brand not found") }
        brand.name = dto.name
        brand.logoUrl = dto.logoUrl
        brand.description = dto.description
        brand.updatedAt = Instant.now()
        return ApiResponse.success(brandRepository.save(brand), "Brand updated successfully")
    }

    @DeleteMapping("/brands/{id}")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteBrand(@PathVariable id: String): ApiResponse<Boolean> {
        val brand = brandRepository.findById(id).orElseThrow { IllegalArgumentException("Brand not found") }
        brand.isActive = false
        brand.updatedAt = Instant.now()
        brandRepository.save(brand)
        return ApiResponse.success(true, "Brand deleted successfully")
    }

    // ── Branches ──

    @GetMapping("/branches")
    fun getBranches(
        @RequestParam(required = false) companyId: String?,
        @RequestParam(required = false) brandId: String?
    ): ApiResponse<List<Branch>> {
        val branches = when {
            !brandId.isNullOrBlank() -> branchRepository.findByBrandId(brandId)
            !companyId.isNullOrBlank() -> branchRepository.findByCompanyId(companyId)
            else -> branchRepository.findAll()
        }
        return ApiResponse.success(branches)
    }

    @PostMapping("/branches")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun createBranch(@RequestBody dto: BranchCreateDto): ApiResponse<Branch> {
        if (branchRepository.findByCode(dto.code).isPresent) {
            throw IllegalArgumentException("Branch code '${dto.code}' already exists")
        }

        val branch = Branch(
            companyId = dto.companyId,
            brandId = dto.brandId,
            name = dto.name,
            code = dto.code,
            address = dto.address,
            phone = dto.phone,
            openTime = dto.openTime,
            closeTime = dto.closeTime,
            businessDayCloseTime = dto.businessDayCloseTime,
            taxRate = dto.taxRate,
            serviceChargeRate = dto.serviceChargeRate,
            ipAddress = dto.ipAddress,
            dynDnsHost = dto.dynDnsHost,
            allowedIpSubnets = dto.allowedIpSubnets,
            activationCode = dto.activationCode,
            isActive = dto.isActive
        )
        val saved = branchRepository.save(branch)

        // If activationCode is supplied, ensure it exists in activation_codes collection
        if (!dto.activationCode.isNullOrBlank()) {
            val code = dto.activationCode.trim()
            if (!activationCodeRepository.findByCode(code).isPresent) {
                activationCodeRepository.save(
                    ActivationCode(
                        code = code,
                        branchId = saved.id,
                        branchName = saved.name,
                        branchCode = saved.code,
                        deviceCode = "POS-01",
                        deviceName = "POS Terminal (POS-01)",
                        companyId = saved.companyId,
                        companyName = "SunPOS Restaurant Group Co., Ltd.",
                        status = "UNUSED",
                        createdAt = Instant.now(),
                        expiresAt = Instant.now().plusSeconds(72 * 3600)
                    )
                )
            }
        }

        return ApiResponse.success(saved, "Branch created successfully")
    }

    @PutMapping("/branches/{id}")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun updateBranch(@PathVariable id: String, @RequestBody dto: BranchCreateDto): ApiResponse<Branch> {
        val branch = branchRepository.findById(id).orElseThrow { IllegalArgumentException("Branch not found") }
        branch.name = dto.name
        if (dto.code.isNotBlank()) branch.code = dto.code
        branch.brandId = dto.brandId
        branch.address = dto.address
        branch.phone = dto.phone
        branch.openTime = dto.openTime
        branch.closeTime = dto.closeTime
        branch.businessDayCloseTime = dto.businessDayCloseTime
        branch.taxRate = dto.taxRate
        branch.serviceChargeRate = dto.serviceChargeRate
        branch.ipAddress = dto.ipAddress
        branch.dynDnsHost = dto.dynDnsHost
        branch.allowedIpSubnets = dto.allowedIpSubnets
        if (!dto.activationCode.isNullOrBlank()) {
            branch.activationCode = dto.activationCode
            val code = dto.activationCode.trim()
            if (!activationCodeRepository.findByCode(code).isPresent) {
                activationCodeRepository.save(
                    ActivationCode(
                        code = code,
                        branchId = branch.id,
                        branchName = branch.name,
                        branchCode = branch.code,
                        deviceCode = "POS-01",
                        deviceName = "POS Terminal (POS-01)",
                        companyId = branch.companyId,
                        companyName = "SunPOS Restaurant Group Co., Ltd.",
                        status = "UNUSED",
                        createdAt = Instant.now(),
                        expiresAt = Instant.now().plusSeconds(72 * 3600)
                    )
                )
            }
        }
        branch.isActive = dto.isActive
        branch.updatedAt = Instant.now()

        return ApiResponse.success(branchRepository.save(branch), "Branch updated successfully")
    }

    /**
     * Resolve current branch by caller's IP / DynDNS strictly matching database configuration.
     */
    @GetMapping("/branches/resolve-current")
    fun resolveCurrentBranch(
        httpRequest: jakarta.servlet.http.HttpServletRequest,
        @RequestParam(required = false) requestedBranchId: String?
    ): ApiResponse<Map<String, Any?>> {
        val clientIp = httpRequest.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: httpRequest.getHeader("X-Real-IP")
            ?: httpRequest.remoteAddr
            ?: "127.0.0.1"

        val allBranches = branchRepository.findAll()

        // Match branch strictly by configured IP Address or DynDNS hostname
        val matchedBranch = allBranches.find { b ->
            if (!b.ipAddress.isNullOrBlank() && b.ipAddress == clientIp) return@find true
            if (b.allowedIpSubnets.contains(clientIp)) return@find true
            if (!b.dynDnsHost.isNullOrBlank()) {
                try {
                    val resolvedIps = java.net.InetAddress.getAllByName(b.dynDnsHost).map { it.hostAddress }
                    if (resolvedIps.contains(clientIp)) return@find true
                } catch (_: Exception) {}
            }
            false
        } ?: if (!requestedBranchId.isNullOrBlank()) {
            allBranches.find { it.id == requestedBranchId } ?: allBranches.firstOrNull()
        } else {
            allBranches.firstOrNull()
        }

        return ApiResponse.success(
            mapOf(
                "clientIp" to clientIp,
                "resolvedBranchId" to matchedBranch?.id,
                "resolvedBranchName" to matchedBranch?.name,
                "resolvedBranchCode" to matchedBranch?.code,
                "isMatchedByNetwork" to (matchedBranch != null && matchedBranch.ipAddress == clientIp),
                "availableBranchesCount" to allBranches.size
            )
        )
    }

    // ── Devices ──

    @GetMapping("/devices")
    fun getDevices(@RequestParam(required = false) branchId: String?): ApiResponse<List<Device>> {
        val devices = if (!branchId.isNullOrBlank()) deviceRepository.findByBranchId(branchId) else deviceRepository.findAll()
        return ApiResponse.success(devices)
    }

    @PostMapping("/devices")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun registerDevice(@RequestBody dto: DeviceRegisterDto): ApiResponse<Device> {
        if (deviceRepository.findByDeviceCode(dto.deviceCode).isPresent) {
            throw IllegalArgumentException("Device code '${dto.deviceCode}' already registered")
        }

        val device = Device(
            branchId = dto.branchId,
            deviceName = dto.deviceName,
            deviceCode = dto.deviceCode,
            deviceType = dto.deviceType,
            appVersion = dto.appVersion,
            lastSyncTime = Instant.now()
        )
        return ApiResponse.success(deviceRepository.save(device), "Device registered successfully")
    }

    // ── Multi-Branch POS Activation Code Engine ──

    @PostMapping("/activation-codes/generate", "/devices/generate-activation-code")
    fun generateActivationCode(@RequestBody(required = false) dto: GenerateActivationCodeDto?): ApiResponse<ActivationCode> {
        val req = dto ?: GenerateActivationCodeDto()
        val branchId = if (req.branchId.isNotBlank()) req.branchId else "branch-001"

        val branch = branchRepository.findById(branchId).orElse(null)
        val branchName = req.branchName ?: branch?.name ?: when (branchId) {
            "branch-001" -> "Sukhumvit Main Branch (สุขุมวิท)"
            "branch-002" -> "Siam Paragon Branch (สยามพารากอน)"
            "branch-003" -> "Phuket Old Town Branch (ภูเก็ต)"
            else -> "สาขา $branchId"
        }
        val branchCode = req.branchCode ?: branch?.code ?: when (branchId) {
            "branch-001" -> "BR-01"
            "branch-002" -> "BR-02"
            "branch-003" -> "BR-03"
            else -> branchId.uppercase()
        }

        val deviceCode = if (!req.deviceCode.isNullOrBlank()) req.deviceCode else "POS-01"
        val deviceName = if (!req.deviceName.isNullOrBlank()) req.deviceName else "POS Terminal ($deviceCode)"

        val charset = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val randomSuffix = (1..4).map { charset.random() }.joinToString("")
        val cleanBranchCode = branchCode.replace("-", "").take(4).uppercase()
        val generatedCode = "SUN-$cleanBranchCode-$randomSuffix"

        val expiresInHours = req.expiresInHours ?: 72
        val expiresAt = Instant.now().plusSeconds(expiresInHours.toLong() * 3600)

        val activationRecord = ActivationCode(
            code = generatedCode,
            branchId = branchId,
            branchName = branchName,
            branchCode = branchCode,
            deviceCode = deviceCode,
            deviceName = deviceName,
            companyId = branch?.companyId ?: "comp-001",
            companyName = "SunPOS Restaurant Group Co., Ltd.",
            status = "UNUSED",
            createdAt = Instant.now(),
            expiresAt = expiresAt
        )

        val saved = activationCodeRepository.save(activationRecord)
        return ApiResponse.success(saved, "สร้างรหัสเปิดใช้งานเครื่องสำหรับ '$branchName' สำเร็จ")
    }

    @GetMapping("/activation-codes")
    fun getActivationCodes(@RequestParam(required = false) branchId: String?): ApiResponse<List<ActivationCode>> {
        val list = if (!branchId.isNullOrBlank()) {
            activationCodeRepository.findByBranchId(branchId)
        } else {
            activationCodeRepository.findAll()
        }
        return ApiResponse.success(list.sortedByDescending { it.createdAt })
    }

    @PostMapping("/devices/activate")
    fun activateDevice(@RequestBody dto: ActivateDeviceRequestDto): ApiResponse<DeviceIdentityDto> {
        val code = dto.activationCode.trim()
        if (code.isBlank()) {
            throw IllegalArgumentException("กรุณาระบุรหัส Activation Code")
        }

        val upperCode = code.uppercase()
        val now = Instant.now()

        // 1. Check in activation_codes repository
        val optRecord = activationCodeRepository.findByCode(code).or { activationCodeRepository.findByCode(upperCode) }

        val branchId: String
        val branchName: String
        val branchCode: String
        val deviceCode: String
        val deviceName: String
        val companyId: String
        val companyName: String

        if (optRecord.isPresent) {
            val record = optRecord.get()
            if (record.status == "ACTIVATED") {
                throw IllegalArgumentException("รหัส Activation Code '$code' นี้ถูกใช้งานไปแล้ว")
            }
            if (record.expiresAt.isBefore(now)) {
                throw IllegalArgumentException("รหัส Activation Code '$code' หมดอายุแล้ว กรุณาสร้างรหัสใหม่")
            }

            branchId = record.branchId
            branchName = record.branchName
            branchCode = record.branchCode
            deviceCode = record.deviceCode
            deviceName = record.deviceName
            companyId = if (record.companyId.isNotBlank()) record.companyId else "comp-001"
            companyName = if (record.companyName.isNotBlank()) record.companyName else "SunPOS Restaurant Group Co., Ltd."

            record.status = "ACTIVATED"
            record.activatedAt = now
            record.activatedDeviceId = "pos-$branchId-${deviceCode.lowercase()}"
            activationCodeRepository.save(record)
        } else if (upperCode.startsWith("DEV-")) {
            val trimmed = code.removePrefix("DEV-").removePrefix("dev-")
            val posIdx = trimmed.indexOf("-POS", ignoreCase = true)
            if (posIdx != -1) {
                branchId = trimmed.substring(0, posIdx)
                deviceCode = trimmed.substring(posIdx + 1)
            } else {
                val parts = trimmed.split("-")
                branchId = if (parts.isNotEmpty()) parts[0] else "branch-001"
                deviceCode = if (parts.size >= 2) parts[1] else "POS-01"
            }
            branchCode = branchId.uppercase()
            branchName = when (branchId) {
                "branch-001", "BR-01", "BR01" -> "Sukhumvit Main Branch (สุขุมวิท)"
                "branch-002", "BR-02", "BR02" -> "Siam Paragon Branch (สยามพารากอน)"
                "branch-003", "BR-03", "BR03" -> "Phuket Old Town Branch (ภูเก็ต)"
                else -> "สาขา $branchId"
            }
            deviceName = "POS Terminal ($deviceCode)"
            companyId = "comp-001"
            companyName = "SunPOS Restaurant Group Co., Ltd."
        } else {
            // Check in branchRepository for activationCode
            val branchByCode = branchRepository.findAll().firstOrNull {
                it.activationCode?.trim()?.equals(code, ignoreCase = true) == true
            }
            if (branchByCode != null) {
                branchId = branchByCode.id
                branchName = branchByCode.name
                branchCode = branchByCode.code
                deviceCode = "POS-01"
                deviceName = "Main POS Terminal (${branchByCode.name})"
                companyId = "comp-001"
                companyName = "SunPOS Restaurant Group Co., Ltd."
            } else {
                throw IllegalArgumentException("รหัสเปิดใช้งาน '$code' ไม่ถูกต้อง หรือไม่ตรงกับสาขาใดในระบบ กรุณาตรวจสอบรหัสจากระบบ Backoffice")
            }
        }

        val generatedDeviceId = "pos-$branchId-${deviceCode.lowercase()}-${now.toEpochMilli() % 10000}"

        // Save or update device in DeviceRepository
        val existingDevice = deviceRepository.findByDeviceCode(deviceCode).orElse(null)
        if (existingDevice != null) {
            existingDevice.branchId = branchId
            existingDevice.deviceName = deviceName
            existingDevice.lastSyncTime = now
            existingDevice.status = "ACTIVE"
            existingDevice.isActive = true
            existingDevice.updatedAt = now
            deviceRepository.save(existingDevice)
        } else {
            deviceRepository.save(
                Device(
                    id = generatedDeviceId,
                    branchId = branchId,
                    deviceName = deviceName,
                    deviceCode = deviceCode,
                    deviceType = "POS_MAIN",
                    appVersion = dto.appVersion ?: "1.0.0",
                    lastSyncTime = now,
                    status = "ACTIVE",
                    isActive = true
                )
            )
        }

        val identity = DeviceIdentityDto(
            deviceId = generatedDeviceId,
            branchId = branchId,
            branchName = branchName,
            branchCode = branchCode,
            companyId = companyId,
            companyName = companyName,
            deviceName = deviceName,
            deviceCode = deviceCode,
            activatedAt = now.toEpochMilli(),
            activationCode = code,
            cloudApiUrl = dto.cloudApiUrl
        )

        return ApiResponse.success(identity, "เปิดใช้งานเครื่อง POS สำหรับ '$branchName' สำเร็จ")
    }

    @GetMapping("/devices/validate")
    fun validateDevice(
        @RequestParam activationCode: String,
        @RequestParam(required = false) branchId: String?,
        @RequestParam(required = false) deviceId: String?
    ): ApiResponse<DeviceValidationDto> {
        val cleanCode = activationCode.trim()
        if (cleanCode.isBlank()) {
            return ApiResponse.success(DeviceValidationDto(isValid = false, reason = "รหัส Activation Code ว่างเปล่า"))
        }

        val upperCode = cleanCode.uppercase()

        // 1. Check branchRepository
        val branchByCode = branchRepository.findAll().firstOrNull {
            it.activationCode?.trim()?.equals(cleanCode, ignoreCase = true) == true
        }

        // 2. Check activationCodeRepository
        val optRecord = activationCodeRepository.findByCode(cleanCode)
            .or { activationCodeRepository.findByCode(upperCode) }

        val matchedBranch = branchByCode ?: if (optRecord.isPresent) {
            branchRepository.findById(optRecord.get().branchId).orElse(null)
        } else if (upperCode.startsWith("DEV-")) {
            Branch(id = "branch-001", name = "Sukhumvit Main Branch (สุขุมวิท)", code = "BR-01")
        } else null

        if (matchedBranch == null) {
            return ApiResponse.success(
                DeviceValidationDto(
                    isValid = false,
                    reason = "รหัสเปิดใช้งาน '$cleanCode' ไม่ตรงกับสาขาใดในระบบแล้ว กรุณาเปิดใช้งานใหม่"
                )
            )
        }

        // If branchId was provided, verify it still matches
        if (!branchId.isNullOrBlank() && matchedBranch.id != branchId) {
            return ApiResponse.success(
                DeviceValidationDto(
                    isValid = false,
                    reason = "รหัสเปิดใช้งานถูกเปลี่ยนไปใช้กับสาขาอื่นแล้ว (เครื่อง: $branchId, ปัจจุบัน: ${matchedBranch.name})"
                )
            )
        }

        return ApiResponse.success(
            DeviceValidationDto(
                isValid = true,
                branchId = matchedBranch.id,
                branchName = matchedBranch.name,
                branchCode = matchedBranch.code,
                deviceCode = "POS-01"
            )
        )
    }
}

// ── Device Capabilities ──

@Repository
class DeviceCapabilityRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<DeviceCapabilityEntity>(jdbcTemplate, "device_capabilities", DeviceCapabilityEntity::class.java) {
    fun findByDeviceId(deviceId: String): List<DeviceCapabilityEntity> = findByField("deviceId", deviceId)
    fun findByDeviceIdAndIsActiveTrue(deviceId: String): List<DeviceCapabilityEntity> = findByFields(mapOf("deviceId" to deviceId, "isActive" to true))
    fun findByBranchId(branchId: String): List<DeviceCapabilityEntity> = findByField("branchId", branchId)
    fun findByBranchIdAndCapabilityAndIsActiveTrue(branchId: String, capability: DeviceCapability): List<DeviceCapabilityEntity> =
        findByFields(mapOf("branchId" to branchId, "capability" to capability, "isActive" to true))
    fun findByDeviceIdAndCapability(deviceId: String, capability: DeviceCapability): Optional<DeviceCapabilityEntity> {
        val list = findByFields(mapOf("deviceId" to deviceId, "capability" to capability))
        return Optional.ofNullable(list.firstOrNull())
    }
}

@Repository
class DeviceCapabilityAuditLogRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<DeviceCapabilityAuditLog>(jdbcTemplate, "device_capability_audit_logs", DeviceCapabilityAuditLog::class.java) {
    fun findByDeviceIdOrderByCreatedAtDesc(deviceId: String): List<DeviceCapabilityAuditLog> =
        findByField("deviceId", deviceId).sortedByDescending { it.createdAt }
    fun findByBranchIdOrderByCreatedAtDesc(branchId: String): List<DeviceCapabilityAuditLog> =
        findByField("branchId", branchId).sortedByDescending { it.createdAt }
}

@org.springframework.stereotype.Service
class DeviceCapabilityService(
    private val capabilityRepository: DeviceCapabilityRepository,
    private val deviceRepository: DeviceRepository,
    private val auditLogRepository: DeviceCapabilityAuditLogRepository
) {
    /**
     * Atomically assigns/replaces capabilities for a device.
     * For exclusive capabilities (PAY, CLOSE_BUSINESS_DAY):
     * If another device in the same branch currently holds the exclusive capability,
     * it is automatically deactivated and transferred to this device in the SAME database transaction.
     * Records an audit log entry for the operation.
     */
    @org.springframework.transaction.annotation.Transactional
    fun replaceCapabilities(deviceId: String, dto: AssignCapabilityDto): List<DeviceCapabilityResponseDto> {
        val targetDevice = deviceRepository.findById(deviceId)
            .orElseThrow { NoSuchElementException("Device '$deviceId' not found") }

        val previousCaps = capabilityRepository.findByDeviceIdAndIsActiveTrue(deviceId).map { it.capability.name }

        // 1. Handle Exclusive Capabilities Auto-Transfer (PAY, CLOSE_BUSINESS_DAY)
        for (exclusiveCap in DeviceCapability.EXCLUSIVE_PER_BRANCH) {
            if (exclusiveCap in dto.capabilities) {
                val existingHolders = capabilityRepository.findByBranchIdAndCapabilityAndIsActiveTrue(targetDevice.branchId, exclusiveCap)
                val otherHolders = existingHolders.filter { it.deviceId != deviceId }
                for (other in otherHolders) {
                    other.isActive = false
                    capabilityRepository.save(other)

                    // Audit transfer from old device
                    auditLogRepository.save(
                        DeviceCapabilityAuditLog(
                            deviceId = other.deviceId,
                            branchId = targetDevice.branchId,
                            action = "TRANSFERRED",
                            previousCapabilities = exclusiveCap.name,
                            newCapabilities = "",
                            changedBy = dto.assignedBy,
                            reason = "Capability '$exclusiveCap' transferred to device '${targetDevice.deviceName}' ($deviceId)"
                        )
                    )
                }
            }
        }

        // 2. Deactivate capabilities on target device that are NOT in the new list
        val currentCaps = capabilityRepository.findByDeviceId(deviceId)
        for (cap in currentCaps) {
            if (cap.capability !in dto.capabilities && cap.isActive) {
                cap.isActive = false
                capabilityRepository.save(cap)
            }
        }

        // 3. Activate or insert new capabilities
        for (cap in dto.capabilities) {
            val existing = capabilityRepository.findByDeviceIdAndCapability(deviceId, cap)
            if (existing.isPresent) {
                val entity = existing.get()
                entity.isActive = true
                capabilityRepository.save(entity)
            } else {
                capabilityRepository.save(
                    DeviceCapabilityEntity(
                        deviceId = deviceId,
                        branchId = targetDevice.branchId,
                        capability = cap,
                        isActive = true,
                        assignedBy = dto.assignedBy
                    )
                )
            }
        }

        val updatedCaps = getDeviceCapabilities(deviceId)

        // 4. Record Audit Log
        auditLogRepository.save(
            DeviceCapabilityAuditLog(
                deviceId = deviceId,
                branchId = targetDevice.branchId,
                action = "REPLACED",
                previousCapabilities = previousCaps.joinToString(", "),
                newCapabilities = dto.capabilities.joinToString(", ") { it.name },
                changedBy = dto.assignedBy,
                reason = "Device capabilities updated"
            )
        )

        return updatedCaps
    }

    @org.springframework.transaction.annotation.Transactional
    fun revokeCapability(deviceId: String, capability: DeviceCapability, revokedBy: String? = null) {
        val device = deviceRepository.findById(deviceId)
            .orElseThrow { NoSuchElementException("Device '$deviceId' not found") }

        val existing = capabilityRepository.findByDeviceIdAndCapability(deviceId, capability)
        if (existing.isPresent && existing.get().isActive) {
            val entity = existing.get()
            entity.isActive = false
            capabilityRepository.save(entity)

            auditLogRepository.save(
                DeviceCapabilityAuditLog(
                    deviceId = deviceId,
                    branchId = device.branchId,
                    action = "REVOKED",
                    previousCapabilities = capability.name,
                    newCapabilities = "",
                    changedBy = revokedBy,
                    reason = "Capability '$capability' revoked"
                )
            )
        }
    }

    fun getDeviceCapabilities(deviceId: String): List<DeviceCapabilityResponseDto> {
        return capabilityRepository.findByDeviceIdAndIsActiveTrue(deviceId).map { it.toDto() }
    }

    fun getBranchDevicesWithCapabilities(branchId: String): List<DeviceWithCapabilitiesDto> {
        val devices = deviceRepository.findByBranchId(branchId)
        val allCaps = capabilityRepository.findByBranchId(branchId)
        val deviceCapMap = allCaps.filter { it.isActive }.groupBy { it.deviceId }

        return devices.map { device ->
            DeviceWithCapabilitiesDto(
                deviceId = device.id,
                deviceName = device.deviceName,
                deviceCode = device.deviceCode,
                deviceType = device.deviceType,
                status = device.status,
                capabilities = (deviceCapMap[device.id] ?: emptyList()).map { it.toDto() }
            )
        }
    }

    fun getBranchCapabilityMap(branchId: String): BranchCapabilityMapDto {
        return BranchCapabilityMapDto(
            branchId = branchId,
            devices = getBranchDevicesWithCapabilities(branchId)
        )
    }

    fun getAuditLogs(deviceId: String?, branchId: String?): List<DeviceCapabilityAuditLog> {
        return when {
            deviceId != null -> auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId)
            branchId != null -> auditLogRepository.findByBranchIdOrderByCreatedAtDesc(branchId)
            else -> auditLogRepository.findAll().sortedByDescending { it.createdAt }
        }
    }

    private fun DeviceCapabilityEntity.toDto() = DeviceCapabilityResponseDto(
        id = this.id,
        deviceId = this.deviceId,
        branchId = this.branchId,
        capability = this.capability.name,
        isActive = this.isActive,
        assignedBy = this.assignedBy,
        assignedAt = this.assignedAt
    )
}

@RestController
@RequestMapping
class DeviceCapabilityApiController(
    private val capabilityService: DeviceCapabilityService
) {
    // ── GET /api/v1/devices/{deviceId}/capabilities & /api/v1/organization/devices/{deviceId}/capabilities ──
    @GetMapping(value = ["/api/v1/devices/{deviceId}/capabilities", "/api/v1/organization/devices/{deviceId}/capabilities"])
    fun getCapabilities(@PathVariable deviceId: String): ApiResponse<List<DeviceCapabilityResponseDto>> {
        return ApiResponse.success(capabilityService.getDeviceCapabilities(deviceId))
    }

    // ── PUT /api/v1/devices/{deviceId}/capabilities & /api/v1/organization/devices/{deviceId}/capabilities ──
    @PutMapping(value = ["/api/v1/devices/{deviceId}/capabilities", "/api/v1/organization/devices/{deviceId}/capabilities"])
    @PreAuthorize("hasAuthority('DEVICE_CAPABILITY_MANAGE') or hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun assignCapabilities(
        @PathVariable deviceId: String,
        @RequestBody dto: AssignCapabilityDto
    ): ApiResponse<List<DeviceCapabilityResponseDto>> {
        val result = capabilityService.replaceCapabilities(deviceId, dto)
        return ApiResponse.success(result, "Device capabilities updated successfully")
    }

    // ── DELETE /api/v1/devices/{deviceId}/capabilities/{capability} ──
    @DeleteMapping(value = ["/api/v1/devices/{deviceId}/capabilities/{capability}", "/api/v1/organization/devices/{deviceId}/capabilities/{capability}"])
    @PreAuthorize("hasAuthority('DEVICE_CAPABILITY_MANAGE') or hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun revokeCapability(
        @PathVariable deviceId: String,
        @PathVariable capability: DeviceCapability
    ): ApiResponse<Boolean> {
        capabilityService.revokeCapability(deviceId, capability)
        return ApiResponse.success(true, "Capability '$capability' revoked successfully")
    }

    // ── GET /api/v1/branches/{branchId}/devices & /api/v1/organization/branches/{branchId}/devices ──
    @GetMapping(value = ["/api/v1/branches/{branchId}/devices", "/api/v1/organization/branches/{branchId}/devices"])
    fun getBranchDevices(@PathVariable branchId: String): ApiResponse<List<DeviceWithCapabilitiesDto>> {
        return ApiResponse.success(capabilityService.getBranchDevicesWithCapabilities(branchId))
    }

    // ── GET /api/v1/organization/branches/{branchId}/capability-map ──
    @GetMapping("/api/v1/organization/branches/{branchId}/capability-map")
    fun getCapabilityMap(@PathVariable branchId: String): ApiResponse<BranchCapabilityMapDto> {
        return ApiResponse.success(capabilityService.getBranchCapabilityMap(branchId))
    }

    // ── GET /api/v1/organization/device-capabilities/audit-logs ──
    @GetMapping("/api/v1/organization/device-capabilities/audit-logs")
    @PreAuthorize("hasAuthority('DEVICE_CAPABILITY_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun getAuditLogs(
        @RequestParam(required = false) deviceId: String?,
        @RequestParam(required = false) branchId: String?
    ): ApiResponse<List<DeviceCapabilityAuditLog>> {
        return ApiResponse.success(capabilityService.getAuditLogs(deviceId, branchId))
    }
}

@RestController
class NavigationConfigController(
    private val navigationSettingRepository: NavigationSettingRepository
) {
    @GetMapping(value = ["/api/v1/navigation/config", "/api/v1/organization/navigation/config"])
    fun getNavigationConfig(@RequestParam(required = false) companyId: String?): ApiResponse<NavigationSetting> {
        val setting = navigationSettingRepository.findAll().firstOrNull() ?: NavigationSetting()
        return ApiResponse.success(setting)
    }

    @PutMapping(value = ["/api/v1/navigation/config", "/api/v1/organization/navigation/config"])
    @PreAuthorize("hasAuthority('SYSTEM_CONFIG') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun updateNavigationConfig(
        @RequestBody dto: NavigationSettingUpdateDto,
        authentication: org.springframework.security.core.Authentication?
    ): ApiResponse<NavigationSetting> {
        val existing = navigationSettingRepository.findAll().firstOrNull() ?: NavigationSetting()
        existing.companyId = dto.companyId ?: existing.companyId
        existing.disabledGroupIds = dto.disabledGroupIds
        existing.disabledItemPaths = dto.disabledItemPaths
        existing.updatedAt = Instant.now()
        existing.updatedBy = authentication?.name ?: "admin"
        val saved = navigationSettingRepository.save(existing)
        return ApiResponse.success(saved, "Navigation configuration updated in database successfully")
    }
}

