package sun.clientpos.data.local

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sun.clientpos.data.local.entity.*

/**
 * Seeds initial master data into Room SQLite database if empty.
 * Ensures the POS is completely usable offline immediately upon first install.
 */
object PosDatabaseSeeder {

    suspend fun seedIfEmpty(db: PosDatabase) = withContext(Dispatchers.IO) {
        val companyId = "comp-001"
        val branchId = "branch-001"

        // 1. Seed Branch & Device if missing
        val existingBranches = db.branchDao().getBranchById(branchId)
        if (existingBranches == null) {
            db.branchDao().insertBranch(
                CachedBranchEntity(
                    branchId = branchId,
                    companyId = companyId,
                    name = "Sukhumvit Main Branch",
                    code = "BR-01",
                    businessDayCloseTime = "02:00",
                    taxRate = 7.0,
                    serviceChargeRate = 0.0
                )
            )
            db.deviceDao().insertDevice(
                CachedDeviceEntity(
                    deviceId = "pos-device-001",
                    branchId = branchId,
                    deviceName = "Main POS Terminal #1",
                    deviceCode = "POS-01",
                    deviceType = "POS_MAIN"
                )
            )
        }

        // 2. Seed Users & Permissions if missing
        val userDao = db.userDao()
        val existingUsers = userDao.getAllActiveUsers()
        if (existingUsers.isEmpty()) {
            val hash1234 = BCrypt.withDefaults().hashToString(10, "1234".toCharArray())
            val hash0000 = BCrypt.withDefaults().hashToString(10, "0000".toCharArray())

            val users = listOf(
                CachedUserEntity(
                    userId = "usr-admin",
                    companyId = companyId,
                    username = "admin",
                    fullName = "Store Manager",
                    pinHash = hash1234,
                    isActive = true
                ),
                CachedUserEntity(
                    userId = "usr-cashier",
                    companyId = companyId,
                    username = "cashier",
                    fullName = "Frontline Cashier",
                    pinHash = hash0000,
                    isActive = true
                )
            )
            userDao.insertUsers(users)

            val permissions = listOf(
                "ORDER_VIEW", "ORDER_CREATE", "ORDER_CANCEL", "ORDER_VOID",
                "DISCOUNT_APPLY", "DISCOUNT_OVERRIDE", "PAYMENT_REFUND", "USER_MANAGE"
            )
            val permEntities = permissions.map {
                CachedPermissionEntity(userId = "usr-admin", permissionCode = it)
            } + permissions.take(5).map {
                CachedPermissionEntity(userId = "usr-cashier", permissionCode = it)
            }
            db.permissionDao().insertPermissions(permEntities)
        }

        // 3. Seed Zones if missing
        val zoneCount = db.tableDao().getZoneCount()
        if (zoneCount == 0) {
            val zones = listOf(
                RoomZoneEntity(zoneId = "zone-main", branchId = branchId, name = "โถงหลัก (Main Dining)", zoneType = "DINE_IN", sortOrder = 1, isActive = true),
                RoomZoneEntity(zoneId = "zone-buffet", branchId = branchId, name = "โซนชาบูบุฟเฟ่ต์ A", zoneType = "BUFFET", sortOrder = 2, isActive = true),
                RoomZoneEntity(zoneId = "zone-buffet-vip", branchId = branchId, name = "โซนพรีเมียมบุฟเฟ่ต์ VIP", zoneType = "BUFFET", sortOrder = 3, isActive = true),
                RoomZoneEntity(zoneId = "zone-outdoor", branchId = branchId, name = "ระเบียงกลางแจ้ง (Outdoor)", zoneType = "DINE_IN", sortOrder = 4, isActive = true)
            )
            db.tableDao().insertZones(zones)
        }

        // 4. Seed Tables if missing
        val tableCount = db.tableDao().getTableCount()
        if (tableCount == 0) {
            val tables = listOf(
                // Zone 1: Main Dining
                RoomTableEntity(tableId = "tbl-01", branchId = branchId, zoneId = "zone-main", tableTypeId = "type-std", nameNumber = "11", capacity = 4, status = "AVAILABLE", isActive = true),
                RoomTableEntity(tableId = "tbl-02", branchId = branchId, zoneId = "zone-main", tableTypeId = "type-std", nameNumber = "12", capacity = 4, status = "AVAILABLE", isActive = true),
                RoomTableEntity(tableId = "tbl-03", branchId = branchId, zoneId = "zone-main", tableTypeId = "type-std", nameNumber = "14", capacity = 2, status = "AVAILABLE", isActive = true),
                RoomTableEntity(tableId = "tbl-04", branchId = branchId, zoneId = "zone-main", tableTypeId = "type-std", nameNumber = "15", capacity = 6, status = "AVAILABLE", isActive = true),
                // Zone 2: Buffet Area A
                RoomTableEntity(tableId = "tbl-05", branchId = branchId, zoneId = "zone-buffet", tableTypeId = "type-buffet", nameNumber = "B-01", capacity = 4, status = "AVAILABLE", isActive = true),
                RoomTableEntity(tableId = "tbl-06", branchId = branchId, zoneId = "zone-buffet", tableTypeId = "type-buffet", nameNumber = "B-02", capacity = 4, status = "AVAILABLE", isActive = true),
                RoomTableEntity(tableId = "tbl-07", branchId = branchId, zoneId = "zone-buffet", tableTypeId = "type-buffet", nameNumber = "B-03", capacity = 6, status = "AVAILABLE", isActive = true),
                // Zone 3: Multi-Buffet Zone (VIP Buffet)
                RoomTableEntity(tableId = "tbl-08", branchId = branchId, zoneId = "zone-buffet-vip", tableTypeId = "type-vip", nameNumber = "VIP-01", capacity = 8, status = "AVAILABLE", isActive = true),
                RoomTableEntity(tableId = "tbl-09", branchId = branchId, zoneId = "zone-buffet-vip", tableTypeId = "type-vip", nameNumber = "VIP-02", capacity = 10, status = "AVAILABLE", isActive = true),
                // Zone 4: Outdoor
                RoomTableEntity(tableId = "tbl-10", branchId = branchId, zoneId = "zone-outdoor", tableTypeId = "type-std", nameNumber = "OD-01", capacity = 4, status = "AVAILABLE", isActive = true),
                RoomTableEntity(tableId = "tbl-11", branchId = branchId, zoneId = "zone-outdoor", tableTypeId = "type-std", nameNumber = "OD-02", capacity = 4, status = "AVAILABLE", isActive = true),
                // Inactive Table (Demonstrates disabled table filtering)
                RoomTableEntity(tableId = "tbl-12", branchId = branchId, zoneId = "zone-main", tableTypeId = "type-std", nameNumber = "CLOSED-01", capacity = 4, status = "AVAILABLE", isActive = false)
            )
            db.tableDao().insertTables(tables)
        }

        // 5. Seed Menu Categories if missing
        val categoryCount = db.menuDao().getCategoryCount()
        if (categoryCount == 0) {
            val categories = listOf(
                RoomMenuCategoryEntity(categoryId = "cat-main", branchId = branchId, name = "อาหารจานหลัก", description = "Main Dishes", sortOrder = 1),
                RoomMenuCategoryEntity(categoryId = "cat-snack", branchId = branchId, name = "ของทานเล่น", description = "Appetizers", sortOrder = 2),
                RoomMenuCategoryEntity(categoryId = "cat-buffet", branchId = branchId, name = "เนื้อ & บุฟเฟ่ต์", description = "Buffet Meat Items", sortOrder = 3),
                RoomMenuCategoryEntity(categoryId = "cat-drink", branchId = branchId, name = "เครื่องดื่ม", description = "Beverages", sortOrder = 4)
            )
            db.menuDao().insertCategories(categories)
        }

        // 6. Seed Menu Items if missing
        val menuItemCount = db.menuDao().getMenuItemCount()
        if (menuItemCount == 0) {
            val menuItems = listOf(
                RoomMenuItemEntity(itemId = "item-01", branchId = branchId, categoryId = "cat-main", name = "ข้าวผัดปูพิเศษ", description = "Crab Fried Rice", sku = "SKU-01", basePrice = 12000L, availability = "AVAILABLE", sortOrder = 1),
                RoomMenuItemEntity(itemId = "item-02", branchId = branchId, categoryId = "cat-main", name = "ต้มยำกุ้งน้ำข้น", description = "Tom Yum Goong", sku = "SKU-02", basePrice = 22000L, availability = "AVAILABLE", sortOrder = 2),
                RoomMenuItemEntity(itemId = "item-03", branchId = branchId, categoryId = "cat-snack", name = "ปีกไก่ทอดน้ำปลา", description = "Fried Chicken Wings", sku = "SKU-03", basePrice = 9500L, availability = "AVAILABLE", sortOrder = 3),
                RoomMenuItemEntity(itemId = "item-04", branchId = branchId, categoryId = "cat-buffet", name = "ชุดหมูคุโรบุตะสไลซ์", description = "Kurobuta Pork Set", sku = "SKU-04", basePrice = 25000L, availability = "AVAILABLE", sortOrder = 4),
                RoomMenuItemEntity(itemId = "item-05", branchId = branchId, categoryId = "cat-buffet", name = "ชุดเนื้อวากิวออสเตรเลีย", description = "Aussie Wagyu Set", sku = "SKU-05", basePrice = 39000L, availability = "AVAILABLE", sortOrder = 5),
                RoomMenuItemEntity(itemId = "item-06", branchId = branchId, categoryId = "cat-drink", name = "ชามะนาวเย็น", description = "Iced Lemon Tea", sku = "SKU-06", basePrice = 4500L, availability = "AVAILABLE", sortOrder = 6),
                RoomMenuItemEntity(itemId = "item-07", branchId = branchId, categoryId = "cat-drink", name = "น้ำแร่ธรรมชาติ", description = "Mineral Water", sku = "SKU-07", basePrice = 2000L, availability = "AVAILABLE", sortOrder = 7)
            )
            db.menuDao().insertMenuItems(menuItems)
        }

        // 7. Seed Buffet Tiers if missing
        val tierCount = db.buffetDao().getTierCount()
        if (tierCount == 0) {
            val buffetTiers = listOf(
                RoomBuffetTierEntity(
                    tierId = "tier-std-399",
                    promotionId = "promo-buf-std",
                    name = "Standard Buffet ฿399",
                    adultPrice = 39900L,
                    childPrice = 19900L,
                    timeLimitMinutes = 90,
                    brandId = "brand-001",
                    branchId = branchId
                ),
                RoomBuffetTierEntity(
                    tierId = "tier-prem-599",
                    promotionId = "promo-buf-prem",
                    name = "Premium Wagyu Buffet ฿599",
                    adultPrice = 59900L,
                    childPrice = 29900L,
                    timeLimitMinutes = 120,
                    brandId = "brand-001",
                    branchId = branchId
                )
            )
            db.buffetDao().insertTiers(buffetTiers)

            // Link eligible menu items to tiers
            val tierLinks = listOf(
                RoomBuffetTierMenuItemEntity(buffetTierId = "tier-std-399", menuItemId = "item-03"),
                RoomBuffetTierMenuItemEntity(buffetTierId = "tier-std-399", menuItemId = "item-04"),
                RoomBuffetTierMenuItemEntity(buffetTierId = "tier-std-399", menuItemId = "item-06"),
                RoomBuffetTierMenuItemEntity(buffetTierId = "tier-std-399", menuItemId = "item-07"),
                RoomBuffetTierMenuItemEntity(buffetTierId = "tier-prem-599", menuItemId = "item-01"),
                RoomBuffetTierMenuItemEntity(buffetTierId = "tier-prem-599", menuItemId = "item-02"),
                RoomBuffetTierMenuItemEntity(buffetTierId = "tier-prem-599", menuItemId = "item-03"),
                RoomBuffetTierMenuItemEntity(buffetTierId = "tier-prem-599", menuItemId = "item-04"),
                RoomBuffetTierMenuItemEntity(buffetTierId = "tier-prem-599", menuItemId = "item-05"),
                RoomBuffetTierMenuItemEntity(buffetTierId = "tier-prem-599", menuItemId = "item-06"),
                RoomBuffetTierMenuItemEntity(buffetTierId = "tier-prem-599", menuItemId = "item-07")
            )
            db.buffetDao().insertTierMenuItems(tierLinks)
        }
    }
}
