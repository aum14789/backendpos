package com.sunpos.backend.domain.identity

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.config.JwtTokenProvider
import com.sunpos.backend.domain.organization.DeviceCapabilityRepository
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class PinAuthController(
    private val userService: UserService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val deviceCapabilityRepository: DeviceCapabilityRepository? = null
) {

    private val failedAttempts = java.util.concurrent.ConcurrentHashMap<String, Pair<Int, Long>>()

    /**
     * Public PIN login endpoint with brute-force rate-limiting protection.
     * Scoped to deviceId / branchId / username to avoid full-table scans.
     * Returns JWT token, user permissions, and cached device capabilities.
     */
    @PostMapping("/pin-login")
    fun loginWithPin(
        @RequestBody request: PinLoginRequest,
        httpRequest: jakarta.servlet.http.HttpServletRequest
    ): ApiResponse<Map<String, Any>> {
        val clientIp = httpRequest.remoteAddr ?: "unknown"
        val now = System.currentTimeMillis()
        val attemptInfo = failedAttempts[clientIp]
        if (attemptInfo != null && attemptInfo.first >= 5 && (now - attemptInfo.second) < 60_000) {
            val remainingSec = (60_000 - (now - attemptInfo.second)) / 1000
            throw IllegalArgumentException("คุณใส่ PIN ผิดเกิน 5 ครั้ง กรุณารอ $remainingSec วินาที (Rate Limit Lockout)")
        }

        try {
            val userDto = userService.authenticatePin(request)
            failedAttempts.remove(clientIp)
            val allAuthorities = (userDto.roles + userDto.permissions).distinct()
            val token = jwtTokenProvider.generateToken(userDto.username, allAuthorities)

            val capabilities = if (!request.deviceId.isNullOrBlank() && deviceCapabilityRepository != null) {
                deviceCapabilityRepository.findByDeviceIdAndIsActiveTrue(request.deviceId)
                    .map { it.capability.name }
            } else {
                emptyList()
            }

            return ApiResponse.success(
                mapOf(
                    "token" to token,
                    "user" to userDto,
                    "deviceCapabilities" to capabilities
                ),
                "PIN Login successful"
            )
        } catch (e: Exception) {
            val currentCount = (failedAttempts[clientIp]?.first ?: 0) + 1
            failedAttempts[clientIp] = Pair(currentCount, now)
            throw e
        }
    }

    /**
     * Protected endpoint: Change PIN for authenticated user (Requires valid JWT).
     */
    @PostMapping("/change-pin")
    @PreAuthorize("isAuthenticated()")
    fun changePin(
        @RequestBody request: PinChangeRequest,
        authentication: Authentication
    ): ApiResponse<UserResponseDto> {
        val username = authentication.name
        val user = userService.authenticatePin(PinLoginRequest(pinCode = request.currentPin, username = username))
        val updated = userService.updatePin(user.id, request.newPin)
        return ApiResponse.success(updated, "PIN changed successfully")
    }
}

@RestController
@RequestMapping("/api/v1/identity", "/api/v1")
class IdentityController(
    private val userService: UserService,
    private val roleService: RoleService
) {

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun listUsers(@RequestParam(required = false) companyId: String?): ApiResponse<List<UserResponseDto>> {
        return ApiResponse.success(userService.listUsers(companyId))
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun createUser(@RequestBody dto: UserCreateDto): ApiResponse<UserResponseDto> {
        return ApiResponse.success(userService.createUser(dto), "User created successfully")
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun updateUser(
        @PathVariable id: String,
        @RequestBody dto: UserUpdateDto
    ): ApiResponse<UserResponseDto> {
        return ApiResponse.success(userService.updateUser(id, dto), "User updated successfully")
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteUser(@PathVariable id: String): ApiResponse<Boolean> {
        return ApiResponse.success(userService.deleteUser(id), "User deleted successfully")
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun listRoles(): ApiResponse<List<Map<String, Any>>> {
        return ApiResponse.success(roleService.listRoles())
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createRole(@RequestBody dto: RoleCreateDto): ApiResponse<Map<String, Any>> {
        return ApiResponse.success(roleService.createRole(dto), "Role created successfully")
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun updateRole(
        @PathVariable id: String,
        @RequestBody dto: RoleUpdateDto
    ): ApiResponse<Map<String, Any>> {
        return ApiResponse.success(roleService.updateRole(id, dto), "Role updated successfully")
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteRole(@PathVariable id: String): ApiResponse<Boolean> {
        return ApiResponse.success(roleService.deleteRole(id), "Role deleted successfully")
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun updateRolePermissions(
        @PathVariable id: String,
        @RequestBody permissions: List<String>
    ): ApiResponse<Map<String, Any>> {
        return ApiResponse.success(roleService.updateRolePermissions(id, permissions), "Role permissions updated successfully")
    }

    @PutMapping("/roles/{id}/users")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun assignUsersToRole(
        @PathVariable id: String,
        @RequestBody userIds: List<String>
    ): ApiResponse<Map<String, Any>> {
        return ApiResponse.success(roleService.assignUsersToRole(id, userIds), "Users assigned to role successfully")
    }

    @GetMapping("/permissions", "/roles/permissions")
    @PreAuthorize("hasAuthority('USER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun listPermissions(): ApiResponse<List<Permission>> {
        return ApiResponse.success(roleService.listPermissions())
    }
}
