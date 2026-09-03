package sun.clientpos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached device capabilities pulled from the Cloud Backend during sync.
 * Controls which POS actions (pay, open table, etc.) are available on this device.
 * Capabilities gate UI actions only — they do NOT restrict data storage.
 */
@Entity(tableName = "cached_device_capabilities")
data class DeviceCapabilityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceId: String,
    val capability: String,  // OPEN_TABLE, TAKE_ORDER, PAY, OPEN_SHIFT, CLOSE_SHIFT, PRINT_RECEIPT, CLOSE_BUSINESS_DAY, STOCK_ADJUST
    val isActive: Boolean = true
)

/**
 * All known device capabilities.
 * Mirrors the backend DeviceCapability enum.
 */
enum class DeviceCapabilityType {
    OPEN_TABLE,
    TAKE_ORDER,
    PAY,
    OPEN_SHIFT,
    CLOSE_SHIFT,
    PRINT_RECEIPT,
    CLOSE_BUSINESS_DAY,
    STOCK_ADJUST
}
