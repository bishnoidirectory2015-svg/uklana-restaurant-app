package com.uklanafood.restaurant;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private LinearLayout ordersContainer;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView totalText;
    private TextView pendingCount;
    private TextView doneCount;
    private Switch openSwitch;
    private Button resetTotalButton;
    private Button paymentPendingTab;
    private Button paymentReceivedTab;
    private LinearLayout receivedHistoryContainer;
    private String currentPaymentTab = "pending";
    private String latestPendingTotal = "₹0";
    private JSONArray latestReceivedHistory = new JSONArray();

    private String currentTab = "pending";

    private final Handler refreshHandler =
            new Handler(Looper.getMainLooper());

    private final Set<String> knownPendingOrders =
            new HashSet<>();

    private boolean firstOrderLoad = true;
    private boolean ignoreOpenSwitch = false;

    private final ActivityResultLauncher<String>
            notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            NotificationHelper.createNotificationChannel(this);
                        } else {
                            Toast.makeText(
                                    this,
                                    "Notification permission बंद है",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
            );

    private final Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            loadAllData(false);

            refreshHandler.postDelayed(
                    this,
                    AppConfig.REFRESH_MS
            );
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SessionManager.loggedIn(this)) {
            openLoginScreen();
            return;
        }

        setContentView(R.layout.activity_main);

        initializeViews();
        setRestaurantInformation();
        setClickListeners();

        requestNotificationPermission();
        requestFullScreenIntentPermission();
        NotificationHelper.createNotificationChannel(this);

        registerFirebaseToken();

        styleTabs();
        handleNotificationIntent(getIntent());

        loadAllData(true);
    }


    private void requestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return;

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.canUseFullScreenIntent()) return;

        new AlertDialog.Builder(this)
                .setTitle("Full-screen order alert चालू करें")
                .setMessage("Screen बंद होने पर नया order full screen दिखाने के लिए अगली screen में Uklana Restaurant को अनुमति दें।")
                .setPositiveButton("Allow", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception exception) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void initializeViews() {
        ordersContainer =
                findViewById(R.id.ordersContainer);

        progressBar =
                findViewById(R.id.progressBar);

        statusText =
                findViewById(R.id.statusText);

        totalText =
                findViewById(R.id.runningTotal);

        pendingCount =
                findViewById(R.id.pendingCount);

        doneCount =
                findViewById(R.id.doneCount);

        openSwitch =
                findViewById(R.id.openSwitch);

        resetTotalButton = findViewById(R.id.resetTotalButton);
        paymentPendingTab = findViewById(R.id.paymentPendingTab);
        paymentReceivedTab = findViewById(R.id.paymentReceivedTab);
        receivedHistoryContainer = findViewById(R.id.receivedHistoryContainer);
    }

    private void setRestaurantInformation() {
        TextView restaurantName =
                findViewById(R.id.restaurantName);

        TextView restaurantPhone =
                findViewById(R.id.restaurantPhone);

        restaurantName.setText(
                "🏪 " + SessionManager.name(this)
        );

        restaurantPhone.setText(
                SessionManager.phone(this)
        );
    }

    private void setClickListeners() {
        Button ringtoneButton =
                findViewById(R.id.ringtoneButton);

        Button logoutButton =
                findViewById(R.id.logoutButton);

        Button refreshButton =
                findViewById(R.id.refreshButton);

        Button pendingTab =
                findViewById(R.id.pendingTab);

        Button doneTab =
                findViewById(R.id.doneTab);



        ringtoneButton.setOnClickListener(view -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    RingtoneSettingsActivity.class
            );

            startActivity(intent);
        });

        logoutButton.setOnClickListener(view -> {
            SessionManager.clear(this);
            openLoginScreen();
        });

        refreshButton.setOnClickListener(view ->
                loadAllData(false)
        );

        pendingTab.setOnClickListener(view -> {
            currentTab = "pending";
            styleTabs();
            loadOrders(false);
        });

        doneTab.setOnClickListener(view -> {
            currentTab = "done";
            styleTabs();
            loadOrders(false);
        });

        resetTotalButton.setOnClickListener(view -> showResetConfirmation());

        paymentPendingTab.setOnClickListener(view -> {
            currentPaymentTab = "pending";
            renderPaymentPanel();
        });

        paymentReceivedTab.setOnClickListener(view -> {
            currentPaymentTab = "received";
            renderPaymentPanel();
        });

        openSwitch.setOnCheckedChangeListener(
                this::changeRestaurantOpenStatus
        );
    }

    private void registerFirebaseToken() {
        FirebaseMessaging.getInstance()
                .getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        return;
                    }

                    String token = task.getResult();

                    if (token != null &&
                            !token.trim().isEmpty()) {

                        SessionManager.saveFcmToken(
                                this,
                                token
                        );
                        uploadFirebaseToken(token);
                    }
                });
    }

    private void uploadFirebaseToken(String token) {
        if (!SessionManager.loggedIn(this) || token == null || token.trim().isEmpty()) return;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("fcm_token", token.trim());
                ApiClient.post(this, AppConfig.REGISTER_FCM_URL, body);
            } catch (Exception ignored) {
                // Next app launch/token refresh retries registration.
            }
        }).start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        boolean openPendingOrders =
                intent.getBooleanExtra(
                        "open_pending_orders",
                        false
                );

        if (openPendingOrders) {
            currentTab = "pending";
            styleTabs();
            loadOrders(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        refreshHandler.removeCallbacks(
                pollingRunnable
        );

        refreshHandler.postDelayed(
                pollingRunnable,
                AppConfig.REFRESH_MS
        );
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(
                pollingRunnable
        );

        super.onPause();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU) {

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {

                notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                );
            }
        }
    }

    private void openLoginScreen() {
        Intent intent = new Intent(
                this,
                LoginActivity.class
        );

        startActivity(intent);
        finish();
    }

    private void forceLoginAgain() {
        SessionManager.clear(this);

        Toast.makeText(
                this,
                "Login expired. Dobara login karein.",
                Toast.LENGTH_LONG
        ).show();

        openLoginScreen();
    }

    private void loadAllData(boolean initialLoad) {
        loadDashboard();
        loadOrders(initialLoad);
    }

    private void loadDashboard() {
        new Thread(() -> {
            try {
                JSONObject response = ApiClient.get(
                        this,
                        AppConfig.DASHBOARD_URL
                );

                if (response.optInt("_http_code") == 401) {
                    runOnUiThread(
                            this::forceLoginAgain
                    );
                    return;
                }

                if (!response.optBoolean("ok")) {
                    throw new Exception(
                            response.optString(
                                    "message",
                                    "Dashboard load nahi hua"
                            )
                    );
                }

                runOnUiThread(() -> {
                    JSONObject restaurant =
                            response.optJSONObject(
                                    "restaurant"
                            );

                    boolean isRestaurantOpen =
                            restaurant != null &&
                                    restaurant.optBoolean(
                                            "is_open"
                                    );

                    ignoreOpenSwitch = true;
                    openSwitch.setChecked(
                            isRestaurantOpen
                    );
                    ignoreOpenSwitch = false;

                    updateOpenStatusLabel(
                            isRestaurantOpen
                    );

                    int pendingOrders =
                            response.optInt(
                                    "pending_count"
                            );

                    int doneOrders =
                            response.optInt(
                                    "done_count"
                            );

                    pendingCount.setText(
                            String.valueOf(
                                    pendingOrders
                            )
                    );

                    doneCount.setText(
                            String.valueOf(
                                    doneOrders
                            )
                    );

                    Button pendingButton =
                            findViewById(
                                    R.id.pendingTab
                            );

                    Button doneButton =
                            findViewById(
                                    R.id.doneTab
                            );

                    pendingButton.setText(
                            "PENDING\n" +
                                    pendingOrders
                    );

                    doneButton.setText(
                            "DONE\n" +
                                    doneOrders
                    );

                    latestPendingTotal = response.optString("running_total", "₹0");
                    JSONArray history = response.optJSONArray("received_history");
                    latestReceivedHistory = history != null ? history : new JSONArray();
                    renderPaymentPanel();
                });

            } catch (Exception exception) {
                runOnUiThread(() ->
                        statusText.setText(
                                "Connection error: " +
                                        exception.getMessage()
                        )
                );
            }
        }).start();
    }

    private void loadOrders(boolean initialLoad) {
        progressBar.setVisibility(View.VISIBLE);

        if ("pending".equals(currentTab)) {
            statusText.setText(
                    "Pending orders load ho rahe hain…"
            );
        } else {
            statusText.setText(
                    "Done orders load ho rahe hain…"
            );
        }

        new Thread(() -> {
            try {
                String requestUrl =
                        AppConfig.ORDERS_URL +
                                "?type=" +
                                currentTab;

                JSONObject response =
                        ApiClient.get(
                                this,
                                requestUrl
                        );

                if (response.optInt("_http_code") == 401) {
                    runOnUiThread(
                            this::forceLoginAgain
                    );
                    return;
                }

                if (!response.optBoolean("ok")) {
                    throw new Exception(
                            response.optString(
                                    "message",
                                    "Orders load nahi hue"
                            )
                    );
                }

                JSONArray orders =
                        response.optJSONArray(
                                "orders"
                        );

                if ("pending".equals(currentTab)) {
                    detectNewOrders(
                            orders,
                            initialLoad
                    );
                }

                runOnUiThread(() ->
                        displayOrders(orders)
                );

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(
                            View.GONE
                    );

                    statusText.setText(
                            "Connection error: " +
                                    exception.getMessage()
                    );
                });
            }
        }).start();
    }

    private synchronized void detectNewOrders(
            JSONArray orders,
            boolean initialLoad
    ) {
        Set<String> currentPendingOrders =
                new HashSet<>();

        if (orders != null) {
            for (int index = 0;
                 index < orders.length();
                 index++) {

                JSONObject order =
                        orders.optJSONObject(index);

                if (order == null) {
                    continue;
                }

                String orderId =
                        String.valueOf(
                                order.optInt("id")
                        );

                currentPendingOrders.add(orderId);

                if (!firstOrderLoad &&
                        !knownPendingOrders.contains(
                                orderId
                        )) {

                    String orderNumber =
                            order.optString(
                                    "number",
                                    orderId
                            );

                    String orderTotal =
                            order.optString(
                                    "total",
                                    ""
                            );

                    NotificationHelper
                            .showOrderNotification(
                                    this,
                                    orderId,
                                    "🔔 New Order #" +
                                            orderNumber,
                                    "Restaurant Total: " +
                                            orderTotal
                            );
                }
            }
        }

        knownPendingOrders.clear();
        knownPendingOrders.addAll(
                currentPendingOrders
        );

        if (firstOrderLoad || initialLoad) {
            firstOrderLoad = false;
        }
    }

    private void displayOrders(JSONArray orders) {
        progressBar.setVisibility(View.GONE);
        ordersContainer.removeAllViews();

        int orderCount =
                orders == null
                        ? 0
                        : orders.length();

        String heading =
                "pending".equals(currentTab)
                        ? "Pending Orders: "
                        : "Done Orders: ";

        statusText.setText(
                heading + orderCount
        );

        if (orderCount == 0) {
            TextView emptyMessage = createTextView(
                    "Abhi koi " +
                            currentTab +
                            " order nahi hai.",
                    18,
                    true
            );

            emptyMessage.setGravity(
                    Gravity.CENTER
            );

            emptyMessage.setPadding(
                    20,
                    80,
                    20,
                    80
            );

            ordersContainer.addView(
                    emptyMessage
            );

            return;
        }

        for (int index = 0;
             index < orderCount;
             index++) {

            JSONObject order =
                    orders.optJSONObject(index);

            if (order != null) {
                ordersContainer.addView(
                        createOrderCard(order)
                );
            }
        }
    }

    private View createOrderCard(JSONObject order) {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                dp(16),
                dp(14),
                dp(16),
                dp(14)
        );

        card.setBackgroundResource(
                R.drawable.order_card
        );

        LinearLayout.LayoutParams parameters =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        parameters.setMargins(
                dp(12),
                dp(8),
                dp(12),
                dp(8)
        );

        card.setLayoutParams(parameters);

        String orderNumber =
                order.optString(
                        "number",
                        ""
                );

        card.addView(
                createTextView(
                        "📦 Order #" +
                                orderNumber,
                        21,
                        true
                )
        );

        card.addView(
                createTextView(
                        "🕒 " +
                                order.optString(
                                        "date_created",
                                        ""
                                ),
                        14,
                        false
                )
        );

        JSONArray items =
                order.optJSONArray("items");

        if (items != null) {
            for (int index = 0;
                 index < items.length();
                 index++) {

                JSONObject item =
                        items.optJSONObject(index);

                if (item == null) {
                    continue;
                }

                String itemName = item.optString("name", "").trim();
                int itemQty = item.optInt("qty", 1);
                String itemLineTotal = item.optString("line_total", "").trim();

                // Multi-options may already contain quantity/price in the item name.
                // Avoid duplicate qty/rate/total lines and show one clean item-total line only.
                String itemText;
                if (itemName.contains("=") || itemName.matches(".*[₹$]\\s*\\d+(?:\\.\\d+)?$")) {
                    itemText = "• " + itemName;
                } else {
                    itemText = "• " + itemName + " × " + itemQty;
                    if (!itemLineTotal.isEmpty()) {
                        itemText += " = " + itemLineTotal;
                    }
                }

                LinearLayout itemRow = new LinearLayout(this);
                itemRow.setOrientation(LinearLayout.HORIZONTAL);
                itemRow.setGravity(Gravity.CENTER_VERTICAL);
                itemRow.setPadding(0, dp(7), 0, dp(7));

                ImageView itemImage = new ImageView(this);
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(72), dp(72));
                imageParams.setMargins(0, 0, dp(12), 0);
                itemImage.setLayoutParams(imageParams);
                itemImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
                itemImage.setContentDescription(itemName);
                itemImage.setBackgroundColor(Color.rgb(245, 245, 245));

                TextView itemTextView = createTextView(itemText, 16, false);
                itemTextView.setLayoutParams(new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                ));

                itemRow.addView(itemImage);
                itemRow.addView(itemTextView);
                card.addView(itemRow);

                loadItemImage(itemImage, item.optString("image", ""));
            }
        }

        String customerNote =
                order.optString(
                        "customer_note",
                        ""
                );

        if (!customerNote.trim().isEmpty()) {
            card.addView(
                    createTextView(
                            "📝 Note: " +
                                    customerNote,
                            15,
                            false
                    )
            );
        }

        TextView orderTotal =
                createTextView(
                        "ITEMS TOTAL: " +
                                order.optString(
                                        "total",
                                        ""
                                ),
                        19,
                        true
                );

        orderTotal.setTextColor(
                Color.rgb(
                        255,
                        122,
                        0
                )
        );

        card.addView(orderTotal);

        if ("pending".equals(currentTab)) {
            Button doneButton =
                    new Button(this);

            doneButton.setText(
                    "✅ DONE / FOOD READY"
            );

            doneButton.setOnClickListener(view ->
                    showDoneConfirmation(
                            order.optInt("id"),
                            doneButton
                    )
            );

            card.addView(doneButton);
        }

        return card;
    }

    private void loadItemImage(ImageView imageView, String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageView.setImageResource(R.drawable.uklana_restaurant_logo);
            imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            return;
        }

        imageView.setTag(imageUrl);
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                    throw new Exception("Image HTTP " + connection.getResponseCode());
                }

                try (InputStream input = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            if (imageUrl.equals(imageView.getTag())) {
                                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                        return;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }

            runOnUiThread(() -> {
                if (imageUrl.equals(imageView.getTag())) {
                    imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    imageView.setImageResource(R.drawable.uklana_restaurant_logo);
                }
            });
        }).start();
    }

    private void showDoneConfirmation(
            int orderId,
            Button doneButton
    ) {
        new AlertDialog.Builder(this)
                .setTitle("Order Done?")
                .setMessage(
                        "Kya food ready ho gaya hai?"
                )
                .setNegativeButton(
                        "Nahi",
                        null
                )
                .setPositiveButton(
                        "Haan",
                        (dialog, which) ->
                                markOrderDone(
                                        orderId,
                                        doneButton
                                )
                )
                .show();
    }

    private void markOrderDone(
            int orderId,
            Button doneButton
    ) {
        doneButton.setEnabled(false);
        doneButton.setText("Updating…");

        new Thread(() -> {
            try {
                JSONObject requestBody =
                        new JSONObject();

                requestBody.put(
                        "order_id",
                        orderId
                );

                requestBody.put(
                        "device_id",
                        ApiClient.deviceId(this)
                );

                JSONObject response =
                        ApiClient.post(
                                this,
                                AppConfig.DONE_URL,
                                requestBody
                        );

                if (!response.optBoolean("ok")) {
                    throw new Exception(
                            response.optString(
                                    "message",
                                    "Update nahi hua"
                            )
                    );
                }

                runOnUiThread(() -> {
                    Toast.makeText(
                            this,
                            "Order Done ho gaya",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadAllData(false);
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    doneButton.setEnabled(true);
                    doneButton.setText(
                            "✅ DONE / FOOD READY"
                    );

                    Toast.makeText(
                            this,
                            exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        }).start();
    }

    private void changeRestaurantOpenStatus(
            CompoundButton button,
            boolean isChecked
    ) {
        if (ignoreOpenSwitch) {
            return;
        }

        openSwitch.setEnabled(false);

        new Thread(() -> {
            try {
                JSONObject requestBody =
                        new JSONObject();

                requestBody.put(
                        "is_open",
                        isChecked
                );

                requestBody.put(
                        "device_id",
                        ApiClient.deviceId(this)
                );

                JSONObject response =
                        ApiClient.post(
                                this,
                                AppConfig.SET_OPEN_URL,
                                requestBody
                        );

                if (!response.optBoolean("ok")) {
                    throw new Exception(
                            response.optString(
                                    "message",
                                    "Status change nahi hua"
                            )
                    );
                }

                boolean serverOpenStatus =
                        response.optBoolean(
                                "is_open"
                        );

                runOnUiThread(() -> {
                    ignoreOpenSwitch = true;
                    openSwitch.setChecked(
                            serverOpenStatus
                    );
                    ignoreOpenSwitch = false;

                    updateOpenStatusLabel(
                            serverOpenStatus
                    );

                    openSwitch.setEnabled(true);

                    Toast.makeText(
                            this,
                            response.optString(
                                    "message",
                                    "Status updated"
                            ),
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } catch (Exception exception) {
                runOnUiThread(() -> {
                    ignoreOpenSwitch = true;

                    openSwitch.setChecked(
                            !isChecked
                    );

                    ignoreOpenSwitch = false;
                    openSwitch.setEnabled(true);

                    Toast.makeText(
                            this,
                            exception.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
            }
        }).start();
    }

    private void updateOpenStatusLabel(
            boolean isOpen
    ) {
        TextView openStatusText =
                findViewById(
                        R.id.openStatusText
                );

        if (isOpen) {
            openStatusText.setText(
                    "OPEN – Orders aa sakte hain"
            );

            openStatusText.setTextColor(
                    Color.rgb(
                            18,
                            148,
                            71
                    )
            );
        } else {
            openStatusText.setText(
                    "CLOSED – Naye orders band"
            );

            openStatusText.setTextColor(
                    Color.RED
            );
        }
    }

    private void showResetConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Payment Received?")
                .setMessage("Pending payment Received में भेजना है? Amount ₹0 होगा और date-time के साथ history में save रहेगा।")
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "RECEIVED",
                        (dialog, which) -> resetRunningTotal()
                )
                .show();
    }

    private void resetRunningTotal() {
        new Thread(() -> {
            try {
                JSONObject requestBody =
                        new JSONObject();

                requestBody.put(
                        "device_id",
                        ApiClient.deviceId(this)
                );

                JSONObject response =
                        ApiClient.post(
                                this,
                                AppConfig.RESET_URL,
                                requestBody
                        );

                if (!response.optBoolean("ok")) {
                    throw new Exception(
                            response.optString(
                                    "message",
                                    "Reset nahi hua"
                            )
                    );
                }

                runOnUiThread(() -> {
                    latestPendingTotal = response.optString("running_total", "₹0");
                    JSONArray history = response.optJSONArray("received_history");
                    latestReceivedHistory = history != null ? history : new JSONArray();
                    currentPaymentTab = "received";
                    renderPaymentPanel();
                    Toast.makeText(this, response.optString("message", "Payment Received में save हो गया"), Toast.LENGTH_SHORT).show();
                });

            } catch (Exception exception) {
                runOnUiThread(() ->
                        Toast.makeText(
                                this,
                                exception.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
            }
        }).start();
    }

    private void renderPaymentPanel() {
        boolean pending = "pending".equals(currentPaymentTab);
        paymentPendingTab.setAlpha(pending ? 1.0f : 0.55f);
        paymentReceivedTab.setAlpha(pending ? 0.55f : 1.0f);
        totalText.setVisibility(pending ? View.VISIBLE : View.GONE);
        resetTotalButton.setVisibility(pending ? View.VISIBLE : View.GONE);
        receivedHistoryContainer.setVisibility(pending ? View.GONE : View.VISIBLE);

        if (pending) {
            totalText.setText(latestPendingTotal);
            return;
        }

        receivedHistoryContainer.removeAllViews();
        if (latestReceivedHistory == null || latestReceivedHistory.length() == 0) {
            TextView empty = createTextView("Abhi koi received payment history nahi hai.", 15, false);
            empty.setTextColor(Color.WHITE);
            receivedHistoryContainer.addView(empty);
            return;
        }

        for (int i = 0; i < latestReceivedHistory.length() && i < 10; i++) {
            JSONObject entry = latestReceivedHistory.optJSONObject(i);
            if (entry == null) continue;
            TextView row = createTextView(
                    (i + 1) + ". " + entry.optString("amount", "₹0") + "  •  " + entry.optString("date_time", ""),
                    15,
                    i == 0
            );
            row.setTextColor(Color.WHITE);
            receivedHistoryContainer.addView(row);
        }
    }

    private void styleTabs() {
        Button pendingButton =
                findViewById(R.id.pendingTab);

        Button doneButton =
                findViewById(R.id.doneTab);

        if ("pending".equals(currentTab)) {
            pendingButton.setAlpha(1.0f);
            doneButton.setAlpha(0.55f);
        } else {
            pendingButton.setAlpha(0.55f);
            doneButton.setAlpha(1.0f);
        }
    }

    private TextView createTextView(
            String text,
            int textSize,
            boolean bold
    ) {
        TextView textView =
                new TextView(this);

        textView.setText(text);
        textView.setTextSize(textSize);

        textView.setTextColor(
                Color.rgb(
                        35,
                        35,
                        35
                )
        );

        textView.setPadding(
                0,
                dp(6),
                0,
                dp(6)
        );

        if (bold) {
            textView.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return textView;
    }

    private int dp(int value) {
        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}