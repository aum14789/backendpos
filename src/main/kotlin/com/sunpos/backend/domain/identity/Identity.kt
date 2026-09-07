package com.sunpos.backend.domain.identity

import java.io.Serializable
import java.time.Instant
import java.util.UUID

class User(
    val id: String = UUID.randomUUID().toString(),
    var companyId: String = "",
    var username: String = "",
    var passwordHash: String = "",
    var fullName: String = "",
    var email: String? = null,
    var phone: String? = null,
    var pinCode: String? = null, // BCrypt hash
    var assignedModules: List<String> = emptyList(), // ERP Sidebar Modules
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    var createdBy: String? = null,
    var updatedAt: Instant = Instant.now(),
    var updatedBy: String? = null,
    var version: Long = 0
)

class Role(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var description: String? = null,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
)

class Permission(
    val id: String = UUID.randomUUID().toString(),
    var code: String = "",
    var description: String? = null,
    val createdAt: Instant = Instant.now()
)

data class RolePermissionId(
    var roleId: String = "",
    var permissionId: String = ""
) : Serializable

class RolePermission(
    val id: String = UUID.randomUUID().toString(),
    var roleId: String = "",
    var permissionId: String = ""
) {
    constructor(roleId: String, permissionId: String) : this(
        id = "${roleId}_$permissionId",
        roleId = roleId,
        permissionId = permissionId
    )
}

data class UserRoleId(
    var userId: String = "",
    var roleId: String = ""
) : Serializable

class UserRole(
    val id: String = UUID.randomUUID().toString(),
    var userId: String = "",
    var roleId: String = ""
) {
    constructor(userId: String, roleId: String) : this(
        id = "${userId}_$roleId",
        userId = userId,
        roleId = roleId
    )
}

// DTOs
data class UserCreateDto(
    val companyId: String = "",
    val username: String = "",
    val password: String = "",
    val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val pinCode: String? = null,
    val assignedModules: List<String> = emptyList(),
    val roleIds: List<String> = emptyList()
)

data class UserResponseDto(
    val id: String = "",
    val companyId: String = "",
    val username: String = "",
    val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val hasPin: Boolean = false,
    val assignedModules: List<String> = emptyList(),
    val isActive: Boolean = true,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList()
)

data class PinLoginRequest(
    val pinCode: String = "",
    val deviceId: String? = null,
    val branchId: String? = null,
    val username: String? = null
)

data class PinChangeRequest(
    val currentPin: String = "",
    val newPin: String = ""
)

data class UserUpdateDto(
    val fullName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val pinCode: String? = null,
    val password: String? = null,
    val assignedModules: List<String>? = null,
    val roleIds: List<String>? = null,
    val isActive: Boolean? = null
)

data class RoleCreateDto(
    val name: String = "",
    val description: String? = null,
    val permissionIds: List<String> = emptyList()
)

data class RoleUpdateDto(
    val name: String? = null,
    val description: String? = null,
    val permissionIds: List<String>? = null
)
