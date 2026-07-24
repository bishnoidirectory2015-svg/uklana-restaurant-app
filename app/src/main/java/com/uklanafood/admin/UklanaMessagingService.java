package com.uklanafood.admin;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class UklanaMessagingService extends FirebaseMessagingService {
    @Override public void onNewToken(String token) {
        super.onNewToken(token);
        if (SessionManager.loggedIn(this)) TokenRegistrar.register(this, token);
    }

    @Override public void onMessageReceived(RemoteMessage remoteMessage) {
        if (!SessionManager.loggedIn(this)) return;
        Map<String, String> data = remoteMessage.getData();
        String title = value(data, "title", "NEW ORDER");
        String body = value(data, "body", "Tap to open the new order");
        String openUrl = value(data, "open_url", AppConfig.ORDERS_URL);
        showOrderNotification(title, body, openUrl, data);
    }

    private String value(Map<String,String> data, String key, String fallback) {
        String value = data.get(key);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private void showOrderNotification(String title, String body, String openUrl, Map<String,String> data) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Uri sound = RingtoneHelper.getSelectedUri(this);
        String channelId = RingtoneHelper.channelId(this);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(channelId, "New Order Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Urgent Uklana Food order notifications");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0,1000,250,1000,250,1000});
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
            AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            channel.setSound(sound, attrs);
            manager.createNotificationChannel(channel);
        }

        int notificationId = (int) (System.currentTimeMillis() & 0x0fffffff);
        Intent alertIntent = new Intent(this, OrderAlertActivity.class);
        for (Map.Entry<String,String> entry : data.entrySet()) alertIntent.putExtra(entry.getKey(), entry.getValue());
        alertIntent.putExtra("title", title);
        alertIntent.putExtra("body", body);
        alertIntent.putExtra("open_url", openUrl);
        alertIntent.putExtra("notification_id", notificationId);
        alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent fullScreenIntent = PendingIntent.getActivity(this, notificationId, alertIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true)
                .setSound(sound)
                .setVibrate(new long[]{0,1000,250,1000,250,1000})
                .setContentIntent(fullScreenIntent)
                .setFullScreenIntent(fullScreenIntent, true);
        manager.notify(notificationId, builder.build());
    }
}
