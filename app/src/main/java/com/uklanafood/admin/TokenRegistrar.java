package com.uklanafood.admin;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class TokenRegistrar {
    private TokenRegistrar() {}

    public static void register(Context context, String token) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
                String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
                String body = "token=" + enc(token) + "&secret=" + enc(AppConfig.APP_SECRET)
                        + "&device_name=" + enc(deviceName) + "&device_id=" + enc(androidId == null ? "" : androidId);
                connection = (HttpURLConnection) new URL(AppConfig.REGISTER_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                connection.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }
}
