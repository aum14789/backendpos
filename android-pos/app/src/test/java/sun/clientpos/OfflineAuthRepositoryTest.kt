package sun.clientpos

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import sun.clientpos.data.local.dao.PermissionDao
import sun.clientpos.data.local.dao.UserDao
import sun.clientpos.data.local.entity.CachedPermissionEntity
import sun.clientpos.data.local.entity.CachedUserEntity
import sun.clientpos.data.repository.OfflineAuthRepository

class MockUserDao : UserDao {
    private val users = mutableMapOf<String, CachedUserEntity>()

    override suspend fun insertUser(user: CachedUserEntity) {
        users[user.userId] = user
    }

    override suspend fun insertUsers(usersList: List<CachedUserEntity>) {
        usersList.forEach { users[it.userId] = it }
    }

    override suspend fun getUserById(userId: String): CachedUserEntity? = users[userId]

    override suspend fun getAllActiveUsers(): List<CachedUserEntity> = users.values.filter { it.isActive }
}

class MockPermissionDao : PermissionDao {
    private val perms = mutableListOf<CachedPermissionEntity>()

    override suspend fun insertPermissions(permissions: List<CachedPermissionEntity>) {
        perms.addAll(permissions)
    }

    override suspend fun deleteUserPermissions(userId: String) {
        perms.removeAll { it.userId == userId }
    }

    override suspend fun getUserPermissionCodes(userId: String): List<String> {
        return perms.filter { it.userId == userId }.map { it.permissionCode }
    }
}

class OfflineAuthRepositoryTest {

    @Test
    fun testOfflinePinAuthenticationWithBcrypt() = runBlocking {
        val userDao = MockUserDao()
        val permDao = MockPermissionDao()
        val repo = OfflineAuthRepository(userDao, permDao)

        // Store PIN strictly as BCrypt hash
        val bcryptHash = repo.hashPinForStorage("1234")

        val user = CachedUserEntity(
            userId = "usr-101",
            companyId = "comp-01",
            username = "cashier1",
            fullName = "Cashier John",
            pinHash = bcryptHash
        )
        repo.cacheUserSession(user, listOf("ORDER_CREATE", "PAYMENT_PROCESS"))

        // Correct PIN
        val result = repo.authenticatePinOffline("1234")
        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertNotNull(session)
        assertEquals("usr-101", session?.userId)
        assertEquals("Cashier John", session?.fullName)
        assertTrue(session?.permissions?.contains("ORDER_CREATE") == true)

        // Wrong PIN
        val wrongResult = repo.authenticatePinOffline("9999")
        assertTrue(wrongResult.isFailure)
    }
}
