# Как контрибьютить в VibeFly

Пока проект на ранней стадии и репозиторий приватный. После публичного релиза (фаза 3 в [ROADMAP.md](ROADMAP.md)) этот документ будет дополнен.

## Текущий процесс

- Все коммиты пушатся прямо в `main` (репозиторий приватный, один разработчик).
- Сообщения коммитов — на русском, формат [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`).
- Код — английский (имена переменных, функций, типов).
- Комментарии, README, документация — русский.

## После публичного релиза

- Любые изменения через Pull Request в ветку `develop`.
- Сквошить мерж в `main` при релизе.
- Issues — на английском (или русском, если автор предпочитает).
- Code review обязателен для всех PR от внешних контрибьюторов.
- CLA (Contributor License Agreement) — да, потребуется для возможной двойной лицензии (AGPL + коммерческая).

## Стиль кода

- **Kotlin (apps/android):** `ktlint` defaults.
- **Go (apps/agent):** `gofmt` + `golangci-lint`.
- **TypeScript (apps/cloud):** Prettier + ESLint (Next.js defaults).
- **Shell (runtime/rootfs-builder):** `shellcheck` clean.

## Контакты

[@igor1000rr](https://t.me/igor1000rr)
