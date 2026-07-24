package com.uklanafood.admin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.firebase.messaging.FirebaseMessaging;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> TokenRegistrar.register(context, token));
    }
}
