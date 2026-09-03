package sun.clientpos.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sun.clientpos.common.MoneyUtils.toDisplayBahtWithSymbol
import sun.clientpos.data.local.entity.RoomBuffetTierEntity
import sun.clientpos.data.repository.AuthenticatedUserSession
import sun.clientpos.sync.ConnectionStatus
import sun.clientpos.sync.POSSyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderTypeSelectionScreen(
    user: AuthenticatedUserSession,
    branchCode: String,
    deviceId: String,
    syncState: POSSyncState,
    connectionStatus: ConnectionStatus,
    buffetTiers: List<RoomBuffetTierEntity>,
    onSelectDineIn: () -> Unit,
    onSelectBuffet: (RoomBuffetTierEntity, Int, Int) -> Unit,
    onSelectTakeaway: () -> Unit,
    onSelectDelivery: (String, String) -> Unit,
    onManualSync: () -> Unit,
    onToggleConnection: () -> Unit,
    onLogout: () -> Unit
) {
    var showBuffetDialog by remember { mutableStateOf(false) }
    var selectedBuffetTier by remember { mutableStateOf(buffetTiers.firstOrNull()) }
    var adultCount by remember { mutableStateOf(2) }
    var childCount by remember { mutableStateOf(0) }

    var showDeliveryDialog by remember { mutableStateOf(false) }
    var deliveryName by remember { mutableStateOf("") }
    var deliveryPhone by remember { mutableStateOf("") }

    LaunchedEffect(buffetTiers) {
        if (selectedBuffetTier == null && buffetTiers.isNotEmpty()) {
            selectedBuffetTier = buffetTiers.first()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "SunPOS",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A)
                        ) {
                            Text(
                                text = "สาขา: $branchCode  |  เครื่อง: $deviceId  |  แคชเชียร์: ${user.fullName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Online / Offline Switch Chip
                    Surface(
                        onClick = onToggleConnection,
                        shape = RoundedCornerShape(8.dp),
                        color = if (connectionStatus == ConnectionStatus.ONLINE) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (connectionStatus == ConnectionStatus.ONLINE) Color(0xFF10B981) else Color(0xFFEF4444)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (connectionStatus == ConnectionStatus.ONLINE) Color(0xFF10B981) else Color(0xFFEF4444),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = if (connectionStatus == ConnectionStatus.ONLINE) "ONLINE" else "OFFLINE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (connectionStatus == ConnectionStatus.ONLINE) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }
                    }

                    // Sync Status Indicator Badge
                    val (syncText, syncColor) = when (syncState) {
                        POSSyncState.SYNCED -> Pair("SYNCED", Color(0xFF10B981))
                        POSSyncState.SYNCING -> Pair("SYNCING...", Color(0xFF38BDF8))
                        POSSyncState.PENDING_CHANGES -> Pair("PENDING", Color(0xFFF59E0B))
                        POSSyncState.SYNC_ERROR -> Pair("ERROR", Color(0xFFEF4444))
                    }

                    Surface(
                        onClick = onManualSync,
                        shape = RoundedCornerShape(8.dp),
                        color = syncColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, syncColor.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync",
                                tint = syncColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = syncText,
                                color = syncColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Logout / Lock Button
                    Button(
                        onClick = onLogout,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(14.dp)
                            )
                            Text("ล็อกระบบ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
            )
        },
        containerColor = Color(0xFF0F172A)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Text(
                    text = "เลือกประเภทการให้บริการ (Service Mode)",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "แตะเพื่อเริ่มต้นรับออเดอร์ตามประเภทที่ลูกค้าต้องการ",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 4 Main Service Cards (FoodStory Grid Style)
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 240.dp)
            ) {
                item {
                    FoodStoryOrderTypeCard(
                        emoji = "🍽️",
                        title = "ทานที่ร้าน (Dine-In)",
                        subtitle = "เปิดโต๊ะ & ผังร้านอาหาร",
                        accentColor = Color(0xFF0284C7),
                        onClick = onSelectDineIn
                    )
                }
                item {
                    FoodStoryOrderTypeCard(
                        emoji = "🥩",
                        title = "บุฟเฟ่ต์ (Buffet)",
                        subtitle = "เลือกแพ็กเกจ & นับเวลา",
                        accentColor = Color(0xFFF59E0B),
                        onClick = { showBuffetDialog = true }
                    )
                }
                item {
                    FoodStoryOrderTypeCard(
                        emoji = "🥡",
                        title = "กลับบ้าน (Takeaway)",
                        subtitle = "สั่งด่วนหน้าร้าน / ใส่กล่อง",
                        accentColor = Color(0xFF10B981),
                        onClick = onSelectTakeaway
                    )
                }
                item {
                    FoodStoryOrderTypeCard(
                        emoji = "🛵",
                        title = "เดลิเวอรี (Delivery)",
                        subtitle = "Grab / LINE MAN / Direct",
                        accentColor = Color(0xFF8B5CF6),
                        onClick = { showDeliveryDialog = true }
                    )
                }
            }
        }
    }

    // ── Buffet Setup Dialog ──
    if (showBuffetDialog) {
        AlertDialog(
            onDismissRequest = { showBuffetDialog = false },
            title = {
                Text(
                    "🥩 เริ่มเซสชันบุฟเฟ่ต์ (Start Buffet Session)",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("1. เลือกแพ็กเกจบุฟเฟ่ต์:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                    buffetTiers.forEach { tier ->
                        val isSelected = selectedBuffetTier?.tierId == tier.tierId
                        Surface(
                            onClick = { selectedBuffetTier = tier },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF1E293B),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFF59E0B) else Color(0xFF334155)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(tier.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                                    Text("⏳ จำกัดเวลา: ${tier.timeLimitMinutes} นาที", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("ผู้ใหญ่: ${tier.adultPrice.toDisplayBahtWithSymbol()}", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B), fontSize = 13.sp)
                                    Text("เด็ก: ${tier.childPrice.toDisplayBahtWithSymbol()}", fontSize = 11.sp, color = Color(0xFF38BDF8))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    Text("2. ระบุจำนวนผู้รับประทาน:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ผู้ใหญ่:", color = Color.White, fontSize = 13.sp)
                            FilledIconButton(
                                onClick = { if (adultCount > 1) adultCount-- },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155))
                            ) {
                                Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("$adultCount ท่าน", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            FilledIconButton(
                                onClick = { adultCount++ },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("เด็ก:", color = Color.White, fontSize = 13.sp)
                            FilledIconButton(
                                onClick = { if (childCount > 0) childCount-- },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF334155))
                            ) {
                                Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("$childCount ท่าน", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            FilledIconButton(
                                onClick = { childCount++ },
                                modifier = Modifier.size(32.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tier = selectedBuffetTier ?: buffetTiers.firstOrNull()
                        if (tier != null) {
                            onSelectBuffet(tier, adultCount, childCount)
                        }
                        showBuffetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("เริ่มออเดอร์บุฟเฟ่ต์ ➔", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBuffetDialog = false }) {
                    Text("ยกเลิก", color = Color(0xFFEF4444))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    // ── Delivery Setup Dialog ──
    if (showDeliveryDialog) {
        AlertDialog(
            onDismissRequest = { showDeliveryDialog = false },
            title = {
                Text("🛵 ออเดอร์เดลิเวอรี (Delivery Order)", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = deliveryName,
                        onValueChange = { deliveryName = it },
                        label = { Text("ชื่อลูกค้า / แพลตฟอร์ม *") },
                        placeholder = { Text("เช่น GrabFood #104, คุณสมชาย") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = deliveryPhone,
                        onValueChange = { deliveryPhone = it },
                        label = { Text("เบอร์โทรศัพท์ติดต่อ (ถ้ามี)") },
                        placeholder = { Text("เช่น 081-234-5678") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = deliveryName.ifBlank { "Delivery Customer" }
                        onSelectDelivery(name, deliveryPhone)
                        showDeliveryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("เปิดออเดอร์ ➔", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeliveryDialog = false }) {
                    Text("ยกเลิก", color = Color(0xFFEF4444))
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

@Composable
fun FoodStoryOrderTypeCard(
    modifier: Modifier = Modifier,
    emoji: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = accentColor.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(accentColor.copy(alpha = 0.05f))
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(accentColor.copy(alpha = 0.15f), shape = CircleShape)
                    .border(1.dp, accentColor.copy(alpha = 0.4f), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 34.sp)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = accentColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "แตะเพื่อเข้าใช้งาน",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}
