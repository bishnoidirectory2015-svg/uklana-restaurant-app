package com.uklanafood.restaurant;

import android.content.Context;
import android.net.Uri;

public final class RingtoneHelper {
    private static final String PREFS="ringtone_settings",KEY_URI="selected_ringtone_uri",KEY_NAME="selected_ringtone_name";
    private RingtoneHelper(){}
    public static Uri getSelectedUri(Context c){String saved=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_URI,"");return saved==null||saved.trim().isEmpty()?Uri.parse("android.resource://"+c.getPackageName()+"/"+R.raw.order_alert):Uri.parse(saved);}
    public static String getSelectedName(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_NAME,"Default Order Tone");}
    public static void save(Context c,Uri u,String n){c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_URI,u==null?"":u.toString()).putString(KEY_NAME,n==null||n.trim().isEmpty()?"Selected Ringtone":n).apply();}
    public static void reset(Context c){c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();}
}
