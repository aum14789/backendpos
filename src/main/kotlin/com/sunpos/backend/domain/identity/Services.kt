package com.sunpos.backend.domain.identity

import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.organization.BranchRepository
import com.sunpos.backend.domain.organization.DeviceRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional

@Repository
class UserRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<User>(jdbcTemplate, "users", User::class.java) {
    fun findByUsername(username: String): Optional<User> = findOneByField("username", username)
    fun findByUsernameAndIsActiveTrue(username: String): Optional<User> {
        val list = findByFields(mapOf("username" to username, "isActive" to true))
        return Optional.ofNullable(list.firstOrNull())
    }
    fun findByCompanyId(companyId: String): List<User> = findByField("companyId", companyId)
    fun findByCompanyIdAndIsActiveTrue(companyId: String): List<User> = findByFields(mapOf("companyId" to companyId, "isActive" to true))
    fun findByIsActiveTrueAndPinCodeIsNotNull(): List<User> = findAll().filter { it.isActive && !it.pinCode.isNullOrBlank() }
}

@Repository
class RoleRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Role>(jdbcTemplate, "roles", Role::class.java) {
    fun findByName(name: String): Optional<Role> = findOneByField("name", name)
}

@Repository
class PermissionRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Permission>(jdbcTemplate, "permissions", Permission::class.java) {
    fun findByCode(code: String): Optional<Permission> = findOneByField("code", code)
}

@Repository
class RolePermissionRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<RolePermission>(jdbcTemplate, "role_permissions", RolePermission::class.java) {
    fun findByIdRoleId(roleId: String): List<RolePermission> = findByField("roleId", roleId)
    fun deleteByIdRoleId(roleId: String) {
        val list = findByIdRoleId(roleId)
        list.forEach { deleteById(it.id) }
    }
}

@Repository
class UserRoleRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<UserRole>(jdbcTemplate, "user_roles", UserRole::class.java) {
    fun findByIdUserId(userId: String): List<UserRole> = findByField("userId", userId)
    fun deleteByIdUserId(userId: String) {
        val list = findByIdUserId(userId)
        list.forEach { deleteById(it.id) }
    }
    fun findByIdRoleId(roleId: String): List<UserRole> = findByField("roleId", roleId)
    fun deleteByIdRoleId(roleId: String) {
        val list = findByIdRoleId(roleId)
        list.forEach { deleteById(it.id) }
    }
}

@Service
class UserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val userRoleRepository: UserRoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val branchRepository: BranchRepository,
    private val deviceRepository: DeviceRepository,
    private val passwordEncoder: PasswordEncoder
) {

    @Transactional
    fun createUser(dto: UserCreateDto): UserResponseDto {
        if (userRepository.findByUsername(dto.username).isPresent) {
            throw IllegalArgumentException("Username '${dto.username}' already exists")
        }

        // Store PIN strictly as BCrypt hash
        val pinHash = dto.pinCode?.let {
            val cleaned = it.trim()
            if (cleaned.isNotEmpty()) passwordEncoder.encode(cleaned) else null
        }

        val user = User(
            companyId = dto.companyId,
            username = dto.username,
            passwordHash = passwordEncoder.encode(dto.password),
            fullName = dto.fullName,
            email = dto.email,
            phone = dto.phone,
            pinCode = pinHash,
            assignedModules = dto.assignedModules
        )
        val savedUser = userRepository.save(user)

        // Assign Roles
        for (roleId in dto.roleIds) {
            userRoleRepository.save(UserRole(userId = savedUser.id, roleId = roleId))
        }

        return getUserById(savedUser.id)
    }

    @Transactional
    fun updateUser(userId: String, dto: UserUpdateDto): UserResponseDto {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        dto.fullName?.let { user.fullName = it }
        dto.email?.let { user.email = it }
        dto.phone?.let { user.phone = it }
        dto.isActive?.let { user.isActive = it }
        dto.assignedModules?.let { user.assignedModules = it }
        dto.password?.let { if (it.isNotBlank()) user.passwordHash = passwordEncoder.encode(it) }
        dto.pinCode?.let {
            val cleaned = it.trim()
            if (cleaned.isNotEmpty()) user.pinCode = passwordEncoder.encode(cleaned)
        }
        userRepository.save(user)

        dto.roleIds?.let { newRoleIds ->
            userRoleRepository.deleteByIdUserId(userId)
            for (roleId in newRoleIds) {
                userRoleRepository.save(UserRole(userId = userId, roleId = roleId))
            }
        }

        return getUserById(userId)
    }

    @Transactional
    fun deleteUser(userId: String): Boolean {
        userRoleRepository.deleteByIdUserId(userId)
        userRepository.deleteById(userId)
        return true
    }

    @Transactional
    fun updatePin(userId: String, rawPin: String): UserResponseDto {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        val cleaned = rawPin.trim()
        if (cleaned.length < 4 || cleaned.length > 8) {
            throw IllegalArgumentException("PIN must be between 4 and 8 digits")
        }
        user.pinCode = passwordEncoder.encode(cleaned)
        userRepository.save(user)
        return getUserById(user.id)
    }

    fun getUserById(userId: String): UserResponseDto {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }
        val userRoles = userRoleRepository.findByIdUserId(userId)
        val roleIds = userRoles.map { it.roleId }
        val roles = roleRepository.findAllById(roleIds)
        val roleNames = roles.map { it.name }

        val permissionIds = roleIds.flatMap { rId -> rolePermissionRepository.findByIdRoleId(rId).map { it.permissionId } }.distinct()
        val permissions = permissionRepository.findAllById(permissionIds).map { it.code }

        return UserResponseDto(
            id = user.id,
            companyId = user.companyId,
            username = user.username,
            fullName = user.fullName,
            email = user.email,
            phone = user.phone,
            hasPin = !user.pinCode.isNullOrEmpty(),
            assignedModules = user.assignedModules,
            isActive = user.isActive,
            roles = roleNames,
            permissions = permissions
        )
    }

    fun listUsers(companyId: String?): List<UserResponseDto> {
        val users = if (companyId != null) userRepository.findByCompanyId(companyId) else userRepository.findAll()
        return users.map { getUserById(it.id) }
    }

    /**
     * Authenticate Cashier by PIN without loading the entire user database table.
     *
     * Scoping rules:
     *   1. If `username` is provided: target single active user directly.
     *   2. If `deviceId` is provided: target active users within device's company.
     *   3. If `branchId` is provided: target active users within branch's company.
     *   4. Otherwise: target active users having configured PIN codes.
     *
     * Verifies PIN matches strictly using BCrypt `passwordEncoder.matches`.
     */
    @Transactional(readOnly = true)
    fun authenticatePin(request: PinLoginRequest): UserResponseDto {
        val pin = request.pinCode.trim()
        if (pin.isEmpty()) {
            throw IllegalArgumentException("PIN code cannot be empty")
        }

        // Direct username lookup
        if (!request.username.isNullOrBlank()) {
            val user = userRepository.findByUsernameAndIsActiveTrue(request.username.trim())
                .orElseThrow { IllegalArgumentException("Invalid credentials") }

            val hashed = user.pinCode
            if (hashed != null && passwordEncoder.matches(pin, hashed)) {
                return getUserById(user.id)
            }
            throw IllegalArgumentException("Invalid credentials")
        }

        // Scoped active user candidate selection
        val candidates: List<User> = when {
            !request.deviceId.isNullOrBlank() -> {
                val device = deviceRepository.findById(request.deviceId).orElse(null)
                val branch = device?.let { branchRepository.findById(it.branchId).orElse(null) }
                if (branch != null) {
                    userRepository.findByCompanyIdAndIsActiveTrue(branch.companyId)
                        .filter { !it.pinCode.isNullOrEmpty() }
                } else {
                    userRepository.findByIsActiveTrueAndPinCodeIsNotNull()
                }
            }
            !request.branchId.isNullOrBlank() -> {
                val branch = branchRepository.findById(request.branchId).orElse(null)
                if (branch != null) {
                    userRepository.findByCompanyIdAndIsActiveTrue(branch.companyId)
                        .filter { !it.pinCode.isNullOrEmpty() }
                } else {
                    userRepository.findByIsActiveTrueAndPinCodeIsNotNull()
                }
            }
            else -> {
                userRepository.findByIsActiveTrueAndPinCodeIsNotNull()
            }
        }

        // Match PIN securely using BCrypt constant-time hashing
        for (user in candidates) {
            val hashed = user.pinCode
            if (hashed != null && passwordEncoder.matches(pin, hashed)) {
                return getUserById(user.id)
            }
        }

        throw IllegalArgumentException("Invalid PIN code")
    }
}

@Service
class RoleService(
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val userRoleRepository: UserRoleRepository
) {

    fun listPermissions(): List<Permission> = permissionRepository.findAll()

    fun listRoles(): List<Map<String, Any>> {
        val roles = roleRepository.findAll()
        val allUserRoles = userRoleRepository.findAll()
        val allPermissions = permissionRepository.findAll().associateBy { it.id }
        val allRolePerms = rolePermissionRepository.findAll()

        return roles.map { role ->
            val rPermissions = allRolePerms.filter { it.roleId == role.id }
            val permissions = rPermissions.mapNotNull { allPermissions[it.permissionId]?.code }
            val assignedUserIds = allUserRoles.filter { it.roleId == role.id }.map { it.userId }
            mapOf(
                "id" to role.id,
                "name" to role.name,
                "description" to (role.description ?: ""),
                "permissions" to permissions,
                "assignedUserIds" to assignedUserIds,
                "userCount" to assignedUserIds.size
            )
        }
    }

    @Transactional
    fun createRole(dto: RoleCreateDto): Map<String, Any> {
        if (roleRepository.findByName(dto.name).isPresent) {
            throw IllegalArgumentException("Role '${dto.name}' already exists")
        }

        val role = roleRepository.save(Role(name = dto.name, description = dto.description))
        for (pId in dto.permissionIds) {
            rolePermissionRepository.save(RolePermission(roleId = role.id, permissionId = pId))
        }

        return mapOf("id" to role.id, "name" to role.name, "description" to (role.description ?: ""), "permissions" to emptyList<String>())
    }

    @Transactional
    fun updateRolePermissions(roleId: String, permissionCodesOrIds: List<String>): Map<String, Any> {
        val role = roleRepository.findById(roleId).orElseThrow { IllegalArgumentException("Role not found: $roleId") }

        val existing = rolePermissionRepository.findByIdRoleId(role.id)
        for (rp in existing) {
            rolePermissionRepository.deleteById(rp.id)
        }

        val allPermissions = permissionRepository.findAll()
        val pMapByCode = allPermissions.associateBy { it.code }
        val pMapById = allPermissions.associateBy { it.id }

        for (item in permissionCodesOrIds) {
            val perm = pMapByCode[item] ?: pMapById[item]
            if (perm != null) {
                rolePermissionRepository.save(RolePermission(roleId = role.id, permissionId = perm.id))
            }
        }

        val updatedPermissions = rolePermissionRepository.findByIdRoleId(role.id)
        val pIds = updatedPermissions.map { it.permissionId }
        val permissions = permissionRepository.findAllById(pIds)

        return mapOf(
            "id" to role.id,
            "name" to role.name,
            "description" to (role.description ?: ""),
            "permissions" to permissions.map { it.code }
        )
    }

    @Transactional
    fun updateRole(roleId: String, dto: RoleUpdateDto): Map<String, Any> {
        val role = roleRepository.findById(roleId).orElseThrow { IllegalArgumentException("Role not found: $roleId") }
        if (!dto.name.isNullOrBlank()) {
            val existing = roleRepository.findByName(dto.name)
            if (existing.isPresent && existing.get().id != roleId) {
                throw IllegalArgumentException("Role name '${dto.name}' already exists")
            }
            role.name = dto.name
        }
        if (dto.description != null) {
            role.description = dto.description
        }
        roleRepository.save(role)

        if (dto.permissionIds != null) {
            updateRolePermissions(roleId, dto.permissionIds)
        }

        val updatedPermissions = rolePermissionRepository.findByIdRoleId(role.id)
        val pIds = updatedPermissions.map { it.permissionId }
        val permissions = permissionRepository.findAllById(pIds)

        return mapOf(
            "id" to role.id,
            "name" to role.name,
            "description" to (role.description ?: ""),
            "permissions" to permissions.map { it.code }
        )
    }

    @Transactional
    fun deleteRole(roleId: String): Boolean {
        val role = roleRepository.findById(roleId).orElseThrow { IllegalArgumentException("Role not found: $roleId") }
        if (role.name == "ROLE_SUPER_ADMIN" || role.name == "SUPER_ADMIN") {
            throw IllegalArgumentException("ไม่สามารถลบบทบาท Super Admin สูงสุดของระบบได้")
        }
        rolePermissionRepository.deleteByIdRoleId(roleId)
        userRoleRepository.deleteByIdRoleId(roleId)
        roleRepository.deleteById(roleId)
        return true
    }

    @Transactional
    fun assignUsersToRole(roleId: String, userIds: List<String>): Map<String, Any> {
        val role = roleRepository.findById(roleId).orElseThrow { IllegalArgumentException("Role not found: $roleId") }

        val allUserRoles = userRoleRepository.findAll().filter { it.roleId == roleId }
        for (ur in allUserRoles) {
            if (!userIds.contains(ur.userId)) {
                userRoleRepository.deleteById(ur.id)
            }
        }
        for (uId in userIds) {
            val exists = allUserRoles.any { it.userId == uId }
            if (!exists) {
                userRoleRepository.save(UserRole(userId = uId, roleId = roleId))
            }
        }

        return mapOf("roleId" to role.id, "assignedUserIds" to userIds, "count" to userIds.size)
    }
}
