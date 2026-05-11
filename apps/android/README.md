# Android-приложение VibeFly

Kotlin + Jetpack Compose. Точка входа всего продукта. Внутри: UI + Foreground Service + JNI-обёртка над namespace-runtime'ом, внутри которого живёт Debian + Go-агент.

## Статус

Каркас готов, компилируется, ставится на телефон. Dashboard живой, подключается к Go-агенту на 127.0.0.1:3001 и тянет реальные метрики + список приложений.

## Как собрать

Требуется Android Studio Ladybug Feature Drop (2024.2.2)+ или выше, JDK 17, Android SDK 35.

```bash
cd apps/android
# Первый раз: сгенерировать gradle-wrapper.
gradle wrapper --gradle-version 8.10.2
./gradlew assembleDebug
```

APK будет в `app/build/outputs/apk/debug/app-debug.apk`.

## Для живого Dashboard нужно запустить агента

До фазы 2 (когда агент будет жить внутри APK) — запускай его вручную на десктопе/VPS:

```bash
cd ../../apps/agent
make run
```

И пропиши в `AgentClient.DEFAULT_BASE_URL` адрес десктопа в локальной сети (напр. `http://192.168.1.10:3001`) — или запусти эмулятор и используй `http://10.0.2.2:3001`.

В фазе 2 это решится автоматически — агент будет жить в namespace-runtime внутри APK на 127.0.0.1:3001.

## Пакеты

- `by.vibefly.app` — Application, MainActivity
- `by.vibefly.app.service` — Foreground Service, BootReceiver, notification channels
- `by.vibefly.app.runtime` — JNI-обёртка над namespace-runtime (заглушки до фазы 2)
- `by.vibefly.app.agent` — Ktor-клиент к Go-агенту + DTO
- `by.vibefly.app.data` — репозитории + ServiceLocator
- `by.vibefly.app.ui` — NavHost + bottom navigation
- `by.vibefly.app.ui.theme` — Material 3 theme + brand colors
- `by.vibefly.app.ui.screens` — dashboard (живой), chat/marketplace/settings/app detail (заглушки)

## Экраны

| Экран | Статус | Фаза |
|---|---|---|
| Dashboard (health + apps) | Живой | Сейчас |
| App detail | TODO | 1 |
| Chat (AI) | TODO | 4 |
| Marketplace | TODO | 2 |
| Settings | TODO | 1–2 |
| Onboarding | TODO | 2 |

## Дизайнерские решения

- **Edge-to-edge** включён через `enableEdgeToEdge()`.
- **Dynamic color** (Material You) включен для Android 12+, fallback на brand-палитру.
- **Бренд-цвета:** зелёный (running, success), индиго (links, actions), фиолетовый (AI accent).
- **Network security config** пускает cleartext только на 127.0.0.1/localhost.

## TODO ближайшее

- [ ] gradle-wrapper.jar (добавится локально через `gradle wrapper` при первой сборке)
- [ ] gradlew/gradlew.bat
- [ ] Реальная реализация RuntimeManager (JNI к Droidspaces / proot)
- [ ] AppDetail с лайв-логами (WebSocket)
- [ ] Onboarding wizard и storage permission flow для загрузки rootfs.img
- [ ] Settings: хранение baseUrl и token в EncryptedSharedPreferences
