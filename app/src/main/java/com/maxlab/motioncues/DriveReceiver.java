package com.maxlab.motioncues;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.ArrayList;
import java.util.List;

/**
 * Автовключение по распознаванию поездки (Activity Recognition, IN_VEHICLE ENTER/EXIT).
 * Событие перехода — исключение из запрета на старт FGS из фона, так что обычно включается сразу.
 */
public class DriveReceiver extends BroadcastReceiver {
    static final String PERM = Manifest.permission.ACTIVITY_RECOGNITION;
    private static final String TAG = "MotionCues";
    // Выключили точки вручную в дороге: следующий ENTER считаем новой поездкой, только если
    // до него не были в транспорте хотя бы NEW_RIDE_GAP_MS (иначе это остановка той же поездки).
    private static final long NEW_RIDE_GAP_MS = 10 * 60 * 1000;
    private static final long SUPPRESS_MAX_MS = 12 * 60 * 60 * 1000; // на случай потерянного EXIT
    // ENTER, пришедший с таким опозданием (Doze, батчинг), точки не включает
    private static final long STALE_ENTER_NS = 10 * 60 * 1000_000_000L;

    static boolean hasPerm(Context c) {
        return c.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED;
    }

    /** Повторная подписка безопасна. Слетает после перезагрузки и обновления — см. BootReceiver. */
    static Task<Void> register(Context c) { return update(c, true); }

    static Task<Void> unregister(Context c) { return update(c, false); }

    private static Task<Void> update(Context c, boolean on) {
        ActivityRecognitionClient ar = ActivityRecognition.getClient(c);
        Task<Void> t;
        try {
            t = on ? ar.requestActivityTransitionUpdates(request(), pendingIntent(c))
                    : ar.removeActivityTransitionUpdates(pendingIntent(c));
        } catch (SecurityException e) {
            t = Tasks.forException(e);
        }
        final String what = on ? "register" : "unregister";
        return t.addOnFailureListener(new OnFailureListener() {
            @Override public void onFailure(Exception e) { Log.w(TAG, "Activity transitions " + what + " failed", e); }
        });
    }

    private static ActivityTransitionRequest request() {
        List<ActivityTransition> list = new ArrayList<ActivityTransition>();
        list.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build());
        list.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build());
        return new ActivityTransitionRequest(list);
    }

    private static PendingIntent pendingIntent(Context c) {
        // FLAG_MUTABLE: Play Services дописывает результат в extras
        return PendingIntent.getBroadcast(c, 0, new Intent(c, DriveReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return;
        ActivityTransitionResult res = ActivityTransitionResult.extractResult(intent);
        if (res == null) return;
        // События в хронологическом порядке — важно только последнее
        ActivityTransitionEvent last = null;
        for (ActivityTransitionEvent e : res.getTransitionEvents()) {
            if (e.getActivityType() == DetectedActivity.IN_VEHICLE) last = e;
        }
        if (last == null) return;
        boolean enter = last.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER;
        long ageNs = SystemClock.elapsedRealtimeNanos() - last.getElapsedRealTimeNanos();
        Log.i(TAG, "IN_VEHICLE " + (enter ? "ENTER" : "EXIT") + ", " + ageNs / 1_000_000_000L + " s ago");
        long now = System.currentTimeMillis();

        if (!enter) {
            Prefs.setExit(ctx, now);
            if (Prefs.autoDrive(ctx)) CuesService.autoStop(ctx);
            return;
        }
        long lastExit = Prefs.lastExit(ctx);
        Prefs.setEnter(ctx);
        if (!Prefs.autoDrive(ctx)) return;
        long suppressed = Prefs.suppressedAt(ctx);
        if (suppressed > 0) {
            boolean newRide = now - suppressed > SUPPRESS_MAX_MS
                    || (lastExit > suppressed && now - lastExit >= NEW_RIDE_GAP_MS);
            if (!newRide) return;
            Prefs.setSuppressedAt(ctx, 0);
        }
        if (ageNs > STALE_ENTER_NS) return;
        CuesService.autoStart(ctx);
    }
}
