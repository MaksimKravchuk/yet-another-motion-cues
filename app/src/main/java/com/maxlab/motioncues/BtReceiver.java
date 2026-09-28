package com.maxlab.motioncues;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Автовключение при подключении к Bluetooth машины, выключение при отключении. */
public class BtReceiver extends BroadcastReceiver {
    @Override
    @SuppressWarnings("deprecation")
    public void onReceive(Context ctx, Intent intent) {
        if (!Prefs.autoBt(ctx)) return;
        BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (d == null) return;
        String addr;
        try { addr = d.getAddress(); } catch (SecurityException e) { return; }
        if (addr == null || !Prefs.isCarDevice(ctx, addr)) return;

        String action = intent.getAction();
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            if (CuesService.running || !Settings.canDrawOverlays(ctx)) return;
            Prefs.setAutoStarted(ctx, true);
            try {
                ctx.startForegroundService(new Intent(ctx, CuesService.class));
            } catch (Exception e) {
                // Система не дала стартовать из фона — предлагаем включить одним тапом
                Prefs.setAutoStarted(ctx, false);
                CuesService.ensureChannel(ctx);
                PendingIntent pi = PendingIntent.getForegroundService(ctx, 5,
                        new Intent(ctx, CuesService.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                Notification n = new Notification.Builder(ctx, CuesService.CHANNEL)
                        .setSmallIcon(R.drawable.ic_tile)
                        .setContentTitle("Подключено к машине")
                        .setContentText("Нажми, чтобы включить Motion Cues")
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build();
                try { ctx.getSystemService(NotificationManager.class).notify(2, n); }
                catch (Exception ignored) {}
            }
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            if (CuesService.running && Prefs.autoStarted(ctx)) {
                ctx.stopService(new Intent(ctx, CuesService.class));
            }
        }
    }
}
