package com.rn_ppg_ekg;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.*;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class ReactNativeSimpleUsbModule extends ReactContextBaseJavaModule {
    private static final String TAG = "ReactNative";
    private static final String ACTION_USB_PERMISSION = "me.andyshea.scanner.USB_PERMISSION";
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static final int READ_INTERVAL = 50;

    private ReactApplicationContext reactContext;

    private Object locker = new Object();
    private UsbManager manager;
    private UsbDevice device;
    private UsbEndpoint endpointIn;
    private UsbEndpoint endpointOut;
    private UsbDeviceConnection connection;
    private Promise connectionPromise;

    ReactNativeSimpleUsbModule(ReactApplicationContext reactContext){
        super(reactContext);
        this.reactContext = reactContext;
    }

    @NonNull
    @Override
    public String getName() {
        return "ReactNativeSimpleUsb";
    }

    @ReactMethod
    public void listUsbDevices(Promise promise) {
        connectionPromise = promise;
        manager = (UsbManager)this.reactContext.getSystemService(Context.USB_SERVICE);
        try{
            HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

            while (deviceIterator.hasNext()) {
                UsbDevice device = deviceIterator.next();

                Log.d(TAG, "ral: " + device.toString());
                Log.d(TAG, "Device: " + device.getDeviceName() + " " + device.getDeviceId() + " " + device.getVendorId() + " " + device.getProductId());

                final Map<String, String> deviceInfo = new HashMap<>();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    deviceInfo.put("deviceName", device.getManufacturerName());
                }else {
                    deviceInfo.put("deviceName", device.getDeviceName());
                }
                deviceInfo.put("deviceId", String.valueOf(device.getDeviceId()));
                deviceInfo.put("vendorId", String.valueOf(device.getVendorId()));
                deviceInfo.put("productId", String.valueOf(device.getProductId()));

                WritableNativeMap writableMap = new WritableNativeMap();
                
                for (Map.Entry<String, String> entry : deviceInfo.entrySet()) {
                    writableMap.putString(entry.getKey(), entry.getValue());
                }
                
                promise.resolve(writableMap);
            }
        } catch (Exception e) {
            promise.reject("Create Event Error", e);
            throw new RuntimeException(e);
        }
    }

    @ReactMethod
    public void ConnectUsbDevices(int vendorId, int productId, Promise promise) {
        connectionPromise = promise;
        manager = (UsbManager)this.reactContext.getSystemService(Context.USB_SERVICE);
        try{
            HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

            while (deviceIterator.hasNext()) {
                UsbDevice device = deviceIterator.next();
                if (device.getVendorId() == vendorId && device.getProductId() == productId) {
                    this.device = device;
                }
            }
            if (device == null) {
                rejectConnectionPromise(
                        "E100",
                        String.format(Locale.US, "No USB device found matching vendor ID %d and product ID %d", vendorId, productId)
                );
            }else{
                Log.d(TAG, "Checking USB permission...");
                PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this.reactContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
                IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
                this.reactContext.registerReceiver(usbReceiver, filter);
                manager.requestPermission(device, usbPermissionIntent);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void rejectConnectionPromise(String code, String message) {
        Log.e(TAG, message);
        connectionPromise.reject(code, message);
        connectionPromise = null;
    }
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) setDevice(device);
                        else rejectConnectionPromise("E101", "Device is null");
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                        rejectConnectionPromise("E102", "Permission denied for device");
                    }
                }
            }
        }
    };
    private Runnable reader = new Runnable() {
        public void run() {
            int readBufferMaxLength = endpointIn.getMaxPacketSize();
            while (true) {
                synchronized (locker) {
                    byte[] bytes = new byte[readBufferMaxLength];
                    int response = connection.bulkTransfer(endpointIn, bytes, readBufferMaxLength, 50);
                    if (response >= 0) {
                        byte[] truncatedBytes = new byte[response];
                        int i = 0;
                        for (byte b : bytes) {
                            truncatedBytes[i] = b;
                            i++;
                        }
                        String hex = bytesToHexString(truncatedBytes);
                        Log.i(TAG, "USB data read: " + hex);
                        reactContext
                                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                .emit("usbData", hex);
                    }
                }
                sleep(READ_INTERVAL);
            }
        }
    };
    private void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        }
        catch (InterruptedException ie) {
            ie.printStackTrace();
        }
    }
    private static String bytesToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    private void setDevice(UsbDevice device) {
        Log.d(TAG, "setDevice " + device);
//        if (device.getInterfaceCount() != 1) {
//            rejectConnectionPromise("E103", "Could not find device interface");
//            return;
//        }
        UsbInterface usbInterface = device.getInterface(0);

        // device should have two endpoints
//        if (usbInterface.getEndpointCount() != 2) {
//            rejectConnectionPromise("E104", "Could not find device endpoints");
//            return;
//        }

        // first endpoint should be of type interrupt with direction of in
        UsbEndpoint endpointIn = usbInterface.getEndpoint(0);
        if (endpointIn.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
            rejectConnectionPromise("E105", "First endpoint is not interrupt type");
            return;
        }
        if (endpointIn.getDirection() != UsbConstants.USB_DIR_IN) {
            rejectConnectionPromise("E106", "First endpoint direction is not in");
            return;
        }

        // second endpoint should be of type interrupt with direction of out
        UsbEndpoint endpointOut = usbInterface.getEndpoint(1);
        if (endpointOut.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
            rejectConnectionPromise("E107", "Second endpoint is not interrupt type");
            return;
        }
        if (endpointOut.getDirection() != UsbConstants.USB_DIR_OUT) {
            rejectConnectionPromise("E108", "Second endpoint direction is not out");
            return;
        }

        this.device = device;
        this.endpointIn = endpointIn;
        this.endpointOut = endpointOut;

        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection != null && connection.claimInterface(usbInterface, true)) {
            Log.d(TAG, "USB device opened successfully");
            this.connection = connection;
            Thread thread = new Thread(reader);
            thread.start();
            connectionPromise.resolve(null);
            connectionPromise = null;
        } else {
            rejectConnectionPromise("E109", "Failed opening USB device");
            this.connection = null;
        }
    }
}
