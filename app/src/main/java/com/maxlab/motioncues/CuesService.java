package com.maxlab.motioncues;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

public class CuesService extends Service implements SensorEventListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

    static final String ACTION_STOP = "com.maxlab.motioncues.STOP";
    static final String ACTION_AUTO_START = "com.maxlab.motioncues.AUTO_START";
    static final String ACTION_AUTO_EXIT = "com.maxlab.motioncues.AUTO_EXIT";
    static final String CHANNEL = "cues";
    private static final int PROMPT_ID = 2;
    private static final long EXIT_GRACE_MS = 2 * 60 * 1000; // не гасим на светофоре и в пробке
    static volatile boolean running = false;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = new Runnable() { @Override public void run() { stopSelf(); } };

    private WindowManager wm;
    private CuesView view;
    private SensorManager sm;
    private final float[] rot = new float[9];
    private boolean haveRot = false;
    private float fRight = 0f, fFwd = 0f, vis = 1f;
    private long lastNs = 0;
    private float density;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        density = getResources().getDisplayMetrics().density;
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Motion Cues", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    /** Автовключение (Bluetooth машины, распознана поездка). Если старт из фона запрещён — уведомление. */
    static void autoStart(Context c, String reason) {
        if (running) {
            // Снова в пути — отменяем отложенное выключение, если включали автоматически
            if (Prefs.autoStarted(c)) {
                try { c.startService(new Intent(c, CuesService.class)); } catch (Exception ignored) {}
            }
            return;
        }
        if (!Settings.canDrawOverlays(c)) return;
        Intent i = new Intent(c, CuesService.class).setAction(ACTION_AUTO_START);
        try {
            c.startForegroundService(i);
        } catch (Exception e) {
            // Система не дала стартовать из фона — предлагаем включить одним тапом
            ensureChannel(c);
            PendingIntent pi = PendingIntent.getForegroundService(c, 5, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, CHANNEL)
                    .setSmallIcon(R.drawable.ic_tile)
                    .setContentTitle(reason)
                    .setContentText("Нажми, чтобы включить Motion Cues")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            try { c.getSystemService(NotificationManager.class).notify(PROMPT_ID, n); }
            catch (Exception ignored) {}
        }
    }

    /** Поездка закончилась: выключаем, только если включали автоматически. delayed — через EXIT_GRACE_MS. */
    static void autoStop(Context c, boolean delayed) {
        try { c.getSystemService(NotificationManager.class).cancel(PROMPT_ID); } catch (Exception ignored) {}
        if (!running || !Prefs.autoStarted(c)) return;
        if (delayed) {
            try {
                c.startService(new Intent(c, CuesService.class).setAction(ACTION_AUTO_EXIT));
                return;
            } catch (Exception ignored) {}
        }
        c.stopService(new Intent(c, CuesService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_AUTO_EXIT.equals(action)) {
            if (view == null) stopSelf(); // сервис успел остановиться — не оставляем пустой
            else if (Prefs.autoStarted(this)) {
                h.removeCallbacks(autoStop);
                h.postDelayed(autoStop, EXIT_GRACE_MS);
            }
            return START_NOT_STICKY;
        }
        h.removeCallbacks(autoStop);
        // Уже показываем: только отменили отложенное выключение. startForeground() повторно не
        // зовём — из фона Android 12+ может его запретить.
        if (view != null) return START_NOT_STICKY;
        if (ACTION_AUTO_START.equals(action)) Prefs.setAutoStarted(this, true);
        goForeground();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        start();
        return START_NOT_STICKY;
    }

    private void goForeground() {
        ensureChannel(this);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, CuesService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open = PendingIntent.getActivity(this, 2,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean auto = Prefs.autoStarted(this);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(auto ? "Motion Cues включены автоматически" : "Motion Cues включены")
                .setContentText("Точки по краям экрана показывают движение машины")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Выключить", stop).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, n);
        }
    }

    private void start() {
        wm = getSystemService(WindowManager.class);
        view = new CuesView(this);
        applyStyle();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.alpha = 0.8f; // Android 12+: иначе касания сквозь оверлей блокируются
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        lp.setTitle("MotionCues");
        wm.addView(view, lp);

        sm = getSystemService(SensorManager.class);
        Sensor lin = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        Sensor rv = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        if (rv == null) rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (lin != null) sm.registerListener(this, lin, SensorManager.SENSOR_DELAY_GAME);
        if (rv != null) sm.registerListener(this, rv, SensorManager.SENSOR_DELAY_GAME);

        Prefs.get(this).registerOnSharedPreferenceChangeListener(this);
        running = true;
        refreshTile(this);
    }

    private void applyStyle() {
        if (view == null) return;
        view.setDotSizeDp(Prefs.size(this));
        view.setIntensity(Prefs.intensity(this) / 100f);
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int dark = 0xFF1E1E1E, light = 0xFFF2F2F2, white = 0xFFFFFFFF, black = 0xFF202020;
        int mode = Prefs.color(this);
        if (mode == 1) view.setColors(dark, white);
        else if (mode == 2) view.setColors(light, black);
        else if (mode == 3) {
            int accent = Build.VERSION.SDK_INT >= 31
                    ? getColor(android.R.color.system_accent1_400) : 0xFF4A7CFF;
            view.setColors(accent, night ? black : white);
        } else {
            // Авто: светлые точки в тёмной теме, тёмные — в светлой
            if (night) view.setColors(light, black); else view.setColors(dark, white);
        }
        if (!Prefs.motionOnly(this)) { vis = 1f; view.setVis(1f); }
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyStyle();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        int t = e.sensor.getType();
        if (t == Sensor.TYPE_GAME_ROTATION_VECTOR || t == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rot, e.values);
            haveRot = true;
            return;
        }
        if (t != Sensor.TYPE_LINEAR_ACCELERATION || !haveRot || view == null) return;

        float ax = e.values[0], ay = e.values[1], az = e.values[2];
        float[] R = rot;
        float wx = R[0] * ax + R[1] * ay + R[2] * az;
        float wy = R[3] * ax + R[4] * ay + R[5] * az;

        float ux = 0f, uy = 1f;
        int r = displayRotation();
        if (r == Surface.ROTATION_90) { ux = 1f; uy = 0f; }
        else if (r == Surface.ROTATION_180) { ux = 0f; uy = -1f; }
        else if (r == Surface.ROTATION_270) { ux = -1f; uy = 0f; }

        float fx = R[0] * ux + R[1] * uy - R[2];
        float fy = R[3] * ux + R[4] * uy - R[5];
        float n = (float) Math.sqrt(fx * fx + fy * fy);
        if (n < 0.2f) return;
        fx /= n; fy /= n;

        float fwd = wx * fx + wy * fy;
        float right = wx * fy - wy * fx;

        long now = e.timestamp;
        float dt = lastNs == 0 ? 0.02f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        float a = dt / (0.25f + dt);
        fRight += a * (dz(right) - fRight);
        fFwd += a * (dz(fwd) - fFwd);

        if (Prefs.motionOnly(this)) {
            float mag = (float) Math.sqrt(fRight * fRight + fFwd * fFwd);
            float target = Math.min(1f, mag / 0.5f);
            float tau = target > vis ? 0.15f : 2.5f; // быстро появляются, медленно гаснут
            vis += dt / (tau + dt) * (target - vis);
            view.setVis(vis);
        }

        float gain = Prefs.sens(this) * 7f * density;
        float max = 90f * density;
        view.setOffsetPx(clamp(-fRight * gain, max), clamp(fFwd * gain, max));
    }

    private int displayRotation() {
        Display d = view != null ? view.getDisplay() : null;
        return d != null ? d.getRotation() : Surface.ROTATION_0;
    }

    private static float dz(float v) {
        float dead = 0.12f;
        if (Math.abs(v) < dead) return 0f;
        return v > 0 ? v - dead : v + dead;
    }

    private static float clamp(float v, float m) { return Math.max(-m, Math.min(m, v)); }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) { applyStyle(); }

    @Override
    public void onDestroy() {
        running = false;
        h.removeCallbacks(autoStop);
        Prefs.setAutoStarted(this, false);
        if (sm != null) sm.unregisterListener(this);
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this);
        if (view != null && wm != null) {
            try { wm.removeView(view); } catch (Exception ignored) {}
        }
        view = null;
        refreshTile(this);
        super.onDestroy();
    }

    static void refreshTile(Context c) {
        try {
            TileService.requestListeningState(c, new ComponentName(c, CuesTileService.class));
        } catch (Exception ignored) {}
    }
}
