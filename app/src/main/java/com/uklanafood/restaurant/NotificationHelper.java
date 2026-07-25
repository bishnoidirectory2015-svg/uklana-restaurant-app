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

    private static final String CHANNEL_PREFIX = "new_order_channel_";
    private static final String CHANNEL_NAME = "New Orders";

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
                        .setSmallIcon(R.mipmap.ic_launcher)
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
                        .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
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
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Test New Order")
                        .setContentText(
                                "Your selected order ringtone is working."
                        )
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
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

        String ringtoneValue =
                ringtoneUri == null
                        ? "default"
                        : ringtoneUri.toString();

        String channelId =
                CHANNEL_PREFIX +
                        Math.abs(ringtoneValue.hashCode());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    context.getSystemService(
                            NotificationManager.class
                    );

            NotificationChannel channel =
                    new NotificationChannel(
                            channelId,
                            CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "Notifications for new restaurant orders"
            );

            channel.enableVibration(true);
            channel.setVibrationPattern(
                    new long[]{
                            0,
                            600,
                            250,
                            600,
                            250,
                            900
                    }
            );

            channel.enableLights(true);
            channel.setLightColor(Color.rgb(255, 122, 0));

            AudioAttributes audioAttributes =
                    new AudioAttributes.Builder()
                            .setUsage(
                                    AudioAttributes.USAGE_NOTIFICATION_EVENT
                            )
                            .setContentType(
                                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build();

            channel.setSound(ringtoneUri, audioAttributes);

            manager.createNotificationChannel(channel);
        }

        return channelId;
    }

    public static void recreateNotificationChannel(Context context) {
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