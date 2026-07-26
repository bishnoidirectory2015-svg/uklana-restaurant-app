package com.uklanafood.restaurant;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class NotificationHelper {

    private static final String CHANNEL_ID_PREFIX = "uklana_restaurant_orders_v4_";
    private static final String CHANNEL_NAME = "Urgent New Orders";

    private NotificationHelper() { }

    public static void showOrderNotification(Context context, String orderId, String title, String message) {
        if (context == null) return;

        String safeOrderId = orderId == null || orderId.trim().isEmpty()
                ? String.valueOf(System.currentTimeMillis()) : orderId.trim();

        if (isDuplicateNotification(context, safeOrderId)) return;
        saveLastNotification(context, safeOrderId);

        wakeScreen(context);

        String channelId = createNotificationChannel(context);

        String finalTitle = title == null || title.trim().isEmpty()
                ? "New Order Received" : title.trim();
        String finalMessage = message == null || message.trim().isEmpty()
                ? "Open the app to view order details." : message.trim();

        Intent alertIntent = new Intent(context, OrderAlertActivity.class);
        alertIntent.putExtra("order_id", safeOrderId);
        alertIntent.putExtra("title", finalTitle);
        alertIntent.putExtra("message", finalMessage);
        alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int requestCode = Math.abs(safeOrderId.hashCode());
        PendingIntent fullScreenIntent = PendingIntent.getActivity(
                context,
                requestCode,
                alertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra("open_order_id", safeOrderId);
        openIntent.putExtra("open_pending_orders", true);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                requestCode + 10000,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(finalTitle)
                .setContentText(finalMessage)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(finalMessage))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setFullScreenIntent(fullScreenIntent, true)
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                .setVibrate(new long[]{0, 700, 300, 700, 300, 1200})
                .setColor(Color.rgb(255, 122, 0));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(RingtoneHelper.getRingtone(context));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_INSISTENT;
        NotificationManagerCompat.from(context).notify(requestCode, notification);
    }

    private static void wakeScreen(Context context) {
        try {
            PowerManager powerManager =
                    (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) return;

            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "UklanaFood:NewOrderWakeLock"
            );
            wakeLock.acquire(10_000L);
        } catch (Exception ignored) { }
    }

    public static void showTestNotification(Context context) {
        showOrderNotification(context, "TEST-" + System.currentTimeMillis(),
                "Test New Order", "Screen wake, ringtone and vibration test.");
    }

    public static String getCurrentChannelId(Context context) {
        Uri ringtoneUri = RingtoneHelper.getRingtone(context);
        String value = ringtoneUri == null ? "default" : ringtoneUri.toString();
        return CHANNEL_ID_PREFIX + Integer.toHexString(value.hashCode());
    }

    public static String createNotificationChannel(Context context) {
        String channelId = getCurrentChannelId(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        channelId,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Full-screen alerts for urgent new restaurant orders");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 700, 300, 700, 300, 1200});
                channel.enableLights(true);
                channel.setLightColor(Color.rgb(255, 122, 0));
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                channel.setBypassDnd(true);

                Uri ringtoneUri = RingtoneHelper.getRingtone(context);
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                channel.setSound(ringtoneUri, audioAttributes);
                manager.createNotificationChannel(channel);
            }
        }
        return channelId;
    }

    public static void recreateNotificationChannel(Context context) {
        // Android notification-channel sound is immutable after creation.
        // A changed ringtone therefore gets a fresh channel ID derived from its URI.
        createNotificationChannel(context);
    }

    private static boolean isDuplicateNotification(Context context, String orderId) {
        return context.getSharedPreferences("ukf_notification_history", Context.MODE_PRIVATE)
                .getBoolean("order_" + orderId, false);
    }

    private static void saveLastNotification(Context context, String orderId) {
        context.getSharedPreferences("ukf_notification_history", Context.MODE_PRIVATE)
                .edit().putBoolean("order_" + orderId, true).apply();
    }
}
