package com.maxlab.motioncues;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class CuesTileService extends TileService {

    @Override public void onStartListening() { update(); }
    @Override public void onTileAdded() { update(); }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = CuesService.running;
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel("Motion Cues");
        t.setSubtitle(on ? (Prefs.autoStarted(this) ? "Вкл · авто" : "Вкл")
                : (Prefs.autoBt(this) ? "Выкл · авто BT" : "Выкл"));
        t.updateTile();
    }

    @Override
    public void onClick() {
        if (CuesService.running) {
            stopService(new Intent(this, CuesService.class));
        } else if (!Settings.canDrawOverlays(this)) {
            openApp(false);
            return;
        } else {
            Prefs.setAutoStarted(this, false);
            try {
                startForegroundService(new Intent(this, CuesService.class));
            } catch (Exception e) {
                openApp(true);
                return;
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { update(); }
        }, 300);
    }

    @SuppressWarnings("deprecation")
    private void openApp(boolean autostart) {
        Intent i = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_AUTOSTART, autostart);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 3, i,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            startActivityAndCollapse(i);
        }
    }
}
