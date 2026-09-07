package com.sunpos.backend.domain.payment

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.order.OrderRepository
import com.sunpos.backend.domain.order.OrderItemRepository
import com.sunpos.backend.domain.order.OrderStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Repository
class TaxInvoiceRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<TaxInvoice>(jdbcTemplate, "tax_invoices", TaxInvoice::class.java) {
    fun findByTaxInvoiceNumber(taxInvoiceNumber: String): java.util.Optional<TaxInvoice> =
        findOneByField("taxInvoiceNumber", taxInvoiceNumber)
}

@Repository
class TaxInvoiceReceiptRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<TaxInvoiceReceipt>(jdbcTemplate, "tax_invoice_receipts", TaxInvoiceReceipt::class.java) {
    fun findByIdOrderId(orderId: String): List<TaxInvoiceReceipt> = findByField("orderId", orderId)
    fun findByIdTaxInvoiceId(taxInvoiceId: String): List<TaxInvoiceReceipt> = findByField("taxInvoiceId", taxInvoiceId)
}

@Repository
class TaxInvoiceItemSnapshotRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<TaxInvoiceItemSnapshot>(jdbcTemplate, "tax_invoice_item_snapshots", TaxInvoiceItemSnapshot::class.java) {
    fun findByTaxInvoiceId(taxInvoiceId: String): List<TaxInvoiceItemSnapshot> = findByField("taxInvoiceId", taxInvoiceId)
}

@Repository
class TaxInvoiceCounterRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<TaxInvoiceCounter>(jdbcTemplate, "tax_invoice_counters", TaxInvoiceCounter::class.java) {
    fun findByIdBranchIdAndIdYearVal(branchId: String, yearVal: Int): java.util.Optional<TaxInvoiceCounter> {
        val id = "${branchId}_$yearVal"
        return findById(id)
    }
}

data class CreateTaxInvoiceDto(
    val branchId: String,
    val customerId: String?,
    val taxpayerName: String,
    val taxId: String,
    val branchNumber: String = "00000",
    val address: String,
    val email: String?,
    val phone: String?,
    val orderIds: List<String>,
    val createdBy: String?
)

@Service
class TaxInvoiceService(
    private val taxInvoiceRepository: TaxInvoiceRepository,
    private val taxInvoiceReceiptRepository: TaxInvoiceReceiptRepository,
    private val taxInvoiceItemSnapshotRepository: TaxInvoiceItemSnapshotRepository,
    private val taxInvoiceCounterRepository: TaxInvoiceCounterRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
        val VAT_RATE = BigDecimal("0.07") // 7% VAT
    }

    @Transactional
    fun mergeReceiptsToTaxInvoice(dto: CreateTaxInvoiceDto): TaxInvoice {
        if (dto.orderIds.isEmpty()) {
            throw IllegalArgumentException("Receipt order list cannot be empty")
        }

        var totalGross = BigDecimal.ZERO

        // Validate receipt status and ensure they are not already associated with a tax invoice
        val validatedOrders = mutableListOf<com.sunpos.backend.domain.order.Order>()
        for (orderId in dto.orderIds) {
            val order = orderRepository.findById(orderId)
                .orElseThrow { IllegalArgumentException("Receipt order '$orderId' not found") }
            if (order.status != OrderStatus.COMPLETED) {
                throw IllegalArgumentException("Only COMPLETED orders can be merged into a Tax Invoice")
            }

            val existing = taxInvoiceReceiptRepository.findByIdOrderId(orderId)
            if (existing.isNotEmpty()) {
                throw IllegalArgumentException("Order '$orderId' already associated with Tax Invoice '${existing.first().taxInvoiceId}'")
            }

            totalGross = totalGross.add(order.totalAmount)
            validatedOrders.add(order)
        }

        // Formula for Inclusive VAT:
        // Total Gross = 1,605 => Taxable (Net) = 1,500, VAT 7% = 105
        // Net = Gross / 1.07, VAT = Gross - Net
        val divisor = BigDecimal.ONE.add(VAT_RATE)
        val calculatedNet = totalGross.divide(divisor, SCALE, ROUNDING)
        val calculatedTax = totalGross.subtract(calculatedNet).setScale(SCALE, ROUNDING)

        // Generate Concurrency-Safe Tax Invoice Sequence Number (e.g. TI-2026-000001)
        val currentYear = ZonedDateTime.now(ZoneId.of("Asia/Bangkok")).year
        val counter = taxInvoiceCounterRepository.findByIdBranchIdAndIdYearVal(dto.branchId, currentYear)
            .orElseGet {
                taxInvoiceCounterRepository.save(TaxInvoiceCounter(branchId = dto.branchId, yearVal = currentYear, currentVal = 0))
            }
        counter.currentVal += 1
        taxInvoiceCounterRepository.save(counter)

        val seqFormatted = String.format("%06d", counter.currentVal)
        val invoiceNo = "TI-$currentYear-$seqFormatted"

        val taxInvoice = TaxInvoice(
            taxInvoiceNumber = invoiceNo,
            branchId = dto.branchId,
            customerId = dto.customerId,
            taxpayerName = dto.taxpayerName,
            taxId = dto.taxId,
            branchNumber = dto.branchNumber,
            address = dto.address,
            email = dto.email,
            phone = dto.phone,
            totalNetAmount = calculatedNet,
            totalTaxAmount = calculatedTax,
            createdBy = dto.createdBy
        )
        val savedInvoice = taxInvoiceRepository.save(taxInvoice)

        for (order in validatedOrders) {
            taxInvoiceReceiptRepository.save(
                TaxInvoiceReceipt(
                    taxInvoiceId = savedInvoice.id,
                    orderId = order.id
                )
            )

            // Save line item snapshots
            val items = orderItemRepository.findByOrderId(order.id)
            for (it in items) {
                val itemNet = it.subtotal.divide(divisor, SCALE, ROUNDING)
                val itemTax = it.subtotal.subtract(itemNet).setScale(SCALE, ROUNDING)
                taxInvoiceItemSnapshotRepository.save(
                    TaxInvoiceItemSnapshot(
                        taxInvoiceId = savedInvoice.id,
                        orderId = order.id,
                        orderItemId = it.id,
                        itemName = it.nameSnapshot,
                        quantity = it.quantity,
                        unitPrice = it.unitPriceSnapshot,
                        taxAmount = itemTax,
                        netAmount = itemNet
                    )
                )
            }
        }

        return savedInvoice
    }

    @Transactional
    fun cancelTaxInvoice(taxInvoiceId: String, cancelledBy: String?): TaxInvoice {
        val taxInvoice = taxInvoiceRepository.findById(taxInvoiceId)
            .orElseThrow { IllegalArgumentException("Tax Invoice not found") }
        if (taxInvoice.status == "CANCELLED") {
            throw IllegalArgumentException("Tax Invoice is already cancelled")
        }

        taxInvoice.status = "CANCELLED"
        taxInvoice.cancelledAt = Instant.now()
        taxInvoice.cancelledBy = cancelledBy
        return taxInvoiceRepository.save(taxInvoice)
    }

    fun getTaxInvoice(id: String): TaxInvoice {
        return taxInvoiceRepository.findById(id).orElseThrow { IllegalArgumentException("Tax Invoice not found") }
    }
}

@RestController
@RequestMapping("/api/v1/tax-invoices")
class TaxInvoiceController(
    private val taxInvoiceService: TaxInvoiceService
) {
    @PostMapping
    @PreAuthorize("hasAuthority('TAX_INVOICE_CREATE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun mergeReceipts(@RequestBody dto: CreateTaxInvoiceDto): ApiResponse<TaxInvoice> {
        return ApiResponse.success(taxInvoiceService.mergeReceiptsToTaxInvoice(dto), "Tax Invoice issued successfully")
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('TAX_INVOICE_CANCEL') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun cancelInvoice(@PathVariable id: String, @RequestParam cancelledBy: String?): ApiResponse<TaxInvoice> {
        return ApiResponse.success(taxInvoiceService.cancelTaxInvoice(id, cancelledBy), "Tax Invoice cancelled successfully")
    }

    @GetMapping("/{id}")
    fun getInvoice(@PathVariable id: String): ApiResponse<TaxInvoice> {
        return ApiResponse.success(taxInvoiceService.getTaxInvoice(id))
    }
}
