package sun.clientpos.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinLoginScreen(
    onPinSubmitted: (String) -> Unit,
    errorMessage: String? = null
) {
    var pinState by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(440.dp)
                .padding(20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // SunPOS Brand Icon & Title
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF0284C7).copy(alpha = 0.15f), shape = CircleShape)
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "☀️", fontSize = 28.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "เข้าสู่ระบบ SunPOS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "กรุณากดรหัส PIN 4 หลักประจำตัวพนักงาน",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                // PIN Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    repeat(4) { index ->
                        val isFilled = index < pinState.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (errorMessage != null) Color(0xFFEF4444)
                                    else if (isFilled) Color(0xFF38BDF8)
                                    else Color(0xFF334155)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isFilled) Color(0xFF38BDF8) else Color(0xFF475569),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                // Error Message Badge
                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "⚠️ $errorMessage",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // FoodStory Touch Keypad (3x4 Grid)
                val keypadLayout = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "⌫")
                )

                for (row in keypadLayout) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        for (key in row) {
                            Surface(
                                onClick = {
                                    when (key) {
                                        "C" -> pinState = ""
                                        "⌫" -> {
                                            if (pinState.isNotEmpty()) {
                                                pinState = pinState.dropLast(1)
                                            }
                                        }
                                        else -> {
                                            if (pinState.length < 4) {
                                                val newPin = pinState + key
                                                pinState = newPin
                                                if (newPin.length == 4) {
                                                    onPinSubmitted(newPin)
                                                }
                                            }
                                        }
                                    }
                                },
                                shape = CircleShape,
                                color = when (key) {
                                    "C" -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                    "⌫" -> Color(0xFF38BDF8).copy(alpha = 0.15f)
                                    else -> Color(0xFF0F172A)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when (key) {
                                        "C" -> Color(0xFFEF4444).copy(alpha = 0.4f)
                                        "⌫" -> Color(0xFF38BDF8).copy(alpha = 0.4f)
                                        else -> Color(0xFF334155)
                                    }
                                ),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (key) {
                                            "C" -> Color(0xFFEF4444)
                                            "⌫" -> Color(0xFF38BDF8)
                                            else -> Color.White
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Helpful PIN hint footer
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "💡 รหัสเริ่มต้น: แคชเชียร์ 1234  |  ผู้จัดการ 9999",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
