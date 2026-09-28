package com.maxlab.motioncues;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

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
            CuesService.autoStart(ctx, "Подключено к машине");
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            CuesService.autoStop(ctx, false);
        }
    }
}
