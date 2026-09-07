package com.sunpos.backend.domain.inventory

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class UnitConversionService {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    /**
     * Converts a quantity from one unit to another based on conversion definitions.
     * E.g., converting "kg" to "g" (using conversionFactor = 1000.0) or vice versa.
     */
    fun convert(quantity: BigDecimal, fromUnit: String, toUnit: String, conversionFactor: BigDecimal): BigDecimal {
        if (fromUnit.equals(toUnit, ignoreCase = true)) {
            return quantity
        }

        // Canonical conversions:
        // Weight: kg <-> g
        if (fromUnit.equals("kg", ignoreCase = true) && toUnit.equals("g", ignoreCase = true)) {
            return quantity.multiply(conversionFactor).setScale(SCALE, ROUNDING)
        }
        if (fromUnit.equals("g", ignoreCase = true) && toUnit.equals("kg", ignoreCase = true)) {
            return quantity.divide(conversionFactor, SCALE, ROUNDING)
        }

        // Volume: L <-> ml
        if (fromUnit.equals("L", ignoreCase = true) && toUnit.equals("ml", ignoreCase = true)) {
            return quantity.multiply(conversionFactor).setScale(SCALE, ROUNDING)
        }
        if (fromUnit.equals("ml", ignoreCase = true) && toUnit.equals("L", ignoreCase = true)) {
            return quantity.divide(conversionFactor, SCALE, ROUNDING)
        }

        throw IllegalArgumentException("Incompatible unit conversion from $fromUnit to $toUnit")
    }
}
