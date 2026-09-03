package sun.clientpos.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors network connectivity state for POS offline/online policies.
 * Emits real-time online status via [isOnline].
 *
 * Rules:
 *   - Online: Cloud access available -> point redemption, coupon validation enabled
 *   - Offline: No cloud access -> point earning only, point redemption/coupon blocked with clear message
 */
class ConnectivityChecker(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    init {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(
            networkRequest,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isOnline.value = true
                }

                override fun onLost(network: Network) {
                    _isOnline.value = checkInitialConnectivity()
                }

                override fun onUnavailable() {
                    _isOnline.value = false
                }
            }
        )
    }

    private fun checkInitialConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
