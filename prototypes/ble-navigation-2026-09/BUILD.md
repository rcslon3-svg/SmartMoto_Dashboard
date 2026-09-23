# Сборка из сохранённых исходников

Правило пользователя: применять **только проверенные способы сборки**. Команды ниже взяты из выполненных на исходной рабочей станции сборок. Копирование исходников в этот репозиторий проверено хешами, но сборка непосредственно из нового каталога не выполнялась. Не считать успешную компиляцию проверкой поведения Xiaomi или экрана.

## Окружение

- Windows PowerShell, Android Studio JBR: `C:\Program Files\Android\Android Studio\jbr`.
- Android SDK исходной станции: `C:\Users\LENOVO\AppData\Local\Android\Sdk`. Локальный `android/local.properties` содержит `sdk.dir` и **не хранится в Git**. В новом checkout его нужно создать под свои пути до сборки Android.
- Проверенный Gradle 9.2.1 находится в кэше `C:\Users\LENOVO\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat`; Android Gradle Plugin 9.0.1 требуется из кэша либо из официальных репозиториев.
- PlatformIO `espressif32@6.10.0` для CYD и `espressif32@6.12.0` для Waveshare; точные зависимости закреплены в `platformio.ini`.

В примерах ниже текущий каталог — `prototypes/ble-navigation-2026-09/source`.

## Android-мост и диагност CYD

Из `source/cyd-companion-v2/android` команда, сохранённая в исходном [HANDOFF](source/cyd-companion-v2/HANDOFF.md):

```powershell
.\build-local.bat --no-daemon --gradle-user-home ../../ble-internet-demo/android/.gradle-home assembleDebug lintDebug
```

Она собирает модули `app`, `diagnostics` и `navprobe`. Для локальных протокольных проверок из `source/cyd-companion-v2`: `./test-local.ps1`. Часть старых Robolectric-тестов `IpcResultsTest` ранее не прошла; не объявлять полный набор тестов зелёным по результату сборки.

## NavProbe Google Maps → круглый дисплей

Проверенная на исходной станции команда из `source/cyd-companion-v2/android`:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
& 'C:\Users\LENOVO\.gradle\wrapper\dists\gradle-9.2.1-bin\2t0n5ozlw9xmuyvbp7dnzaxug\gradle-9.2.1\bin\gradle.bat' :navprobe:assembleDebug :navprobe:lintDebug
```

Использовать то же окружение с доступом к установленным кэшам. Обычный изолированный запуск этой команды не находил Android Gradle Plugin; переключение на `gradlew.bat` также пыталось заново скачать Gradle. APK появляется в `navprobe/build/outputs/apk/debug/` и должен быть подписан тем же сертификатом для обновления поверх установленной версии. `apksigner verify --print-certs` проверяет подпись, но не работу на телефоне.

## Прошивка CYD

Из `source/ble-internet-demo/firmware` проверенный проект PlatformIO: `pio run`. Исходная инструкция также содержит `pio run -t upload --upload-port COMx`. Записывать только после проверки модели и фактического COM-порта; исторический CYD был на COM4. Резервный образ старой flash в Git **не включён**.

## Waveshare и круглый GC9A01

Из каталога `source` применялись:

```powershell
pio run -d waveshare-round-display -e waveshare_s3_147b
pio run -d waveshare-round-display -e waveshare_s3_147b -t upload --upload-port COM5
```

Вторая команда была проверена на Waveshare с MAC `A4:CB:8F:DA:D3:B0` и COM5 на исходной станции. COM5 не является постоянной характеристикой платы: перед повторной загрузкой снова проверить порт и MAC. `esptool` подтвердил хеши записи; Serial показал цикл одометра. Это не доказывает устойчивость BLE на телефоне.

## Локальный сервер

Из `source/ble-internet-demo/server`: `node server.mjs` (Node.js 20+). Он импортирует соседний каталог `source/cyd-companion-v2/server`. Рабочая директория `server/data/` создаётся локально и не входит в Git. По умолчанию сервер слушает `0.0.0.0:8787`: использовать только в доверенной локальной сети, не публиковать в Интернет без отдельной защиты.
