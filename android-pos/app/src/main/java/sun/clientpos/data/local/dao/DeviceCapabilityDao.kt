package sun.clientpos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import sun.clientpos.data.local.entity.DeviceCapabilityEntity

@Dao
interface DeviceCapabilityDao {

    @Query("SELECT * FROM cached_device_capabilities WHERE deviceId = :deviceId AND isActive = 1")
    fun getActiveCapabilities(deviceId: String): Flow<List<DeviceCapabilityEntity>>

    @Query("SELECT * FROM cached_device_capabilities WHERE deviceId = :deviceId AND isActive = 1")
    suspend fun getActiveCapabilitiesList(deviceId: String): List<DeviceCapabilityEntity>

    @Query("SELECT COUNT(*) > 0 FROM cached_device_capabilities WHERE deviceId = :deviceId AND capability = :capability AND isActive = 1")
    suspend fun hasCapability(deviceId: String, capability: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(capabilities: List<DeviceCapabilityEntity>)

    @Query("DELETE FROM cached_device_capabilities WHERE deviceId = :deviceId")
    suspend fun deleteAllForDevice(deviceId: String)

    /**
     * Replace all capabilities for a device atomically.
     * Called during sync pull to refresh cached capabilities from cloud.
     */
    @androidx.room.Transaction
    suspend fun replaceCapabilities(deviceId: String, capabilities: List<DeviceCapabilityEntity>) {
        deleteAllForDevice(deviceId)
        insertAll(capabilities)
    }
}
