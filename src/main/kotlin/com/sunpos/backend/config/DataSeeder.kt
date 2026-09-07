package com.sunpos.backend.config

import com.sunpos.backend.domain.businessday.*
import com.sunpos.backend.domain.catalog.*
import com.sunpos.backend.domain.crm.*
import com.sunpos.backend.domain.identity.*
import com.sunpos.backend.domain.integration.line.*
import com.sunpos.backend.domain.inventory.*
import com.sunpos.backend.domain.order.*
import com.sunpos.backend.domain.organization.*
import com.sunpos.backend.domain.payment.*
import com.sunpos.backend.domain.promotion.*
import com.sunpos.backend.domain.purchasing.*
import com.sunpos.backend.domain.recipe.*
import com.sunpos.backend.domain.shift.*
import com.sunpos.backend.domain.table.*
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Component
class DataSeeder(
    private val companyRepository: CompanyRepository,
    private val brandRepository: BrandRepository,
    private val branchRepository: BranchRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceCapabilityRepository: DeviceCapabilityRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val userRoleRepository: UserRoleRepository,
    private val rolePermissionRepository: RolePermissionRepository,
    private val zoneRepository: ZoneRepository,
    private val tableTypeRepository: TableTypeRepository,
    private val tableRepository: TableRepository,
    private val menuCategoryRepository: MenuCategoryRepository,
    private val menuItemRepository: MenuItemRepository,
    private val modifierGroupRepository: ModifierGroupRepository,
    private val modifierRepository: ModifierRepository,
    private val buffetPromotionRepository: BuffetPromotionRepository,
    private val buffetPromotionTierRepository: BuffetPromotionTierRepository,
    private val buffetTierMenuItemRepository: BuffetTierMenuItemRepository,
    private val buffetPromotionMenuItemRepository: BuffetPromotionMenuItemRepository,
    private val membershipTierRepository: MembershipTierRepository,
    private val customerRepository: CustomerRepository,
    private val customerIdentityRepository: CustomerIdentityRepository,
    private val pointLedgerRepository: PointLedgerRepository,
    private val warehouseRepository: WarehouseRepository,
    private val inventoryItemRepository: InventoryItemRepository,
    private val inventoryStockRepository: InventoryStockRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val stockTransferRepository: StockTransferRepository,
    private val stockCountRepository: StockCountRepository,
    private val stockWasteRepository: StockWasteRepository,
    private val supplierRepository: SupplierRepository,
    private val supplierPriceHistoryRepository: SupplierPriceHistoryRepository,
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val purchaseOrderItemRepository: PurchaseOrderItemRepository,
    private val goodsReceiveRepository: GoodsReceiveRepository,
    private val purchaseReturnRepository: PurchaseReturnRepository,
    private val recipeRepository: RecipeRepository,
    private val recipeIngredientRepository: RecipeIngredientRepository,
    private val bomRepository: BomRepository,
    private val productionOrderRepository: ProductionOrderRepository,
    private val buffetPackageRecipeRepository: BuffetPackageRecipeRepository,
    private val promotionRepository: PromotionRepository,
    private val couponRepository: CouponRepository,
    private val businessDayRepository: BusinessDayRepository,
    private val cashierShiftRepository: CashierShiftRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val passwordEncoder: PasswordEncoder,
    @org.springframework.beans.factory.annotation.Value("\${sunpos.seeder.enabled:false}")
    private val seederEnabled: Boolean = false
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(DataSeeder::class.java)

    override fun run(args: ApplicationArguments?) {
        if (!seederEnabled) {
            logger.info("🛑 Auto-Seeder is DISABLED (sunpos.seeder.enabled=false). No data will be written to database on startup.")
            return
        }
        try {
            seedMasterDataIfEmpty(force = false)
        } catch (e: Exception) {
            logger.warn("Could not seed data to database: {}", e.message)
        }
    }

    fun seedMasterDataIfEmpty(force: Boolean = false) {
        if (!seederEnabled && !force) {
            logger.info("🛑 Seeder is DISABLED (sunpos.seeder.enabled=false). Skipping data seed.")
            return
        }
        logger.info("Initializing and seeding database with Comprehensive Master Mockup Data (3 Brands, 4 Branches, Menus, Stocks)...")

        val companyId = "comp-001"
        val brandId = "brand-001"
        val branchId = "branch-001"

        // 1. Organization (3 Brands, 4 Branches)
        val company = Company(
            id = companyId,
            name = "SunPOS Enterprise Co., Ltd.",
            taxId = "0105559123456"
        )
        companyRepository.save(company)

        // Brand 1: Shabu (2 Branches in Sukhumvit)
        val brand1 = Brand(
            id = "brand-001",
            companyId = companyId,
            name = "Sun Shabu & Grill (ซันชาบู บุฟเฟ่ต์)",
            code = "SUN-SHABU"
        )
        // Brand 2: Japanese A la carte (1 Branch)
        val brand2 = Brand(
            id = "brand-002",
            companyId = companyId,
            name = "Sun Japanese Dining & Izakaya (ซัน อาหารญี่ปุ่น อะลาคาร์ท)",
            code = "SUN-IZAKAYA"
        )
        // Brand 3: Cafe & Bakery (1 Branch)
        val brand3 = Brand(
            id = "brand-003",
            companyId = companyId,
            name = "Sun Coffee & Artisan Bakery (ซัน คาเฟ่ & เบเกอรี่)",
            code = "SUN-CAFE"
        )
        brandRepository.save(brand1)
        brandRepository.save(brand2)
        brandRepository.save(brand3)

        // Shabu Branch 1: Sukhumvit 24
        val branch1 = Branch(
            id = "branch-001",
            brandId = "brand-001",
            companyId = companyId,
            name = "Sun Shabu Sukhumvit 24 (สุขุมวิท 24)",
            code = "SUK-01",
            address = "888 ซอยสุขุมวิท 24 แขวงคลองตัน เขตคลองเตย กรุงเทพฯ",
            phone = "02-123-4561",
            openTime = "10:00",
            closeTime = "22:00",
            businessDayCloseTime = "02:00",
            taxRate = BigDecimal("7.00"),
            serviceChargeRate = BigDecimal("10.00"),
            ipAddress = "127.0.0.1",
            dynDnsHost = "branch-sukhumvit.dyndns.org",
            allowedIpSubnets = listOf("127.0.0.1", "192.168.1.0/24"),
            isActive = true
        )
        // Shabu Branch 2: Sukhumvit Asoke
        val branch2 = Branch(
            id = "branch-002",
            brandId = "brand-001",
            companyId = companyId,
            name = "Sun Shabu Sukhumvit Asoke (สุขุมวิท อโศก)",
            code = "SUK-02",
            address = "219 อาคารอโศกทาวเวอร์ ถนนสุขุมวิท 21 แขวงคลองเตยเหนือ เขตวัฒนา กรุงเทพฯ",
            phone = "02-123-4562",
            openTime = "10:00",
            closeTime = "22:00",
            businessDayCloseTime = "02:00",
            taxRate = BigDecimal("7.00"),
            serviceChargeRate = BigDecimal("10.00"),
            ipAddress = "192.168.1.50",
            dynDnsHost = "branch-asoke.ddns.net",
            allowedIpSubnets = listOf("192.168.1.0/24"),
            isActive = true
        )
        // Japanese Branch: Thonglor
        val branch3 = Branch(
            id = "branch-003",
            brandId = "brand-002",
            companyId = companyId,
            name = "Sun Japanese Thonglor (สาขาทองหล่อ)",
            code = "JPN-01",
            address = "55/1 ซอยทองหล่อ 13 แขวงคลองตันเหนือ เขตวัฒนา กรุงเทพฯ",
            phone = "02-712-8899",
            openTime = "11:30",
            closeTime = "23:00",
            businessDayCloseTime = "00:00",
            taxRate = BigDecimal("7.00"),
            serviceChargeRate = BigDecimal("10.00"),
            ipAddress = "192.168.1.60",
            dynDnsHost = "branch-thonglor.dyndns.org",
            allowedIpSubnets = listOf("192.168.1.0/24"),
            isActive = true
        )
        // Cafe Branch: Ari
        val branch4 = Branch(
            id = "branch-004",
            brandId = "brand-003",
            companyId = companyId,
            name = "Sun Coffee Ari Craft Cafe (สาขาอารีย์)",
            code = "CAF-01",
            address = "12/4 ซอยอารีย์สัมพันธ์ 5 แขวงพญาไท เขตพญาไท กรุงเทพฯ",
            phone = "02-619-7788",
            openTime = "07:00",
            closeTime = "20:00",
            businessDayCloseTime = "21:00",
            taxRate = BigDecimal("7.00"),
            serviceChargeRate = BigDecimal("0.00"),
            ipAddress = "192.168.1.70",
            dynDnsHost = "branch-ari.ddns.net",
            allowedIpSubnets = listOf("192.168.1.0/24"),
            isActive = true
        )
        branchRepository.save(branch1)
        branchRepository.save(branch2)
        branchRepository.save(branch3)
        branchRepository.save(branch4)

        val device1 = Device(
            id = "pos-device-001",
            branchId = branchId,
            deviceName = "Main POS Terminal #1",
            deviceCode = "POS-01",
            deviceType = "POS_MAIN",
            status = "ACTIVE"
        )
        val device2 = Device(
            id = "pos-device-002",
            branchId = branchId,
            deviceName = "Table Service Tablet #1",
            deviceCode = "TAB-01",
            deviceType = "POS_TABLET",
            status = "ACTIVE"
        )
        deviceRepository.save(device1)
        deviceRepository.save(device2)

        val capabilities = listOf(
            DeviceCapability.OPEN_TABLE,
            DeviceCapability.TAKE_ORDER,
            DeviceCapability.PAY,
            DeviceCapability.OPEN_SHIFT,
            DeviceCapability.CLOSE_SHIFT,
            DeviceCapability.PRINT_RECEIPT,
            DeviceCapability.CLOSE_BUSINESS_DAY
        )
        capabilities.forEach { cap ->
            deviceCapabilityRepository.save(
                DeviceCapabilityEntity(
                    id = "cap_${device1.id}_${cap.name}",
                    deviceId = device1.id,
                    branchId = branchId,
                    capability = cap,
                    isActive = true
                )
            )
        }

        // 2. Identity & RBAC (Dynamic Permission Nodes & Role Matrix)
        val permissions = listOf(
            // System Config & Distribution
            "SYSTEM_CONFIG" to "ตั้งค่านโยบายระบบและ Fast Setup",
            "CATALOG_DISTRIBUTE" to "จัดสรรเมนูอาหารเข้าสาขา",
            "INVENTORY_DISTRIBUTE" to "จัดสรรวัตถุดิบเข้าคลังสาขา",
            // Organization & Master
            "BRAND_MANAGE" to "จัดการแบรนด์ร้านอาหาร",
            "BRANCH_MANAGE" to "จัดการสาขาและข้อมูลสาขา",
            "DEVICE_MANAGE" to "จัดการอุปกรณ์ POS และการซิงค์",
            // Users & Roles
            "USER_MANAGE" to "จัดการพนักงานและผู้ใช้งานระบบ",
            "ROLE_MANAGE" to "จัดการบทบาทและกำหนดสิทธิ์ (Role Matrix)",
            // Menu & Catalog
            "MENU_MANAGE" to "จัดการเมนูอาหารและหมวดหมู่",
            "BUFFET_MANAGE" to "จัดการแพ็กเกจและโปรโมชั่นบุฟเฟต์",
            // Inventory & Warehouse
            "INVENTORY_VIEW" to "ดูรายการสต็อกและมูลค่าสินค้าคงเหลือ",
            "INVENTORY_ITEM_MANAGE" to "จัดการวัตถุดิบและโครงสร้างหน่วย 3 ระดับ",
            "STOCK_ADJUST" to "ปรับยอดสต็อกและบันทึกของเสียสูญหาย",
            "STOCK_TRANSFER" to "โอนย้ายสินค้าระหว่างคลัง",
            "STOCK_COUNT" to "ตรวจนับสต็อกจริงประจำรอบ",
            // Recipes & Production
            "RECIPE_MANAGE" to "จัดการสูตรอาหารและเวอร์ชันสูตร",
            "PRODUCTION_MANAGE" to "จัดการ BOM และใบสั่งผลิตวัตถุดิบ",
            // Purchasing & Suppliers
            "PURCHASE_ORDER" to "ออกและอนุมัติใบสั่งซื้อสินค้า (PO)",
            "GOODS_RECEIVE" to "รับสินค้าเข้าคลัง (GRN) และใบคืนสินค้า",
            "SUPPLIER_MANAGE" to "จัดการข้อมูลซัพพลายเออร์และประวัติราคา",
            // Sales, POS & Shifts
            "ORDER_VIEW" to "ดูรายการสั่งซื้อของลูกค้า",
            "ORDER_CREATE" to "เปิดโต๊ะและรับรายการสั่งซื้อใหม่",
            "ORDER_CANCEL" to "ยกเลิกรายการสั่งซื้อ",
            "ORDER_VOID" to "ยกเลิกบิลหรือรายการอาหาร (Void)",
            "DISCOUNT_APPLY" to "ใช้ส่วนลดโปรโมชั่นและสมาชิก",
            "DISCOUNT_OVERRIDE" to "อนุมัติส่วนลดพิเศษเกินวงเงิน",
            "PAYMENT_MANAGE" to "รับชำระเงินและจัดการธุรกรรมการเงิน",
            "PAYMENT_REFUND" to "ดำเนินการคืนเงิน (Refund)",
            "SHIFT_MANAGE" to "จัดการกะพนักงานและปิดรอบวันทำการ (EOD)",
            // CRM & Loyalty
            "CRM_MANAGE" to "จัดการสมาชิกลูกค้า แต้มสะสม และคูปอง",
            // Reports & Analytics
            "REPORT_SALES_VIEW" to "ดูรายงานสรุปยอดขาย",
            "REPORT_FINANCIAL_VIEW" to "ดูรายงานกำไรขั้นต้นและต้นทุน COGS",
            "REPORT_EXECUTIVE_VIEW" to "ดูภาพรวม Dashboard ผู้บริหาร",
            "REPORT_INVENTORY_VIEW" to "ดูรายงานการเคลื่อนไหวและมูลค่าคลังสินค้า"
        )
        val permEntities = permissions.map { (code, desc) ->
            val p = Permission(id = "perm_$code", code = code, description = desc)
            permissionRepository.save(p)
        }
        val permMap = permEntities.associateBy { it.code }

        // Standard Roles
        val roleSuperAdmin = Role(id = "role-super-admin", name = "ROLE_SUPER_ADMIN", description = "ผู้ดูแลระบบส่วนกลางสูงสุด (HQ Super Admin)")
        val roleManager = Role(id = "role-manager", name = "ROLE_STORE_MANAGER", description = "ผู้จัดการสาขา (Store Manager)")
        val roleCashier = Role(id = "role-cashier", name = "ROLE_CASHIER", description = "พนักงานแคชเชียร์และบริการหน้าร้าน")
        val roleWarehouse = Role(id = "role-warehouse", name = "ROLE_WAREHOUSE_STAFF", description = "เจ้าหน้าที่คลังสินค้าและจัดซื้อ")
        val roleChef = Role(id = "role-chef", name = "ROLE_CHEF", description = "หัวหน้าเชฟและฝ่ายผลิตครัวกลาง")
        val roleAccountant = Role(id = "role-accountant", name = "ROLE_ACCOUNTANT", description = "ฝ่ายบัญชีและการเงิน")
        val roleOffice = Role(id = "role-office", name = "ROLE_OFFICE_STAFF", description = "เจ้าหน้าที่สำนักงานใหญ่ (HQ Office Operations)")

        roleRepository.save(roleSuperAdmin)
        roleRepository.save(roleManager)
        roleRepository.save(roleCashier)
        roleRepository.save(roleWarehouse)
        roleRepository.save(roleChef)
        roleRepository.save(roleAccountant)
        roleRepository.save(roleOffice)

        // Assign permissions to roles
        // 1. Super Admin: All permissions
        permEntities.forEach { p ->
            rolePermissionRepository.save(RolePermission(roleId = roleSuperAdmin.id, permissionId = p.id))
        }

        // 2. Store Manager: Operations, POS, Inventory, Shifts, Sales Reports
        listOf(
            "ORDER_VIEW", "ORDER_CREATE", "ORDER_CANCEL", "ORDER_VOID",
            "DISCOUNT_APPLY", "DISCOUNT_OVERRIDE", "PAYMENT_MANAGE", "PAYMENT_REFUND",
            "SHIFT_MANAGE", "MENU_MANAGE", "BUFFET_MANAGE", "INVENTORY_VIEW",
            "INVENTORY_ITEM_MANAGE", "STOCK_ADJUST", "STOCK_TRANSFER", "STOCK_COUNT",
            "RECIPE_MANAGE", "PURCHASE_ORDER", "GOODS_RECEIVE", "CRM_MANAGE", "REPORT_SALES_VIEW"
        ).mapNotNull { permMap[it] }.forEach { p ->
            rolePermissionRepository.save(RolePermission(roleId = roleManager.id, permissionId = p.id))
        }

        // 3. Cashier: POS operations, Take Order, Payment, Apply Discount, Shift
        listOf(
            "ORDER_VIEW", "ORDER_CREATE", "ORDER_CANCEL", "DISCOUNT_APPLY",
            "PAYMENT_MANAGE", "SHIFT_MANAGE", "MENU_MANAGE", "CRM_MANAGE"
        ).mapNotNull { permMap[it] }.forEach { p ->
            rolePermissionRepository.save(RolePermission(roleId = roleCashier.id, permissionId = p.id))
        }

        // 4. Warehouse Staff: Inventory, Movements, Waste, Transfer, Stock Count, PO, GRN
        listOf(
            "INVENTORY_VIEW", "INVENTORY_ITEM_MANAGE", "STOCK_ADJUST", "STOCK_TRANSFER",
            "STOCK_COUNT", "PURCHASE_ORDER", "GOODS_RECEIVE", "SUPPLIER_MANAGE", "REPORT_INVENTORY_VIEW"
        ).mapNotNull { permMap[it] }.forEach { p ->
            rolePermissionRepository.save(RolePermission(roleId = roleWarehouse.id, permissionId = p.id))
        }

        // 5. Chef: Recipes, BOM, Production, Inventory View, Stock Waste
        listOf(
            "RECIPE_MANAGE", "PRODUCTION_MANAGE", "INVENTORY_VIEW", "STOCK_ADJUST", "MENU_MANAGE"
        ).mapNotNull { permMap[it] }.forEach { p ->
            rolePermissionRepository.save(RolePermission(roleId = roleChef.id, permissionId = p.id))
        }

        // 6. Accountant: Reports, Sales, Financial COGS, Payment Transactions
        listOf(
            "REPORT_SALES_VIEW", "REPORT_FINANCIAL_VIEW", "REPORT_INVENTORY_VIEW", "REPORT_EXECUTIVE_VIEW",
            "PAYMENT_MANAGE", "INVENTORY_VIEW"
        ).mapNotNull { permMap[it] }.forEach { p ->
            rolePermissionRepository.save(RolePermission(roleId = roleAccountant.id, permissionId = p.id))
        }

        // 7. Office Staff: Master Catalogs, Purchasing, CRM, Inventory & Sales/Financial Reports
        listOf(
            "MENU_MANAGE", "BUFFET_MANAGE", "RECIPE_MANAGE", "PRODUCTION_MANAGE",
            "SUPPLIER_MANAGE", "PURCHASE_ORDER", "GOODS_RECEIVE", "CRM_MANAGE",
            "INVENTORY_VIEW", "INVENTORY_ITEM_MANAGE", "REPORT_SALES_VIEW",
            "REPORT_FINANCIAL_VIEW", "REPORT_INVENTORY_VIEW"
        ).mapNotNull { permMap[it] }.forEach { p ->
            rolePermissionRepository.save(RolePermission(roleId = roleOffice.id, permissionId = p.id))
        }

        // Users
        val allModules = listOf(
            "FINANCIAL_MANAGEMENT", "SALES_MARKETING", "PURCHASE", "WAREHOUSE",
            "PRODUCTION", "ORGANIZATION", "USERS_SECURITY", "SYSTEM_CONFIG"
        )

        val userAdmin = User(
            id = "usr-admin",
            companyId = companyId,
            username = "admin",
            passwordHash = passwordEncoder.encode("password123"),
            fullName = "HQ Super Admin",
            pinCode = passwordEncoder.encode("1234"),
            assignedModules = allModules,
            email = "admin@sunpos.com",
            phone = "081-111-2222",
            isActive = true
        )
        val userOffice = User(
            id = "usr-office",
            companyId = companyId,
            username = "office",
            passwordHash = passwordEncoder.encode("password123"),
            fullName = "HQ Backoffice Officer",
            pinCode = passwordEncoder.encode("4444"),
            assignedModules = listOf("FINANCIAL_MANAGEMENT", "SALES_MARKETING", "PURCHASE", "WAREHOUSE", "PRODUCTION"),
            email = "office@sunpos.com",
            phone = "081-555-6666",
            isActive = true
        )
        val userManager = User(
            id = "usr-manager",
            companyId = companyId,
            username = "manager",
            passwordHash = passwordEncoder.encode("password123"),
            fullName = "Sukhumvit Store Manager",
            pinCode = passwordEncoder.encode("2222"),
            assignedModules = listOf("FINANCIAL_MANAGEMENT", "SALES_MARKETING", "PURCHASE", "WAREHOUSE", "PRODUCTION"),
            email = "manager@sunpos.com",
            phone = "081-222-3333",
            isActive = true
        )
        val userCashier = User(
            id = "usr-cashier",
            companyId = companyId,
            username = "cashier",
            passwordHash = passwordEncoder.encode("password123"),
            fullName = "Frontline Cashier",
            pinCode = passwordEncoder.encode("0000"),
            assignedModules = listOf("SALES_MARKETING"),
            email = "cashier@sunpos.com",
            phone = "081-333-4444",
            isActive = true
        )
        val userWarehouse = User(
            id = "usr-warehouse",
            companyId = companyId,
            username = "warehouse",
            passwordHash = passwordEncoder.encode("password123"),
            fullName = "Central DC Warehouse Staff",
            pinCode = passwordEncoder.encode("3333"),
            assignedModules = listOf("WAREHOUSE", "PURCHASE"),
            email = "warehouse@sunpos.com",
            phone = "081-444-5555",
            isActive = true
        )

        userRepository.save(userAdmin)
        userRepository.save(userOffice)
        userRepository.save(userManager)
        userRepository.save(userCashier)
        userRepository.save(userWarehouse)

        userRoleRepository.save(UserRole(userId = userAdmin.id, roleId = roleSuperAdmin.id))
        userRoleRepository.save(UserRole(userId = userOffice.id, roleId = roleOffice.id))
        userRoleRepository.save(UserRole(userId = userManager.id, roleId = roleManager.id))
        userRoleRepository.save(UserRole(userId = userCashier.id, roleId = roleCashier.id))
        userRoleRepository.save(UserRole(userId = userWarehouse.id, roleId = roleWarehouse.id))

        // 3. Table Types, Zones, Tables
        val typeStd = TableType(id = "type-std", branchId = branchId, name = "Standard Table", code = "STD", isDefault = true)
        val typeBuf = TableType(id = "type-buffet", branchId = branchId, name = "Buffet Table", code = "BUF")
        val typeVip = TableType(id = "type-vip", branchId = branchId, name = "VIP Room Table", code = "VIP")
        tableTypeRepository.save(typeStd)
        tableTypeRepository.save(typeBuf)
        tableTypeRepository.save(typeVip)

        val zoneMain = Zone(id = "zone-main", branchId = branchId, name = "โถงหลัก (Main Dining)", zoneType = "DINE_IN", sortOrder = 1)
        val zoneBuffet = Zone(id = "zone-buffet", branchId = branchId, name = "โซนชาบูบุฟเฟ่ต์ A", zoneType = "BUFFET", sortOrder = 2)
        val zoneBuffetVip = Zone(id = "zone-buffet-vip", branchId = branchId, name = "โซนพรีเมียมบุฟเฟ่ต์ VIP", zoneType = "BUFFET", sortOrder = 3)
        val zoneOutdoor = Zone(id = "zone-outdoor", branchId = branchId, name = "ระเบียงกลางแจ้ง (Outdoor)", zoneType = "DINE_IN", sortOrder = 4)
        zoneRepository.save(zoneMain)
        zoneRepository.save(zoneBuffet)
        zoneRepository.save(zoneBuffetVip)
        zoneRepository.save(zoneOutdoor)

        val tables = listOf(
            RestaurantTable(id = "tbl-01", branchId = branchId, zoneId = "zone-main", tableTypeId = "type-std", nameNumber = "11", capacity = 4),
            RestaurantTable(id = "tbl-02", branchId = branchId, zoneId = "zone-main", tableTypeId = "type-std", nameNumber = "12", capacity = 4),
            RestaurantTable(id = "tbl-03", branchId = branchId, zoneId = "zone-main", tableTypeId = "type-std", nameNumber = "14", capacity = 2),
            RestaurantTable(id = "tbl-04", branchId = branchId, zoneId = "zone-main", tableTypeId = "type-std", nameNumber = "15", capacity = 6),
            RestaurantTable(id = "tbl-05", branchId = branchId, zoneId = "zone-buffet", tableTypeId = "type-buffet", nameNumber = "B-01", capacity = 4),
            RestaurantTable(id = "tbl-06", branchId = branchId, zoneId = "zone-buffet", tableTypeId = "type-buffet", nameNumber = "B-02", capacity = 4),
            RestaurantTable(id = "tbl-07", branchId = branchId, zoneId = "zone-buffet", tableTypeId = "type-buffet", nameNumber = "B-03", capacity = 6),
            RestaurantTable(id = "tbl-08", branchId = branchId, zoneId = "zone-buffet-vip", tableTypeId = "type-vip", nameNumber = "VIP-01", capacity = 8),
            RestaurantTable(id = "tbl-09", branchId = branchId, zoneId = "zone-buffet-vip", tableTypeId = "type-vip", nameNumber = "VIP-02", capacity = 10),
            RestaurantTable(id = "tbl-10", branchId = branchId, zoneId = "zone-outdoor", tableTypeId = "type-std", nameNumber = "OD-01", capacity = 4),
            RestaurantTable(id = "tbl-11", branchId = branchId, zoneId = "zone-outdoor", tableTypeId = "type-std", nameNumber = "OD-02", capacity = 4)
        )
        tables.forEach { tableRepository.save(it) }

        // 4. Menu Categories, Menu Items, Modifiers
        val catMain = MenuCategory(id = "cat-main", branchId = branchId, name = "อาหารจานหลัก", description = "Main Dishes", sortOrder = 1)
        val catSnack = MenuCategory(id = "cat-snack", branchId = branchId, name = "ของทานเล่น", description = "Appetizers", sortOrder = 2)
        val catBuffet = MenuCategory(id = "cat-buffet", branchId = branchId, name = "เนื้อ & บุฟเฟ่ต์", description = "Buffet Items", sortOrder = 3)
        val catDrink = MenuCategory(id = "cat-drink", branchId = branchId, name = "เครื่องดื่ม", description = "Beverages", sortOrder = 4)
        val catDessert = MenuCategory(id = "cat-dessert", branchId = branchId, name = "ของหวาน", description = "Desserts", sortOrder = 5)
        menuCategoryRepository.save(catMain)
        menuCategoryRepository.save(catSnack)
        menuCategoryRepository.save(catBuffet)
        menuCategoryRepository.save(catDrink)
        menuCategoryRepository.save(catDessert)

        // Shabu Items (Brand 1)
        val items = listOf(
            MenuItem(id = "item-01", branchId = "branch-001", categoryId = "cat-main", name = "ข้าวผัดปูพิเศษ", description = "Crab Fried Rice", sku = "SKU-01", basePrice = BigDecimal("120.00"), sortOrder = 1),
            MenuItem(id = "item-02", branchId = "branch-001", categoryId = "cat-main", name = "ต้มยำกุ้งน้ำข้น", description = "Tom Yum Goong", sku = "SKU-02", basePrice = BigDecimal("220.00"), sortOrder = 2),
            MenuItem(id = "item-03", branchId = "branch-001", categoryId = "cat-snack", name = "ปีกไก่ทอดน้ำปลา", description = "Fried Chicken Wings", sku = "SKU-03", basePrice = BigDecimal("95.00"), sortOrder = 3),
            MenuItem(id = "item-04", branchId = "branch-001", categoryId = "cat-buffet", name = "ชุดหมูคุโรบุตะสไลซ์", description = "Kurobuta Pork Set", sku = "SKU-04", basePrice = BigDecimal("250.00"), sortOrder = 4),
            MenuItem(id = "item-05", branchId = "branch-001", categoryId = "cat-buffet", name = "ชุดเนื้อวากิวออสเตรเลีย", description = "Aussie Wagyu Set", sku = "SKU-05", basePrice = BigDecimal("390.00"), sortOrder = 5),
            MenuItem(id = "item-06", branchId = "branch-001", categoryId = "cat-drink", name = "ชามะนาวเย็น", description = "Iced Lemon Tea", sku = "SKU-06", basePrice = BigDecimal("45.00"), sortOrder = 6),
            MenuItem(id = "item-07", branchId = "branch-001", categoryId = "cat-drink", name = "น้ำแร่ธรรมชาติ", description = "Mineral Water", sku = "SKU-07", basePrice = BigDecimal("20.00"), sortOrder = 7),
            MenuItem(id = "item-08", branchId = "branch-001", categoryId = "cat-dessert", name = "ไอศกรีมมัทฉะฮอกไกโด", description = "Matcha Ice Cream", sku = "SKU-08", basePrice = BigDecimal("65.00"), sortOrder = 8)
        )
        items.forEach { menuItemRepository.save(it) }

        // Brand 2: Japanese A la carte Categories & Items (Branch 3)
        val catJpSashimi = MenuCategory(id = "cat-jp-01", branchId = "branch-003", name = "ซาชิมิ & ซูชิพรีเมียม", description = "Sashimi & Sushi", sortOrder = 1)
        val catJpDon = MenuCategory(id = "cat-jp-02", branchId = "branch-003", name = "ข้าวหน้าดงบุริ & วากิว", description = "Donburi & Wagyu", sortOrder = 2)
        val catJpRamen = MenuCategory(id = "cat-jp-03", branchId = "branch-003", name = "ราเมง & เมนูเส้น", description = "Ramen & Noodles", sortOrder = 3)
        val catJpSnack = MenuCategory(id = "cat-jp-04", branchId = "branch-003", name = "เมนูทานเล่น & อิซากายะ", description = "Appetizers & Fried", sortOrder = 4)
        val catJpDrink = MenuCategory(id = "cat-jp-05", branchId = "branch-003", name = "เครื่องดื่มญี่ปุ่น", description = "Japanese Drinks", sortOrder = 5)
        menuCategoryRepository.save(catJpSashimi)
        menuCategoryRepository.save(catJpDon)
        menuCategoryRepository.save(catJpRamen)
        menuCategoryRepository.save(catJpSnack)
        menuCategoryRepository.save(catJpDrink)

        val jpItems = listOf(
            MenuItem(id = "item-jp-01", branchId = "branch-003", categoryId = "cat-jp-01", name = "ซาชิมิแซลมอนนอร์เวย์พรีเมียม (Salmon Sashimi)", description = "Salmon Sashimi 5 pcs", sku = "SKU-JP-001", basePrice = BigDecimal("350.00"), sortOrder = 1),
            MenuItem(id = "item-jp-02", branchId = "branch-003", categoryId = "cat-jp-02", name = "ข้าวหน้าเนื้อวากิวภูเขาไฟไข่ดอง (Wagyu Lava Don)", description = "Wagyu Lava Don with Pickled Yolk", sku = "SKU-JP-002", basePrice = BigDecimal("450.00"), sortOrder = 2),
            MenuItem(id = "item-jp-03", branchId = "branch-003", categoryId = "cat-jp-03", name = "ราเมงซุปกระดูกหมูทงคัตสึชาชู (Tonkotsu Ramen)", description = "Tonkotsu Chashu Ramen", sku = "SKU-JP-003", basePrice = BigDecimal("240.00"), sortOrder = 3),
            MenuItem(id = "item-jp-04", branchId = "branch-003", categoryId = "cat-jp-01", name = "เซ็ตซูชิโอโทโร่ & ปลาไหลอุนางิ (Otoro & Unagi Set)", description = "Otoro & Unagi Sushi Set", sku = "SKU-JP-004", basePrice = BigDecimal("590.00"), sortOrder = 4),
            MenuItem(id = "item-jp-05", branchId = "branch-003", categoryId = "cat-jp-04", name = "เทมปุระกุ้งลายเสือรวมมิตร (Ebi Tempura)", description = "Tiger Prawn Tempura Moriawase", sku = "SKU-JP-005", basePrice = BigDecimal("260.00"), sortOrder = 5),
            MenuItem(id = "item-jp-06", branchId = "branch-003", categoryId = "cat-jp-04", name = "สลัดหนังปลาแซลมอนกรอบน้ำสลัดงา (Salmon Skin Salad)", description = "Crispy Salmon Skin Salad", sku = "SKU-JP-006", basePrice = BigDecimal("180.00"), sortOrder = 6),
            MenuItem(id = "item-jp-07", branchId = "branch-003", categoryId = "cat-jp-04", name = "ไก่ทอดคาราเกะซอสสไปซี่มาโย (Tori Karaage)", description = "Japanese Fried Chicken Spicy Mayo", sku = "SKU-JP-007", basePrice = BigDecimal("145.00"), sortOrder = 7),
            MenuItem(id = "item-jp-08", branchId = "branch-003", categoryId = "cat-jp-05", name = "ชาเขียวมัทฉะอุจิเย็นพรีเมียม (Uji Iced Matcha)", description = "Premium Iced Uji Matcha", sku = "SKU-JP-008", basePrice = BigDecimal("85.00"), sortOrder = 8)
        )
        jpItems.forEach { menuItemRepository.save(it) }

        // Brand 3: Cafe & Bakery Categories & Items (Branch 4)
        val catCfSpecialty = MenuCategory(id = "cat-cf-01", branchId = "branch-004", name = "สเปเชียลตี้คอฟฟี่", description = "Specialty Coffee", sortOrder = 1)
        val catCfTea = MenuCategory(id = "cat-cf-02", branchId = "branch-004", name = "ชา & เครื่องดื่มเย็นสดชื่น", description = "Tea & Refreshers", sortOrder = 2)
        val catCfBakery = MenuCategory(id = "cat-cf-03", branchId = "branch-004", name = "ครัวซองต์ & เบเกอรี่อบสด", description = "Artisan Bakery", sortOrder = 3)
        val catCfDessert = MenuCategory(id = "cat-cf-04", branchId = "branch-004", name = "เค้กโฮมเมด & ของหวาน", description = "Homemade Cakes", sortOrder = 4)
        menuCategoryRepository.save(catCfSpecialty)
        menuCategoryRepository.save(catCfTea)
        menuCategoryRepository.save(catCfBakery)
        menuCategoryRepository.save(catCfDessert)

        val cfItems = listOf(
            MenuItem(id = "item-cf-01", branchId = "branch-004", categoryId = "cat-cf-01", name = "Signature Dirty Coffee (เดอร์ตี้ กาแฟนมสกัดเย็น)", description = "Signature Cold Milk Dirty Espresso", sku = "SKU-CF-001", basePrice = BigDecimal("120.00"), sortOrder = 1),
            MenuItem(id = "item-cf-02", branchId = "branch-004", categoryId = "cat-cf-01", name = "Cold Brew Yuzu Orange Tonic (โคลด์บรูว์ ส้มยูซุ)", description = "Cold Brew Yuzu Orange Sparkling Tonic", sku = "SKU-CF-002", basePrice = BigDecimal("135.00"), sortOrder = 2),
            MenuItem(id = "item-cf-03", branchId = "branch-004", categoryId = "cat-cf-01", name = "Artisan Hot Cafe Latte (ลาเต้ร้อน เมล็ดไทย-บราซิล)", description = "Hot Cafe Latte Thai-Brazil Blend", sku = "SKU-CF-003", basePrice = BigDecimal("95.00"), sortOrder = 3),
            MenuItem(id = "item-cf-04", branchId = "branch-004", categoryId = "cat-cf-01", name = "Iced Salted Caramel Macchiato", description = "Iced Salted Caramel Macchiato", sku = "SKU-CF-004", basePrice = BigDecimal("125.00"), sortOrder = 4),
            MenuItem(id = "item-cf-05", branchId = "branch-004", categoryId = "cat-cf-03", name = "ครัวซองต์เนยสดฝรั่งเศส (French Butter Croissant)", description = "Fresh Baked French Butter Croissant", sku = "SKU-CF-005", basePrice = BigDecimal("85.00"), sortOrder = 5),
            MenuItem(id = "item-cf-06", branchId = "branch-004", categoryId = "cat-cf-03", name = "ครัวซองต์อัลมอนด์ครีมสด (Almond Croissant)", description = "Almond Cream Topped Croissant", sku = "SKU-CF-006", basePrice = BigDecimal("115.00"), sortOrder = 6),
            MenuItem(id = "item-cf-07", branchId = "branch-004", categoryId = "cat-cf-02", name = "ชาไทยพรีเมียมเย็น Signature (Royal Iced Thai Tea)", description = "Royal Iced Thai Tea", sku = "SKU-CF-007", basePrice = BigDecimal("90.00"), sortOrder = 7),
            MenuItem(id = "item-cf-08", branchId = "branch-004", categoryId = "cat-cf-04", name = "บาสก์ชีสเค้กหน้าไหม้ (Basque Burnt Cheesecake)", description = "Spanish Basque Burnt Cheesecake", sku = "SKU-CF-008", basePrice = BigDecimal("150.00"), sortOrder = 8)
        )
        cfItems.forEach { menuItemRepository.save(it) }

        // Modifiers
        val modSpicy = ModifierGroup(id = "modg-spicy", branchId = branchId, name = "ระดับความเผ็ด (Spiciness)", minSelection = 0, maxSelection = 1)
        val modSweet = ModifierGroup(id = "modg-sweet", branchId = branchId, name = "ระดับความหวาน (Sweetness)", minSelection = 0, maxSelection = 1)
        modifierGroupRepository.save(modSpicy)
        modifierGroupRepository.save(modSweet)

        modifierRepository.save(Modifier(id = "mod-spicy-none", modifierGroupId = "modg-spicy", name = "ไม่เผ็ด", price = BigDecimal.ZERO))
        modifierRepository.save(Modifier(id = "mod-spicy-low", modifierGroupId = "modg-spicy", name = "เผ็ดน้อย", price = BigDecimal.ZERO))
        modifierRepository.save(Modifier(id = "mod-spicy-high", modifierGroupId = "modg-spicy", name = "เผ็ดมาก (+฿10)", price = BigDecimal("10.00")))

        modifierRepository.save(Modifier(id = "mod-sweet-100", modifierGroupId = "modg-sweet", name = "หวานปกติ (100%)", price = BigDecimal.ZERO))
        modifierRepository.save(Modifier(id = "mod-sweet-50", modifierGroupId = "modg-sweet", name = "หวานน้อย (50%)", price = BigDecimal.ZERO))
        modifierRepository.save(Modifier(id = "mod-sweet-0", modifierGroupId = "modg-sweet", name = "ไม่หวาน (0%)", price = BigDecimal.ZERO))

        // 5. Buffet Promotions & Tiers (Multi-Brand)
        // ── Brand 1: Shabu Master Buffet ──
        val promoStd = BuffetPromotion(
            id = "promo-buf-std",
            brandId = "brand-001",
            branchId = "branch-001",
            name = "Standard Buffet ฿399",
            pricePerPerson = BigDecimal("399.00"),
            durationMinutes = 90,
            status = BuffetPromotionStatus.ACTIVE
        )
        val promoPrem = BuffetPromotion(
            id = "promo-buf-prem",
            brandId = "brand-001",
            branchId = "branch-001",
            name = "Premium Wagyu Buffet ฿599",
            pricePerPerson = BigDecimal("599.00"),
            durationMinutes = 120,
            status = BuffetPromotionStatus.ACTIVE
        )
        buffetPromotionRepository.save(promoStd)
        buffetPromotionRepository.save(promoPrem)

        val tierStd = BuffetPromotionTier(
            id = "tier-std-399",
            promotionId = "promo-buf-std",
            name = "Standard Buffet ฿399",
            adultPrice = BigDecimal("399.00"),
            childPrice = BigDecimal("199.00"),
            timeLimitMinutes = 90,
            brandId = "brand-001",
            branchId = "branch-001",
            isActive = true
        )
        val tierPrem = BuffetPromotionTier(
            id = "tier-prem-599",
            promotionId = "promo-buf-prem",
            name = "Premium Wagyu Buffet ฿599",
            adultPrice = BigDecimal("599.00"),
            childPrice = BigDecimal("299.00"),
            timeLimitMinutes = 120,
            brandId = "brand-001",
            branchId = "branch-001",
            isActive = true
        )
        buffetPromotionTierRepository.save(tierStd)
        buffetPromotionTierRepository.save(tierPrem)

        // Eligible menu items for Brand 1
        val stdItemIds = listOf("item-03", "item-04", "item-06", "item-07", "item-08")
        val premItemIds = listOf("item-01", "item-02", "item-03", "item-04", "item-05", "item-06", "item-07", "item-08")

        stdItemIds.forEach { itemId ->
            buffetTierMenuItemRepository.save(BuffetTierMenuItem(buffetTierId = tierStd.id, menuItemId = itemId))
            buffetPromotionMenuItemRepository.save(BuffetPromotionMenuItem(promotionId = promoStd.id, menuItemId = itemId))
        }
        premItemIds.forEach { itemId ->
            buffetTierMenuItemRepository.save(BuffetTierMenuItem(buffetTierId = tierPrem.id, menuItemId = itemId))
            buffetPromotionMenuItemRepository.save(BuffetPromotionMenuItem(promotionId = promoPrem.id, menuItemId = itemId))
        }

        // ── Brand 2: Tokyo Yakiniku Buffet (Branch 3) ──
        val promoYaki699 = BuffetPromotion(
            id = "promo-buf-yaki-699",
            brandId = "brand-002",
            branchId = "branch-003",
            name = "Yakiniku Classic Buffet ฿699",
            pricePerPerson = BigDecimal("699.00"),
            durationMinutes = 100,
            status = BuffetPromotionStatus.ACTIVE
        )
        val promoYaki999 = BuffetPromotion(
            id = "promo-buf-yaki-999",
            brandId = "brand-002",
            branchId = "branch-003",
            name = "Ultimate Wagyu & Seafood ฿999",
            pricePerPerson = BigDecimal("999.00"),
            durationMinutes = 120,
            status = BuffetPromotionStatus.ACTIVE
        )
        buffetPromotionRepository.save(promoYaki699)
        buffetPromotionRepository.save(promoYaki999)

        val tierYaki699 = BuffetPromotionTier(
            id = "tier-yaki-699",
            promotionId = "promo-buf-yaki-699",
            name = "Yakiniku Classic Buffet ฿699",
            adultPrice = BigDecimal("699.00"),
            childPrice = BigDecimal("349.00"),
            timeLimitMinutes = 100,
            brandId = "brand-002",
            branchId = "branch-003",
            isActive = true
        )
        val tierYaki999 = BuffetPromotionTier(
            id = "tier-yaki-999",
            promotionId = "promo-buf-yaki-999",
            name = "Ultimate Wagyu & Seafood ฿999",
            adultPrice = BigDecimal("999.00"),
            childPrice = BigDecimal("499.00"),
            timeLimitMinutes = 120,
            brandId = "brand-002",
            branchId = "branch-003",
            isActive = true
        )
        buffetPromotionTierRepository.save(tierYaki699)
        buffetPromotionTierRepository.save(tierYaki999)

        val yakiItemIds = listOf("item-jp-01", "item-jp-02", "item-jp-03", "item-jp-04", "item-jp-05", "item-jp-06", "item-jp-07", "item-jp-08")
        yakiItemIds.forEach { itemId ->
            buffetTierMenuItemRepository.save(BuffetTierMenuItem(buffetTierId = tierYaki699.id, menuItemId = itemId))
            buffetPromotionMenuItemRepository.save(BuffetPromotionMenuItem(promotionId = promoYaki699.id, menuItemId = itemId))
            buffetTierMenuItemRepository.save(BuffetTierMenuItem(buffetTierId = tierYaki999.id, menuItemId = itemId))
            buffetPromotionMenuItemRepository.save(BuffetPromotionMenuItem(promotionId = promoYaki999.id, menuItemId = itemId))
        }

        // ── Brand 3: Sunrise Cafe & Bakery Buffet (Branch 4) ──
        val promoCafe299 = BuffetPromotion(
            id = "promo-buf-cafe-299",
            brandId = "brand-003",
            branchId = "branch-004",
            name = "Afternoon High Tea & Bakery Buffet ฿299",
            pricePerPerson = BigDecimal("299.00"),
            durationMinutes = 90,
            status = BuffetPromotionStatus.ACTIVE
        )
        buffetPromotionRepository.save(promoCafe299)

        val tierCafe299 = BuffetPromotionTier(
            id = "tier-cafe-299",
            promotionId = "promo-buf-cafe-299",
            name = "Afternoon High Tea & Bakery Buffet ฿299",
            adultPrice = BigDecimal("299.00"),
            childPrice = BigDecimal("149.00"),
            timeLimitMinutes = 90,
            brandId = "brand-003",
            branchId = "branch-004",
            isActive = true
        )
        buffetPromotionTierRepository.save(tierCafe299)

        // 6. CRM Memberships & Points
        val tierSilver = MembershipTier(id = "tier-silver", companyId = companyId, code = "SILVER", name = "Silver Member", rankLevel = 1, minimumSpent = BigDecimal.ZERO, pointMultiplier = BigDecimal.ONE, discountPercentage = BigDecimal.ZERO)
        val tierGold = MembershipTier(id = "tier-gold", companyId = companyId, code = "GOLD", name = "Gold Member", rankLevel = 2, minimumSpent = BigDecimal("5000.00"), pointMultiplier = BigDecimal("1.50"), discountPercentage = BigDecimal("5.00"))
        val tierPlatinum = MembershipTier(id = "tier-platinum", companyId = companyId, code = "PLATINUM", name = "Platinum Member", rankLevel = 3, minimumSpent = BigDecimal("15000.00"), pointMultiplier = BigDecimal("2.00"), discountPercentage = BigDecimal("10.00"))
        membershipTierRepository.save(tierSilver)
        membershipTierRepository.save(tierGold)
        membershipTierRepository.save(tierPlatinum)

        val customer1 = Customer(
            id = "cust-001",
            companyId = companyId,
            firstName = "สมชาย",
            lastName = "ใจดี",
            displayName = "สมชาย ใจดี",
            primaryBranchId = branchId,
            status = "ACTIVE"
        )
        val customer2 = Customer(
            id = "cust-002",
            companyId = companyId,
            firstName = "วิภา",
            lastName = "สุขสมบูรณ์",
            displayName = "วิภา สุขสมบูรณ์",
            primaryBranchId = branchId,
            status = "ACTIVE"
        )
        customerRepository.save(customer1)
        customerRepository.save(customer2)
        customerIdentityRepository.save(CustomerIdentity(customerId = customer1.id, companyId = companyId, identityType = IdentityType.PHONE, identityValue = "0812345678", isPrimary = true))
        customerIdentityRepository.save(CustomerIdentity(customerId = customer2.id, companyId = companyId, identityType = IdentityType.PHONE, identityValue = "0898765432", isPrimary = true))

        pointLedgerRepository.save(PointLedger(customerId = customer1.id, points = BigDecimal("150.00"), transactionType = PointTransactionType.EARN, notes = "Point earned from Order #ORD-101"))
        pointLedgerRepository.save(PointLedger(customerId = customer1.id, points = BigDecimal("-50.00"), transactionType = PointTransactionType.REDEEM, notes = "Point redeemed for ฿50 discount"))

        // 7. Warehouse, Raw Ingredients & Stocks (WAC)
        val warehouse1 = Warehouse(id = "wh-001", branchId = "branch-001", name = "คลังหลักสุขุมวิท 24 (Sukhumvit 24 WH)", code = "WH-SUK-01", isCentral = true)
        val warehouse2 = Warehouse(id = "wh-002", branchId = "branch-002", name = "คลังหลักสุขุมวิท อโศก (Sukhumvit Asoke WH)", code = "WH-SUK-02", isCentral = false)
        val warehouse3 = Warehouse(id = "wh-003", branchId = "branch-003", name = "คลังวัตถุดิบอาหารญี่ปุ่นทองหล่อ (Thonglor Kitchen WH)", code = "WH-JPN-01", isCentral = false)
        val warehouse4 = Warehouse(id = "wh-004", branchId = "branch-004", name = "คลังวัตถุดิบและเบเกอรี่อารีย์ (Ari Cafe & Bakery WH)", code = "WH-CAF-01", isCentral = false)
        warehouseRepository.save(warehouse1)
        warehouseRepository.save(warehouse2)
        warehouseRepository.save(warehouse3)
        warehouseRepository.save(warehouse4)

        val invPork = InventoryItem(id = "inv-pork", sku = "RAW-PORK", name = "เนื้อหมูคุโรบุตะสไลซ์", categoryName = "MEAT", baseUnit = "g", receivingUnit = "kg", receivingUnitFactor = BigDecimal("1000.0000"), dispenseUnit = "เสิร์ฟ", dispenseUnitFactor = BigDecimal("150.0000"), unit = "kg", conversionFactor = BigDecimal("1000.0000"), minStockAlert = BigDecimal("5.0000"))
        val invBeef = InventoryItem(id = "inv-beef", sku = "RAW-BEEF", name = "เนื้อวากิวออสเตรเลียสไลซ์", categoryName = "MEAT", baseUnit = "g", receivingUnit = "kg", receivingUnitFactor = BigDecimal("1000.0000"), dispenseUnit = "เสิร์ฟ", dispenseUnitFactor = BigDecimal("150.0000"), unit = "kg", conversionFactor = BigDecimal("1000.0000"), minStockAlert = BigDecimal("3.0000"))
        val invRice = InventoryItem(id = "inv-rice", sku = "RAW-RICE", name = "ข้าวหอมมะลิคัดพิเศษ", categoryName = "GRAIN", baseUnit = "g", receivingUnit = "ถุง 5kg", receivingUnitFactor = BigDecimal("5000.0000"), dispenseUnit = "ถ้วย", dispenseUnitFactor = BigDecimal("150.0000"), unit = "ถุง 5kg", conversionFactor = BigDecimal("5000.0000"), minStockAlert = BigDecimal("10.0000"))
        val invCrab = InventoryItem(id = "inv-crab", sku = "RAW-CRAB", name = "เนื้อปูม้าแกะก้อนพรีเมียม", categoryName = "SEAFOOD", baseUnit = "g", receivingUnit = "แพ็ค 1kg", receivingUnitFactor = BigDecimal("1000.0000"), dispenseUnit = "portion", dispenseUnitFactor = BigDecimal("80.0000"), unit = "แพ็ค 1kg", conversionFactor = BigDecimal("1000.0000"), minStockAlert = BigDecimal("2.0000"))
        val invTea = InventoryItem(id = "inv-tea", sku = "RAW-TEA", name = "ใบชาซีลอนแท้อบแห้ง", categoryName = "BEVERAGE", baseUnit = "g", receivingUnit = "ถุง 1kg", receivingUnitFactor = BigDecimal("1000.0000"), dispenseUnit = "g", dispenseUnitFactor = BigDecimal("1.0000"), unit = "ถุง 1kg", conversionFactor = BigDecimal("1000.0000"), minStockAlert = BigDecimal("2.0000"))
        val invSalmon = InventoryItem(id = "inv-salmon", sku = "RAW-SALMON", name = "ปลาแซลมอนนอร์เวย์สด", categoryName = "SEAFOOD", baseUnit = "g", receivingUnit = "kg", receivingUnitFactor = BigDecimal("1000.0000"), dispenseUnit = "เสิร์ฟ", dispenseUnitFactor = BigDecimal("120.0000"), unit = "kg", conversionFactor = BigDecimal("1000.0000"), minStockAlert = BigDecimal("4.0000"))
        val invCoffee = InventoryItem(id = "inv-coffee", sku = "RAW-COFFEE", name = "เมล็ดกาแฟไทย-บราซิล Specialty Blend", categoryName = "COFFEE", baseUnit = "g", receivingUnit = "ถุง 1kg", receivingUnitFactor = BigDecimal("1000.0000"), dispenseUnit = "ช็อต", dispenseUnitFactor = BigDecimal("18.0000"), unit = "ถุง 1kg", conversionFactor = BigDecimal("1000.0000"), minStockAlert = BigDecimal("5.0000"))
        val invButter = InventoryItem(id = "inv-butter", sku = "RAW-BUTTER", name = "เนยสดแท้ฝรั่งเศส (French Butter)", categoryName = "DAIRY", baseUnit = "g", receivingUnit = "ก้อน 500g", receivingUnitFactor = BigDecimal("500.0000"), dispenseUnit = "g", dispenseUnitFactor = BigDecimal("1.0000"), unit = "ก้อน 500g", conversionFactor = BigDecimal("500.0000"), minStockAlert = BigDecimal("3.0000"))
        val invMatcha = InventoryItem(id = "inv-matcha", sku = "RAW-MATCHA", name = "ผงมัทฉะอุจิแท้เกรดพิธีการ", categoryName = "BEVERAGE", baseUnit = "g", receivingUnit = "กระป๋อง 500g", receivingUnitFactor = BigDecimal("500.0000"), dispenseUnit = "ช้อน", dispenseUnitFactor = BigDecimal("5.0000"), unit = "กระป๋อง 500g", conversionFactor = BigDecimal("500.0000"), minStockAlert = BigDecimal("2.0000"))
        inventoryItemRepository.save(invPork)
        inventoryItemRepository.save(invBeef)
        inventoryItemRepository.save(invRice)
        inventoryItemRepository.save(invCrab)
        inventoryItemRepository.save(invTea)
        inventoryItemRepository.save(invSalmon)
        inventoryItemRepository.save(invCoffee)
        inventoryItemRepository.save(invButter)
        inventoryItemRepository.save(invMatcha)

        // Seed stock levels with WAC
        inventoryStockRepository.save(InventoryStock(warehouseId = warehouse1.id, inventoryItemId = invPork.id, quantity = BigDecimal("45.5000"), weightedAverageCost = BigDecimal("180.0000")))
        inventoryStockRepository.save(InventoryStock(warehouseId = warehouse1.id, inventoryItemId = invBeef.id, quantity = BigDecimal("28.0000"), weightedAverageCost = BigDecimal("450.0000")))
        inventoryStockRepository.save(InventoryStock(warehouseId = warehouse1.id, inventoryItemId = invRice.id, quantity = BigDecimal("120.0000"), weightedAverageCost = BigDecimal("35.0000")))
        inventoryStockRepository.save(InventoryStock(warehouseId = warehouse2.id, inventoryItemId = invPork.id, quantity = BigDecimal("30.0000"), weightedAverageCost = BigDecimal("180.0000")))
        inventoryStockRepository.save(InventoryStock(warehouseId = warehouse3.id, inventoryItemId = invSalmon.id, quantity = BigDecimal("18.5000"), weightedAverageCost = BigDecimal("520.0000")))
        inventoryStockRepository.save(InventoryStock(warehouseId = warehouse3.id, inventoryItemId = invMatcha.id, quantity = BigDecimal("8.0000"), weightedAverageCost = BigDecimal("800.0000")))
        inventoryStockRepository.save(InventoryStock(warehouseId = warehouse4.id, inventoryItemId = invCoffee.id, quantity = BigDecimal("25.0000"), weightedAverageCost = BigDecimal("450.0000")))
        inventoryStockRepository.save(InventoryStock(warehouseId = warehouse4.id, inventoryItemId = invButter.id, quantity = BigDecimal("15.0000"), weightedAverageCost = BigDecimal("280.0000")))

        // Movements & Waste
        stockMovementRepository.save(StockMovement(warehouseId = warehouse1.id, inventoryItemId = invPork.id, movementType = MovementType.PURCHASE, quantity = BigDecimal("50.0000"), unitCost = BigDecimal("180.0000"), referenceId = "GRN-2026-001"))
        stockMovementRepository.save(StockMovement(warehouseId = warehouse1.id, inventoryItemId = invBeef.id, movementType = MovementType.PURCHASE, quantity = BigDecimal("30.0000"), unitCost = BigDecimal("450.0000"), referenceId = "GRN-2026-001"))
        stockMovementRepository.save(StockMovement(warehouseId = warehouse1.id, inventoryItemId = invPork.id, movementType = MovementType.SALE_CONSUMPTION, quantity = BigDecimal("4.5000"), unitCost = BigDecimal("180.0000"), referenceId = "ORD-001"))
        stockWasteRepository.save(StockWaste(warehouseId = warehouse1.id, inventoryItemId = invPork.id, quantity = BigDecimal("0.5000"), unit = "kg", reason = "หมดอายุ / เสียสภาพ", unitCost = BigDecimal("180.0000"), approvedBy = "usr-admin"))

        // 8. Purchasing & Suppliers
        val sup1 = Supplier(id = "sup-001", code = "SUP-MEAT-01", name = "สยามฟู้ด ซัพพลาย จำกัด (เนื้อ & สัตว์ปีก)", contactPerson = "คุณกิตติศักดิ์", phone = "02-888-9999", email = "sales@siamfood.com")
        val sup2 = Supplier(id = "sup-002", code = "SUP-AGRI-02", name = "เจริญฟาร์ม เกษตรอินทรีย์ จำกัด", contactPerson = "คุณนภาพร", phone = "02-777-6666", email = "contact@charoenfarm.com")
        supplierRepository.save(sup1)
        supplierRepository.save(sup2)

        supplierPriceHistoryRepository.save(SupplierPriceHistory(supplierId = sup1.id, inventoryItemId = invPork.id, price = BigDecimal("180.0000")))
        supplierPriceHistoryRepository.save(SupplierPriceHistory(supplierId = sup1.id, inventoryItemId = invBeef.id, price = BigDecimal("450.0000")))

        val po1 = PurchaseOrder(id = "po-001", poNumber = "PO-2026-0001", supplierId = sup1.id, warehouseId = warehouse1.id, status = POStatus.RECEIVED, totalExpectedAmount = BigDecimal("22500.00"), createdBy = "usr-admin")
        purchaseOrderRepository.save(po1)
        purchaseOrderItemRepository.save(PurchaseOrderItem(purchaseOrderId = po1.id, inventoryItemId = invPork.id, orderedQty = BigDecimal("50.0000"), receivedQty = BigDecimal("50.0000"), unit = "kg", expectedPrice = BigDecimal("180.0000"), totalPrice = BigDecimal("9000.0000")))
        purchaseOrderItemRepository.save(PurchaseOrderItem(purchaseOrderId = po1.id, inventoryItemId = invBeef.id, orderedQty = BigDecimal("30.0000"), receivedQty = BigDecimal("30.0000"), unit = "kg", expectedPrice = BigDecimal("450.0000"), totalPrice = BigDecimal("13500.0000")))

        goodsReceiveRepository.save(GoodsReceive(id = "grn-001", grnNumber = "GRN-2026-0001", purchaseOrderId = po1.id, warehouseId = warehouse1.id, totalReceivedAmount = BigDecimal("22500.00"), receivedBy = "usr-admin"))

        // 9. Recipes & BOM (Multi-brand Standard Recipes)
        val recipeCrabRice = Recipe(id = "rec-crab-rice", menuItemId = "item-01", name = "สูตรข้าวผัดปูพิเศษ v1", version = "v1.0", yieldQuantity = BigDecimal.ONE, yieldUnit = "จาน")
        val recipePorkSlice = Recipe(id = "rec-pork-slice", menuItemId = "item-02", name = "สูตรสันคอหมูคุโรบุตะสไลซ์ v1", version = "v1.0", yieldQuantity = BigDecimal.ONE, yieldUnit = "ถาด")
        val recipeBeefSlice = Recipe(id = "rec-beef-slice", menuItemId = "item-03", name = "สูตรเนื้อริบอายพรีเมียม v1", version = "v1.0", yieldQuantity = BigDecimal.ONE, yieldUnit = "ถาด")
        val recipeWagyu = Recipe(id = "rec-wagyu-set", menuItemId = "item-05", name = "สูตรชุดเนื้อวากิวออสเตรเลีย v1", version = "v1.0", yieldQuantity = BigDecimal.ONE, yieldUnit = "ชุด")
        val recipeSalmonSashimi = Recipe(id = "rec-salmon-sashimi", menuItemId = "item-jp-01", name = "สูตรซาชิมิแซลมอนนอร์เวย์ 5 ชิ้น v1", version = "v1.0", yieldQuantity = BigDecimal.ONE, yieldUnit = "ที่")
        val recipeWagyuDon = Recipe(id = "rec-wagyu-don", menuItemId = "item-jp-02", name = "สูตรข้าวหน้าเนื้อวากิวภูเขาไฟ v1", version = "v1.0", yieldQuantity = BigDecimal.ONE, yieldUnit = "ชาม")
        val recipeDirtyCoffee = Recipe(id = "rec-dirty-coffee", menuItemId = "item-cf-01", name = "สูตร Signature Dirty Coffee v1", version = "v1.0", yieldQuantity = BigDecimal.ONE, yieldUnit = "แก้ว")
        val recipeCroissant = Recipe(id = "rec-croissant", menuItemId = "item-cf-05", name = "สูตรครัวซองต์เนยสดฝรั่งเศส v1", version = "v1.0", yieldQuantity = BigDecimal.ONE, yieldUnit = "ชิ้น")
        val recipeMatchaTea = Recipe(id = "rec-matcha-tea", menuItemId = "item-jp-08", name = "สูตรชาเขียวมัทฉะอุจิพรีเมียม v1", version = "v1.0", yieldQuantity = BigDecimal.ONE, yieldUnit = "แก้ว")

        recipeRepository.save(recipeCrabRice)
        recipeRepository.save(recipePorkSlice)
        recipeRepository.save(recipeBeefSlice)
        recipeRepository.save(recipeWagyu)
        recipeRepository.save(recipeSalmonSashimi)
        recipeRepository.save(recipeWagyuDon)
        recipeRepository.save(recipeDirtyCoffee)
        recipeRepository.save(recipeCroissant)
        recipeRepository.save(recipeMatchaTea)

        // Recipe Ingredients
        recipeIngredientRepository.save(RecipeIngredient(recipeId = recipeCrabRice.id, inventoryItemId = invRice.id, quantity = BigDecimal("0.2000"), unit = "kg"))
        recipeIngredientRepository.save(RecipeIngredient(recipeId = recipeCrabRice.id, inventoryItemId = invCrab.id, quantity = BigDecimal("0.0800"), unit = "kg"))
        recipeIngredientRepository.save(RecipeIngredient(recipeId = recipePorkSlice.id, inventoryItemId = invPork.id, quantity = BigDecimal("0.1500"), unit = "kg"))
        recipeIngredientRepository.save(RecipeIngredient(recipeId = recipeBeefSlice.id, inventoryItemId = invBeef.id, quantity = BigDecimal("0.1500"), unit = "kg"))
        recipeIngredientRepository.save(RecipeIngredient(recipeId = recipeWagyu.id, inventoryItemId = invBeef.id, quantity = BigDecimal("0.2500"), unit = "kg"))
        recipeIngredientRepository.save(RecipeIngredient(recipeId = recipeSalmonSashimi.id, inventoryItemId = invSalmon.id, quantity = BigDecimal("0.1600"), unit = "kg"))
        recipeIngredientRepository.save(RecipeIngredient(recipeId = recipeWagyuDon.id, inventoryItemId = invBeef.id, quantity = BigDecimal("0.1800"), unit = "kg"))
        recipeIngredientRepository.save(RecipeIngredient(recipeId = recipeWagyuDon.id, inventoryItemId = invRice.id, quantity = BigDecimal("0.2200"), unit = "kg"))
        recipeIngredientRepository.save(RecipeIngredient(recipeId = recipeDirtyCoffee.id, inventoryItemId = invCoffee.id, quantity = BigDecimal("0.0200"), unit = "kg"))
        recipeIngredientRepository.save(RecipeIngredient(recipeId = recipeCroissant.id, inventoryItemId = invButter.id, quantity = BigDecimal("0.0600"), unit = "kg"))
        recipeIngredientRepository.save(RecipeIngredient(recipeId = recipeMatchaTea.id, inventoryItemId = invMatcha.id, quantity = BigDecimal("0.0100"), unit = "kg"))

        val bomKurobuta = Bom(id = "bom-kuro-prep", finishedInventoryItemId = invPork.id, name = "BOM หมูคุโรบุตะตัดแต่งสไลซ์", plannedOutputQuantity = BigDecimal("10.0000"), outputUnit = "kg")
        bomRepository.save(bomKurobuta)

        buffetPackageRecipeRepository.save(BuffetPackageRecipe(buffetTierId = tierStd.id, inventoryItemId = invPork.id, quantityPerHead = BigDecimal("0.3500"), unit = "kg"))
        buffetPackageRecipeRepository.save(BuffetPackageRecipe(buffetTierId = tierPrem.id, inventoryItemId = invBeef.id, quantityPerHead = BigDecimal("0.4500"), unit = "kg"))

        // 10. Promotions & Coupons
        val promo10 = Promotion(
            id = "promo-10pct",
            code = "SUMMER10",
            name = "Summer Special 10% Off",
            promoType = PromotionType.PERCENTAGE,
            discountRate = BigDecimal("10.00"),
            priority = 10,
            isActive = true,
            startAt = Instant.now().minus(7, ChronoUnit.DAYS),
            endAt = Instant.now().plus(30, ChronoUnit.DAYS),
            branchId = branchId
        )
        promotionRepository.save(promo10)

        val coupon1 = Coupon(
            id = "cp-welcome50",
            code = "WELCOME50",
            name = "Welcome Coupon ฿50",
            description = "คูปองต้อนรับสมาชิกใหม่ ลดทันที ฿50",
            type = CouponType.FIXED,
            value = BigDecimal("50.00"),
            minSpend = BigDecimal("300.00"),
            maxUses = 500,
            currentUses = 42,
            status = CouponStatus.ACTIVE,
            expiresAt = Instant.now().plus(60, ChronoUnit.DAYS)
        )
        couponRepository.save(coupon1)

        // 11. Business Day & Shifts
        val bDay = BusinessDay(
            id = "bday-${LocalDate.now()}-$branchId",
            branchId = branchId,
            businessDate = LocalDate.now().toString(),
            status = BusinessDayStatus.OPEN,
            openedAt = Instant.now().minus(8, ChronoUnit.HOURS)
        )
        businessDayRepository.save(bDay)

        val shift1 = CashierShift(
            id = "shift-001",
            branchId = branchId,
            deviceId = device1.id,
            userId = userCashier.id,
            openingCash = BigDecimal("2000.00"),
            status = ShiftStatus.OPEN,
            openedAt = Instant.now().minus(4, ChronoUnit.HOURS)
        )
        cashierShiftRepository.save(shift1)

        // 12. Sample Orders & Payments
        val order1 = Order(
            id = "ord-001",
            branchId = branchId,
            tableId = "tbl-01",
            orderNumber = "ORD-2026-0001",
            status = OrderStatus.COMPLETED,
            subtotalAmount = BigDecimal("460.00"),
            totalAmount = BigDecimal("460.00"),
            businessDayId = bDay.id,
            createdBy = "usr-cashier"
        )
        val order2 = Order(
            id = "ord-002",
            branchId = branchId,
            tableId = "tbl-05",
            orderNumber = "ORD-2026-0002",
            status = OrderStatus.COMPLETED,
            subtotalAmount = BigDecimal("798.00"),
            totalAmount = BigDecimal("798.00"),
            businessDayId = bDay.id,
            createdBy = "usr-cashier"
        )
        orderRepository.save(order1)
        orderRepository.save(order2)

        orderItemRepository.save(OrderItem(orderId = order1.id, menuItemId = "item-01", quantity = BigDecimal("2.0"), unitPriceSnapshot = BigDecimal("120.00"), subtotal = BigDecimal("240.00"), nameSnapshot = "ข้าวผัดปูพิเศษ"))
        orderItemRepository.save(OrderItem(orderId = order1.id, menuItemId = "item-02", quantity = BigDecimal("1.0"), unitPriceSnapshot = BigDecimal("220.00"), subtotal = BigDecimal("220.00"), nameSnapshot = "ต้มยำกุ้งน้ำข้น"))

        paymentTransactionRepository.save(PaymentTransaction(id = "tx-001", orderId = order1.id, branchId = branchId, amount = BigDecimal("460.00"), paymentMethod = PaymentMethod.PROMPTPAY, status = PaymentStatus.SUCCESS))
        paymentTransactionRepository.save(PaymentTransaction(id = "tx-002", orderId = order2.id, branchId = branchId, amount = BigDecimal("798.00"), paymentMethod = PaymentMethod.CARD, status = PaymentStatus.SUCCESS))

        // 13. LINE Notifications
        notificationLogRepository.save(
            NotificationLog(
                id = "notif-001",
                recipientId = "U1234567890abcdef",
                channel = "LINE",
                templateType = NotificationTemplate.ORDER_COMPLETED,
                payloadJson = "{\"orderId\":\"ord-001\",\"total\":460.00}",
                status = NotificationStatus.DELIVERED
            )
        )

        logger.info("Cloud Firestore successfully seeded with all Master Mockup Data across all domains!")
    }
}
