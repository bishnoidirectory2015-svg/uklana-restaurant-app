package com.uklanafood.restaurant;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService
        extends FirebaseMessagingService {

    private static final String TAG = "UKF_FCM";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);

        Log.d(TAG, "New FCM token received");

        SessionManager sessionManager =
                new SessionManager(getApplicationContext());

        sessionManager.saveFcmToken(token);

        /*
         * Token WordPress server पर भेजने का code
         * ApiClient में FCM token endpoint बनने के बाद यहां call होगा।
         */
    }

    @Override
    public void onMessageReceived(
            @NonNull RemoteMessage remoteMessage
    ) {
        super.onMessageReceived(remoteMessage);

        Map<String, String> data = remoteMessage.getData();

        String orderId = getFirstNonEmpty(
                data.get("order_id"),
                data.get("orderId"),
                data.get("id")
        );

        String title = getFirstNonEmpty(
                data.get("title"),
                "New Order Received"
        );

        String message = getFirstNonEmpty(
                data.get("body"),
                data.get("message"),
                "A new restaurant order has been received."
        );

        if (remoteMessage.getNotification() != null) {
            if (isEmpty(title) &&
                    remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }

            if (isEmpty(message) &&
                    remoteMessage.getNotification().getBody() != null) {
                message = remoteMessage.getNotification().getBody();
            }
        }

        if (isEmpty(orderId)) {
            orderId = String.valueOf(
                    System.currentTimeMillis()
            );
        }

        NotificationHelper.showOrderNotification(
                getApplicationContext(),
                orderId,
                title,
                message
        );
    }

    private String getFirstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (!isEmpty(value)) {
                return value.trim();
            }
        }

        return "";
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}