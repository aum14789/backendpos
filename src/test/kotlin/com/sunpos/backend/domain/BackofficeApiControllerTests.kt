package com.sunpos.backend.domain

import com.sunpos.backend.domain.catalog.*
import com.sunpos.backend.domain.organization.*
import com.sunpos.backend.domain.order.*
import com.sunpos.backend.domain.table.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.math.BigDecimal
import java.util.Optional

class BackofficeApiControllerTests {

    private lateinit var brandRepository: BrandRepository
    private lateinit var branchRepository: BranchRepository
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var companyRepository: CompanyRepository
    private lateinit var organizationController: OrganizationController

    private lateinit var menuCategoryRepository: MenuCategoryRepository
    private lateinit var menuItemRepository: MenuItemRepository
    private lateinit var modifierGroupRepository: ModifierGroupRepository
    private lateinit var modifierRepository: ModifierRepository
    private lateinit var menuItemModifierGroupRepository: MenuItemModifierGroupRepository
    private lateinit var comboDefinitionRepository: ComboDefinitionRepository
    private lateinit var comboGroupRepository: ComboGroupRepository
    private lateinit var comboChoiceRepository: ComboChoiceRepository
    private lateinit var catalogService: CatalogService
    private lateinit var catalogController: CatalogController

    private lateinit var zoneRepository: ZoneRepository
    private lateinit var tableTypeRepository: TableTypeRepository
    private lateinit var tableRepository: TableRepository
    private lateinit var tableSessionRepository: TableSessionRepository
    private lateinit var tableService: TableService
    private lateinit var tableSessionService: TableSessionService
    private lateinit var tableController: TableController

    private lateinit var buffetPromotionRepository: BuffetPromotionRepository
    private lateinit var buffetPromotionMenuItemRepository: BuffetPromotionMenuItemRepository
    private lateinit var buffetTierRepository: BuffetPromotionTierRepository
    private lateinit var buffetTierMenuItemRepository: BuffetTierMenuItemRepository
    private lateinit var buffetSessionRepository: BuffetSessionRepository
    private lateinit var buffetService: BuffetService
    private lateinit var buffetController: BuffetController

    private lateinit var orderService: OrderService
    private lateinit var orderController: OrderController

    @BeforeEach
    fun setUp() {
        // Organization mocks
        companyRepository = mock(CompanyRepository::class.java)
        brandRepository = mock(BrandRepository::class.java)
        branchRepository = mock(BranchRepository::class.java)
        deviceRepository = mock(DeviceRepository::class.java)
        val activationCodeRepository = mock(ActivationCodeRepository::class.java)
        organizationController = OrganizationController(
            companyRepository, brandRepository, branchRepository, deviceRepository, activationCodeRepository
        )

        // Catalog mocks
        menuCategoryRepository = mock(MenuCategoryRepository::class.java)
        menuItemRepository = mock(MenuItemRepository::class.java)
        modifierGroupRepository = mock(ModifierGroupRepository::class.java)
        modifierRepository = mock(ModifierRepository::class.java)
        menuItemModifierGroupRepository = mock(MenuItemModifierGroupRepository::class.java)
        comboDefinitionRepository = mock(ComboDefinitionRepository::class.java)
        comboGroupRepository = mock(ComboGroupRepository::class.java)
        comboChoiceRepository = mock(ComboChoiceRepository::class.java)
        catalogService = CatalogService(
            menuCategoryRepository, menuItemRepository, modifierGroupRepository,
            modifierRepository, menuItemModifierGroupRepository, comboDefinitionRepository,
            comboGroupRepository, comboChoiceRepository
        )
        catalogController = CatalogController(catalogService)

        // Table mocks
        zoneRepository = mock(ZoneRepository::class.java)
        tableTypeRepository = mock(TableTypeRepository::class.java)
        tableRepository = mock(TableRepository::class.java)
        tableSessionRepository = mock(TableSessionRepository::class.java)
        tableService = TableService(zoneRepository, tableTypeRepository, tableRepository)
        tableSessionService = TableSessionService(tableSessionRepository, tableRepository)
        tableController = TableController(tableService, tableSessionService)

        // Buffet mocks
        buffetPromotionRepository = mock(BuffetPromotionRepository::class.java)
        buffetPromotionMenuItemRepository = mock(BuffetPromotionMenuItemRepository::class.java)
        buffetTierRepository = mock(BuffetPromotionTierRepository::class.java)
        buffetTierMenuItemRepository = mock(BuffetTierMenuItemRepository::class.java)
        buffetSessionRepository = mock(BuffetSessionRepository::class.java)
        buffetService = BuffetService(
            buffetPromotionRepository, buffetPromotionMenuItemRepository,
            buffetTierRepository, buffetTierMenuItemRepository,
            buffetSessionRepository, branchRepository, menuItemRepository
        )
        buffetController = BuffetController(buffetService)

        // Order mocks
        orderService = mock(OrderService::class.java)
        orderController = OrderController(orderService)
    }

    @Test
    fun `test organization controller branches and devices`() {
        val branchList = listOf(
            Branch(id = "b1", companyId = "c1", brandId = "br1", name = "Siam Branch", code = "B-001"),
            Branch(id = "b2", companyId = "c1", brandId = "br1", name = "Silom Branch", code = "B-002")
        )
        `when`(branchRepository.findAll()).thenReturn(branchList)
        `when`(branchRepository.findByBrandId("br1")).thenReturn(branchList)

        // Query with null/blank branchId
        val resAll = organizationController.getBranches(null, null)
        assertTrue(resAll.success)
        assertEquals(2, resAll.data?.size)

        val resFiltered = organizationController.getBranches(null, "br1")
        assertTrue(resFiltered.success)
        assertEquals(2, resFiltered.data?.size)

        // Devices
        val deviceList = listOf(
            Device(id = "d1", branchId = "b1", deviceCode = "POS-01", deviceName = "Main POS", deviceType = "POS_MAIN")
        )
        `when`(deviceRepository.findAll()).thenReturn(deviceList)
        val resDevices = organizationController.getDevices(null)
        assertTrue(resDevices.success)
        assertEquals(1, resDevices.data?.size)
        assertEquals("POS-01", resDevices.data?.first()?.deviceCode)
    }

    @Test
    fun `test catalog controller categories and items`() {
        val categories = listOf(
            MenuCategory(id = "cat-1", branchId = "b1", name = "Grill & BBQ", sortOrder = 1),
            MenuCategory(id = "cat-2", branchId = "b1", name = "Drinks", sortOrder = 2)
        )
        `when`(menuCategoryRepository.findAll()).thenReturn(categories)
        `when`(menuCategoryRepository.findByBranchIdOrderBySortOrderAsc("b1")).thenReturn(categories)

        val resCats = catalogController.getCategories(null)
        assertTrue(resCats.success)
        assertEquals(2, resCats.data?.size)

        val item = MenuItem(
            id = "item-1",
            branchId = "b1",
            categoryId = "cat-1",
            name = "Kurobuta Pork Slice",
            basePrice = BigDecimal("189.00")
        )
        `when`(menuItemRepository.findAll()).thenReturn(listOf(item))
        `when`(menuItemRepository.findById("item-1")).thenReturn(Optional.of(item))
        `when`(menuItemModifierGroupRepository.findByIdMenuItemId("item-1")).thenReturn(emptyList())
        `when`(comboDefinitionRepository.findByMenuItemId("item-1")).thenReturn(Optional.empty())

        val resItems = catalogController.getMenuItems(null, null)
        assertTrue(resItems.success)
        assertEquals(1, resItems.data?.size)
        assertEquals("Kurobuta Pork Slice", resItems.data?.first()?.name)
    }

    @Test
    fun `test table controller zones and tables`() {
        val zones = listOf(
            Zone(id = "z1", branchId = "b1", name = "Main Dining", zoneType = "DINE_IN", sortOrder = 1)
        )
        val tables = listOf(
            RestaurantTable(id = "t1", branchId = "b1", zoneId = "z1", nameNumber = "11", capacity = 4)
        )
        `when`(zoneRepository.findAll()).thenReturn(zones)
        `when`(tableRepository.findAll()).thenReturn(tables)

        val resZones = tableController.getZones(null)
        assertTrue(resZones.success)
        assertEquals(1, resZones.data?.size)

        val resTables = tableController.getTables(null, null)
        assertTrue(resTables.success)
        assertEquals(1, resTables.data?.size)
        assertEquals("11", resTables.data?.first()?.nameNumber)
    }

    @Test
    fun `test buffet controller promotions and tiers`() {
        val promos = listOf(
            BuffetPromotion(
                id = "bp1",
                brandId = "br1",
                branchId = "b1",
                name = "Standard Buffet 399",
                pricePerPerson = BigDecimal("399.00"),
                status = BuffetPromotionStatus.ACTIVE
            )
        )
        `when`(buffetPromotionRepository.findAll()).thenReturn(promos)
        `when`(buffetPromotionMenuItemRepository.findMenuItemIdsByPromotionId("bp1")).thenReturn(listOf("m1", "m2"))

        val res = buffetController.listPromotions(null)
        assertTrue(res.success)
        assertEquals(1, res.data?.size)
        assertEquals("Standard Buffet 399", res.data?.first()?.name)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyObject(): T {
        org.mockito.Mockito.any<T>()
        return null as T
    }

    @Test
    fun `test brand CRUD in organization controller`() {
        val brand = Brand(id = "br-1", companyId = "c1", name = "Sun Shabu", code = "SHABU-01")
        `when`(brandRepository.findAll()).thenReturn(listOf(brand))
        `when`(brandRepository.findById("br-1")).thenReturn(Optional.of(brand))
        `when`(brandRepository.save(anyObject())).thenAnswer { it.arguments[0] }

        val resAll = organizationController.getBrands(null)
        assertTrue(resAll.success)
        assertEquals(1, resAll.data?.size)
        assertEquals("Sun Shabu", resAll.data?.first()?.name)

        val updated = organizationController.updateBrand("br-1", BrandCreateDto(name = "Sun Shabu Premium", code = "SHABU-01"))
        assertTrue(updated.success)
        assertEquals("Sun Shabu Premium", updated.data?.name)

        val deleted = organizationController.deleteBrand("br-1")
        assertTrue(deleted.success)
        assertEquals(true, deleted.data)
    }
}
