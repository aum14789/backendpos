package sun.clientpos.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sun.clientpos.common.MoneyUtils.toDisplayBahtWithSymbol
import sun.clientpos.ui.viewmodel.CompletedReceiptUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReceiptPreviewScreen(
    receipt: CompletedReceiptUiState,
    onPrintAbbreviated: () -> Unit,
    onPrintTaxInvoice: () -> Unit,
    onNewOrder: () -> Unit
) {
    val scrollState = rememberScrollState()
    val dateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("th-TH")).format(Date(receipt.order.createdAt))

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ══════════════════════════════════════════════════════════════════
        // LEFT: 80mm Thermal Receipt Slip Card (FoodStory Paper Look)
        // ══════════════════════════════════════════════════════════════════
        Card(
            modifier = Modifier
                .width(420.dp)
                .fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "☀️ SunPOS Restaurant",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "สาขาสุขุมวิท (BR-01)",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = "เลขประจำตัวผู้เสียภาษี: 0105560000001",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "โทร: 02-123-4567",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Invoice Type Header
                if (receipt.taxCustomer != null) {
                    Surface(
                        modifier = Modifier.padding(vertical = 4.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "ใบเสร็จรับเงิน / ใบกำกับภาษีเต็มรูป",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Customer Details for Full Tax Invoice
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("ผู้ซื้อ: ${receipt.taxCustomer.taxpayerName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Text("Tax ID: ${receipt.taxCustomer.taxId} (สาขา: ${receipt.taxCustomer.branchNumber})", fontSize = 10.sp, color = Color(0xFF334155))
                            Text("ที่อยู่: ${receipt.taxCustomer.address}", fontSize = 10.sp, color = Color(0xFF64748B))
                            if (!receipt.taxCustomer.phone.isNullOrBlank()) {
                                Text("โทร: ${receipt.taxCustomer.phone}", fontSize = 10.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                } else {
                    Text(
                        text = "ใบเสร็จรับเงิน / ใบกำกับภาษีอย่างย่อ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                // Order Meta
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("เลขที่บิล: ${receipt.order.orderNumber}", fontSize = 11.sp, color = Color(0xFF475569), fontWeight = FontWeight.Medium)
                    val tableDisplay = if (receipt.order.tableId != null) "โต๊ะ: ${receipt.order.tableId}" else when (receipt.order.orderType) {
                        "BUFFET" -> "บุฟเฟ่ต์"
                        "TAKEAWAY" -> "กลับบ้าน"
                        "DELIVERY" -> "เดลิเวอรี"
                        else -> receipt.order.orderType
                    }
                    Text(tableDisplay, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("วันที่: $dateFormatted", fontSize = 10.sp, color = Color(0xFF64748B))
                    Text("แคชเชียร์: POS-01", fontSize = 10.sp, color = Color(0xFF64748B))
                }

                HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                // Buffet Head Charge Line
                if (receipt.buffetHeadChargeSatang > 0L && receipt.buffetTierName != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("🥩 บุฟเฟ่ต์: ${receipt.buffetTierName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            Text("   (${receipt.buffetAdults} ผู้ใหญ่, ${receipt.buffetChildren} เด็ก)", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                        Text(receipt.buffetHeadChargeSatang.toDisplayBahtWithSymbol(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Line Items
                receipt.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${item.quantity}x  ${item.name}", fontSize = 12.sp, color = Color(0xFF1E293B), modifier = Modifier.weight(1f))
                        val priceText = if (item.isBuffetIncluded) "บุฟเฟ่ต์" else item.subtotalSatang.toDisplayBahtWithSymbol()
                        Text(priceText, fontSize = 12.sp, color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                    }
                }

                HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                // Financials Summary
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ยอดรวมก่อนส่วนลด:", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(receipt.order.subtotalAmount.toDisplayBahtWithSymbol(), fontSize = 11.sp, color = Color.Black)
                }

                if (receipt.order.discountAmount > 0L) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ส่วนลด (Discounts):", fontSize = 11.sp, color = Color(0xFF10B981))
                        Text("-${receipt.order.discountAmount.toDisplayBahtWithSymbol()}", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("ภาษีมูลค่าเพิ่ม VAT 7% (รวมในราคา):", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Text(receipt.order.taxAmount.toDisplayBahtWithSymbol(), fontSize = 10.sp, color = Color(0xFF94A3B8))
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Grand Total Row
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ยอดชำระสุทธิ:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Text(
                            text = receipt.order.totalAmount.toDisplayBahtWithSymbol(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0284C7)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

                // Payments Breakdown
                receipt.payments.forEach { p ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ชำระด้วย ${p.method}:", fontSize = 11.sp, color = Color(0xFF475569))
                        Text(p.amountSatang.toDisplayBahtWithSymbol(), fontSize = 11.sp, color = Color.Black)
                    }
                }

                if (receipt.changeSatang > 0L) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("เงินทอน (Change):", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        Text(receipt.changeSatang.toDisplayBahtWithSymbol(), fontSize = 12.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                }

                // Customer Loyalty Earned Points Box
                if (receipt.customer != null && receipt.earnedPoints > 0L) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("⭐ สมาชิก ${receipt.customer.fullName}:", fontSize = 10.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                            Text("+${receipt.earnedPoints} แต้ม", fontSize = 10.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "ขอบคุณที่ใช้บริการ / Thank You",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.width(36.dp))

        // ══════════════════════════════════════════════════════════════════
        // RIGHT: Action Control Buttons (Print / New Order)
        // ══════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            // Success Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ชำระเงินสำเร็จเรียบร้อย!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF10B981)
                    )
                    Text(
                        text = "บิล #${receipt.order.orderNumber}",
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Print Abbreviated Receipt
            Button(
                onClick = onPrintAbbreviated,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🖨️ พิมพ์ใบเสร็จรับเงิน (80mm)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            // Print Tax Invoice (if tax requested)
            if (receipt.taxCustomer != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onPrintTaxInvoice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("🏢 พิมพ์ใบกำกับภาษีเต็มรูป", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // New Order Button
            Button(
                onClick = onNewOrder,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🚀 รับออเดอร์ใหม่ (New Order) ➔", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
            }
        }
    }
}
