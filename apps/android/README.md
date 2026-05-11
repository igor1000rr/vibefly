# Android-приложение

Kotlin + Jetpack Compose APK. Обёртка над namespace-runtime + UI поверх агента.

## Статус

Не начат. Планируется к фазе 2.

## Package name

`by.vibefly.app`

## Минимальные требования

- Android 10 (API 29) +
- 4 ГБ RAM минимум, 6+ рекомендуется
- 16 ГБ свободного интернал storage
- Root (KernelSU-Next recommended) для полного функционала; без root — degraded mode

## Ключевые экраны (из мокапов)

1. Onboarding (диагностика устройства, выбор runtime, Cloudflare привязка)
2. Dashboard (метрики устройства, список apps)
3. App detail (лайв-логи, env, deploys)
4. AI Chat
5. Marketplace
6. Settings

## Как собрать (позже)

```bash
./gradlew assembleDebug
# или
./gradlew bundleRelease
```
