package com.uklanafood.admin;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {
    private LinearLayout ordersContainer;
    private ProgressBar progress;
    private TextView statusText;
    private String focusOrderId = "";

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SessionManager.loggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        ordersContainer = findViewById(R.id.ordersContainer);
        progress = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        findViewById(R.id.refreshButton).setOnClickListener(v -> loadOrders());
        findViewById(R.id.pendingOrdersButton).setOnClickListener(v -> loadOrders());
        findViewById(R.id.ringtoneButton).setOnClickListener(v -> startActivity(new Intent(this, RingtoneSettingsActivity.class)));
        ((TextView)findViewById(R.id.deliveryBoyName)).setText("👤 " + SessionManager.name(this) + "  •  " + SessionManager.phone(this));
        findViewById(R.id.logoutButton).setOnClickListener(v -> {
            SessionManager.clear(this);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        focusOrderId = getIntent().getStringExtra("focus_order_id");
        if (focusOrderId == null) focusOrderId = "";
        requestNotificationPermission();
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> TokenRegistrar.register(this, token));
        loadOrders();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String id = intent.getStringExtra("focus_order_id");
        if (id != null) focusOrderId = id;
        loadOrders();
    }

    @Override protected void onResume() {
        super.onResume();
        loadOrders();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void loadOrders() {
        progress.setVisibility(View.VISIBLE);
        statusText.setText("Pending orders load ho rahe hain…");
        new Thread(() -> {
            try {
                HttpURLConnection c = connection(AppConfig.PENDING_URL, "GET");
                int code = c.getResponseCode();
                String text = read(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
                JSONObject root = new JSONObject(text);
                if (code == 401) { runOnUiThread(this::forceLogin); return; }
                if (!root.optBoolean("ok")) throw new Exception(root.optString("message", "Orders load nahi hue"));
                JSONArray orders = root.optJSONArray("orders");
                runOnUiThread(() -> renderOrders(orders));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    statusText.setText("Connection error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void renderOrders(JSONArray orders) {
        progress.setVisibility(View.GONE);
        ordersContainer.removeAllViews();
        int count = orders == null ? 0 : orders.length();
        statusText.setText("Pending Orders: " + count);
        if (count == 0) {
            TextView empty = text("Abhi koi pending order nahi hai.", 18, true);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(20,80,20,80);
            ordersContainer.addView(empty);
            return;
        }
        for (int i=0; i<count; i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order != null) ordersContainer.addView(orderCard(mergeAcceptedData(order)));
        }
    }

    private JSONObject mergeAcceptedData(JSONObject order) {
        try {
            String id = String.valueOf(order.optInt("id"));
            SharedPreferences prefs = getSharedPreferences("accepted_orders", MODE_PRIVATE);
            String saved = prefs.getString(id, "");
            if (!saved.isEmpty()) {
                JSONObject local = new JSONObject(saved);
                Iterator<String> keys = local.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = local.optString(key, "");
                    if (!value.isEmpty()) order.put(key, value);
                }
                order.put("accepted", true);
            }
        } catch (Exception ignored) { }
        return order;
    }

    private View orderCard(JSONObject o) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(22,20,22,20);
        card.setBackgroundResource(R.drawable.order_card);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(14,12,14,12);
        card.setLayoutParams(cp);

        String orderNo = first(o, "order_number", "number");
        TextView heading = text("Order #" + orderNo, 22, true);
        card.addView(heading);
        if (String.valueOf(o.optInt("id")).equals(focusOrderId)) {
            card.addView(text("✅ ACCEPTED — Ring aur vibration band ho chuki hai", 15, true));
        }

        String restaurant = first(o, "restaurant", "restaurant_name");
        if (!restaurant.isEmpty()) card.addView(text("🏪 Restaurant: " + restaurant, 17, true));
        String date = first(o, "date_created", "order_date");
        if (!date.isEmpty()) card.addView(text("Date: " + date, 14, false));

        String customerName = first(o, "customer_name", "customer");
        String phone = first(o, "customer_phone", "phone");
        String address = first(o, "customer_address", "address");
        String nearby = o.optString("nearby", "");
        String distance = o.optString("distance", "");
        String payment = o.optString("payment", "");
        StringBuilder customer = new StringBuilder("👤 CUSTOMER DETAILS\nName: ").append(customerName)
                .append("\nPhone: ").append(phone)
                .append("\nAddress: ").append(address);
        if (!nearby.isEmpty()) customer.append("\nNearby: ").append(nearby);
        if (!distance.isEmpty()) customer.append("\nDistance: ").append(distance).append(" KM");
        customer.append("\nPayment: ").append(payment);
        if (o.optBoolean("is_cod")) customer.append(" — Customer se paise lene hain");
        card.addView(text(customer.toString(), 15, false));

        String itemsText = o.optString("items_text", "");
        if (itemsText.isEmpty()) {
            JSONArray items = o.optJSONArray("items");
            StringBuilder b = new StringBuilder();
            if (items != null) for (int i=0;i<items.length();i++) {
                JSONObject item = items.optJSONObject(i);
                b.append("• ").append(item.optInt("qty")).append(" × ").append(item.optString("name"));
                String price = item.optString("line_total", "");
                if (!price.isEmpty()) b.append(" — ").append(price);
                b.append("\n");
            }
            itemsText = b.toString().trim();
        }
        card.addView(text("🍽️ ORDER SUMMARY\n\n" + itemsText, 15, false));

        String subtotal = o.optString("subtotal", "");
        String delivery = o.optString("delivery_charge", "");
        String total = first(o, "total", "order_total");
        StringBuilder totals = new StringBuilder();
        if (!subtotal.isEmpty()) totals.append("Subtotal: ").append(subtotal).append("\n");
        if (!delivery.isEmpty()) totals.append("Delivery Charge: ").append(delivery).append("\n");
        totals.append("TOTAL: ").append(total);
        card.addView(text(totals.toString(), 17, true));

        String note = o.optString("customer_note", "");
        if (!note.isEmpty()) card.addView(text("📝 Customer Note: " + note, 15, false));
        card.addView(text("Current Status: " + o.optString("status_label"), 15, true));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button call = new Button(this); call.setText("📞 CALL");
        Button map = new Button(this); map.setText("📍 MAP");
        actions.addView(call, new LinearLayout.LayoutParams(0,-2,1));
        actions.addView(map, new LinearLayout.LayoutParams(0,-2,1));
        call.setEnabled(!phone.isEmpty());
        call.setOnClickListener(v -> openExternal("tel:" + phone));
        String mapLink = o.optString("map_link", "");
        if (mapLink.isEmpty()) mapLink = "geo:0,0?q=" + Uri.encode(address);
        final String finalMapLink = mapLink;
        map.setOnClickListener(v -> openExternal(finalMapLink));
        card.addView(actions);

        String restaurantUrl = o.optString("restaurant_whatsapp_url", "");
        Button restaurantBtn = new Button(this);
        String restaurantButtonName = first(o, "restaurant_button_name", "restaurant_name");
        restaurantBtn.setText("🍽️ RESTAURANT WHATSAPP" + (restaurantButtonName.isEmpty() ? "" : " — " + restaurantButtonName));
        restaurantBtn.setEnabled(!restaurantUrl.isEmpty());
        restaurantBtn.setOnClickListener(v -> openExternal(restaurantUrl));
        card.addView(restaurantBtn);

        JSONArray deliveryBoys = o.optJSONArray("delivery_boys");
        if (deliveryBoys != null) {
            for (int i = 0; i < deliveryBoys.length(); i++) {
                JSONObject boy = deliveryBoys.optJSONObject(i);
                if (boy != null) addDeliveryButton(card, boy.optString("name", "Delivery Boy"), boy.optString("whatsapp_url", ""));
            }
        }

        String nextStatus = o.optString("next_status");
        if (!nextStatus.isEmpty()) {
            Button next = new Button(this);
            next.setText("MARK: " + o.optString("next_label").toUpperCase());
            next.setTextSize(17);
            next.setTypeface(null, Typeface.BOLD);
            next.setOnClickListener(v -> updateStatus(o.optInt("id"), nextStatus, next));
            card.addView(next);
        }
        return card;
    }

    private void addDeliveryButton(LinearLayout card, String name, String url) {
        if (url == null || url.isEmpty()) return;
        Button b = new Button(this);
        b.setText("🛵 DELIVERY BOY WHATSAPP — " + name.toUpperCase());
        b.setOnClickListener(v -> openExternal(url));
        card.addView(b);
    }

    private String first(JSONObject o, String a, String b) {
        String x = o.optString(a, "");
        return x.isEmpty() ? o.optString(b, "") : x;
    }

    private void openExternal(String uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri))); }
        catch (Exception e) { Toast.makeText(this, "Link open nahi hua", Toast.LENGTH_SHORT).show(); }
    }

    private void updateStatus(int orderId, String status, Button button) {
        button.setEnabled(false);
        button.setText("UPDATING…");
        new Thread(() -> {
            try {
                String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
                JSONObject body = new JSONObject();
                body.put("order_id", orderId); body.put("status", status); body.put("device_id", deviceId == null ? "" : deviceId);
                HttpURLConnection c = connection(AppConfig.UPDATE_URL, "POST");
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try(OutputStream out=c.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                int code=c.getResponseCode();
                JSONObject root=new JSONObject(read(code>=200&&code<300?c.getInputStream():c.getErrorStream()));
                if(code==401){ runOnUiThread(this::forceLogin); return; }
                if(!root.optBoolean("ok")) throw new Exception(root.optString("message","Status update failed"));
                if ("ukf-delivered".equals(status)) getSharedPreferences("accepted_orders", MODE_PRIVATE).edit().remove(String.valueOf(orderId)).apply();
                runOnUiThread(() -> { Toast.makeText(this,"Status update ho gaya",Toast.LENGTH_SHORT).show(); loadOrders(); });
            } catch(Exception e) {
                runOnUiThread(() -> { button.setEnabled(true); button.setText("TRY AGAIN"); Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

    private void forceLogin() {
        SessionManager.clear(this);
        Toast.makeText(this, "Login expired. Dobara login karein.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private HttpURLConnection connection(String url, String method) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(15000); c.setReadTimeout(15000);
        c.setRequestProperty("Authorization", "Bearer " + SessionManager.token(this));
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        c.setRequestProperty("X-UKF-Device-ID", deviceId == null ? "" : deviceId);
        c.setRequestProperty("Accept","application/json");
        if("POST".equals(method)) c.setDoOutput(true);
        return c;
    }

    private String read(InputStream in) throws Exception {
        if(in==null) return "{}";
        StringBuilder s=new StringBuilder();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) { String line; while((line=r.readLine())!=null)s.append(line); }
        return s.toString();
    }

    private TextView text(String value,int size,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(0xff222222); t.setPadding(4,6,4,6);
        if(bold)t.setTypeface(null, Typeface.BOLD); return t;
    }
}
