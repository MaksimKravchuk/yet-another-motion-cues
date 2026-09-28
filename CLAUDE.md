# CLAUDE.md

## Проект
Android-аналог Apple Vehicle Motion Cues. Чистая Java, без AndroidX/Compose, UI строится в коде
(без XML-лейаутов). Исключение — `play-services-location` для распознавания поездки: он сам
подтягивает androidx.core/fragment; в своём коде AndroidX не используем. Язык интерфейса — русский. minSdk 29, targetSdk 35. Целевое устройство — Google Pixel.

## Структура (app/src/main/java/com/maxlab/motioncues)
- `CuesService` — foreground service (type `specialUse`). Добавляет полноэкранный оверлей
  `CuesView`, слушает `TYPE_LINEAR_ACCELERATION` + `TYPE_GAME_ROTATION_VECTOR`.
  Ускорение переводится в мировые координаты, берётся горизонтальная часть и проецируется на
  «вперёд» = горизонтальная проекция (screen-up − screen-normal), учитывая поворот дисплея.
  Low-pass ~0.25 с, мёртвая зона 0.12 м/с². Точки смещаются ПРОТИВ ускорения.
- `CuesView` — отрисовка точек (2 колонки с каждой стороны, шахматный сдвиг).
- `CuesTileService` — плитка Quick Settings. Если старт FGS из фона запрещён — открывает
  активити с `EXTRA_AUTOSTART`.
- `DriveReceiver` — автовключение в дороге: Activity Recognition Transition API
  (`play-services-location`), `IN_VEHICLE` ENTER/EXIT, PendingIntent `FLAG_MUTABLE`.
  Нужно runtime-разрешение `ACTIVITY_RECOGNITION`. Подписка слетает после перезагрузки и обновления —
  `BootReceiver` перерегистрирует по `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED`, `MainActivity.onResume`
  подписывается повторно (подписка идемпотентна).
- `CuesService.autoStart/autoStop`. `Prefs.autoStarted` ставит только `onStartCommand`
  (`ACTION_AUTO_START`), выключаем автоматически только то, что включили автоматически.
  События Activity Recognition — исключение из запрета на старт FGS из фона, старт проходит сразу;
  если нет — уведомление «Нажми, чтобы включить». Работающему сервису шлём только
  `ACTION_AUTO_KEEP`/`ACTION_AUTO_EXIT` и не зовём повторно `startForeground()`.
  На EXIT выключение отложено на `EXIT_GRACE_MS` (светофор/пробка), новый ENTER его отменяет;
  таймер сверяется с `elapsedRealtime`, т.к. `Handler` в глубоком сне стоит.
- Ручное выключение в дороге (`Prefs.inVehicle`) запоминается в `Prefs.suppressedAt`: ENTER
  не включает точки, пока не будет EXIT и ≥10 мин вне транспорта (новая поездка) или 12 ч.
- `MainActivity` — настройки. `Prefs` — все ключи SharedPreferences.

## Важные ограничения
- Окно оверлея: `alpha = 0.8`, иначе Android 12+ блокирует касания сквозь него. Не повышать.
- Подпись: `keystore/motioncues.jks` — тем же ключом подписаны уже установленные у пользователя
  APK. Не менять, иначе обновление потребует удаления приложения.
- При каждом релизе увеличивать `versionCode`/`versionName` в `app/build.gradle.kts`: CI публикует
  GitHub Release `v<versionName>` только если такого ещё нет.

## Сборка и проверка
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep -iE "motioncues|AndroidRuntime"
```
CI: `.github/workflows/android.yml` — `assembleRelease` на каждый PR и push в `main`,
релиз с `MotionCues.apk` при новой `versionName`.

Проверка автовключения в дороге без поездки:
```bash
adb logcat | grep -i "IN_VEHICLE"   # DriveReceiver логирует ENTER/EXIT
```

## TODO
1. Проверить на реальном Pixel направление смещения во всех ориентациях и в ландшафте.
2. Проверить автовключение в дороге в реальной поездке (задержка ENTER/EXIT, ложные EXIT в пробке).
