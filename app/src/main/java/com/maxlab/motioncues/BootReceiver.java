package com.maxlab.motioncues;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

/** После перезагрузки и обновления приложения подписка на распознавание поездки слетает — восстанавливаем. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        if (!Prefs.autoDrive(ctx) || !DriveReceiver.hasPerm(ctx)) return;
        // Держим процесс, пока запрос не дошёл до Play Services
        final PendingResult pr = goAsync();
        DriveReceiver.register(ctx).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override public void onComplete(Task<Void> t) { pr.finish(); }
        });
    }
}
