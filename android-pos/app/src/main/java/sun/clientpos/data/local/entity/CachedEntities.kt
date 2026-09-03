package sun.clientpos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_users")
data class CachedUserEntity(
    @PrimaryKey
    val userId: String,
    val companyId: String,
    val username: String,
    val fullName: String,
    val pinHash: String?,
    val isActive: Boolean = true
)

@Entity(tableName = "cached_brands")
data class RoomBrandEntity(
    @PrimaryKey
    val brandId: String,
    val companyId: String,
    val name: String,
    val code: String,
    val logoUrl: String? = null,
    val description: String? = null,
    val isActive: Boolean = true
)

@Entity(tableName = "cached_branches")
data class CachedBranchEntity(
    @PrimaryKey
    val branchId: String,
    val companyId: String,
    val brandId: String? = null,
    val name: String,
    val code: String,
    val businessDayCloseTime: String = "02:00",
    val taxRate: Double = 7.0,
    val serviceChargeRate: Double = 10.0
)

@Entity(tableName = "cached_devices")
data class CachedDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val branchId: String,
    val deviceName: String,
    val deviceCode: String,
    val deviceType: String
)

@Entity(tableName = "cached_permissions")
data class CachedPermissionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val permissionCode: String
)
