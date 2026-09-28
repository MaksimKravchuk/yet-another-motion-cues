package com.maxlab.motioncues;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    static final String NAME = "cfg";
    static final String SENS = "sens";           // сила смещения 1..10
    static final String SIZE = "size";           // dp 4..20
    static final String INTENSITY = "intensity"; // видимость 10..100 %
    static final String COLOR = "color";         // 0 авто, 1 тёмные, 2 светлые, 3 акцент
    static final String MOTION_ONLY = "motion_only";
    static final String AUTO_DRIVE = "auto_drive"; // включать, когда еду (Activity Recognition)
    static final String AUTO_STARTED = "auto_started";
    // Состояние поездки по событиям DriveReceiver
    static final String IN_VEHICLE = "in_vehicle";       // последнее событие — ENTER
    static final String LAST_EXIT = "last_exit";         // время последнего EXIT, мс
    static final String SUPPRESSED_AT = "suppressed_at"; // когда выключили вручную в дороге, 0 — нет

    static SharedPreferences get(Context c) { return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }
    static int sens(Context c) { return get(c).getInt(SENS, 5); }
    static int size(Context c) { return get(c).getInt(SIZE, 9); }
    static int intensity(Context c) { return get(c).getInt(INTENSITY, 100); }
    static int color(Context c) { return get(c).getInt(COLOR, 0); }
    static boolean motionOnly(Context c) { return get(c).getBoolean(MOTION_ONLY, false); }
    static boolean autoDrive(Context c) { return get(c).getBoolean(AUTO_DRIVE, true); }
    static boolean autoStarted(Context c) { return get(c).getBoolean(AUTO_STARTED, false); }
    static void setAutoStarted(Context c, boolean v) { get(c).edit().putBoolean(AUTO_STARTED, v).apply(); }

    static boolean inVehicle(Context c) { return get(c).getBoolean(IN_VEHICLE, false); }
    static long lastExit(Context c) { return get(c).getLong(LAST_EXIT, 0); }
    static long suppressedAt(Context c) { return get(c).getLong(SUPPRESSED_AT, 0); }
    static void setSuppressedAt(Context c, long t) { get(c).edit().putLong(SUPPRESSED_AT, t).apply(); }
    static void setEnter(Context c) { get(c).edit().putBoolean(IN_VEHICLE, true).apply(); }
    static void setExit(Context c, long t) {
        get(c).edit().putBoolean(IN_VEHICLE, false).putLong(LAST_EXIT, t).apply();
    }
    /** Забыть поездку: после перезагрузки или выключения автовключения события могли потеряться. */
    static void resetRide(Context c) {
        get(c).edit().putBoolean(IN_VEHICLE, false).putLong(SUPPRESSED_AT, 0).apply();
    }
}
