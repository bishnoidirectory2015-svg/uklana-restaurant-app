package com.uklanafood.restaurant;

import android.content.Context;
import android.content.SharedPreferences;

public final class SessionManager {

    private static final String PREF = "ukf_restaurant_login";

    private SessionManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(
            Context context,
            String token,
            String name,
            String phone
    ) {
        prefs(context)
                .edit()
                .putString("token", token == null ? "" : token)
                .putString("name", name == null ? "" : name)
                .putString("phone", phone == null ? "" : phone)
                .apply();
    }

    public static String token(Context context) {
        return prefs(context).getString("token", "");
    }

    public static String name(Context context) {
        return prefs(context).getString("name", "");
    }

    public static String phone(Context context) {
        return prefs(context).getString("phone", "");
    }

    public static boolean loggedIn(Context context) {
        return !token(context).isEmpty();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    public static void saveFcmToken(Context context, String token) {
        prefs(context)
                .edit()
                .putString("fcm_token", token == null ? "" : token)
                .apply();
    }

    public static String getFcmToken(Context context) {
        return prefs(context).getString("fcm_token", "");
    }
}