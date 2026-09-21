package com.sunpos.backend.domain.inventory

import java.util.UUID

/**
 * How a branch's main warehouse is created when it does not have one yet.
 *
 * Spec 0033 / ticket 04: every branch needs a main warehouse, because it is where stock arrives and
 * where daily sales consumption is deducted. Two paths create branches -- the master data seeder
 * (Spec 0032) and the branch API -- and both go through here so a branch looks the same however it
 * was created. Nothing else may invent a main warehouse: the daily close fails loudly rather than
 * promoting whichever warehouse it finds (see [SalesDeductionWarehouse]).
 */
object MainWarehouseProvisioning {

    const val NAME_PREFIX = "คลังหลัก - "

    /** The main warehouse a new branch starts with, named after the branch it belongs to. */
    fun warehouseFor(branchId: String, branchName: String, branchCode: String): Warehouse = Warehouse(
        id = UUID.randomUUID().toString(),
        branchId = branchId,
        name = NAME_PREFIX + branchName,
        code = codeFor(branchCode, branchId),
        warehouseRole = WarehouseRole.MAIN
    )

    private fun codeFor(branchCode: String, branchId: String): String =
        if (branchCode.isNotBlank()) "WH-$branchCode" else "WH-${branchId.take(8)}"
}
