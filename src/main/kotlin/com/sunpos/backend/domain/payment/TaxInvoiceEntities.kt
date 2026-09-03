package com.sunpos.backend.domain.payment

import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class TaxInvoice(
    val id: String = UUID.randomUUID().toString(),
    var taxInvoiceNumber: String = "",
    var branchId: String = "",
    var customerId: String? = null,
    var taxpayerName: String = "",
    var taxId: String = "",
    var branchNumber: String = "00000",
    var address: String = "",
    var email: String? = null,
    var phone: String? = null,
    var totalNetAmount: BigDecimal = BigDecimal.ZERO,
    var totalTaxAmount: BigDecimal = BigDecimal.ZERO,
    var status: String = "ISSUED", // ISSUED, CANCELLED
    var cancelledAt: Instant? = null,
    var cancelledBy: String? = null,
    var createdBy: String? = null,
    val createdAt: Instant = Instant.now()
)

data class TaxInvoiceReceiptId(
    var taxInvoiceId: String = "",
    var orderId: String = ""
) : Serializable

class TaxInvoiceReceipt(
    val id: String = UUID.randomUUID().toString(),
    var taxInvoiceId: String = "",
    var orderId: String = ""
) {
    constructor(taxInvoiceId: String, orderId: String) : this(
        id = "${taxInvoiceId}_$orderId",
        taxInvoiceId = taxInvoiceId,
        orderId = orderId
    )
}

class TaxInvoiceItemSnapshot(
    val id: String = UUID.randomUUID().toString(),
    var taxInvoiceId: String = "",
    var orderId: String = "",
    var orderItemId: String? = null,
    var itemName: String = "",
    var sku: String? = null,
    var quantity: BigDecimal = BigDecimal.ZERO,
    var unitPrice: BigDecimal = BigDecimal.ZERO,
    var discountAmount: BigDecimal = BigDecimal.ZERO,
    var taxAmount: BigDecimal = BigDecimal.ZERO,
    var netAmount: BigDecimal = BigDecimal.ZERO,
    val createdAt: Instant = Instant.now()
)

data class TaxInvoiceCounterId(
    var branchId: String = "",
    var yearVal: Int = 0
) : Serializable

class TaxInvoiceCounter(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var yearVal: Int = 0,
    var currentVal: Long = 0
) {
    constructor(branchId: String, yearVal: Int, currentVal: Long) : this(
        id = "${branchId}_$yearVal",
        branchId = branchId,
        yearVal = yearVal,
        currentVal = currentVal
    )
}
