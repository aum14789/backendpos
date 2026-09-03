package sun.clientpos.data.local.dao

import androidx.room.*
import sun.clientpos.data.local.entity.CachedBranchEntity
import sun.clientpos.data.local.entity.CachedDeviceEntity
import sun.clientpos.data.local.entity.CachedPermissionEntity
import sun.clientpos.data.local.entity.CachedUserEntity
import sun.clientpos.data.local.entity.RoomBrandEntity

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: CachedUserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<CachedUserEntity>)

    @Query("SELECT * FROM cached_users WHERE userId = :userId")
    suspend fun getUserById(userId: String): CachedUserEntity?

    @Query("SELECT * FROM cached_users WHERE isActive = 1")
    suspend fun getAllActiveUsers(): List<CachedUserEntity>
}

@Dao
interface BrandDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrand(brand: RoomBrandEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrands(brands: List<RoomBrandEntity>)

    @Query("SELECT * FROM cached_brands WHERE brandId = :brandId")
    suspend fun getBrandById(brandId: String): RoomBrandEntity?

    @Query("SELECT * FROM cached_brands WHERE companyId = :companyId AND isActive = 1")
    suspend fun getBrandsByCompany(companyId: String): List<RoomBrandEntity>
}

@Dao
interface BranchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranch(branch: CachedBranchEntity)

    @Query("SELECT * FROM cached_branches WHERE branchId = :branchId")
    suspend fun getBranchById(branchId: String): CachedBranchEntity?

    @Query("SELECT * FROM cached_branches WHERE brandId = :brandId")
    suspend fun getBranchesByBrand(brandId: String): List<CachedBranchEntity>
}

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: CachedDeviceEntity)

    @Query("SELECT * FROM cached_devices WHERE deviceId = :deviceId")
    suspend fun getDeviceById(deviceId: String): CachedDeviceEntity?
}

@Dao
interface PermissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPermissions(permissions: List<CachedPermissionEntity>)

    @Query("DELETE FROM cached_permissions WHERE userId = :userId")
    suspend fun deleteUserPermissions(userId: String)

    @Query("SELECT permissionCode FROM cached_permissions WHERE userId = :userId")
    suspend fun getUserPermissionCodes(userId: String): List<String>
}
