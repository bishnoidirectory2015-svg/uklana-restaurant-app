package com.uklanafood.admin;

import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

public class OrderAlertActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private int notificationId = -1;
    private boolean alertStopped = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wakeScreen();
        setContentView(R.layout.activity_alert);
        notificationId = getIntent().getIntExtra("notification_id", -1);
        bindOrderData();
        startRepeatingAlert();
    }

    private void wakeScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private String extra(String key, String fallback) {
        String value = getIntent().getStringExtra(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private void bindOrderData() {
        String orderNo = extra("order_number", extra("order_id", ""));
        ((TextView)findViewById(R.id.orderNumberText)).setText(orderNo.isEmpty() ? "" : "Order #" + orderNo);
        ((TextView)findViewById(R.id.restaurantText)).setText("🏪 Restaurant: " + extra("restaurant", extra("restaurant_name", "Restaurant not found")));

        String customer = "👤 CUSTOMER DETAILS\n" +
                "Name: " + extra("customer_name", "—") + "\n" +
                "Phone: " + extra("customer_phone", "—") + "\n" +
                "Address: " + extra("customer_address", "—");
        String nearby = extra("nearby", "");
        if (!nearby.isEmpty()) customer += "\nNearby: " + nearby;
        String distance = extra("distance", "");
        if (!distance.isEmpty()) customer += "\nDistance: " + distance + " KM";
        customer += "\nPayment: " + extra("payment", "—");
        ((TextView)findViewById(R.id.customerText)).setText(customer);

        ((TextView)findViewById(R.id.itemsText)).setText("🍽️ ORDER SUMMARY\n\n" + extra("items_text", extra("body", "Order items not available")));
        String totals = "Subtotal: " + extra("subtotal", "—") +
                "\nDelivery Charge: " + extra("delivery_charge", "—") +
                "\nTOTAL: " + extra("total", "—");
        ((TextView)findViewById(R.id.totalsText)).setText(totals);

        setupActionButton(R.id.callButton, extra("customer_phone", ""), "tel:");
        setupActionButton(R.id.mapButton, extra("map_link", ""), "");
        setupActionButton(R.id.restaurantWhatsappButton, extra("restaurant_whatsapp_url", ""), "");

        Button restaurantButton = findViewById(R.id.restaurantWhatsappButton);
        String restaurantName = extra("restaurant_button_name", extra("restaurant", extra("restaurant_name", "Restaurant")));
        restaurantButton.setText("🍽️ SEND TO " + restaurantName.toUpperCase());

        addDeliveryButton(extra("delivery_suresh_name", "Suresh"), extra("delivery_suresh_url", ""));
        addDeliveryButton(extra("delivery_vishnu_name", "Vishnu"), extra("delivery_vishnu_url", ""));
        addDeliveryButton(extra("delivery_govind_name", "Govind"), extra("delivery_govind_url", ""));

        Button accept = findViewById(R.id.openOrderButton);
        accept.setText("✅ ACCEPT ORDER");
        accept.setOnClickListener(v -> acceptOrder());
        findViewById(R.id.dismissButton).setVisibility(View.GONE);
    }

    private void acceptOrder() {
        saveAcceptedOrder();
        stopRepeatingAlert();
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("focus_order_id", extra("order_id", ""));
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    private void saveAcceptedOrder() {
        String orderId = extra("order_id", extra("order_number", ""));
        if (orderId.isEmpty()) return;
        try {
            JSONObject data = new JSONObject();
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                for (String key : extras.keySet()) {
                    Object value = extras.get(key);
                    if (value != null) data.put(key, String.valueOf(value));
                }
            }
            data.put("accepted_at", System.currentTimeMillis());
            SharedPreferences prefs = getSharedPreferences("accepted_orders", MODE_PRIVATE);
            prefs.edit().putString(orderId, data.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void setupActionButton(int id, String value, String prefix) {
        Button button = findViewById(id);
        if (value.isEmpty()) { button.setVisibility(View.GONE); return; }
        button.setOnClickListener(v -> openExternal(prefix + value));
    }

    private void addDeliveryButton(String name, String url) {
        if (url.isEmpty()) return;
        LinearLayout container = findViewById(R.id.deliveryButtonsContainer);
        Button button = new Button(this);
        button.setText("🛵 SEND TO " + name.toUpperCase());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        button.setLayoutParams(params);
        button.setTextColor(Color.WHITE);
        button.setOnClickListener(v -> openExternal(url));
        container.addView(button);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void openExternal(String uriText) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uriText))); }
        catch (Exception e) { Toast.makeText(this, "App/link open nahi hua", Toast.LENGTH_SHORT).show(); }
    }

    private void startRepeatingAlert() {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mediaPlayer.setDataSource(this, RingtoneHelper.getSelectedUri(this));
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
        } catch (Exception ignored) { }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = new long[]{0,900,250,900,250,900};
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern,0)); else vibrator.vibrate(pattern,0);
        }
    }

    private void stopRepeatingAlert() {
        if (alertStopped) return;
        alertStopped = true;
        try { if (mediaPlayer != null) { if (mediaPlayer.isPlaying()) mediaPlayer.stop(); mediaPlayer.release(); mediaPlayer=null; } } catch(Exception ignored) {}
        if (vibrator != null) vibrator.cancel();
        if (notificationId >= 0) {
            NotificationManager manager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            if(manager!=null) manager.cancel(notificationId);
        }
    }

    @Override public void onBackPressed() {
        Toast.makeText(this, "Ring band karne ke liye ACCEPT ORDER dabaye", Toast.LENGTH_SHORT).show();
    }
    @Override protected void onDestroy() { stopRepeatingAlert(); super.onDestroy(); }
}
