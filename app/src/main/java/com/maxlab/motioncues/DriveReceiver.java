package com.maxlab.motioncues;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;
import com.google.android.gms.location.ActivityRecognition;
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
    static final String PERM = "android.permission.ACTIVITY_RECOGNITION";
    private static final String TAG = "MotionCues";

    static boolean hasPerm(Context c) {
        return c.checkSelfPermission(PERM) == PackageManager.PERMISSION_GRANTED;
    }

    /** Подписка слетает после перезагрузки и обновления — см. BootReceiver. */
    static Task<Void> register(Context c) {
        List<ActivityTransition> list = new ArrayList<ActivityTransition>();
        list.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build());
        list.add(new ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build());
        Task<Void> t;
        try {
            t = ActivityRecognition.getClient(c)
                    .requestActivityTransitionUpdates(new ActivityTransitionRequest(list), pendingIntent(c));
        } catch (SecurityException e) {
            t = Tasks.forException(e);
        }
        return t.addOnFailureListener(logFailure("register"));
    }

    static Task<Void> unregister(Context c) {
        Task<Void> t;
        try {
            t = ActivityRecognition.getClient(c).removeActivityTransitionUpdates(pendingIntent(c));
        } catch (SecurityException e) {
            t = Tasks.forException(e);
        }
        return t.addOnFailureListener(logFailure("unregister"));
    }

    private static PendingIntent pendingIntent(Context c) {
        // FLAG_MUTABLE: Play Services дописывает результат в extras
        return PendingIntent.getBroadcast(c, 0, new Intent(c, DriveReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    private static OnFailureListener logFailure(final String what) {
        return new OnFailureListener() {
            @Override public void onFailure(Exception e) { Log.w(TAG, "Activity transitions " + what + " failed", e); }
        };
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
        Log.i(TAG, "IN_VEHICLE " + (enter ? "ENTER" : "EXIT"));
        if (!Prefs.autoDrive(ctx)) return;
        if (enter) CuesService.autoStart(ctx, "Похоже, ты в дороге");
        else CuesService.autoStop(ctx, true);
    }
}
