package com.uklanafood.restaurant;

import android.content.Context;

public final class SessionManager {
    private static final String PREF = "ukf_restaurant_login";
    private SessionManager() {}
    public static void save(Context c, String token, String name, String phone) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString("token", token).putString("name", name).putString("phone", phone).apply();
    }
    public static String token(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("token", ""); }
    public static String name(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("name", ""); }
    public static String phone(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("phone", ""); }
    public static boolean loggedIn(Context c) { return !token(c).isEmpty(); }
    public static void clear(Context c) { c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply(); }
public void saveFcmToken(String token) {
    preferences.edit()
            .putString("fcm_token", token == null ? "" : token)
            .apply();
}

public String getFcmToken() {
    return preferences.getString("fcm_token", "");

}
