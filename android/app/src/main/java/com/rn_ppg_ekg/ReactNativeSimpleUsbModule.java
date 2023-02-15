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

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class ReactNativeSimpleUsbModule extends ReactContextBaseJavaModule {
    private static final String TAG = "ReactNative";
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static final int READ_INTERVAL = 50;

    private ReactApplicationContext reactContext;

    private Object locker = new Object();
    private UsbManager manager;
    private UsbDevice device;
    private UsbEndpoint endpointIn;
    private UsbEndpoint endpointOut;
    private UsbRequest request;
    private UsbDeviceConnection connection;
    private Promise connectionPromise;



    //My attempt
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private byte[] bytes;
    private static int TIMEOUT = 1;
    private boolean forceClaim = true;
    private ByteBuffer buffer;
    private UsbEndpoint endpoint;

    final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

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
                this.device = deviceList.get(device.getDeviceName());
            }
        } catch (Exception e) {
            promise.reject("Create Event Error", e);
            throw new RuntimeException(e);
        }
    }

    @ReactMethod
    public void ConnectUsbDevices(int vendorId, int productId, Promise promise) {
        PendingIntent permissionIntent = PendingIntent.getBroadcast(this.reactContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        this.reactContext.registerReceiver(usbReceiver, filter);

        manager.requestPermission(device, permissionIntent);
        Log.d(TAG, "The Permission success");
        UsbInterface intf = device.getInterface(0);
        UsbEndpoint endpoint = intf.getEndpoint(0);

        connection = manager.openDevice(device);
        Log.d(TAG, "The openDevice has success");

        byte[] dataToSend = "o".getBytes();
        bytes = new byte[dataToSend.length];

        connection.claimInterface(intf, forceClaim);
        Log.d(TAG, "The claimInterface has success");
        connection.bulkTransfer(endpoint, bytes, bytes.length, TIMEOUT);
        Log.d(TAG, "The connection has initialize successfully");

        request = new UsbRequest();

        int endpointAddress = UsbConstants.USB_DIR_IN | 2;
        this.endpoint = device.getInterface(0).getEndpoint(endpointAddress);

        request.initialize(connection, endpoint);
        Log.d(TAG, "The request has initialize successfully");

        this.buffer = ByteBuffer.allocate(bytes.length);

        request.queue(buffer, bytes.length);

        if (request.queue(buffer, dataToSend.length)){
            Log.d(TAG, "The queue has good start");
            boolean resultReceived = false;
            while (!resultReceived) {
                if (connection.requestWait() == request) {
                    Log.d(TAG, "The request has completed successfully");
                    byte[] receivedData = buffer.array();
                    // Process the received data here
                    resultReceived = true;
                } else {
                    Log.d(TAG, "The request has failed");
                    break;
                }
            }
        }

    }
    
}


