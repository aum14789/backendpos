package com.sunpos.backend.domain.inventory

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class WacCalculationService {

    companion object {
        const val COST_SCALE = 4
        const val QTY_SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    fun calculateNewWac(
        oldQty: BigDecimal,
        oldWac: BigDecimal,
        receivedQty: BigDecimal,
        receivedUnitCost: BigDecimal
    ): BigDecimal {
        val oQty = oldQty.setScale(QTY_SCALE, ROUNDING)
        val oCost = oldWac.setScale(COST_SCALE, ROUNDING)
        val rQty = receivedQty.setScale(QTY_SCALE, ROUNDING)
        val rCost = receivedUnitCost.setScale(COST_SCALE, ROUNDING)

        val totalNewQty = oQty.add(rQty)
        // When initial stock is negative or zero, or the new net balance remains zero/negative,
        // the new cost basis is fully defined by the incoming receipt cost.
        if (oQty.compareTo(BigDecimal.ZERO) <= 0 || totalNewQty.compareTo(BigDecimal.ZERO) <= 0) {
            return rCost
        }

        val oldTotalVal = oQty.multiply(oCost)
        val receivedTotalVal = rQty.multiply(rCost)
        val grandTotalVal = oldTotalVal.add(receivedTotalVal)

        return grandTotalVal.divide(totalNewQty, COST_SCALE, ROUNDING)
    }
}
