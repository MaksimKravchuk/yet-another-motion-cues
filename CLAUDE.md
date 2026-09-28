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
  сервис для выбранных устройств. Фоллбэк — уведомление с `PendingIntent.getForegroundService`.
- `MainActivity` — настройки. `Prefs` — все ключи SharedPreferences.

## Важные ограничения
- Окно оверлея: `alpha = 0.8`, иначе Android 12+ блокирует касания сквозь него. Не повышать.
- Подпись: `keystore/motioncues.jks` — тем же ключом подписаны уже установленные у пользователя
  APK. Не менять, иначе обновление потребует удаления приложения.
- При каждом релизе увеличивать `versionCode`/`versionName` в `app/build.gradle.kts`.

## Сборка и проверка
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat | grep -iE "motioncues|AndroidRuntime"
```
Проект переведён на Gradle из ручной сборки (aapt/dx) и через Gradle ещё НЕ собирался —
первым делом собрать и починить, если что-то упадёт.

## TODO
1. **Автовключение по распознаванию поездки** (как у Apple), дополнительно к Bluetooth:
   - `com.google.android.gms:play-services-location`, `ActivityRecognition.getClient(ctx)
     .requestActivityTransitionUpdates(...)` с `IN_VEHICLE` ENTER/EXIT.
   - Runtime-разрешение `ACTIVITY_RECOGNITION`; PendingIntent для transitions должен быть `FLAG_MUTABLE`.
   - Регистрация слетает после перезагрузки/обновления → перерегистрировать по
     `BOOT_COMPLETED` и `MY_PACKAGE_REPLACED`.
   - Broadcast от Activity Recognition НЕ даёт исключения на старт FGS из фона → при
     `ForegroundServiceStartNotAllowedException` показывать уведомление «Нажми, чтобы включить»
     (как в `BtReceiver`). Проверить, не проходит ли старт благодаря SYSTEM_ALERT_WINDOW.
   - В настройках: переключатель «Включать, когда еду в машине», отдельно от Bluetooth.
     На EXIT выключать только если включили автоматически (`Prefs.autoStarted`).
2. Проверить на реальном Pixel направление смещения во всех ориентациях и в ландшафте.
