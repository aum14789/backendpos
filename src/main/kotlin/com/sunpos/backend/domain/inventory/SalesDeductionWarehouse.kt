package com.sunpos.backend.domain.inventory

/**
 * Which warehouse receives daily sales consumption.
 *
 * Spec 0033 / ticket 04: the answer comes from the warehouse's own role, never from its name or
 * code. Ticket 01 pulled the previous name-matching rule into this module so the swap happened in
 * one place; the migration that introduced `warehouse_role` is the only code left that ever looked
 * at a name.
 */
object SalesDeductionWarehouse {

    /** The three ways resolving a branch's main warehouse can turn out. */
    sealed interface Resolution {
        /** The branch has exactly one active [WarehouseRole.MAIN] warehouse. */
        data class Found(val warehouse: Warehouse) : Resolution

        /** The branch has no active main warehouse, so it is not ready to close a day. */
        data object Missing : Resolution

        /** More than one active main warehouse: the data contradicts the invariant. */
        data class Ambiguous(val warehouses: List<Warehouse>) : Resolution
    }

    /** Only an active main warehouse receives daily sales consumption. */
    fun mayReceiveSalesConsumption(warehouse: Warehouse): Boolean =
        warehouse.isActive && warehouse.warehouseRole == WarehouseRole.MAIN

    fun branchMainWarehouse(warehouses: List<Warehouse>): Resolution {
        val candidates = warehouses.filter { mayReceiveSalesConsumption(it) }
        return when (candidates.size) {
            0 -> Resolution.Missing
            1 -> Resolution.Found(candidates.first())
            else -> Resolution.Ambiguous(candidates)
        }
    }
}
