package com.faizal.OtpVerify;

import androidx.annotation.NonNull;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.WritableArray;

import com.google.android.gms.auth.api.identity.GetPhoneNumberHintIntentRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.GetPhoneNumberHintResult;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;

@ReactModule(name = OtpVerifyModule.NAME)
public class OtpVerifyModule extends ReactContextBaseJavaModule implements LifecycleEventListener, ActivityEventListener {
    public static final String NAME = "OtpVerify";
    private static final String TAG = OtpVerifyModule.class.getSimpleName();
    private static final int RESOLVE_HINT = 10001;

    private Promise requestHintCallback;
    private final ReactApplicationContext reactContext;
    private BroadcastReceiver mReceiver;
    private boolean isReceiverRegistered = false;

    public OtpVerifyModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        mReceiver = new OtpBroadcastReceiver(reactContext);
        getReactApplicationContext().addLifecycleEventListener(this);
        registerReceiverIfNecessary(mReceiver);
        reactContext.addActivityEventListener(this);
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    @ReactMethod
    public void requestHint(Promise promise) {
        Activity currentActivity = getCurrentActivity();
        requestHintCallback = promise;

        if (currentActivity == null) {
            requestHintCallback.reject("No Activity Found", "Current Activity Null.");
            return;
        }

        try {
            GetPhoneNumberHintIntentRequest request =
                    GetPhoneNumberHintIntentRequest.builder().build();

            Identity.getPhoneNumberHintClient(currentActivity)
                    .getPhoneNumberHintIntent(request)
                    .addOnSuccessListener(result -> {
                        try {
                            currentActivity.startIntentSenderForResult(
                                    result.getIntentSender(),
                                    RESOLVE_HINT,
                                    null, 0, 0, 0
                            );
                        } catch (Exception e) {
                            Log.e(TAG, "Launching the PendingIntent failed", e);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Phone Number Hint failed", e);
                        promise.reject(e);
                    });

        } catch (Exception e) {
            requestHintCallback.reject(e);
        }
    }

    @ReactMethod
    public void getOtp(Promise promise) {
        requestOtp(promise);
    }

    @ReactMethod
    public void getHash(Promise promise) {
        try {
            AppSignatureHelper helper = new AppSignatureHelper(reactContext);
            ArrayList<String> signatures = helper.getAppSignatures();
            WritableArray arr = Arguments.createArray();
            for (String s : signatures) {
                arr.pushString(s);
            }
            promise.resolve(arr);
        } catch (Exception e) {
            promise.reject(e);
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerReceiverIfNecessary(BroadcastReceiver receiver) {
        if (getCurrentActivity() == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Removed SmsRetriever.SEND_PERMISSION as it no longer exists
                getCurrentActivity().registerReceiver(
                        receiver,
                        new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
                        Context.RECEIVER_EXPORTED
                );
            } else {
                getCurrentActivity().registerReceiver(
                        receiver,
                        new IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION)
                );
            }
            Log.d(TAG, "Receiver Registered");
            isReceiverRegistered = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void requestOtp(final Promise promise) {
        SmsRetrieverClient client = SmsRetriever.getClient(reactContext);
        Task<Void> task = client.startSmsRetriever();
        task.addOnCanceledListener(() -> Log.e(TAG, "sms listener cancelled"));
        task.addOnCompleteListener(task1 -> Log.e(TAG, "sms listener complete"));
        task.addOnSuccessListener(aVoid -> {
            Log.e(TAG, "started sms listener");
            promise.resolve(true);
        });
        task.addOnFailureListener(e -> {
            Log.e(TAG, "Could not start sms listener", e);
            promise.reject("E_OTP_ERROR", "Could not start SMS listener.", e);
        });
    }

    private void unregisterReceiver(BroadcastReceiver receiver) {
        if (isReceiverRegistered && getCurrentActivity() != null && receiver != null) {
            try {
                getCurrentActivity().unregisterReceiver(receiver);
                Log.d(TAG, "Receiver UnRegistered");
                isReceiverRegistered = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == RESOLVE_HINT) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    GetPhoneNumberHintResult hintResult =
                            Identity.getPhoneNumberHintFromIntent(data);
                    String phoneNumber = hintResult.getPhoneNumber();
                    requestHintCallback.resolve(phoneNumber);
                } catch (ApiException e) {
                    requestHintCallback.reject(e.getMessage());
                } catch (NullPointerException e) {
                    requestHintCallback.reject(e.getMessage());
                }
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) { }

    @Override
    public void onHostResume() {
        registerReceiverIfNecessary(mReceiver);
    }

    @Override
    public void onHostPause() {
        unregisterReceiver(mReceiver);
    }

    @Override
    public void onHostDestroy() {
        unregisterReceiver(mReceiver);
    }

    @ReactMethod
    public void addListener(String eventName) { }

    @ReactMethod
    public void removeListeners(Integer count) { }
}
