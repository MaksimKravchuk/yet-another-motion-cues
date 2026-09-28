package com.maxlab.motioncues;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

final class Prefs {
    static final String NAME = "cfg";
    static final String SENS = "sens";           // сила смещения 1..10
    static final String SIZE = "size";           // dp 4..20
    static final String INTENSITY = "intensity"; // видимость 10..100 %
    static final String COLOR = "color";         // 0 авто, 1 тёмные, 2 светлые, 3 акцент
    static final String MOTION_ONLY = "motion_only";
    static final String AUTO_DRIVE = "auto_drive"; // включать, когда еду (Activity Recognition)
    static final String AUTO_BT = "auto_bt";
    static final String BT_DEVICES = "bt_devices"; // "addr\tname"
    static final String AUTO_STARTED = "auto_started";

    static SharedPreferences get(Context c) { return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }
    static int sens(Context c) { return get(c).getInt(SENS, 5); }
    static int size(Context c) { return get(c).getInt(SIZE, 9); }
    static int intensity(Context c) { return get(c).getInt(INTENSITY, 100); }
    static int color(Context c) { return get(c).getInt(COLOR, 0); }
    static boolean motionOnly(Context c) { return get(c).getBoolean(MOTION_ONLY, false); }
    static boolean autoDrive(Context c) { return get(c).getBoolean(AUTO_DRIVE, true); }
    static boolean autoBt(Context c) { return get(c).getBoolean(AUTO_BT, false); }
    static boolean autoStarted(Context c) { return get(c).getBoolean(AUTO_STARTED, false); }
    static void setAutoStarted(Context c, boolean v) { get(c).edit().putBoolean(AUTO_STARTED, v).apply(); }

    static Set<String> devices(Context c) {
        return new HashSet<String>(get(c).getStringSet(BT_DEVICES, new HashSet<String>()));
    }
    static boolean isCarDevice(Context c, String addr) {
        for (String s : devices(c)) if (s.startsWith(addr + "\t")) return true;
        return false;
    }
    static String deviceNames(Context c) {
        StringBuilder sb = new StringBuilder();
        for (String s : devices(c)) {
            int i = s.indexOf('\t');
            if (sb.length() > 0) sb.append(", ");
            sb.append(i >= 0 ? s.substring(i + 1) : s);
        }
        return sb.toString();
    }
}
