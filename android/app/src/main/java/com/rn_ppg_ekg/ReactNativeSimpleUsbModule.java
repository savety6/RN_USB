package com.rn_ppg_ekg;
// imports
import static android.app.Activity.RESULT_OK;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;

import com.rn_ppg_ekg.connectivity.DpadConnector;

import java.util.Map;
import java.util.HashMap;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;




public class ReactNativeSimpleUsbModule extends ReactContextBaseJavaModule {
    //private variables
    private UsbManager usbManager;
    private static final String TAG = "HC";

    private DpadConnector mDpadConnector;
    private ReactApplicationContext mReactContext;

    //constructor
    private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {

        private static final String ACTION_USB_PERMISSION = "com.rn_ppg_ekg.USB_PERMISSION";
        private static final int VENDOR_ID = 0x303a;
        private static final int PRODUCT_ID = 0x4001;
        @Override
        public void onNewIntent(Intent intent) {
            super.onNewIntent(intent);

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                connectDpad(device);
                updateDpad();
            }
        }
    };
    public ReactNativeSimpleUsbModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.mReactContext = reactContext;
        reactContext.addActivityEventListener(mActivityEventListener);
        usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
        registerReceivers();
        connectDpad(null);
    }

    @NonNull
    @Override
    public String getName() {
        return "ReactNativeSimpleUsb";
    }

    //list devices method implementation
    @ReactMethod
    public void listDevices(Promise promise) {
        // get the list of devices from the usb manager and return it to react native as a promise with a readable map
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        WritableMap map = Arguments.createMap();
        for (Map.Entry<String, UsbDevice> entry : deviceList.entrySet()) {
            UsbDevice device = entry.getValue();
            WritableMap deviceMap = Arguments.createMap();
            deviceMap.putString("deviceName", device.getDeviceName());
            deviceMap.putString("deviceClass", String.valueOf(device.getDeviceClass()));
            deviceMap.putString("deviceSubclass", String.valueOf(device.getDeviceSubclass()));
            deviceMap.putString("vendorId", String.valueOf(device.getVendorId()));
            deviceMap.putString("productId", String.valueOf(device.getProductId()));
            map.putMap(device.getDeviceName(), deviceMap);
        }
        promise.resolve(map);
    }

    @ReactMethod
    public void connect(){
        
    }
    private void connectDpad(UsbDevice device) {
        if (mDpadConnector != null) {
            // Device is already connected
            Log.d(TAG, "device is already connected");
            return;
        }

        UsbManager manager = (UsbManager) this.mReactContext.getSystemService(Context.USB_SERVICE);

        if (device == null) {
            for (UsbDevice d: manager.getDeviceList().values()) {
                if (manager.hasPermission(d)) {
                    device = d;
                    break;
                }
            }
        }

        if (device != null) {
            mDpadConnector = DpadConnector.create(this.mReactContext, manager, device, new DpadConnector.OnCommandReceivedListener() {
                @Override
                public void onIdentReceived(String ident) {
                    setInfo(ident);
                }

                @Override
                public void onKeyReceived(int action, int keyCode) {
//                  if (mDpadViewListener != null) {
//                      mDpadViewListener.onKeyPress(action, keyCode);
//                  }
                    //TODO: send info to react
                }
            });

            if (mDpadConnector != null) {
                mDpadConnector.requestIdent();
            }
        }
    }



    private void registerReceivers() {
        // Register receiver to notify when USB device is detached.
        this.mReactContext.registerReceiver(mUsbDeviceDetachedReceiver, new IntentFilter(UsbManager
                .ACTION_USB_DEVICE_DETACHED));
    }

    private void closeDpad(UsbDevice device) {
        boolean closeDevice = false;

        if (mDpadConnector != null) {
            if (device != null) {
                closeDevice = mDpadConnector.isDeviceAttached(device);
            } else  {
                closeDevice = true;
            }
        }

        if (closeDevice) {
            mDpadConnector.close();
            mDpadConnector = null;
        }
    }
    @ReactMethod
    private void updateDpad() {
        boolean available = mDpadConnector != null;
        try{
            if (available) {
                Log.d(TAG, "updateDpad: " + available);
            } else {
                Log.d(TAG, "updateDpad: " + available);
                setInfo("");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setInfo(String s) {
        Log.d(TAG, "setInfo() - s=" + s);
//        TextView infoView = findViewById(R.id.info);
//        infoView.setText(s);
    }

    private class UsbDeviceDetachedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                UsbDevice device  = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                closeDpad(device);
                updateDpad();
            }
        }
    }

    private final UsbDeviceDetachedReceiver mUsbDeviceDetachedReceiver = new UsbDeviceDetachedReceiver();
}