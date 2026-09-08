package com.sunpos.backend.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import com.sunpos.backend.domain.organization.BranchService

@RestController
@RequestMapping("/api/qrorder")
class QrOrderController(@Autowired private val branchService: BranchService) {

    @GetMapping("/{branchId}/enabled")
    fun getEnabled(@PathVariable branchId: Long): ResponseEntity<Boolean> {
        val branch = branchService.findById(branchId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(branch.isQrOrderEnabled)
    }

    @PostMapping("/{branchId}/enabled")
    @PreAuthorize("hasRole('MANAGER')")
    fun setEnabled(@PathVariable branchId: Long, @RequestBody payload: Map<String, Boolean>): ResponseEntity<Void> {
        val enabled = payload["enabled"] ?: return ResponseEntity.badRequest().build()
        val branch = branchService.findById(branchId) ?: return ResponseEntity.notFound().build()
        branch.isQrOrderEnabled = enabled
        branchService.save(branch)
        return ResponseEntity.ok().build()
    }
}
