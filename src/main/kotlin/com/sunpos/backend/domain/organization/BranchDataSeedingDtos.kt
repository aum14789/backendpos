package com.sunpos.backend.domain.organization

import java.time.Instant

data class CloneBranchMasterDataRequest(
    val sourceBranchId: String = "",
    val cloneCatalog: Boolean = true,
    val cloneRecipes: Boolean = true,
    val cloneTables: Boolean = true,
    val clonePrinters: Boolean = true,
    val cloneStaffAccess: Boolean = false
)

data class BranchDataSeedingSummaryDto(
    val targetBranchId: String,
    val sourceBranchId: String,
    val categoriesCloned: Int = 0,
    val menuItemsCloned: Int = 0,
    val modifierGroupsCloned: Int = 0,
    val modifiersCloned: Int = 0,
    val recipesCloned: Int = 0,
    val warehouseCreated: Boolean = false,
    val zonesCloned: Int = 0,
    val tablesCloned: Int = 0,
    val printersCloned: Int = 0,
    val staffAccessGranted: Int = 0,
    val timestamp: Instant = Instant.now()
)
