package com.faizal.OtpVerify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

public class OtpBroadcastReceiver extends BroadcastReceiver {

    private ReactApplicationContext mContext;

    private static final String EVENT = "com.faizalshap.otpVerify:otpReceived";

    public OtpBroadcastReceiver(ReactApplicationContext context) {
        mContext = context;
    }

    private void sendEvent(Object data) {
        mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(EVENT, data);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
            Bundle extras = intent.getExtras();
            if (extras == null) return;

            int statusCode = ((Status) extras
                    .get(SmsRetriever.EXTRA_STATUS))
                    .getStatusCode();

            switch (statusCode) {
                case CommonStatusCodes.SUCCESS:
                    // Get SMS message contents
                    String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                    WritableMap payload = Arguments.createMap();
                    payload.putString("message", message);
                    payload.putString("error", message==null ? "UNEXPECTED" : null);
                    sendEvent(EVENT, payload);
                    break;
                case CommonStatusCodes.TIMEOUT:
                    WritableMap timeoutPayload = Arguments.createMap();
                    timeoutPayload.putString("message", null);
                    timeoutPayload.putString("error", "TIMEOUT");
                    sendEvent(EVENT, timeoutPayload);
                    break;
            }
        }
    }
}