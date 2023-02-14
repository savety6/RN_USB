//import all libs needed for the usb connection 
package com.rn_ppg_ekg

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import java.nio.ByteBuffer
import java.util.*


class ReactNativeUsbModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {
    private val locker = Any()
    private var manager: UsbManager? = null
    private var device: UsbDevice? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private var connection: UsbDeviceConnection? = null
    private var connectionPromise: Promise? = null
    override fun getName(): String {
        return "ReactNativeUsb"
    }

    @ReactMethod
    fun connect(vendorId: Int, productId: Int, promise: Promise?) {
        connectionPromise = promise
        manager = reactContext.getSystemService(Context.USB_SERVICE) as UsbManager
        try {
            val deviceList = manager!!.deviceList
            val deviceIterator: Iterator<UsbDevice> = deviceList.values.iterator()
            while (deviceIterator.hasNext()) {
                val device = deviceIterator.next()
                if (device.vendorId == vendorId && device.productId == productId) {
                    this.device = device
                }
            }
            if (device == null) {
                rejectConnectionPromise(
                    "E100",
                    String.format(
                        Locale.US,
                        "No USB device found matching vendor ID %d and product ID %d",
                        vendorId,
                        productId
                    )
                )
            } else {
                Log.d(TAG, "Checking USB permission...")
                val usbPermissionIntent = PendingIntent.getBroadcast(
                    reactContext, 0, Intent(
                        ACTION_USB_PERMISSION
                    ), 0
                )
                val filter = IntentFilter(ACTION_USB_PERMISSION)
                reactContext.registerReceiver(usbReceiver, filter)
                manager!!.requestPermission(device, usbPermissionIntent)
            }
        } catch (npe: NullPointerException) {
            rejectConnectionPromise("E110", "No USB devices found")
        }
    }

    @ReactMethod
    fun disconnect(promise: Promise) {
        if (connection == null) {
            val error = "No USB connection established"
            Log.e(TAG, error)
            promise.reject("E400", error)
        } else {
            connection!!.close()
            promise.resolve(null)
        }
    }

    private fun rejectConnectionPromise(code: String, message: String) {
        Log.e(TAG, message)
        connectionPromise!!.reject(code, message)
        connectionPromise = null
    }

    private fun setDevice(device: UsbDevice) {
        Log.d(TAG, "setDevice $device")
        if (device.interfaceCount != 0) {
            rejectConnectionPromise("E103", "Could not find device interface")
            return
        }
        val usbInterface = device.getInterface(0)

        // device should have two endpoints
        if (usbInterface.endpointCount != 2) {
            rejectConnectionPromise("E104", "Could not find device endpoints")
            return
        }

        // first endpoint should be of type interrupt with direction of in
        val endpointIn = usbInterface.getEndpoint(0)
        if (endpointIn.type != UsbConstants.USB_ENDPOINT_XFER_INT) {
            rejectConnectionPromise("E105", "First endpoint is not interrupt type")
            return
        }
        if (endpointIn.direction != UsbConstants.USB_DIR_IN) {
            rejectConnectionPromise("E106", "First endpoint direction is not in")
            return
        }

        // second endpoint should be of type interrupt with direction of out
        val endpointOut = usbInterface.getEndpoint(1)
        if (endpointOut.type != UsbConstants.USB_ENDPOINT_XFER_INT) {
            rejectConnectionPromise("E107", "Second endpoint is not interrupt type")
            return
        }
        if (endpointOut.direction != UsbConstants.USB_DIR_OUT) {
            rejectConnectionPromise("E108", "Second endpoint direction is not out")
            return
        }
        this.device = device
        this.endpointIn = endpointIn
        this.endpointOut = endpointOut
        val connection = manager!!.openDevice(device)
        if (connection != null && connection.claimInterface(usbInterface, true)) {
            Log.d(TAG, "USB device opened successfully")
            this.connection = connection
            val thread = Thread(reader)
            thread.start()
            connectionPromise!!.resolve(null)
            connectionPromise = null
        } else {
            rejectConnectionPromise("E109", "Failed opening USB device")
            this.connection = null
        }
    }

    @ReactMethod
    fun write(data: String, promise: Promise) {
        try {
            if (connection == null) {
                val error = "No USB connection established"
                Log.e(TAG, error)
                promise.reject("E200", error)
            }
            synchronized(locker) {
                val writeBufferMaxLength = endpointOut!!.maxPacketSize
                val writeBuffer =
                    ByteBuffer.allocate(writeBufferMaxLength)
                val writeRequest = UsbRequest()
                writeRequest.initialize(connection, endpointOut)
                writeBuffer.put(hexStringToBytes(data))
                if (!writeRequest.queue(writeBuffer, writeBufferMaxLength)) {
                    val error = "Write request queue failed"
                    Log.e(TAG, error)
                    promise.reject("E201", error)
                }
                Log.d(
                    TAG,
                    "write request sent, waiting for confirmation..."
                )
                if (connection!!.requestWait() === writeRequest) {
                    Log.d(
                        TAG,
                        "write confirmation received!"
                    )
                    promise.resolve(null)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, e.message!!)
            promise.reject(e)
        }
    }

    private val reader = Runnable {
        val readBufferMaxLength = endpointIn!!.maxPacketSize
        while (true) {
            synchronized(locker) {
                val bytes = ByteArray(readBufferMaxLength)
                val response =
                    connection!!.bulkTransfer(endpointIn, bytes, readBufferMaxLength, 50)
                if (response >= 0) {
                    val truncatedBytes = ByteArray(response)
                    var i = 0
                    for (b in bytes) {
                        truncatedBytes[i] = b
                        i++
                    }
                    val hex =
                        bytesToHexString(truncatedBytes)
                    Log.i(
                        TAG,
                        "USB data read: $hex"
                    )
                    reactContext
                        .getJSModule(RCTDeviceEventEmitter::class.java)
                        .emit("usbData", hex)
                }
            }
            sleep(READ_INTERVAL)
        }
    }

    private fun sleep(milliseconds: Int) {
        try {
            Thread.sleep(milliseconds.toLong())
        } catch (ie: InterruptedException) {
            ie.printStackTrace()
        }
    }

    private val usbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val device =
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) setDevice(device) else rejectConnectionPromise(
                            "E101",
                            "Device is null"
                        )
                    } else {
                        Log.d(
                            TAG,
                            "permission denied for device $device"
                        )
                        rejectConnectionPromise("E102", "Permission denied for device")
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "ReactNative"
        private const val ACTION_USB_PERMISSION = "me.andyshea.scanner.USB_PERMISSION"
        private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()
        private const val READ_INTERVAL = 50
        private fun bytesToHexString(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 2)
            for (i in bytes.indices) {
                val v = bytes[i].toInt() and 0xFF
                hexChars[i * 2] = HEX_ARRAY[v ushr 4]
                hexChars[i * 2 + 1] = HEX_ARRAY[v and 0x0F]
            }
            return String(hexChars)
        }

        private fun hexStringToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((hex[i].digitToIntOrNull(16) ?: -1 shl 4)
                + hex[i + 1].digitToIntOrNull(16)!! ?: -1).toByte()
                i += 2
            }
            return data
        }
    }
}