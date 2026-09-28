# Motion Cues для Android

Аналог Apple Vehicle Motion Cues для Pixel (Android 10+). Точки по краям экрана поверх всех
приложений смещаются против ускорения машины и снижают укачивание при чтении в дороге.

## Возможности
- Оверлей с точками (TYPE_APPLICATION_OVERLAY), работает поверх любых приложений
- Корректно при любом положении телефона (плашмя, вертикально, ландшафт)
- Плитка в шторке: тап — вкл/выкл, долгий тап — настройки
- Настройки: интенсивность, сила смещения, размер, цвет (авто/тёмные/светлые/Material You),
  режим «только при ускорении»
- Автовключение в дороге: телефон сам распознаёт поездку (машина, автобус, поезд) через
  Activity Recognition Google Play Services, включает точки и выключает их после поездки.
  Нужно разрешение «Физическая активность» — приложение попросит его при первом запуске
- Автовключение при подключении к Bluetooth машины и выключение при отключении

## Установка
Свежий APK всегда здесь:
https://github.com/MaksimKravchuk/yet-another-motion-cues/releases/latest/download/MotionCues.apk

Все версии — на странице [Releases](https://github.com/MaksimKravchuk/yet-another-motion-cues/releases).
После установки обновления один раз открой приложение, чтобы выдать новые разрешения.

## CI
GitHub Actions (`.github/workflows/android.yml`) собирает подписанный release APK на каждый PR и
push в `main` — его можно скачать в артефактах запуска. Push в `main` с новой `versionName`
публикует релиз `vX.Y` с `MotionCues.apk`.

## Сборка
Нужны JDK 17 и Android SDK (платформа 35).

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Или открыть папку в Android Studio.
