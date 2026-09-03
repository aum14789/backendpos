package sun.clientpos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import sun.clientpos.sync.ConnectionStatus
import sun.clientpos.sync.POSSyncState
import sun.clientpos.ui.auth.PinLoginScreen
import sun.clientpos.ui.pos.*
import sun.clientpos.ui.theme.ClientPOSTheme
import sun.clientpos.ui.viewmodel.POSViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object OrderTypeSelect : Screen("order_type_select")
    object TableSelect : Screen("table_select")
    object PosOrder : Screen("pos_order")
    object Payment : Screen("payment")
    object ReceiptPreview : Screen("receipt_preview")
}

class MainActivity : ComponentActivity() {

    private val viewModel: POSViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ClientPOSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    SunPOSAppNavHost(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun SunPOSAppNavHost(viewModel: POSViewModel) {
    val navController = rememberNavController()

    val authenticatedUser by viewModel.authenticatedUser.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val zones by viewModel.zones.collectAsState()
    val tables by viewModel.tables.collectAsState()
    val activeOrders by viewModel.activeOrders.collectAsState()
    val activeBuffetSessions by viewModel.activeBuffetSessions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val menuItems by viewModel.menuItems.collectAsState()
    val buffetTiers by viewModel.buffetTiers.collectAsState()
    val eligibleBuffetItemIds by viewModel.eligibleBuffetItemIds.collectAsState()
    val currentOrderState by viewModel.currentOrderState.collectAsState()
    val completedReceipt by viewModel.completedReceipt.collectAsState()
    val selectedCustomer by viewModel.selectedCustomer.collectAsState()
    val appliedCoupon by viewModel.appliedCoupon.collectAsState()
    val redeemedPointsSatang by viewModel.redeemedPointsSatang.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.route
    ) {
        // ── 1. PIN Login Screen (Directly navigates to Table Floor Plan) ──
        composable(Screen.Login.route) {
            PinLoginScreen(
                onPinSubmitted = { pin ->
                    viewModel.loginWithPin(pin) {
                        navController.navigate(Screen.TableSelect.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
                errorMessage = loginError
            )
        }

        // ── 2. Order Type Selection Screen (Optional Secondary Screen) ──
        composable(Screen.OrderTypeSelect.route) {
            val user = authenticatedUser
            if (user == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            } else {
                OrderTypeSelectionScreen(
                    user = user,
                    branchCode = "BR-01",
                    deviceId = viewModel.deviceId,
                    syncState = syncState,
                    connectionStatus = connectionStatus,
                    buffetTiers = buffetTiers,
                    onSelectDineIn = {
                        navController.navigate(Screen.TableSelect.route)
                    },
                    onSelectBuffet = { tier, adults, children ->
                        viewModel.startBuffetOrder(tier, adults, children, table = null)
                        navController.navigate(Screen.TableSelect.route)
                    },
                    onSelectTakeaway = {
                        viewModel.startTakeawayOrder()
                        navController.navigate(Screen.PosOrder.route)
                    },
                    onSelectDelivery = { name, phone ->
                        viewModel.startDeliveryOrder(name, phone)
                        navController.navigate(Screen.PosOrder.route)
                    },
                    onManualSync = { viewModel.triggerManualSync() },
                    onToggleConnection = { viewModel.toggleConnectionMode() },
                    onLogout = {
                        viewModel.logout {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                )
            }
        }

        // ── 3. Table Floor Plan Grid (Primary Screen After Login) ──
        composable(Screen.TableSelect.route) {
            TableGridScreen(
                tables = tables,
                zones = zones,
                activeOrders = activeOrders,
                activeBuffetSessions = activeBuffetSessions,
                buffetTiers = buffetTiers,
                onOpenDineInTable = { table, guestCount ->
                    viewModel.openDineInTable(table, guestCount) {
                        // Stays on Table Floor Plan screen
                    }
                },
                onOpenBuffetTable = { table, tier, adults, children ->
                    viewModel.openBuffetTable(table, tier, adults, children) {
                        // Stays on Table Floor Plan screen
                    }
                },
                onSelectActiveOrderTable = { table ->
                    viewModel.selectOrOpenTable(table) {
                        navController.navigate(Screen.PosOrder.route)
                    }
                },
                onSelectTakeaway = {
                    viewModel.startTakeawayOrder()
                    navController.navigate(Screen.PosOrder.route)
                },
                onSelectDelivery = { name, phone ->
                    viewModel.startDeliveryOrder(name, phone)
                    navController.navigate(Screen.PosOrder.route)
                },
                onLogout = {
                    viewModel.logout {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        // ── 4. POS Order Screen (Catalog, Cart, Pricing, Buffet, CRM Loyalty) ──
        composable(Screen.PosOrder.route) {
            val orderState = currentOrderState
            val canEditBuffet = authenticatedUser?.permissions?.any { it in listOf("USER_MANAGE", "DISCOUNT_OVERRIDE", "ORDER_VOID") } == true || authenticatedUser?.userId == "usr-admin"

            POSOrderScreen(
                categories = categories,
                menuItems = menuItems,
                buffetTiers = buffetTiers,
                activePromotions = emptyList(),
                activeBuffetSession = orderState?.buffetSession,
                eligibleBuffetMenuItemIds = eligibleBuffetItemIds,
                selectedCustomer = selectedCustomer,
                tableName = orderState?.table?.nameNumber,
                canEditBuffetSettings = canEditBuffet,
                onSelectCustomer = { cust -> viewModel.selectCustomer(cust) },
                onSearchCustomers = { q -> viewModel.searchCustomers(q) },
                onRegisterCustomer = { name, phone -> viewModel.registerLocalCustomer(name, phone) },
                onSendToKitchen = { cartItems, orderType, tier, adults, children, manualDiscountSatang, manualDiscountPercent ->
                    viewModel.sendToKitchen(
                        cartItems = cartItems,
                        orderType = orderType,
                        buffetTier = tier,
                        adults = adults,
                        children = children,
                        manualDiscountSatang = manualDiscountSatang,
                        manualDiscountPercent = manualDiscountPercent,
                        onSent = {
                            if (orderType == "DINE_IN" || orderType == "BUFFET") {
                                navController.navigate(Screen.TableSelect.route) {
                                    popUpTo(Screen.TableSelect.route) { inclusive = true }
                                }
                            }
                        }
                    )
                },
                onProceedToCheckout = { cartItems, orderType, tier, adults, children, manualDiscountSatang, manualDiscountPercent ->
                    viewModel.proceedToCheckout(
                        cartItems = cartItems,
                        orderType = orderType,
                        buffetTier = tier,
                        adults = adults,
                        children = children,
                        manualDiscountSatang = manualDiscountSatang,
                        manualDiscountPercent = manualDiscountPercent,
                        onProceedToPayment = {
                            navController.navigate(Screen.Payment.route)
                        }
                    )
                },
                onConfirmOrder = { cartItems, orderType, tier, adults, children, manualDiscountSatang, manualDiscountPercent ->
                    viewModel.confirmOrder(
                        cartItems = cartItems,
                        orderType = orderType,
                        buffetTier = tier,
                        adults = adults,
                        children = children,
                        manualDiscountSatang = manualDiscountSatang,
                        manualDiscountPercent = manualDiscountPercent,
                        onProceedToPayment = {
                            navController.navigate(Screen.Payment.route)
                        }
                    )
                }
            )
        }

        // ── 5. Multi-Payment, Loyalty Points & Coupon Screen ──
        composable(Screen.Payment.route) {
            val order = currentOrderState?.createdOrder
            val totalAmount = order?.totalAmount ?: 0L

            PaymentScreen(
                orderTotalAmount = totalAmount,
                selectedCustomer = selectedCustomer,
                appliedCoupon = appliedCoupon,
                redeemedPointsSatang = redeemedPointsSatang,
                isOnline = isOnline,
                canPay = viewModel.canPay,
                canRedeemPoints = viewModel.canRedeemPoints,
                canUseCoupon = viewModel.canUseCoupon,
                onApplyCoupon = { code ->
                    viewModel.applyCouponWithReason(code, totalAmount)
                },
                onRemoveCoupon = { viewModel.removeCoupon() },
                onRedeemPoints = { satang -> viewModel.setRedeemedPoints(satang) },
                onCompletePayment = { appliedPayments, taxCustomer ->
                    viewModel.completePayment(appliedPayments, taxCustomer) {
                        navController.navigate(Screen.ReceiptPreview.route)
                    }
                }
            )
        }

        // ── 6. Receipt Preview & Thermal Print Screen ──
        composable(Screen.ReceiptPreview.route) {
            val receipt = completedReceipt
            if (receipt != null) {
                ReceiptPreviewScreen(
                    receipt = receipt,
                    onPrintAbbreviated = {
                        viewModel.printReceipt(receipt, isTaxInvoice = false)
                    },
                    onPrintTaxInvoice = {
                        viewModel.printReceipt(receipt, isTaxInvoice = true)
                    },
                    onNewOrder = {
                        navController.navigate(Screen.TableSelect.route) {
                            popUpTo(Screen.TableSelect.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}