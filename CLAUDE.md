# CLAUDE.md

## Проект
Android-аналог Apple Vehicle Motion Cues. Чистая Java, без AndroidX/Compose, UI строится в коде
(без XML-лейаутов). Язык интерфейса — русский. minSdk 29, targetSdk 35. Целевое устройство — Google Pixel.

## Структура (app/src/main/java/com/maxlab/motioncues)
- `CuesService` — foreground service (type `specialUse`). Добавляет полноэкранный оверлей
  `CuesView`, слушает `TYPE_LINEAR_ACCELERATION` + `TYPE_GAME_ROTATION_VECTOR`.
  Ускорение переводится в мировые координаты, берётся горизонтальная часть и проецируется на
  «вперёд» = горизонтальная проекция (screen-up − screen-normal), учитывая поворот дисплея.
  Low-pass ~0.25 с, мёртвая зона 0.12 м/с². Точки смещаются ПРОТИВ ускорения.
- `CuesView` — отрисовка точек (2 колонки с каждой стороны, шахматный сдвиг).
- `CuesTileService` — плитка Quick Settings. Если старт FGS из фона запрещён — открывает
  активити с `EXTRA_AUTOSTART`.
- `BtReceiver` — манифестный ресивер `ACL_CONNECTED/DISCONNECTED`; стартует/останавливает
  сервис для выбранных устройств.
- `DriveReceiver` — автовключение в дороге: Activity Recognition Transition API
  (`play-services-location`), `IN_VEHICLE` ENTER/EXIT, PendingIntent `FLAG_MUTABLE`.
  Нужно runtime-разрешение `ACTIVITY_RECOGNITION`. Подписка слетает после перезагрузки и обновления —
  `BootReceiver` перерегистрирует по `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED`.
- Автостарт общий: `CuesService.autoStart/autoStop`. Старт с `ACTION_AUTO_START` ставит
  `Prefs.autoStarted`; выключаем автоматически только то, что включили автоматически. Если старт
  FGS из фона запрещён (Bluetooth-broadcast не даёт исключения) — уведомление «Нажми, чтобы включить».
  События Activity Recognition — исключение из запрета, там старт проходит сразу.
  На EXIT выключение отложено на `EXIT_GRACE_MS` (светофор/пробка), новый ENTER его отменяет.
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
