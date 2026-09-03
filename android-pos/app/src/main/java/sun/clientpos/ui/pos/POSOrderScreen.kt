package sun.clientpos.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sun.clientpos.common.MoneyUtils.toDisplayBahtWithSymbol
import sun.clientpos.data.local.entity.RoomBuffetSessionEntity
import sun.clientpos.data.local.entity.RoomBuffetTierEntity
import sun.clientpos.data.local.entity.RoomMenuCategoryEntity
import sun.clientpos.data.local.entity.RoomMenuItemEntity
import sun.clientpos.data.local.entity.RoomPromotionEntity
import sun.clientpos.domain.pricing.PricingEngine
import sun.clientpos.domain.pricing.PricingItemInput
import sun.clientpos.ui.viewmodel.CustomerUiState

/**
 * Cart item for POS order screen.
 * All prices in satang (minor units). quantity is Int.
 */
data class CartItem(
    val item: RoomMenuItemEntity,
    var quantity: Int = 1,
    val selectedModifiers: List<String> = emptyList(),
    val modifierSum: Long = 0L, // satang
    val isBuffetIncluded: Boolean = false
) {
    val subtotal: Long
        get() {
            val unitBase = if (isBuffetIncluded) 0L else item.basePrice
            return (unitBase + modifierSum) * quantity
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun POSOrderScreen(
    categories: List<RoomMenuCategoryEntity>,
    menuItems: List<RoomMenuItemEntity>,
    buffetTiers: List<RoomBuffetTierEntity> = emptyList(),
    activePromotions: List<RoomPromotionEntity> = emptyList(),
    activeBuffetSession: RoomBuffetSessionEntity? = null,
    eligibleBuffetMenuItemIds: Set<String> = emptySet(),
    selectedCustomer: CustomerUiState? = null,
    tableName: String? = null,
    canEditBuffetSettings: Boolean = false,
    onSelectCustomer: (CustomerUiState?) -> Unit = {},
    onSearchCustomers: (String) -> List<CustomerUiState> = { emptyList() },
    onRegisterCustomer: (String, String) -> CustomerUiState = { name, phone -> CustomerUiState(java.util.UUID.randomUUID().toString(), name, null, phone) },
    onSendToKitchen: ((List<CartItem>, String, RoomBuffetTierEntity?, Int, Int, Long, Double) -> Unit)? = null,
    onProceedToCheckout: ((List<CartItem>, String, RoomBuffetTierEntity?, Int, Int, Long, Double) -> Unit)? = null,
    onConfirmOrder: (List<CartItem>, String, RoomBuffetTierEntity?, Int, Int, Long, Double) -> Unit = { c, o, b, a, ch, md, mdp -> }
) {
    var orderType by remember { mutableStateOf(if (activeBuffetSession != null) "BUFFET" else "DINE_IN") }
    var selectedBuffetTier by remember { mutableStateOf(buffetTiers.firstOrNull()) }
    var adultCount by remember { mutableStateOf(activeBuffetSession?.adultCount ?: 2) }
    var childCount by remember { mutableStateOf(activeBuffetSession?.childCount ?: 0) }

    var selectedCategoryId by remember { mutableStateOf("") }
    var menuSearchQuery by remember { mutableStateOf("") }
    var cartItems by remember { mutableStateOf(listOf<CartItem>()) }

    // Manual Discount State
    var manualDiscountSatang by remember { mutableStateOf(0L) }
    var manualDiscountPercent by remember { mutableStateOf(0.0) }
    var manualDiscountReason by remember { mutableStateOf("") }
    var manualDiscountAuthorizedBy by remember { mutableStateOf("") }
    var showDiscountDialog by remember { mutableStateOf(false) }

    // CRM Customer Lookup Dialog State
    var showCustomerDialog by remember { mutableStateOf(false) }

    // Live Second-by-Second Countdown Timer
    var remainingMillis by remember { mutableStateOf(activeBuffetSession?.remainingMillis() ?: 0L) }

    LaunchedEffect(activeBuffetSession) {
        while (activeBuffetSession != null) {
            remainingMillis = activeBuffetSession.remainingMillis()
            delay(1000)
        }
    }

    val isBuffet = orderType == "BUFFET"
    val buffetHeadCharge = if (isBuffet && selectedBuffetTier != null) {
        (selectedBuffetTier!!.adultPrice * adultCount) + (selectedBuffetTier!!.childPrice * childCount)
    } else 0L

    // Pricing calculation pipeline (Unchanged business logic)
    val pricingInputs = cartItems.map {
        PricingItemInput(
            menuItemId = it.item.itemId,
            name = it.item.name,
            unitPriceSatang = it.item.basePrice,
            quantity = it.quantity,
            modifierPricesSatang = listOf(it.modifierSum),
            isBuffetIncluded = it.isBuffetIncluded
        )
    }

    val memberDiscountPercent = selectedCustomer?.discountPercentage ?: 0.0

    val pricingResult = remember(cartItems, buffetHeadCharge, manualDiscountSatang, manualDiscountPercent, memberDiscountPercent, activePromotions) {
        PricingEngine.calculatePricing(
            items = pricingInputs,
            buffetHeadChargeSatang = buffetHeadCharge,
            activePromotions = activePromotions,
            manualDiscountSatang = manualDiscountSatang,
            manualDiscountPercent = manualDiscountPercent,
            memberDiscountPercent = memberDiscountPercent,
            isVatInclusive = true
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        // ══════════════════════════════════════════════════════════════════
        // 1. LEFT COLUMN: Vertical Category Tabs (16% width with safe margin)
        // ══════════════════════════════════════════════════════════════════
        Surface(
            modifier = Modifier
                .widthIn(min = 135.dp)
                .weight(0.18f)
                .fillMaxHeight(),
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                // Category Header
                Text(
                    text = "หมวดหมู่อาหาร",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // "All" Category Tab
                    item {
                        val isAllSelected = selectedCategoryId.isBlank()
                        Surface(
                            onClick = { selectedCategoryId = "" },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAllSelected) Color(0xFF0284C7) else Color(0xFF0F172A),
                            border = BorderStroke(
                                1.dp,
                                if (isAllSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🍽️ ทั้งหมด",
                                    fontSize = 13.sp,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAllSelected) Color.White else Color(0xFFCBD5E1)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (isAllSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
                                ) {
                                    Text(
                                        text = "${menuItems.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAllSelected) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Categories List
                    items(categories) { category ->
                        val isSelected = category.categoryId == selectedCategoryId
                        val count = menuItems.count { it.categoryId == category.categoryId }

                        Surface(
                            onClick = { selectedCategoryId = category.categoryId },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = category.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // 2. CENTER COLUMN: Menu Catalog Grid & Search (50% width)
        // ══════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .weight(0.48f)
                .fillMaxHeight()
                .padding(14.dp)
        ) {
            // Search Bar & Filter Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search Box
                OutlinedTextField(
                    value = menuSearchQuery,
                    onValueChange = { menuSearchQuery = it },
                    placeholder = { Text("🔍 ค้นหาชื่ออาหาร, เครื่องดื่ม...", fontSize = 13.sp, color = Color(0xFF64748B)) },
                    trailingIcon = {
                        if (menuSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { menuSearchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "ล้าง",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                )
            }

            // Buffet Promotion Tier Picker & Headcount (if Buffet Mode)
            if (isBuffet && (selectedBuffetTier != null || buffetTiers.isNotEmpty())) {
                val currentTier = selectedBuffetTier ?: buffetTiers.firstOrNull()
                if (!canEditBuffetSettings) {
                    // Read-only Locked Buffet Info (Staff cannot change after opening)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🥩 ${currentTier?.name ?: "บุฟเฟ่ต์"}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Text(
                                        text = "👥 $adultCount ผู้ใหญ่${if (childCount > 0) ", $childCount เด็ก" else ""}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🔒 ล็อกแล้ว (เฉพาะผู้จัดการแก้ไขได้)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                } else {
                    // Manager Editable Buffet Settings
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "🥩 แพ็กเกจบุฟเฟ่ต์:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFD97706).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "✏️ สิทธิ์ผู้จัดการ",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFBBF24),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(buffetTiers) { tier ->
                                        val isSelected = selectedBuffetTier?.tierId == tier.tierId
                                        Surface(
                                            onClick = { selectedBuffetTier = tier },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A),
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155))
                                        ) {
                                            Text(
                                                text = "${tier.name} (${tier.adultPrice.toDisplayBahtWithSymbol()})",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Headcount Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("ผู้ใหญ่:", fontSize = 12.sp, color = Color.White)
                                    FilledIconButton(
                                        onClick = { if (adultCount > 1) adultCount-- },
                                        modifier = Modifier.size(26.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155))
                                    ) {
                                        Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Text("$adultCount ท่าน", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    FilledIconButton(
                                        onClick = { adultCount++ },
                                        modifier = Modifier.size(26.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0284C7))
                                    ) {
                                        Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("เด็ก:", fontSize = 12.sp, color = Color.White)
                                    FilledIconButton(
                                        onClick = { if (childCount > 0) childCount-- },
                                        modifier = Modifier.size(26.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155))
                                    ) {
                                        Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                    Text("$childCount ท่าน", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    FilledIconButton(
                                        onClick = { childCount++ },
                                        modifier = Modifier.size(26.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0284C7))
                                    ) {
                                        Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Menu Items Grid ──
            val filteredItems = menuItems.filter { item ->
                val matchesCategory = if (selectedCategoryId.isNotBlank()) item.categoryId == selectedCategoryId else true
                val matchesSearch = if (menuSearchQuery.isBlank()) true else item.name.contains(menuSearchQuery.trim(), ignoreCase = true)
                matchesCategory && matchesSearch
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🍽️ ไม่พบรายการอาหาร", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Text("ลองเปลี่ยนคำค้นหาหรือเลือกหมวดหมู่อื่นทางด้านซ้าย", fontSize = 13.sp, color = Color(0xFF64748B))
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.itemId }) { item ->
                        val isIncludedInBuffet = isBuffet && eligibleBuffetMenuItemIds.contains(item.itemId)
                        val inCartQty = cartItems.find { it.item.itemId == item.itemId }?.quantity ?: 0

                        FoodMenuItemCard(
                            item = item,
                            isIncludedInBuffet = isIncludedInBuffet,
                            inCartQuantity = inCartQty,
                            onClick = {
                                val existingIndex = cartItems.indexOfFirst { it.item.itemId == item.itemId }
                                cartItems = if (existingIndex >= 0) {
                                    cartItems.mapIndexed { index, cartItem ->
                                        if (index == existingIndex) cartItem.copy(quantity = cartItem.quantity + 1) else cartItem
                                    }
                                } else {
                                    cartItems + CartItem(item = item, quantity = 1, isBuffetIncluded = isIncludedInBuffet)
                                }
                            }
                        )
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // 3. RIGHT COLUMN: Order Bill / Cart Sidebar (34% width)
        // ══════════════════════════════════════════════════════════════════
        Card(
            modifier = Modifier
                .weight(0.34f)
                .fillMaxHeight()
                .padding(top = 14.dp, end = 14.dp, bottom = 14.dp),
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
                // Top Section: Order Header, Member & Timer
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    // Service Type & Timer Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Order Type Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (orderType) {
                                "BUFFET" -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                "TAKEAWAY" -> Color(0xFF10B981).copy(alpha = 0.2f)
                                "DELIVERY" -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                else -> Color(0xFF0284C7).copy(alpha = 0.2f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when (orderType) {
                                    "BUFFET" -> Color(0xFFF59E0B)
                                    "TAKEAWAY" -> Color(0xFF10B981)
                                    "DELIVERY" -> Color(0xFF8B5CF6)
                                    else -> Color(0xFF38BDF8)
                                }
                            )
                        ) {
                            Text(
                                text = when (orderType) {
                                    "BUFFET" -> "🥩 บุฟเฟ่ต์"
                                    "TAKEAWAY" -> "🥡 กลับบ้าน"
                                    "DELIVERY" -> "🛵 เดลิเวอรี"
                                    else -> "🍽️ ทานที่ร้าน"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Live Buffet Timer (if active)
                        if (isBuffet && activeBuffetSession != null) {
                            val remainingSeconds = (remainingMillis / 1000)
                            val mins = remainingSeconds / 60
                            val secs = remainingSeconds % 60
                            val isNearExpiry = mins < 15

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isNearExpiry) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, if (isNearExpiry) Color(0xFFEF4444) else Color(0xFF10B981))
                            ) {
                                Text(
                                    text = if (remainingMillis <= 0) "⏰ หมดเวลา" else String.format("⏳ %02d:%02d น.", mins, secs),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNearExpiry) Color(0xFFEF4444) else Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Customer / Member Button
                        if (selectedCustomer != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                modifier = Modifier.clickable { showCustomerDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("⭐ ${selectedCustomer.fullName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "ลบ",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onSelectCustomer(null) }
                                    )
                                }
                            }
                        } else {
                            Surface(
                                onClick = { showCustomerDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0F172A),
                                border = BorderStroke(1.dp, Color(0xFF0284C7))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                    Text("สมาชิกลูกค้า", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                            }
                        }
                    }

                    // Cart Header & Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("บิลสั่งอาหาร", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF0284C7)
                            ) {
                                Text(
                                    text = "${cartItems.sumOf { it.quantity }}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Discount Button
                            Surface(
                                onClick = { showDiscountDialog = true },
                                shape = RoundedCornerShape(6.dp),
                                color = if (manualDiscountSatang > 0L || manualDiscountPercent > 0.0) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF334155),
                                border = BorderStroke(
                                    1.dp,
                                    if (manualDiscountSatang > 0L || manualDiscountPercent > 0.0) Color(0xFF10B981) else Color(0xFF475569)
                                )
                            ) {
                                Text(
                                    text = if (manualDiscountSatang > 0L || manualDiscountPercent > 0.0) "✅ ลด" else "🎁 ลด",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (manualDiscountSatang > 0L || manualDiscountPercent > 0.0) Color(0xFF10B981) else Color(0xFF38BDF8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            // Clear Button
                            if (cartItems.isNotEmpty()) {
                                IconButton(
                                    onClick = { cartItems = emptyList() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "ล้าง", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(bottom = 6.dp))

                    // Scrollable Cart Items
                    if (cartItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🛒", fontSize = 28.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("ยังไม่มีรายการในบิล", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text("แตะเมนูเพื่อเพิ่มรายการอาหาร", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(cartItems, key = { it.item.itemId }) { cartItem ->
                                CartItemRow(
                                    cartItem = cartItem,
                                    onIncrease = {
                                        cartItems = cartItems.map {
                                            if (it.item.itemId == cartItem.item.itemId) it.copy(quantity = it.quantity + 1) else it
                                        }
                                    },
                                    onDecrease = {
                                        cartItems = if (cartItem.quantity > 1) {
                                            cartItems.map {
                                                if (it.item.itemId == cartItem.item.itemId) it.copy(quantity = it.quantity - 1) else it
                                            }
                                        } else {
                                            cartItems.filter { it.item.itemId != cartItem.item.itemId }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Bottom Section: Summary & Big CTA Button
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 6.dp))

                    if (isBuffet && buffetHeadCharge > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ค่าบุฟเฟ่ต์ ($adultCount ผู้ใหญ่, $childCount เด็ก):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Text(buffetHeadCharge.toDisplayBahtWithSymbol(), fontSize = 11.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ยอดรวมก่อนส่วนลด:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Text(pricingResult.grossAmount.toDisplayBahtWithSymbol(), fontSize = 11.sp, color = Color.White)
                    }

                    if (pricingResult.automaticPromotionDiscount > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ส่วนลดโปรโมชั่น:", fontSize = 11.sp, color = Color(0xFF38BDF8))
                            Text("-${pricingResult.automaticPromotionDiscount.toDisplayBahtWithSymbol()}", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }
                    }

                    if (pricingResult.memberDiscount > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ส่วนลดสมาชิก (${selectedCustomer?.discountPercentage?.toInt()}%):", fontSize = 11.sp, color = Color(0xFFF59E0B))
                            Text("-${pricingResult.memberDiscount.toDisplayBahtWithSymbol()}", fontSize = 11.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                        }
                    }

                    if (pricingResult.manualDiscount > 0L) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ส่วนลดพิเศษ:", fontSize = 11.sp, color = Color(0xFF10B981))
                            Text("-${pricingResult.manualDiscount.toDisplayBahtWithSymbol()}", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("VAT 7% (รวมในราคา):", fontSize = 10.sp, color = Color(0xFF64748B))
                        Text(pricingResult.taxAmount.toDisplayBahtWithSymbol(), fontSize = 10.sp, color = Color(0xFF64748B))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Grand Total Card
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ยอดชำระสุทธิ", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                text = pricingResult.grandTotal.toDisplayBahtWithSymbol(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    // Action Buttons: Send to Kitchen & Proceed to Payment
                    val isReadyToOrder = cartItems.isNotEmpty() || (isBuffet && buffetHeadCharge > 0L)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Send to Kitchen Button (Stay in Table flow, don't force payment)
                        Button(
                            onClick = {
                                if (onSendToKitchen != null) {
                                    onSendToKitchen(
                                        cartItems,
                                        orderType,
                                        selectedBuffetTier,
                                        adultCount,
                                        childCount,
                                        manualDiscountSatang,
                                        manualDiscountPercent
                                    )
                                } else {
                                    onConfirmOrder(
                                        cartItems,
                                        orderType,
                                        selectedBuffetTier,
                                        adultCount,
                                        childCount,
                                        manualDiscountSatang,
                                        manualDiscountPercent
                                    )
                                }
                            },
                            enabled = isReadyToOrder,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                disabledContainerColor = Color(0xFF334155)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "🍳 ส่งครัว",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isReadyToOrder) Color.White else Color(0xFF94A3B8)
                            )
                        }

                        // 2. Check Bill / Payment Button (Proceed to Payment)
                        Button(
                            onClick = {
                                if (onProceedToCheckout != null) {
                                    onProceedToCheckout(
                                        cartItems,
                                        orderType,
                                        selectedBuffetTier,
                                        adultCount,
                                        childCount,
                                        manualDiscountSatang,
                                        manualDiscountPercent
                                    )
                                } else {
                                    onConfirmOrder(
                                        cartItems,
                                        orderType,
                                        selectedBuffetTier,
                                        adultCount,
                                        childCount,
                                        manualDiscountSatang,
                                        manualDiscountPercent
                                    )
                                }
                            },
                            enabled = isReadyToOrder,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7),
                                disabledContainerColor = Color(0xFF334155)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "💵 เช็คบิล / จ่าย ➔",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isReadyToOrder) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── CRM Customer Lookup & Registration Dialog ──
    if (showCustomerDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var showQuickRegister by remember { mutableStateOf(false) }
        var newCustomerName by remember { mutableStateOf("") }
        var newCustomerPhone by remember { mutableStateOf("") }

        val searchResults = remember(searchQuery) { onSearchCustomers(searchQuery) }

        AlertDialog(
            onDismissRequest = { showCustomerDialog = false },
            title = {
                Text(
                    if (showQuickRegister) "➕ สมัครสมาชิกลูกค้าด่วน" else "👤 ค้นหาสมาชิก / ลูกค้า (CRM)",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (showQuickRegister) {
                        OutlinedTextField(
                            value = newCustomerName,
                            onValueChange = { newCustomerName = it },
                            label = { Text("ชื่อลูกค้า (Customer Name) *") },
                            placeholder = { Text("เช่น สมชาย ประเสริฐ") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = newCustomerPhone,
                            onValueChange = { newCustomerPhone = it },
                            label = { Text("เบอร์โทรศัพท์ (Phone Number) *") },
                            placeholder = { Text("เช่น 0812345678") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            singleLine = true
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (newCustomerName.isNotBlank() && newCustomerPhone.isNotBlank()) {
                                        val newCust = onRegisterCustomer(newCustomerName, newCustomerPhone)
                                        onSelectCustomer(newCust)
                                        showCustomerDialog = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                Text("บันทึก & ผูกลูกค้า")
                            }
                            OutlinedButton(
                                onClick = { showQuickRegister = false }
                            ) {
                                Text("ย้อนกลับ")
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("เบอร์โทรศัพท์, Member ID, หรือ LINE ID") },
                            placeholder = { Text("เช่น 081-111-2222, MEM-10001") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ผลการค้นหาสมาชิก (${searchResults.size}):", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            TextButton(
                                onClick = {
                                    newCustomerPhone = searchQuery
                                    showQuickRegister = true
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("➕ สมัครใหม่ด่วน", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }
                        }

                        if (searchResults.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("ไม่พบข้อมูลสมาชิกในระบบ", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            newCustomerPhone = searchQuery
                                            showQuickRegister = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("➕ สร้างโปรไฟล์ลูกค้าใหม่ทันที", fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(searchResults) { customer ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectCustomer(customer)
                                                showCustomerDialog = false
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(customer.fullName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                                Text("📞 ${customer.phone} | ID: ${customer.memberId ?: "-"}", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = when (customer.tierCode) {
                                                        "PLATINUM" -> Color(0xFF8B5CF6).copy(alpha = 0.3f)
                                                        "GOLD" -> Color(0xFFF59E0B).copy(alpha = 0.3f)
                                                        else -> Color(0xFF64748B).copy(alpha = 0.3f)
                                                    }
                                                ) {
                                                    Text(
                                                        customer.tierName,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    "แต้ม: ${customer.pointsBalance.toInt()} | ลด ${customer.discountPercentage.toInt()}%",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF38BDF8)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCustomerDialog = false }) {
                    Text("ปิด", color = Color(0xFF38BDF8))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    // ── Manual Discount Modal Dialog ──
    if (showDiscountDialog) {
        var discountType by remember { mutableStateOf(if (manualDiscountPercent > 0.0) "PERCENT" else "AMOUNT") }
        var inputValText by remember { mutableStateOf(if (manualDiscountPercent > 0.0) "$manualDiscountPercent" else if (manualDiscountSatang > 0L) "${manualDiscountSatang / 100}" else "") }
        var inputReason by remember { mutableStateOf(manualDiscountReason.ifBlank { "โปรโมชั่นผู้จัดการ" }) }
        var inputAuthorizedBy by remember { mutableStateOf(manualDiscountAuthorizedBy.ifBlank { "admin" }) }

        val numericVal = inputValText.toDoubleOrNull() ?: 0.0
        val isHighDiscount = (discountType == "PERCENT" && numericVal > 10.0) || (discountType == "AMOUNT" && numericVal > 100.0)

        AlertDialog(
            onDismissRequest = { showDiscountDialog = false },
            title = { Text("🎁 ระบุส่วนลดพิเศษ (Manual Discount)", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = discountType == "PERCENT",
                            onClick = { discountType = "PERCENT" },
                            label = { Text("เปอร์เซ็นต์ (%)") }
                        )
                        FilterChip(
                            selected = discountType == "AMOUNT",
                            onClick = { discountType = "AMOUNT" },
                            label = { Text("จำนวนเงิน (฿)") }
                        )
                    }

                    OutlinedTextField(
                        value = inputValText,
                        onValueChange = { inputValText = it },
                        label = { Text(if (discountType == "PERCENT") "เปอร์เซ็นต์ส่วนลด (%)" else "จำนวนเงินส่วนลด (บาท)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputReason,
                        onValueChange = { inputReason = it },
                        label = { Text("เหตุผลการให้ส่วนลด *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = inputAuthorizedBy,
                        onValueChange = { inputAuthorizedBy = it },
                        label = { Text("ผู้มีสิทธิ์อนุมัติ (Manager Auth) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (isHighDiscount) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ ส่วนลดมากกว่า 10% หรือ ฿100 ต้องได้รับการอนุมัติจากผู้จัดการ (Manager PIN 9999)",
                                fontSize = 11.sp,
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (discountType == "PERCENT") {
                            manualDiscountPercent = numericVal
                            manualDiscountSatang = 0L
                        } else {
                            manualDiscountSatang = (numericVal * 100).toLong()
                            manualDiscountPercent = 0.0
                        }
                        manualDiscountReason = inputReason
                        manualDiscountAuthorizedBy = inputAuthorizedBy
                        showDiscountDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("บันทึกส่วนลด")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        manualDiscountSatang = 0L
                        manualDiscountPercent = 0.0
                        manualDiscountReason = ""
                        manualDiscountAuthorizedBy = ""
                        showDiscountDialog = false
                    }
                ) {
                    Text("ยกเลิกส่วนลด", color = Color(0xFFEF4444))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

@Composable
private fun FoodMenuItemCard(
    item: RoomMenuItemEntity,
    isIncludedInBuffet: Boolean,
    inCartQuantity: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(
            1.5.dp,
            if (inCartQuantity > 0) Color(0xFF38BDF8) else Color(0xFF334155)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Item Name (Max 2 lines)
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Price or Buffet status
                Column {
                    if (isIncludedInBuffet) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "รวมในบุฟเฟ่ต์ ฿0",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = item.basePrice.toDisplayBahtWithSymbol(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            // In-Cart Badge Indicator on top right
            if (inCartQuantity > 0) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    color = Color(0xFF0284C7),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "x$inCartQuantity",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CartItemRow(
    cartItem: CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Item details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cartItem.item.name,
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (cartItem.isBuffetIncluded) {
                    Text(
                        text = "บุฟเฟ่ต์ (฿0.00)",
                        fontSize = 10.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "${cartItem.item.basePrice.toDisplayBahtWithSymbol()} / จาน",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Stepper buttons & subtotal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledIconButton(
                    onClick = onDecrease,
                    modifier = Modifier.size(24.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155))
                ) {
                    Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Text(
                    text = "${cartItem.quantity}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.widthIn(min = 16.dp)
                )

                FilledIconButton(
                    onClick = onIncrease,
                    modifier = Modifier.size(24.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.width(4.dp))

                val priceDisplay = if (cartItem.isBuffetIncluded) "฿0.00" else cartItem.subtotal.toDisplayBahtWithSymbol()
                Text(
                    text = priceDisplay,
                    fontSize = 12.sp,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.widthIn(min = 55.dp)
                )
            }
        }
    }
}
