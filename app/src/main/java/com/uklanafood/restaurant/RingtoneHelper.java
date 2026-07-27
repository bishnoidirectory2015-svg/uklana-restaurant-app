package com.uklanafood.restaurant;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;

public final class RingtoneHelper {

    private static final String PREF_NAME = "ukf_notification_settings";
    private static final String KEY_RINGTONE_URI = "selected_ringtone_uri";

    private RingtoneHelper() {
    }

    public static void saveRingtone(Context context, Uri ringtoneUri) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );

        if (ringtoneUri == null) {
            preferences.edit().remove(KEY_RINGTONE_URI).apply();
        } else {
            preferences.edit()
                    .putString(KEY_RINGTONE_URI, ringtoneUri.toString())
                    .apply();
        }
    }

    public static Uri getRingtone(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );

        String savedUri = preferences.getString(KEY_RINGTONE_URI, "");

        if (savedUri == null || savedUri.trim().isEmpty()) {
            return RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
            );
        }

        try {
            return Uri.parse(savedUri);
        } catch (Exception exception) {
            return RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
            );
        }
    }

    public static String getRingtoneName(Context context) {
        try {
            Uri uri = getRingtone(context);

            android.media.Ringtone ringtone =
                    RingtoneManager.getRingtone(context, uri);

            if (ringtone != null) {
                return ringtone.getTitle(context);
            }
        } catch (Exception ignored) {
        }

        return "Default Notification Sound";
    }

    public static void resetToDefault(Context context) {
        saveRingtone(context, null);
    }
}