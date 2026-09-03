package sun.clientpos.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sun.clientpos.common.MoneyUtils.parseBahtToSatang
import sun.clientpos.common.MoneyUtils.toDisplayBahtWithSymbol
import sun.clientpos.printer.TaxInvoiceCustomer
import sun.clientpos.ui.viewmodel.AppliedCouponUiState
import sun.clientpos.ui.viewmodel.CustomerUiState

/**
 * Applied payment entry.
 * amount in satang (minor units).
 */
data class AppliedPayment(
    val method: String,
    val amount: Long // satang
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    orderTotalAmount: Long, // satang
    selectedCustomer: CustomerUiState? = null,
    appliedCoupon: AppliedCouponUiState? = null,
    redeemedPointsSatang: Long = 0L,
    isOnline: Boolean = true,
    canPay: Boolean = true,
    canRedeemPoints: Boolean = true,
    canUseCoupon: Boolean = true,
    onApplyCoupon: (String) -> String? = { null },
    onRemoveCoupon: () -> Unit = {},
    onRedeemPoints: (Long) -> Unit = {},
    onCompletePayment: (List<AppliedPayment>, TaxInvoiceCustomer?) -> Unit
) {
    val methods = listOf(
        Triple("CASH", "💵 เงินสด", Color(0xFF10B981)),
        Triple("QR", "📱 พร้อมเพย์ / QR", Color(0xFF0284C7)),
        Triple("CARD", "💳 บัตรเครดิต/เดบิต", Color(0xFF8B5CF6)),
        Triple("EWALLET", "👛 กระเป๋าเงิน", Color(0xFFF59E0B)),
        Triple("VOUCHER", "🎟️ บัตรกำนัล", Color(0xFFEC4899))
    )

    var selectedMethod by remember { mutableStateOf("CASH") }
    var inputTenderedText by remember { mutableStateOf("") }
    var appliedPayments by remember { mutableStateOf(listOf<AppliedPayment>()) }

    // Coupon Code Input State
    var couponInputText by remember { mutableStateOf("") }
    var couponErrorText by remember { mutableStateOf<String?>(null) }

    // Tax Invoice State
    var showTaxInvoiceDialog by remember { mutableStateOf(false) }
    var taxCustomer by remember { mutableStateOf<TaxInvoiceCustomer?>(null) }
    var inputTaxName by remember { mutableStateOf("") }
    var inputTaxId by remember { mutableStateOf("") }
    var inputTaxBranch by remember { mutableStateOf("00000") }
    var inputTaxAddress by remember { mutableStateOf("") }
    var inputTaxPhone by remember { mutableStateOf("") }

    val paidSum = appliedPayments.sumOf { it.amount }
    val remainingAmount = (orderTotalAmount - paidSum).coerceAtLeast(0L)
    val tenderedSatang = inputTenderedText.parseBahtToSatang()
    val changeAmount = if (selectedMethod == "CASH" && tenderedSatang > remainingAmount) tenderedSatang - remainingAmount else 0L

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        // ══════════════════════════════════════════════════════════════════
        // LEFT PANEL: Payment Methods, Numpad & Loyalty (58% width)
        // ══════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Top Alerts (Offline / Permission)
                if (!isOnline) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        color = Color(0xFFD97706).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "📶", fontSize = 12.sp)
                            Text(
                                text = "โหมดออฟไลน์: บันทึกรับชำระเงินสด/บัตรได้ (สะสมแต้มได้ แต่แลกแต้ม/คูปองไม่ได้)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFBBF24)
                            )
                        }
                    }
                }

                if (!canPay) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "⚠️", fontSize = 12.sp)
                            Text(
                                text = "อุปกรณ์นี้ไม่ได้รับสิทธิ์รับชำระเงิน (PAY Capability Required)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF87171)
                            )
                        }
                    }
                }

                // Header Row & Tax Invoice Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "เลือกช่องทางการชำระเงิน",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    // Tax Invoice Button
                    Surface(
                        onClick = { showTaxInvoiceDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = if (taxCustomer != null) Color(0xFF0284C7).copy(alpha = 0.2f) else Color(0xFF1E293B),
                        border = BorderStroke(1.dp, if (taxCustomer != null) Color(0xFF38BDF8) else Color(0xFF334155))
                    ) {
                        Text(
                            text = if (taxCustomer != null) "📄 ใบกำกับ: ${taxCustomer!!.taxpayerName}" else "📄 ขอใบกำกับภาษีเต็มรูป",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (taxCustomer != null) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Payment Method Cards Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    items(methods) { (methodKey, label, accentColor) ->
                        val isSelected = methodKey == selectedMethod
                        Surface(
                            onClick = {
                                selectedMethod = methodKey
                                if (methodKey != "CASH") {
                                    // For electronic payment, auto-fill remaining amount
                                    inputTenderedText = "${remainingAmount / 100}"
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) accentColor.copy(alpha = 0.2f) else Color(0xFF1E293B),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) accentColor else Color(0xFF334155)
                            ),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // ── Cash / Tendered Input & Numpad Card ──
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Display Amount Input
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "จำนวนเงินที่รับชำระ (Tendered Amount)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = if (inputTenderedText.isBlank()) "฿0.00" else "฿$inputTenderedText",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF38BDF8)
                                )
                            }

                            if (inputTenderedText.isNotEmpty()) {
                                IconButton(onClick = { inputTenderedText = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "ล้างจำนวนเงิน",
                                        tint = Color(0xFFEF4444)
                                    )
                                }
                            }
                        }

                        // Quick Cash Presets
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Exact Amount Preset
                            Surface(
                                onClick = { inputTenderedText = "${remainingAmount / 100}" },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("พอดี (฿${remainingAmount / 100})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                            }

                            // ฿100, ฿500, ฿1,000 Presets
                            listOf(100, 500, 1000).forEach { preset ->
                                Surface(
                                    onClick = { inputTenderedText = "$preset" },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("฿$preset", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }

                        // FoodStory Touch Numpad (3x4 Grid)
                        val numpadRows = listOf(
                            listOf("1", "2", "3", "100"),
                            listOf("4", "5", "6", "500"),
                            listOf("7", "8", "9", "1000"),
                            listOf("C", "0", "00", "⌫")
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            numpadRows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    row.forEach { key ->
                                        Surface(
                                            onClick = {
                                                when (key) {
                                                    "C" -> inputTenderedText = ""
                                                    "⌫" -> {
                                                        if (inputTenderedText.isNotEmpty()) {
                                                            inputTenderedText = inputTenderedText.dropLast(1)
                                                        }
                                                    }
                                                    "100", "500", "1000" -> {
                                                        val current = inputTenderedText.toDoubleOrNull() ?: 0.0
                                                        val added = current + key.toDouble()
                                                        inputTenderedText = added.toInt().toString()
                                                    }
                                                    else -> {
                                                        if (inputTenderedText.length < 8) {
                                                            inputTenderedText += key
                                                        }
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = when (key) {
                                                "C" -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                                "⌫" -> Color(0xFF38BDF8).copy(alpha = 0.2f)
                                                "100", "500", "1000" -> Color(0xFF0F172A)
                                                else -> Color(0xFF0F172A)
                                            },
                                            border = BorderStroke(1.dp, Color(0xFF334155)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = when (key) {
                                                        "100", "500", "1000" -> "+$key"
                                                        else -> key
                                                    },
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (key) {
                                                        "C" -> Color(0xFFEF4444)
                                                        "⌫" -> Color(0xFF38BDF8)
                                                        "100", "500", "1000" -> Color(0xFF10B981)
                                                        else -> Color.White
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Add Payment / Append to Split List Button
            val addAmountSatang = if (tenderedSatang > 0L) tenderedSatang.coerceAtMost(remainingAmount) else remainingAmount
            Button(
                onClick = {
                    val appliedAmt = if (tenderedSatang > 0L) tenderedSatang.coerceAtMost(remainingAmount) else remainingAmount
                    if (appliedAmt > 0L) {
                        appliedPayments = appliedPayments + AppliedPayment(selectedMethod, appliedAmt)
                        inputTenderedText = ""
                    }
                },
                enabled = remainingAmount > 0L,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981),
                    disabledContainerColor = Color(0xFF334155)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "➕ บันทึกรับชำระ (${addAmountSatang.toDisplayBahtWithSymbol()})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // RIGHT PANEL: Big Net Total, Bill Summary & Pay CTA (42% width)
        // ══════════════════════════════════════════════════════════════════
        Card(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .padding(start = 14.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "สรุปยอดรับชำระ (Bill Summary)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // ── Prominent Giant Net Total Card ──
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.5.dp, Color(0xFF0284C7).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("ยอดที่ต้องชำระสุทธิ", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = orderTotalAmount.toDisplayBahtWithSymbol(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    // Customer & Coupon / Points Status
                    if (selectedCustomer != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("⭐ สมาชิก: ${selectedCustomer.fullName}", fontSize = 11.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                                Text("${selectedCustomer.tierName}", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                            }
                        }

                        // Loyalty Points Redemption Option
                        if (selectedCustomer.pointsBalance > 0) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("แลกแต้ม (${selectedCustomer.pointsBalance.toInt()} แต้ม):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    if (canRedeemPoints && isOnline) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf(100, 200, 500).forEach { pts ->
                                                val satangVal = (pts * 10).toLong()
                                                if (selectedCustomer.pointsBalance >= pts && satangVal <= orderTotalAmount) {
                                                    val isSelected = redeemedPointsSatang == satangVal
                                                    Surface(
                                                        onClick = { onRedeemPoints(if (isSelected) 0L else satangVal) },
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B)
                                                    ) {
                                                        Text("$pts แต้ม", fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Text(if (!isOnline) "ออฟไลน์" else "ไม่มีสิทธิ์", fontSize = 10.sp, color = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }

                    // Coupon Input Row
                    if (appliedCoupon != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🏷️ คูปอง: ${appliedCoupon.name} (-${appliedCoupon.discountSatang.toDisplayBahtWithSymbol()})", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                TextButton(onClick = onRemoveCoupon, contentPadding = PaddingValues(0.dp)) {
                                    Text("ลบ", fontSize = 10.sp, color = Color(0xFFEF4444))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 6.dp))

                    // Applied Payments Breakdown List
                    Text(
                        text = "รายการที่บันทึกรับแล้ว (${appliedPayments.size}):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    if (appliedPayments.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("ยังไม่ได้บันทึกรับเงิน", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(appliedPayments) { p ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = p.method, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(text = p.amount.toDisplayBahtWithSymbol(), fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                            IconButton(
                                                onClick = { appliedPayments = appliedPayments.filter { it != p } },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "ลบ",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Balance & Giant Checkout CTA
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 8.dp))

                    // Remaining Amount
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "ยอดคงเหลือที่ต้องชำระ:", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                        Text(
                            text = remainingAmount.toDisplayBahtWithSymbol(),
                            fontSize = 16.sp,
                            color = if (remainingAmount > 0L) Color(0xFFEF4444) else Color(0xFF10B981),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Change Due
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "เงินทอน (Change Due):", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                        Text(
                            text = changeAmount.toDisplayBahtWithSymbol(),
                            fontSize = 16.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Giant Checkout CTA Button
                    val isReadyToComplete = remainingAmount <= 0L && appliedPayments.isNotEmpty() && canPay
                    Button(
                        onClick = { onCompletePayment(appliedPayments, taxCustomer) },
                        enabled = isReadyToComplete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            disabledContainerColor = Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (!canPay) "ไม่มีสิทธิ์รับชำระเงิน" else "ปิดบิล & พิมพ์ใบเสร็จ 🖨️",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isReadyToComplete) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }

    // ── Full Tax Invoice Modal Dialog ──
    if (showTaxInvoiceDialog) {
        AlertDialog(
            onDismissRequest = { showTaxInvoiceDialog = false },
            title = {
                Text(
                    "📄 ออกใบกำกับภาษีเต็มรูป (Full Tax Invoice 80mm)",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputTaxName,
                        onValueChange = { inputTaxName = it },
                        label = { Text("ชื่อนิติบุคคล / ผู้เสียภาษี *") },
                        placeholder = { Text("เช่น บริษัท เจริญโภคภัณฑ์ จำกัด") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputTaxId,
                        onValueChange = { inputTaxId = it },
                        label = { Text("เลขประจำตัวผู้เสียภาษี 13 หลัก *") },
                        placeholder = { Text("เช่น 0105558012345") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputTaxBranch,
                        onValueChange = { inputTaxBranch = it },
                        label = { Text("รหัสสาขา (00000 = สำนักงานใหญ่)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputTaxAddress,
                        onValueChange = { inputTaxAddress = it },
                        label = { Text("ที่อยู่ตาม ภ.พ.20") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputTaxPhone,
                        onValueChange = { inputTaxPhone = it },
                        label = { Text("เบอร์โทรศัพท์ติดต่อ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputTaxName.isNotBlank() && inputTaxId.isNotBlank()) {
                            taxCustomer = TaxInvoiceCustomer(
                                taxpayerName = inputTaxName,
                                taxId = inputTaxId,
                                branchNumber = inputTaxBranch.ifBlank { "00000" },
                                address = inputTaxAddress,
                                phone = inputTaxPhone.takeIf { it.isNotBlank() }
                            )
                        }
                        showTaxInvoiceDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("บันทึกข้อมูลใบกำกับ")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        taxCustomer = null
                        showTaxInvoiceDialog = false
                    }
                ) {
                    Text("ยกเลิก / ไม่ขอใบกำกับ", color = Color(0xFFEF4444))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}
