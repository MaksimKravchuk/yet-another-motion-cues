package com.maxlab.motioncues;

import android.app.Activity;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static final String EXTRA_AUTOSTART = "autostart";
    private static final int REQ_PERMS = 1, REQ_DRIVE = 2;

    private Button permBtn, toggleBtn;
    private TextView status, sensLbl, sizeLbl, intLbl, driveLbl;
    private Switch driveSw;
    private LinearLayout ll;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = dp(20);
        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(pad, pad, pad, pad);
        sv.addView(ll);

        TextView title = new TextView(this);
        title.setText("Motion Cues");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        ll.addView(title);

        TextView info = new TextView(this);
        info.setText("Точки по краям экрана смещаются вместе с движением машины: разгон — вниз, "
                + "торможение — вверх, поворот налево — вправо.");
        info.setPadding(0, dp(8), 0, dp(16));
        ll.addView(info);

        status = new TextView(this);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setPadding(0, 0, 0, dp(8));
        ll.addView(status);

        permBtn = new Button(this);
        permBtn.setText("Разрешить показ поверх других приложений");
        permBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        ll.addView(permBtn);

        toggleBtn = new Button(this);
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (CuesService.running) stopService(new Intent(MainActivity.this, CuesService.class));
                else startCues();
                h.postDelayed(new Runnable() { @Override public void run() { refresh(); } }, 300);
            }
        });
        ll.addView(toggleBtn);

        Button tileBtn = new Button(this);
        tileBtn.setText("Добавить переключатель в шторку");
        tileBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { addTile(); }
        });
        ll.addView(tileBtn);

        // ---- Внешний вид
        header("Внешний вид");
        intLbl = label();
        slider(10, 100, Prefs.intensity(this), Prefs.INTENSITY);
        sensLbl = label();
        slider(1, 10, Prefs.sens(this), Prefs.SENS);
        sizeLbl = label();
        slider(4, 20, Prefs.size(this), Prefs.SIZE);

        TextView colLbl = label();
        colLbl.setText("Цвет точек");
        RadioGroup rg = new RadioGroup(this);
        String[] colors = {"Авто (по теме системы)", "Тёмные", "Светлые", "Акцентный (Material You)"};
        for (int i = 0; i < colors.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(100 + i);
            rb.setText(colors[i]);
            rg.addView(rb);
        }
        rg.check(100 + Prefs.color(this));
        rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(RadioGroup g, int id) {
                Prefs.get(MainActivity.this).edit().putInt(Prefs.COLOR, id - 100).apply();
            }
        });
        ll.addView(rg);

        Switch motionSw = new Switch(this);
        motionSw.setText("Показывать точки только при ускорении/торможении/повороте");
        motionSw.setChecked(Prefs.motionOnly(this));
        motionSw.setPadding(0, dp(12), 0, 0);
        motionSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean v) {
                Prefs.get(MainActivity.this).edit().putBoolean(Prefs.MOTION_ONLY, v).apply();
            }
        });
        ll.addView(motionSw);

        // ---- Автовключение
        header("Автовключение");
        driveSw = new Switch(this);
        driveSw.setText("Включать, когда еду в машине, автобусе или поезде, и выключать после поездки");
        driveSw.setChecked(Prefs.autoDrive(this));
        driveSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean v) {
                Prefs.get(MainActivity.this).edit().putBoolean(Prefs.AUTO_DRIVE, v).apply();
                if (!v) {
                    DriveReceiver.unregister(MainActivity.this);
                    Prefs.resetRide(MainActivity.this);
                    // Событий больше не будет: уже включённые точки теперь выключаются только вручную
                    if (CuesService.running) Prefs.setAutoStarted(MainActivity.this, false);
                } else if (DriveReceiver.hasPerm(MainActivity.this)) {
                    registerDrive();
                } else {
                    requestPermissions(new String[]{DriveReceiver.PERM}, REQ_DRIVE);
                }
                refresh();
                CuesService.refreshTile(MainActivity.this);
            }
        });
        ll.addView(driveSw);
        driveLbl = label();

        setContentView(sv);

        List<String> perms = new ArrayList<String>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            perms.add("android.permission.POST_NOTIFICATIONS");
        }
        if (Prefs.autoDrive(this) && !DriveReceiver.hasPerm(this)) perms.add(DriveReceiver.PERM);
        if (!perms.isEmpty()) requestPermissions(perms.toArray(new String[0]), REQ_PERMS);
        handleAutostart(getIntent());
    }

    private void header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setPadding(0, dp(28), 0, dp(4));
        ll.addView(t);
    }

    private TextView label() {
        TextView t = new TextView(this);
        t.setPadding(0, dp(12), 0, 0);
        ll.addView(t);
        return t;
    }

    private void slider(final int min, int max, int value, final String key) {
        SeekBar s = new SeekBar(this);
        s.setMax(max - min);
        s.setProgress(value - min);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                Prefs.get(MainActivity.this).edit().putInt(key, p + min).apply();
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        ll.addView(s);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleAutostart(i);
    }

    private void handleAutostart(Intent i) {
        if (i != null && i.getBooleanExtra(EXTRA_AUTOSTART, false) && Settings.canDrawOverlays(this)
                && !CuesService.running) {
            startCues();
            i.removeExtra(EXTRA_AUTOSTART);
        }
    }

    private void startCues() {
        if (!Settings.canDrawOverlays(this)) { refresh(); return; }
        startForegroundService(new Intent(this, CuesService.class));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        for (int i = 0; i < perms.length && i < res.length; i++) {
            if (!DriveReceiver.PERM.equals(perms[i])) continue;
            if (res[i] == PackageManager.PERMISSION_GRANTED) {
                registerDrive();
            } else if (code == REQ_DRIVE && !shouldShowRequestPermissionRationale(DriveReceiver.PERM)) {
                // «Больше не спрашивать»: диалога не будет, выдать можно только в настройках.
                // Переключатель оставляем — после возврата onResume подпишется сам.
                toast("Разреши «Физическая активность» в настройках приложения");
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } else {
                driveSw.setChecked(false);
                toast("Без разрешения «Физическая активность» автовключение в дороге не работает");
            }
            refresh();
        }
    }

    private void registerDrive() {
        DriveReceiver.register(this).addOnFailureListener(this, new OnFailureListener() {
            @Override public void onFailure(Exception e) {
                toast("Не удалось включить распознавание поездки — проверь Google Play Services");
            }
        });
    }

    private void addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager sbm = getSystemService(StatusBarManager.class);
            sbm.requestAddTileService(new ComponentName(this, CuesTileService.class),
                    "Motion Cues", Icon.createWithResource(this, R.drawable.ic_tile),
                    getMainExecutor(), new java.util.function.Consumer<Integer>() {
                        @Override public void accept(Integer r) {}
                    });
        } else {
            toast("Шторка → карандаш → перетащи плитку Motion Cues");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Подписка могла слететь (force stop, сброс Play Services, разрешение выдали в настройках,
        // не удалось подписаться при загрузке). Повторная подписка безопасна.
        if (Prefs.autoDrive(this) && DriveReceiver.hasPerm(this)) DriveReceiver.register(this);
        refresh();
    }

    private void refresh() {
        boolean perm = Settings.canDrawOverlays(this);
        permBtn.setVisibility(perm ? View.GONE : View.VISIBLE);
        toggleBtn.setEnabled(perm);
        boolean on = CuesService.running;
        toggleBtn.setText(on ? "Выключить" : "Включить");
        status.setText(!perm ? "Нужно разрешение на показ поверх других приложений"
                : on ? (Prefs.autoStarted(this) ? "Статус: включено автоматически" : "Статус: включено")
                : "Статус: выключено");
        intLbl.setText("Интенсивность (видимость точек): " + Prefs.intensity(this) + "%");
        sensLbl.setText("Сила смещения: " + Prefs.sens(this));
        sizeLbl.setText("Размер точек: " + Prefs.size(this) + " dp");
        boolean drive = Prefs.autoDrive(this);
        driveLbl.setText(!drive ? "Автовключение в дороге выключено"
                : !DriveReceiver.hasPerm(this) ? "Нужно разрешение «Физическая активность»"
                : "Включится, когда телефон поймёт, что ты едешь");
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
