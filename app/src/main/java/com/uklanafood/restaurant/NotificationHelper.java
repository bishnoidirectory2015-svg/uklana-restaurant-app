package com.uklanafood.restaurant;

import android.Manifest;
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

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class NotificationHelper {

    public static final String CHANNEL_ID = "uklana_restaurant_orders_v2";
    private static final String LEGACY_CHANNEL_ID = "uklana_restaurant_orders_v1";
    private static final String CHANNEL_NAME = "New Restaurant Orders";

    private NotificationHelper() {
    }

    public static void showOrderNotification(
            Context context,
            String orderId,
            String title,
            String message
    ) {
        if (context == null) {
            return;
        }

        String safeOrderId =
                orderId == null || orderId.trim().isEmpty()
                        ? String.valueOf(System.currentTimeMillis())
                        : orderId.trim();

        if (isDuplicateNotification(context, safeOrderId)) {
            return;
        }

        saveLastNotification(context, safeOrderId);

        String channelId = createNotificationChannel(context);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.putExtra("open_order_id", safeOrderId);
        openIntent.putExtra("open_pending_orders", true);

        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        int requestCode = Math.abs(safeOrderId.hashCode());

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        String finalTitle =
                title == null || title.trim().isEmpty()
                        ? "New Order Received"
                        : title.trim();

        String finalMessage =
                message == null || message.trim().isEmpty()
                        ? "Open the app to view order details."
                        : message.trim();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_app)
                        .setContentTitle(finalTitle)
                        .setContentText(finalMessage)
                        .setStyle(
                                new NotificationCompat.BigTextStyle()
                                        .bigText(finalMessage)
                        )
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setFullScreenIntent(pendingIntent, true)
                        .setOnlyAlertOnce(false)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setVibrate(
                                new long[]{
                                        0,
                                        600,
                                        250,
                                        600,
                                        250,
                                        900
                                }
                        )
                        .setColor(Color.rgb(255, 122, 0));

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(RingtoneHelper.getRingtone(context));
        }

        NotificationManagerCompat manager =
                NotificationManagerCompat.from(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        manager.notify(requestCode, builder.build());
    }

    public static void showTestNotification(Context context) {
        String channelId = createNotificationChannel(context);

        Intent intent = new Intent(context, MainActivity.class);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                98765,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_app)
                        .setContentTitle("Test New Order")
                        .setContentText(
                                "Your selected order ringtone is working."
                        )
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setFullScreenIntent(pendingIntent, true)
                        .setOnlyAlertOnce(false)
                        .setDefaults(NotificationCompat.DEFAULT_ALL)
                        .setVibrate(
                                new long[]{0, 600, 250, 600}
                        );

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(RingtoneHelper.getRingtone(context));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManagerCompat.from(context)
                .notify(98765, builder.build());
    }

    public static String createNotificationChannel(Context context) {
        Uri ringtoneUri = RingtoneHelper.getRingtone(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    context.getSystemService(NotificationManager.class);

            if (manager == null) {
                return CHANNEL_ID;
            }

            android.content.SharedPreferences migrationPrefs =
                    context.getSharedPreferences(
                            "ukf_notification_channel_migration",
                            Context.MODE_PRIVATE
                    );

            if (!migrationPrefs.getBoolean("v2_done", false)) {
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID);
                manager.deleteNotificationChannel(CHANNEL_ID);
                migrationPrefs.edit().putBoolean("v2_done", true).apply();
            }

            createHighPriorityChannel(
                    manager,
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    ringtoneUri
            );

            // Keep the old server channel working too. Some existing FCM
            // payloads may still specify uklana_restaurant_orders_v1.
            createHighPriorityChannel(
                    manager,
                    LEGACY_CHANNEL_ID,
                    CHANNEL_NAME + " (Legacy)",
                    ringtoneUri
            );
        }

        return CHANNEL_ID;
    }

    private static void createHighPriorityChannel(
            NotificationManager manager,
            String channelId,
            String channelName,
            Uri ringtoneUri
    ) {
        NotificationChannel channel =
                new NotificationChannel(
                        channelId,
                        channelName,
                        NotificationManager.IMPORTANCE_HIGH
                );

        channel.setDescription("Loud alerts for new restaurant orders");
        channel.enableVibration(true);
        channel.setVibrationPattern(
                new long[]{0, 700, 250, 700, 250, 1000}
        );
        channel.enableLights(true);
        channel.setLightColor(Color.rgb(255, 122, 0));
        channel.setLockscreenVisibility(
                android.app.Notification.VISIBILITY_PUBLIC
        );
        channel.setBypassDnd(false);

        AudioAttributes audioAttributes =
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(
                                AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build();

        channel.setSound(ringtoneUri, audioAttributes);
        manager.createNotificationChannel(channel);
    }

    public static void recreateNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.deleteNotificationChannel(CHANNEL_ID);
                manager.deleteNotificationChannel(LEGACY_CHANNEL_ID);
            }
        }
        createNotificationChannel(context);
    }

    private static boolean isDuplicateNotification(
            Context context,
            String orderId
    ) {
        android.content.SharedPreferences preferences =
                context.getSharedPreferences(
                        "ukf_notification_history",
                        Context.MODE_PRIVATE
                );

        return preferences.getBoolean(
                "order_" + orderId,
                false
        );
    }

    private static void saveLastNotification(
            Context context,
            String orderId
    ) {
        android.content.SharedPreferences preferences =
                context.getSharedPreferences(
                        "ukf_notification_history",
                        Context.MODE_PRIVATE
                );

        preferences.edit()
                .putBoolean("order_" + orderId, true)
                .apply();
    }
}