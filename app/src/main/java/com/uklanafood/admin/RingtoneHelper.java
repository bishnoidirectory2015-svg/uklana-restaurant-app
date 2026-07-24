package com.uklanafood.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

public final class RingtoneHelper {
    private static final String PREFS = "ringtone_settings";
    private static final String KEY_URI = "selected_ringtone_uri";
    private static final String KEY_NAME = "selected_ringtone_name";

    private RingtoneHelper() {}

    public static Uri getSelectedUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_URI, "");
        if (saved == null || saved.trim().isEmpty()) {
            return Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.order_alert);
        }
        return Uri.parse(saved);
    }

    public static String getSelectedName(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_NAME, "Default Order Tone");
    }

    public static void save(Context context, Uri uri, String name) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_URI, uri == null ? "" : uri.toString())
                .putString(KEY_NAME, name == null || name.trim().isEmpty() ? "Selected Ringtone" : name)
                .apply();
    }

    public static void reset(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    public static String channelId(Context context) {
        return AppConfig.CHANNEL_ID + "_v304_" + Math.abs(getSelectedUri(context).toString().hashCode());
    }
}
