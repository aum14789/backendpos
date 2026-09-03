package sun.clientpos.data.repository

import at.favre.lib.crypto.bcrypt.BCrypt
import sun.clientpos.data.local.dao.PermissionDao
import sun.clientpos.data.local.dao.UserDao
import sun.clientpos.data.local.entity.CachedPermissionEntity
import sun.clientpos.data.local.entity.CachedUserEntity

data class AuthenticatedUserSession(
    val userId: String,
    val username: String,
    val fullName: String,
    val permissions: List<String>
)

/**
 * Offline Authentication Repository.
 *
 * Security guarantees:
 *   - Plain text PIN is NEVER stored in Room SQLite database.
 *   - Offline PIN verification strictly evaluates against BCrypt hash using BCrypt.verifyer().
 *   - Insecure plain text comparisons or suffix matching are completely prohibited.
 */
class OfflineAuthRepository(
    private val userDao: UserDao,
    private val permissionDao: PermissionDao
) {

    /**
     * Verify cashier PIN against cached BCrypt hashes.
     */
    suspend fun authenticatePinOffline(pinCode: String, username: String? = null): Result<AuthenticatedUserSession> {
        val cleanPin = pinCode.trim()
        if (cleanPin.isEmpty()) {
            return Result.failure(IllegalArgumentException("PIN code cannot be empty"))
        }

        val users = userDao.getAllActiveUsers()

        // Filter by username if specified
        val candidateUsers = if (!username.isNullOrBlank()) {
            users.filter { it.username.equals(username.trim(), ignoreCase = true) }
        } else {
            users
        }

        for (user in candidateUsers) {
            val storedHash = user.pinHash
            if (!storedHash.isNullOrBlank()) {
                val verificationResult = try {
                    BCrypt.verifyer().verify(cleanPin.toCharArray(), storedHash.toCharArray())
                } catch (_: Exception) {
                    null
                }

                if (verificationResult != null && verificationResult.verified) {
                    val permissions = permissionDao.getUserPermissionCodes(user.userId)
                    return Result.success(
                        AuthenticatedUserSession(
                            userId = user.userId,
                            username = user.username,
                            fullName = user.fullName,
                            permissions = permissions
                        )
                    )
                }
            }
        }

        return Result.failure(IllegalArgumentException("Invalid PIN code"))
    }

    /**
     * Cache user session securely.
     * `user.pinHash` MUST already be a BCrypt hash from the server, never a plain text PIN.
     */
    suspend fun cacheUserSession(user: CachedUserEntity, permissions: List<String>) {
        userDao.insertUser(user)
        permissionDao.deleteUserPermissions(user.userId)
        val permEntities = permissions.map { code ->
            CachedPermissionEntity(
                userId = user.userId,
                permissionCode = code
            )
        }
        permissionDao.insertPermissions(permEntities)
    }

    /**
     * Helper to compute BCrypt hash if caching locally (e.g. offline user provisioning).
     */
    fun hashPinForStorage(rawPin: String): String {
        return BCrypt.withDefaults().hashToString(10, rawPin.trim().toCharArray())
    }
}
