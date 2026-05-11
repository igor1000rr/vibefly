# Android-приложение VibeFly

Kotlin + Jetpack Compose. Точка входа всего продукта. Внутри: UI + Foreground Service + JNI-обёртка над namespace-runtime'ом, внутри которого живёт Debian + Go-агент.

## Статус

Каркас готов, компилируется, ставится на телефон. Реальная функциональность появится по фазам.

## Как собрать

Требуется Android Studio Ladybug Feature Drop (2024.2.2)+ или выше, JDK 17, Android SDK 35.

```bash
cd apps/android
# Первый раз: сгенерировать gradle-wrapper.
gradle wrapper --gradle-version 8.10.2
./gradlew assembleDebug
```

APK будет в `app/build/outputs/apk/debug/app-debug.apk`.

## Пакеты

- `by.vibefly.app` — Application, MainActivity
- `by.vibefly.app.service` — Foreground Service, BootReceiver, notification channels
- `by.vibefly.app.runtime` — JNI-обёртка над namespace-runtime
- `by.vibefly.app.agent` — Ktor-клиент к Go-агенту на 127.0.0.1:3001
- `by.vibefly.app.ui` — NavHost + bottom navigation
- `by.vibefly.app.ui.theme` — Material 3 theme + brand colors
- `by.vibefly.app.ui.screens` — dashboard, chat, marketplace, settings, app detail

## Экраны

| Экран | Статус | Фаза |
|---|---|---|
| Dashboard (health + apps) | Скелет с фейк-данными | 1 |
| App detail | TODO | 1 |
| Chat (AI) | TODO | 4 |
| Marketplace | TODO | 2 |
| Settings | TODO | 1–2 |
| Onboarding | TODO | 2 |

## Дизайнерские решения

- **Edge-to-edge** включён через `enableEdgeToEdge()`.
- **Dynamic color** (Material You) включен для Android 12+, fallback на brand-палитру.
- **Бренд-цвета:** зелёный (running, success), индиго (links, actions), фиолетовый (AI accent).

## TODO ближайшее

- [ ] gradle-wrapper.jar (добавится локально через `gradle wrapper` при первой сборке)
- [ ] gradlew/gradlew.bat
- [ ] Реальная реализация RuntimeManager (JNI к Droidspaces / proot)
- [ ] AgentClient методы /health, /apps, /apps/{id}/restart
- [ ] WebSocket-подписка на логи
- [ ] Storage permission flow для загрузки rootfs.img
