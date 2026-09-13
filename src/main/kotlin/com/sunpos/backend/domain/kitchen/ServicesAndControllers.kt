package com.sunpos.backend.domain.kitchen

import com.sunpos.backend.common.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class UpsertKitchenStationRequest(
    val id: String? = null,
    val brandId: String? = null,
    val branchId: String? = null,
    val code: String? = null,
    val name: String,
    val description: String? = null,
    val sortOrder: Int? = 0,
    val isActive: Boolean? = true
)

@Service
class KitchenStationService(
    private val repository: KitchenStationRepository
) {
    fun listStations(brandId: String? = null, branchId: String? = null): List<KitchenStation> {
        val all = repository.findAllOrdered()
        return all.filter { station ->
            val matchBrand = brandId.isNullOrBlank() || station.brandId == null || station.brandId == brandId
            val matchBranch = branchId.isNullOrBlank() || station.branchId == null || station.branchId == branchId
            matchBrand && matchBranch
        }
    }

    fun getStation(id: String): KitchenStation? = repository.findById(id).orElse(null)

    @Transactional
    fun createStation(req: UpsertKitchenStationRequest): KitchenStation {
        require(req.name.isNotBlank()) { "ชื่อสเตชันครัวห้ามว่าง" }
        val generatedCode = if (!req.code.isNullOrBlank()) {
            req.code.trim().uppercase().replace("\\s+".toRegex(), "_")
        } else {
            "KS_" + UUID.randomUUID().toString().substring(0, 8).uppercase()
        }

        val existing = repository.findByCode(generatedCode)
        require(existing == null) { "รหัสสเตชันครัว '$generatedCode' มีอยู่ในระบบแล้ว กรุณาใช้รหัสอื่น" }

        val station = KitchenStation(
            id = req.id ?: UUID.randomUUID().toString(),
            brandId = req.brandId,
            branchId = req.branchId,
            code = generatedCode,
            name = req.name.trim(),
            description = req.description?.trim(),
            sortOrder = req.sortOrder ?: 0,
            isActive = req.isActive ?: true
        )
        return repository.save(station)
    }

    @Transactional
    fun updateStation(id: String, req: UpsertKitchenStationRequest): KitchenStation {
        val existing = repository.findById(id).orElseThrow { IllegalArgumentException("ไม่พบสเตชันครัวรหัส $id") }
        require(req.name.isNotBlank()) { "ชื่อสเตชันครัวห้ามว่าง" }

        if (!req.code.isNullOrBlank()) {
            val targetCode = req.code.trim().uppercase().replace("\\s+".toRegex(), "_")
            val codeHolder = repository.findByCode(targetCode)
            if (codeHolder != null && codeHolder.id != id) {
                throw IllegalArgumentException("รหัสสเตชันครัว '$targetCode' มีอยู่ในระบบแล้ว")
            }
            existing.code = targetCode
        }

        existing.name = req.name.trim()
        existing.description = req.description?.trim()
        if (req.sortOrder != null) existing.sortOrder = req.sortOrder
        if (req.isActive != null) existing.isActive = req.isActive
        if (req.brandId != null) existing.brandId = req.brandId
        if (req.branchId != null) existing.branchId = req.branchId

        return repository.save(existing)
    }

    @Transactional
    fun deleteStation(id: String) {
        val existing = repository.findById(id).orElseThrow { IllegalArgumentException("ไม่พบสเตชันครัวรหัส $id") }
        repository.deleteById(existing.id)
    }
}

@RestController
@RequestMapping("/api/v1/kitchen-stations")
class KitchenStationController(
    private val service: KitchenStationService
) {
    @GetMapping
    fun getStations(
        @RequestParam(required = false) brandId: String?,
        @RequestParam(required = false) branchId: String?
    ): ApiResponse<List<KitchenStation>> {
        return ApiResponse.success(service.listStations(brandId, branchId))
    }

    @GetMapping("/{id}")
    fun getStation(@PathVariable id: String): ApiResponse<KitchenStation> {
        val s = service.getStation(id) ?: throw IllegalArgumentException("ไม่พบสเตชันครัวรหัส $id")
        return ApiResponse.success(s)
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('POS_CONFIG_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createStation(@RequestBody req: UpsertKitchenStationRequest): ApiResponse<KitchenStation> {
        return ApiResponse.success(service.createStation(req), "สร้างสเตชันครัวสำเร็จ")
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('POS_CONFIG_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun updateStation(
        @PathVariable id: String,
        @RequestBody req: UpsertKitchenStationRequest
    ): ApiResponse<KitchenStation> {
        return ApiResponse.success(service.updateStation(id, req), "แก้ไขสเตชันครัวสำเร็จ")
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('POS_CONFIG_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteStation(@PathVariable id: String): ApiResponse<String> {
        service.deleteStation(id)
        return ApiResponse.success("ลบสเตชันครัวสำเร็จ", "Deleted successfully")
    }
}
