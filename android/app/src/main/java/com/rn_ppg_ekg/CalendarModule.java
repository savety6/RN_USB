package com.rn_ppg_ekg;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;

import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Map;
import java.util.HashMap;

public class CalendarModule extends ReactContextBaseJavaModule {

    private int eventCount = 0;
    CalendarModule(ReactApplicationContext context) {
        super(context);
    }

    @NonNull
    @Override
    public String getName() {
        return "CalendarModule";
    }

    // this method just runs whatever it is inside the body of the method
    @ReactMethod
    public void createCalendarEvent(){
        Log.d("CalendarModule", "Calendar Module Log");
    }

    //this method can return something to the js side using callback
    @ReactMethod
    public void createCalendarCallback(Callback callback){
        Log.d("CalendarModule", "Calendar Module Log");
        callback.invoke("Data returned from Native calendar module");
    }

    //this method returns data with promise
    @ReactMethod
    public void createCalendarPromise(Promise promise){
        try{
            promise.resolve("Data returned from promise");
            eventCount += 1;
            sendEvent(getReactApplicationContext(), "Event Count", eventCount);
        }catch (Exception err){
            promise.reject("error returned from promise ", err);
        }
    }



    private void sendEvent(ReactContext reactContext, String eventName, int params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
