# Outfit Share для Android

Нативное приложение: Java 17, View-система и Material 3, minSdk 26, targetSdk 36. Модули:
`:app` (экраны, сеть, Студия) и `:core:designsystem` (токены, тема, компоненты — см.
[`core/designsystem/README.md`](core/designsystem/README.md)). Интерфейс на русском (по умолчанию)
и английском (`values-en`).

## Сборка в Android Studio

1. Поставьте Android Studio (Narwhal 2025.1 или новее) — вместе с ней придут JDK и Android SDK.
2. **File → Open** и выберите папку `android/`, а не корень репозитория.
3. Дождитесь Gradle Sync. Если Studio предложит обновить версии (AGP, библиотеки), можно согласиться:
   они собраны в `gradle/libs.versions.toml`. SDK 36 Studio предложит установить сама.
4. Запуск на эмуляторе или телефоне — зелёная кнопка ▶ (конфигурация `app`, вариант `debug`).

Сервер по умолчанию — `https://moiservis.pro`. Для локального бэкенда
(`python manage.py runserver 0.0.0.0:8000` с `backend/.env.dev`) добавьте в `android/gradle.properties`:

```properties
outfitshare.server=http://10.0.2.2:8000
```

`10.0.2.2` — это компьютер, если смотреть из эмулятора. В debug-сборке сервер можно поменять и без
пересборки: долгое нажатие на логотип на экране входа. Там же появляется «Тестовый вход», если на
сервере `ALLOW_DEV_LOGIN=True`.

## Релизный APK / AAB

**Build → Generate Signed App Bundle / APK…** → APK (для установки напрямую) или Android App Bundle
(для Google Play) → *Create new…* keystore → вариант `release`.

- Храните `.jks` и пароли вне репозитория и сделайте их резервную копию: без этого ключа обновление
  приложения не установится поверх старого.
- Для сборки из командной строки создайте `android/keystore.properties` (он в `.gitignore`):
  ```properties
  storeFile=/путь/к/outfitshare.jks
  storePassword=...
  keyAlias=outfitshare
  keyPassword=...
  ```
  и выполните `./gradlew assembleRelease` (или `bundleRelease`). Без этого файла релиз собирается
  неподписанным — отладочным ключом он не подписывается никогда.
- Установка на телефон: скопируйте `app-release.apk` и откройте его (разрешите установку из этого
  источника) или выполните `adb install app-release.apk`.

### Ссылки сразу в приложении (App Links)

Ссылки `https://moiservis.pro/o/<id>` и `/u/<id>`, которыми делятся из приложения, открываются в нём,
если сервер подтверждает подпись APK. Отпечаток сертификата:

```bash
keytool -list -v -keystore /путь/к/outfitshare.jks -alias outfitshare | grep SHA256
```

Значение `AA:BB:…` запишите на сервере в `.env` как `ANDROID_APP_CERT_SHA256` (см. `DEPLOY.md`, шаг 12)
и проверьте на телефоне: `adb shell pm verify-app-links --re-verify app.outfitshare`.

## Проверки

```bash
./gradlew spotlessCheck :core:designsystem:checkstyle   # формат (google-java-format) и стиль
./gradlew lintDebug                                      # Lint: доступность и переводы — ошибки
./gradlew testDebugUnitTest                              # unit-тесты
./gradlew connectedDebugAndroidTest                      # Espresso, нужен запущенный эмулятор
```

То же самое, кроме Espresso, выполняет CI (`.github/workflows/ci.yml`).

## Язык

На Android 13+ язык приложения выбирается отдельно от системы: *Настройки → Приложения → Outfit
Share → Язык* или строка «Язык» в настройках самого приложения. На более старых версиях приложение
следует языку системы. Новые строки добавляйте в `values/` и `values-en/` — строка без перевода
считается ошибкой Lint.
