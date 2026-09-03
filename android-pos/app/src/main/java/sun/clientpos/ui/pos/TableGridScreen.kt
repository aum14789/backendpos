package sun.clientpos.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Clear
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
import sun.clientpos.common.MoneyUtils.toDisplayBahtWithSymbol
import sun.clientpos.data.local.entity.RoomBuffetSessionEntity
import sun.clientpos.data.local.entity.RoomBuffetTierEntity
import sun.clientpos.data.local.entity.RoomOrderEntity
import sun.clientpos.data.local.entity.RoomTableEntity
import sun.clientpos.data.local.entity.RoomZoneEntity

/**
 * Filter status definitions matching FoodStory POS table workflows.
 */
enum class TableFilterStatus(val displayName: String, val statusKeys: List<String>?) {
    ALL("ทั้งหมด", null),
    AVAILABLE("ว่าง", listOf("AVAILABLE")),
    OCCUPIED("มีลูกค้า", listOf("OCCUPIED")),
    WAITING_FOOD("รออาหาร", listOf("WAITING_FOOD", "IN_KITCHEN", "COOKING")),
    READY_TO_SERVE("พร้อมเสิร์ฟ", listOf("READY_TO_SERVE", "READY")),
    WAITING_PAYMENT("รอเช็คบิล", listOf("WAITING_PAYMENT", "BILL_REQUESTED")),
    BUFFET_EXPIRING("บุฟเฟ่ต์ใกล้หมดเวลา", listOf("BUFFET_EXPIRING", "BUFFET_NEAR_EXPIRY", "EXPIRING")),
    RESERVED("จองแล้ว", listOf("RESERVED"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableGridScreen(
    tables: List<RoomTableEntity>,
    zones: List<RoomZoneEntity> = emptyList(),
    activeOrders: List<RoomOrderEntity> = emptyList(),
    activeBuffetSessions: List<RoomBuffetSessionEntity> = emptyList(),
    buffetTiers: List<RoomBuffetTierEntity> = emptyList(),
    onOpenDineInTable: (RoomTableEntity, Int) -> Unit = { _, _ -> },
    onOpenBuffetTable: (RoomTableEntity, RoomBuffetTierEntity, Int, Int) -> Unit = { _, _, _, _ -> },
    onSelectActiveOrderTable: (RoomTableEntity) -> Unit = {},
    onSelectTakeaway: () -> Unit = {},
    onSelectDelivery: (String, String) -> Unit = { _, _ -> },
    onLogout: () -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedZoneId by remember { mutableStateOf<String?>(null) } // null = All Zones
    var selectedFilter by remember { mutableStateOf(TableFilterStatus.ALL) }

    // Dialog state for Opening Table
    var tableToOpen by remember { mutableStateOf<RoomTableEntity?>(null) }
    var showDeliveryDialog by remember { mutableStateOf(false) }

    // 1. Filter only active tables from Backend / Room
    val activeTables = tables.filter { it.isActive }

    // 2. Filter by Zone
    val tablesInZone = if (selectedZoneId == null) {
        activeTables
    } else {
        activeTables.filter { it.zoneId == selectedZoneId }
    }

    // Status counts for current zone
    val totalCount = tablesInZone.size
    val availableCount = tablesInZone.count { table ->
        val hasActiveOrder = activeOrders.any { it.tableId == table.tableId }
        table.status.uppercase() == "AVAILABLE" && !hasActiveOrder
    }
    val occupiedCount = tablesInZone.count { table ->
        val hasActiveOrder = activeOrders.any { it.tableId == table.tableId }
        table.status.uppercase() == "OCCUPIED" || hasActiveOrder
    }
    val waitingFoodCount = tablesInZone.count { table ->
        val activeOrder = activeOrders.find { it.tableId == table.tableId }
        table.status.uppercase() in listOf("WAITING_FOOD", "IN_KITCHEN", "COOKING") ||
                activeOrder?.status in listOf("IN_KITCHEN", "COOKING", "WAITING_FOOD")
    }
    val readyToServeCount = tablesInZone.count { table ->
        val activeOrder = activeOrders.find { it.tableId == table.tableId }
        table.status.uppercase() in listOf("READY_TO_SERVE", "READY") ||
                activeOrder?.status in listOf("READY", "READY_TO_SERVE")
    }
    val waitingPaymentCount = tablesInZone.count { table ->
        val activeOrder = activeOrders.find { it.tableId == table.tableId }
        table.status.uppercase() in listOf("WAITING_PAYMENT", "BILL_REQUESTED") ||
                activeOrder?.status in listOf("WAITING_PAYMENT", "BILL_REQUESTED")
    }

    // 2. Filter tables based on status and search query
    val filteredTables = tablesInZone.filter { table ->
        val activeOrder = activeOrders.find { it.tableId == table.tableId }
        val rawStatusUpper = if (activeOrder != null && table.status.uppercase() == "AVAILABLE") "OCCUPIED" else table.status.uppercase()
        val effectiveStatus = when {
            rawStatusUpper in listOf("WAITING_PAYMENT", "BILL_REQUESTED") || activeOrder?.status in listOf("WAITING_PAYMENT", "BILL_REQUESTED") -> "WAITING_PAYMENT"
            activeOrder?.status in listOf("IN_KITCHEN", "COOKING", "WAITING_FOOD") -> "WAITING_FOOD"
            activeOrder?.status in listOf("READY", "READY_TO_SERVE") -> "READY_TO_SERVE"
            activeOrder != null -> "OCCUPIED"
            else -> rawStatusUpper
        }

        val matchesFilter = when (selectedFilter) {
            TableFilterStatus.ALL -> true
            TableFilterStatus.AVAILABLE -> effectiveStatus == "AVAILABLE"
            TableFilterStatus.OCCUPIED -> effectiveStatus == "OCCUPIED"
            TableFilterStatus.WAITING_FOOD -> effectiveStatus == "WAITING_FOOD"
            TableFilterStatus.READY_TO_SERVE -> effectiveStatus == "READY_TO_SERVE"
            TableFilterStatus.WAITING_PAYMENT -> effectiveStatus == "WAITING_PAYMENT"
            TableFilterStatus.BUFFET_EXPIRING -> effectiveStatus in listOf("BUFFET_EXPIRING", "BUFFET_NEAR_EXPIRY", "EXPIRING")
            TableFilterStatus.RESERVED -> effectiveStatus == "RESERVED"
        }
        val matchesSearch = if (searchQuery.isBlank()) {
            true
        } else {
            table.nameNumber.contains(searchQuery.trim(), ignoreCase = true)
        }
        matchesFilter && matchesSearch
    }

    Scaffold(
        containerColor = Color(0xFF0F172A),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ผังโต๊ะอาหาร",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A)
                        ) {
                            Text(
                                text = "ว่าง $availableCount / $totalCount โต๊ะ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "ย้อนกลับ",
                                tint = Color.White
                            )
                        }
                    }
                },
                actions = {
                    // Quick Takeaway Shortcut Button
                    Surface(
                        onClick = onSelectTakeaway,
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .height(38.dp)
                            .padding(end = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "🥡", fontSize = 14.sp)
                            Text(
                                text = "กลับบ้าน",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }

                    // Quick Delivery Shortcut Button
                    Surface(
                        onClick = { showDeliveryDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6)),
                        modifier = Modifier
                            .height(38.dp)
                            .padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "🛵", fontSize = 14.sp)
                            Text(
                                text = "เดลิเวอรี",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC084FC)
                            )
                        }
                    }

                    // Quick Table Search Input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "🔍 ค้นหาเบอร์โต๊ะ...",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "ล้าง",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .width(170.dp)
                            .height(42.dp)
                            .padding(end = 6.dp)
                    )

                    // Logout Button
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "ออกจากระบบ",
                            tint = Color(0xFFEF4444)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B)
                )
            )
        },
        bottomBar = {
            // Status Sub-Filter Chips & Quick Action Hint Footer Bar
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filterTabs = listOf(
                        FilterTabItem(TableFilterStatus.ALL, "ทั้งหมด", totalCount, Color(0xFF38BDF8)),
                        FilterTabItem(TableFilterStatus.AVAILABLE, "🟢 ว่าง", availableCount, Color(0xFF10B981)),
                        FilterTabItem(TableFilterStatus.OCCUPIED, "🔴 มีลูกค้า", occupiedCount, Color(0xFFEF4444)),
                        FilterTabItem(TableFilterStatus.WAITING_FOOD, "🟠 รออาหาร", waitingFoodCount, Color(0xFFF97316)),
                        FilterTabItem(TableFilterStatus.READY_TO_SERVE, "🔵 พร้อมเสิร์ฟ", readyToServeCount, Color(0xFF38BDF8)),
                        FilterTabItem(TableFilterStatus.WAITING_PAYMENT, "🟡 รอเช็คบิล", waitingPaymentCount, Color(0xFFF59E0B))
                    )

                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(filterTabs) { tab ->
                            val isSelected = selectedFilter == tab.status
                            Surface(
                                onClick = { selectedFilter = tab.status },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) tab.color.copy(alpha = 0.25f) else Color(0xFF0F172A),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) tab.color else Color(0xFF334155)
                                ),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) tab.color else Color(0xFFCBD5E1)
                                    )
                                    Text(
                                        text = "(${tab.count})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) tab.color else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "💡 แตะโต๊ะว่างเพื่อเปิดโต๊ะ • แตะโต๊ะมีลูกค้าเพื่อสั่งอาหาร",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ══════════════════════════════════════════════════════════════════
            // 1. ZONE SELECTOR TABS BAR (เลือกโซนก่อนเห็นโต๊ะ)
            // ══════════════════════════════════════════════════════════════════
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "โซนร้าน:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(end = 10.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "All Zones" Chip
                    item {
                        val isAllSelected = selectedZoneId == null
                        Surface(
                            onClick = { selectedZoneId = null },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAllSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                            border = BorderStroke(
                                1.dp,
                                if (isAllSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                            ),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "🏢 ทุกโซน",
                                    fontSize = 13.sp,
                                    fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAllSelected) Color.White else Color(0xFFCBD5E1)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (isAllSelected) Color.White.copy(alpha = 0.3f) else Color(0xFF0F172A)
                                ) {
                                    Text(
                                        text = "${tables.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAllSelected) Color.White else Color(0xFF94A3B8),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Dynamic Zones from Database / Backend
                    items(zones.filter { it.isActive }) { zone ->
                        val isSelected = selectedZoneId == zone.zoneId
                        val count = activeTables.count { it.zoneId == zone.zoneId }
                        val isBuffetZone = zone.zoneType.uppercase() == "BUFFET" || zone.name.contains("บุฟเฟ่ต์", ignoreCase = true)

                        Surface(
                            onClick = { selectedZoneId = zone.zoneId },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) {
                                if (isBuffetZone) Color(0xFFD97706) else Color(0xFF0284C7)
                            } else {
                                Color(0xFF1E293B)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) {
                                    if (isBuffetZone) Color(0xFFFBBF24) else Color(0xFF38BDF8)
                                } else {
                                    Color(0xFF334155)
                                }
                            ),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isBuffetZone) "🥩 ${zone.name}" else "🍽️ ${zone.name}",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) Color.White.copy(alpha = 0.3f) else Color(0xFF0F172A)
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════════
            // 2. TABLE CARDS GRID (Landscape Tablet Optimized)
            // ══════════════════════════════════════════════════════════════════
            if (filteredTables.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🍽️ ไม่พบโต๊ะในโซนหรือเงื่อนไขที่เลือก",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "ลองเปลี่ยนโซน หรือแตะล้างตัวกรอง",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Button(
                            onClick = {
                                selectedZoneId = null
                                selectedFilter = TableFilterStatus.ALL
                                searchQuery = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("แสดงทุกโซนและทุกสถานะ", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 165.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTables, key = { it.tableId }) { table ->
                        val activeOrder = activeOrders.find { it.tableId == table.tableId }
                        val activeSession = activeBuffetSessions.find { it.orderId == activeOrder?.orderId }
                        val isAvailable = (table.status.uppercase() == "AVAILABLE") && (activeOrder == null)

                        FoodStoryTableCard(
                            table = table,
                            activeOrder = activeOrder,
                            activeBuffetSession = activeSession,
                            onClick = {
                                if (isAvailable) {
                                    // Open Table Modal Step (Enter Guest Count / Buffet Tier)
                                    tableToOpen = table
                                } else {
                                    // Directly open existing order
                                    onSelectActiveOrderTable(table)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. OPEN TABLE MODAL DIALOG (ขั้นตอนการเปิดโต๊ะใหม่)
    // ══════════════════════════════════════════════════════════════════
    if (tableToOpen != null) {
        val targetTable = tableToOpen!!
        val targetZone = zones.find { it.zoneId == targetTable.zoneId }
        val isTargetBuffetZone = targetZone?.zoneType?.uppercase() == "BUFFET" ||
                targetZone?.name?.contains("บุฟเฟ่ต์", ignoreCase = true) == true ||
                targetTable.tableTypeId == "type-buffet"

        var isBuffetMode by remember { mutableStateOf(isTargetBuffetZone) }
        var buffetStep by remember { mutableStateOf(1) } // 1 = เลือกโปรโมชัน, 2 = ใส่จำนวนผู้ใหญ่/เด็ก
        var guestCount by remember { mutableStateOf(2) }
        var selectedTier by remember { mutableStateOf(buffetTiers.firstOrNull()) }
        var adultCount by remember { mutableStateOf(2) }
        var childCount by remember { mutableStateOf(0) }

        val hasBuffetPromos = buffetTiers.isNotEmpty()

        AlertDialog(
            onDismissRequest = { tableToOpen = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🟢 เปิดโต๊ะ ${targetTable.nameNumber}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = if (isBuffetMode) {
                                if (buffetStep == 1) "ขั้นตอน 1/2: เลือกแพ็กเกจโปรโมชันบุฟเฟ่ต์"
                                else "ขั้นตอน 2/2: ระบุจำนวนลูกค้า (ผู้ใหญ่ / เด็ก)"
                            } else {
                                "ความจุโต๊ะ: ${targetTable.capacity} ที่นั่ง (อ้างอิง)"
                            },
                            fontSize = 12.sp,
                            color = if (isBuffetMode) Color(0xFFFBBF24) else Color(0xFF94A3B8)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mode Switcher: Only shown in normal non-buffet zone
                    if (!isTargetBuffetZone) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = { isBuffetMode = false },
                                shape = RoundedCornerShape(10.dp),
                                color = if (!isBuffetMode) Color(0xFF0284C7).copy(alpha = 0.2f) else Color(0xFF1E293B),
                                border = BorderStroke(
                                    width = if (!isBuffetMode) 2.dp else 1.dp,
                                    color = if (!isBuffetMode) Color(0xFF38BDF8) else Color(0xFF334155)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "🍽️ ทานที่ร้าน (A La Carte)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isBuffetMode) Color.White else Color(0xFF94A3B8)
                                    )
                                }
                            }

                            Surface(
                                onClick = { isBuffetMode = true },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isBuffetMode) Color(0xFFD97706).copy(alpha = 0.2f) else Color(0xFF1E293B),
                                border = BorderStroke(
                                    width = if (isBuffetMode) 2.dp else 1.dp,
                                    color = if (isBuffetMode) Color(0xFFFBBF24) else Color(0xFF334155)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "🥩 บุฟเฟ่ต์ (Buffet)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBuffetMode) Color.White else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    if (!isBuffetMode) {
                        // ── Standard Dine-In Headcount Stepper ──
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "👥 ระบุจำนวนลูกค้า (ท่าน)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledIconButton(
                                        onClick = { if (guestCount > 1) guestCount-- },
                                        modifier = Modifier.size(42.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155))
                                    ) {
                                        Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    }

                                    Text(
                                        text = "$guestCount ท่าน",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF38BDF8)
                                    )

                                    FilledIconButton(
                                        onClick = { guestCount++ },
                                        modifier = Modifier.size(42.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0284C7))
                                    ) {
                                        Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    }
                                }

                                // Quick Guest Presets
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(1, 2, 4, 6, 8).forEach { preset ->
                                        Surface(
                                            onClick = { guestCount = preset },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (guestCount == preset) Color(0xFF0284C7) else Color(0xFF0F172A),
                                            border = BorderStroke(1.dp, if (guestCount == preset) Color(0xFF38BDF8) else Color(0xFF334155)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(32.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "$preset",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // ── 2-Step Buffet Flow ──
                        if (!hasBuffetPromos) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f)),
                                border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("⚠️", fontSize = 20.sp)
                                        Text(
                                            text = "ไม่พบโปรโมชันบุฟเฟ่ต์ในระบบ",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFFCA5A5)
                                        )
                                    }
                                    Text(
                                        text = "กรุณา sync ข้อมูลจากเซิร์ฟเวอร์ก่อนเปิดโต๊ะบุฟเฟ่ต์",
                                        fontSize = 12.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        } else if (buffetStep == 1) {
                            // ── Step 1: เลือกประเภท / แพ็กเกจโปรโมชัน ──
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "แตะเลือกโปรโมชันบุฟเฟ่ต์ที่ต้องการ:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD5E1)
                                )

                                buffetTiers.forEach { tier ->
                                    val isSelected = selectedTier?.tierId == tier.tierId
                                    Surface(
                                        onClick = {
                                            selectedTier = tier
                                            buffetStep = 2 // Move directly to Step 2 upon selection
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) Color(0xFFD97706).copy(alpha = 0.2f) else Color(0xFF1E293B),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFFFBBF24) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tier.name,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "👨 ผู้ใหญ่ ${tier.adultPrice.toDisplayBahtWithSymbol()}",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF38BDF8)
                                                    )
                                                    if (tier.childPrice > 0L) {
                                                        Text(
                                                            text = "👶 เด็ก ${tier.childPrice.toDisplayBahtWithSymbol()}",
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF94A3B8)
                                                        )
                                                    }
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFD97706).copy(alpha = 0.25f),
                                                border = BorderStroke(1.dp, Color(0xFFFBBF24))
                                            ) {
                                                Text(
                                                    text = "⏱️ ${tier.timeLimitMinutes} นาที",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFBBF24),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // ── Step 2: ระบุจำนวนผู้ใหญ่ และเด็ก ──
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Selected Promo Header with Change Option
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFD97706).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFFFBBF24)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "🥩 ${selectedTier?.name ?: ""}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFFBBF24)
                                            )
                                            Text(
                                                text = "ผู้ใหญ่ ${selectedTier?.adultPrice?.toDisplayBahtWithSymbol()} / เด็ก ${selectedTier?.childPrice?.toDisplayBahtWithSymbol()} (⏱️ ${selectedTier?.timeLimitMinutes} นาที)",
                                                fontSize = 11.sp,
                                                color = Color(0xFFCBD5E1)
                                            )
                                        }

                                        TextButton(
                                            onClick = { buffetStep = 1 },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF38BDF8))
                                        ) {
                                            Text("↩ เปลี่ยนโปร", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Adult Stepper
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("👨 จำนวนผู้ใหญ่ (Adults):", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilledIconButton(
                                                    onClick = { if (adultCount > 0 && (adultCount + childCount) > 1) adultCount-- },
                                                    modifier = Modifier.size(34.dp),
                                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155))
                                                ) {
                                                    Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                }
                                                Text("$adultCount ท่าน", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                                FilledIconButton(
                                                    onClick = { adultCount++ },
                                                    modifier = Modifier.size(34.dp),
                                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFD97706))
                                                ) {
                                                    Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(1, 2, 4, 6, 8).forEach { preset ->
                                                Surface(
                                                    onClick = { adultCount = preset },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (adultCount == preset) Color(0xFFD97706) else Color(0xFF0F172A),
                                                    border = BorderStroke(1.dp, if (adultCount == preset) Color(0xFFFBBF24) else Color(0xFF334155)),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(28.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("$preset", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Child Stepper
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("👶 จำนวนเด็ก (Children):", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilledIconButton(
                                                    onClick = { if (childCount > 0) childCount-- },
                                                    modifier = Modifier.size(34.dp),
                                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155))
                                                ) {
                                                    Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                }
                                                Text("$childCount ท่าน", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                                FilledIconButton(
                                                    onClick = { childCount++ },
                                                    modifier = Modifier.size(34.dp),
                                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFD97706))
                                                ) {
                                                    Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(0, 1, 2, 3).forEach { preset ->
                                                Surface(
                                                    onClick = { childCount = preset },
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (childCount == preset) Color(0xFFD97706) else Color(0xFF0F172A),
                                                    border = BorderStroke(1.dp, if (childCount == preset) Color(0xFFFBBF24) else Color(0xFF334155)),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(28.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("$preset", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Total Headcharge Summary
                                if (selectedTier != null) {
                                    val estTotal = (selectedTier!!.adultPrice * adultCount) + (selectedTier!!.childPrice * childCount)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF0F172A),
                                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.6f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("รวมยอดบุฟเฟ่ต์ ($adultCount ผู้ใหญ่, $childCount เด็ก):", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                                            Text(
                                                text = estTotal.toDisplayBahtWithSymbol(),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFFBBF24)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val isFormValid = if (isBuffetMode) {
                    if (buffetStep == 1) selectedTier != null
                    else hasBuffetPromos && selectedTier != null && (adultCount + childCount) >= 1
                } else {
                    guestCount >= 1
                }

                Button(
                    onClick = {
                        if (isBuffetMode) {
                            if (buffetStep == 1) {
                                buffetStep = 2
                            } else if (selectedTier != null) {
                                onOpenBuffetTable(targetTable, selectedTier!!, adultCount, childCount)
                                tableToOpen = null
                            }
                        } else {
                            onOpenDineInTable(targetTable, guestCount)
                            tableToOpen = null
                        }
                    },
                    enabled = isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBuffetMode) Color(0xFFD97706) else Color(0xFF10B981),
                        disabledContainerColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isBuffetMode) {
                            if (!hasBuffetPromos) "❌ กรุณา sync โปรโมชันก่อน"
                            else if (buffetStep == 1) "ถัดไป: ระบุจำนวนลูกค้า ➔"
                            else "🚀 ยืนยันเปิดโต๊ะบุฟเฟ่ต์ & เริ่มนับเวลา"
                        } else {
                            "🚀 ยืนยันเปิดโต๊ะทานที่ร้าน"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isFormValid) Color.White else Color(0xFF94A3B8)
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (isBuffetMode && buffetStep == 2) {
                            buffetStep = 1
                        } else {
                            tableToOpen = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isBuffetMode && buffetStep == 2) "⬅ ย้อนกลับไปเลือกโปรโมชัน" else "ยกเลิก",
                        color = Color(0xFF94A3B8)
                    )
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    // ── Delivery Quick Dialog ──
    if (showDeliveryDialog) {
        var customerName by remember { mutableStateOf("") }
        var customerPhone by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showDeliveryDialog = false },
            title = { Text("🛵 สั่งออเดอร์เดลิเวอรี (Delivery)", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("ชื่อลูกค้า / ผู้รับ *") },
                        placeholder = { Text("เช่น คุณวิชัย ประเสริฐ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("เบอร์โทรศัพท์ติดต่อ *") },
                        placeholder = { Text("เช่น 0812345678") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customerName.isNotBlank() && customerPhone.isNotBlank()) {
                            onSelectDelivery(customerName, customerPhone)
                            showDeliveryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                ) {
                    Text("เริ่มออเดอร์เดลิเวอรี ➔")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeliveryDialog = false }) {
                    Text("ยกเลิก", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

private data class FilterTabItem(
    val status: TableFilterStatus,
    val title: String,
    val count: Int,
    val color: Color
)

@Composable
fun FoodStoryTableCard(
    table: RoomTableEntity,
    activeOrder: RoomOrderEntity? = null,
    activeBuffetSession: RoomBuffetSessionEntity? = null,
    onClick: () -> Unit
) {
    val rawStatusUpper = if (activeOrder != null && table.status.uppercase() == "AVAILABLE") "OCCUPIED" else table.status.uppercase()
    val statusUpper = when {
        rawStatusUpper in listOf("WAITING_PAYMENT", "BILL_REQUESTED") || activeOrder?.status in listOf("WAITING_PAYMENT", "BILL_REQUESTED") -> "WAITING_PAYMENT"
        activeOrder?.status in listOf("IN_KITCHEN", "COOKING", "WAITING_FOOD") -> "WAITING_FOOD"
        activeOrder?.status in listOf("READY", "READY_TO_SERVE") -> "READY_TO_SERVE"
        activeOrder != null -> "OCCUPIED"
        else -> rawStatusUpper
    }

    // Determine status visual attributes according to FoodStory POS design
    val (statusColor, statusText, actionHint, timeElapsedText) = when (statusUpper) {
        "AVAILABLE" -> TableVisualConfig(
            color = Color(0xFF10B981), // Emerald Green
            text = "ว่าง",
            actionHint = "แตะเพื่อเปิดโต๊ะ ➔",
            timeElapsed = null
        )
        "OCCUPIED" -> TableVisualConfig(
            color = Color(0xFFEF4444), // Crimson Red
            text = "มีลูกค้า",
            actionHint = if (activeBuffetSession != null || activeOrder?.orderType == "BUFFET") "แตะดูบิล / สั่งอาหารบุฟเฟ่ต์ ➔" else "แตะดูบิล / สั่งอาหาร ➔",
            timeElapsed = "⏱️ ทานอยู่"
        )
        "WAITING_FOOD", "IN_KITCHEN", "COOKING" -> TableVisualConfig(
            color = Color(0xFFF97316), // Orange
            text = "รออาหาร",
            actionHint = "แตะดูบิล / สั่งอาหาร ➔",
            timeElapsed = "⏱️ ส่งครัวแล้ว"
        )
        "READY_TO_SERVE", "READY" -> TableVisualConfig(
            color = Color(0xFF38BDF8), // Light Blue
            text = "พร้อมเสิร์ฟ",
            actionHint = "แตะดูบิล / สั่งอาหาร ➔",
            timeElapsed = "🔔 พร้อมเสิร์ฟ"
        )
        "WAITING_PAYMENT", "BILL_REQUESTED" -> TableVisualConfig(
            color = Color(0xFFF59E0B), // Amber Yellow
            text = "รอเช็คบิล",
            actionHint = "แตะเพื่อชำระเงิน ➔",
            timeElapsed = "💵 เรียกบิล"
        )
        "BUFFET_EXPIRING", "BUFFET_NEAR_EXPIRY", "EXPIRING" -> TableVisualConfig(
            color = Color(0xFFEC4899), // Pink / Violet
            text = "บุฟเฟ่ต์ใกล้หมดเวลา",
            actionHint = "เหลือเวลา < 15น. ➔",
            timeElapsed = "⏳ เหลือ <15น."
        )
        "RESERVED" -> TableVisualConfig(
            color = Color(0xFF8B5CF6), // Purple
            text = "จองแล้ว",
            actionHint = "โต๊ะจอง ➔",
            timeElapsed = "📅 จองแล้ว"
        )
        else -> TableVisualConfig(
            color = Color(0xFF64748B),
            text = table.status,
            actionHint = "แตะเพื่อเลือก ➔",
            timeElapsed = null
        )
    }

    val isAvailable = statusUpper == "AVAILABLE"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(145.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = statusColor.copy(alpha = if (isAvailable) 0.5f else 0.85f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(statusColor.copy(alpha = if (isAvailable) 0.04f else 0.08f))
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Row 1: Table Number & Status Badge with Glow Dot ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = table.nameNumber,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                // Status Badge Pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(statusColor, shape = CircleShape)
                        )
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // ── Row 2: Headcount / Capacity & Order Total Amount / Status ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val headcountDisplay = when {
                    activeBuffetSession != null -> {
                        if (activeBuffetSession.childCount > 0) {
                            "👥 ${activeBuffetSession.adultCount}ผญ ${activeBuffetSession.childCount}ด"
                        } else {
                            "👥 ${activeBuffetSession.adultCount} ท่าน"
                        }
                    }
                    activeOrder != null -> {
                        "👥 มีลูกค้า"
                    }
                    else -> {
                        "ความจุ ${table.capacity} ที่นั่ง"
                    }
                }

                Text(
                    text = headcountDisplay,
                    fontSize = 12.sp,
                    fontWeight = if (activeOrder != null) FontWeight.Bold else FontWeight.Medium,
                    color = if (activeOrder != null) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
                )

                if (activeOrder != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, if (activeOrder.totalAmount > 0L) Color(0xFFFBBF24).copy(alpha = 0.6f) else statusColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = activeOrder.totalAmount.toDisplayBahtWithSymbol(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (activeOrder.totalAmount > 0L) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (timeElapsedText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = timeElapsedText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // ── Row 3: Action Hint Button Bottom Bar ──
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isAvailable) Color(0xFF0284C7).copy(alpha = 0.15f) else statusColor.copy(alpha = 0.15f),
                border = BorderStroke(
                    1.dp,
                    if (isAvailable) Color(0xFF38BDF8).copy(alpha = 0.3f) else statusColor.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = actionHint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAvailable) Color(0xFF38BDF8) else statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                )
            }
        }
    }
}

private data class TableVisualConfig(
    val color: Color,
    val text: String,
    val actionHint: String,
    val timeElapsed: String?
)

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = CircleShape)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFFCBD5E1)
        )
    }
}
