package sun.clientpos.printer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

enum class PrinterConnectionMode {
    TCP_NETWORK, // Network Thermal Printer (e.g. 192.168.1.200:9100)
    USB,         // USB OTG Thermal Printer
    BLUETOOTH,   // Bluetooth SPP
    MOCK_LOG     // Log/Dev Mode for testing without physical printer
}

/**
 * Manages receipt printer communication.
 */
class PrinterService(
    var printerIp: String = "192.168.1.200",
    var printerPort: Int = 9100,
    var connectionMode: PrinterConnectionMode = PrinterConnectionMode.MOCK_LOG
) {
    companion object {
        private const val TAG = "PrinterService"
    }

    /**
     * Send raw ESC/POS bytes to the printer.
     */
    suspend fun print(rawBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        return@withContext when (connectionMode) {
            PrinterConnectionMode.TCP_NETWORK -> {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(printerIp, printerPort), 3000)
                    val outputStream: OutputStream = socket.getOutputStream()
                    outputStream.write(rawBytes)
                    outputStream.flush()
                    outputStream.close()
                    socket.close()
                    Log.d(TAG, "Printed ${rawBytes.size} bytes over TCP to $printerIp:$printerPort")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to print over TCP to $printerIp:$printerPort: ${e.message}", e)
                    false
                }
            }
            PrinterConnectionMode.MOCK_LOG, PrinterConnectionMode.USB, PrinterConnectionMode.BLUETOOTH -> {
                Log.d(TAG, "MOCK PRINT: ${rawBytes.size} bytes formatted for 80mm printer")
                true
            }
        }
    }
}
