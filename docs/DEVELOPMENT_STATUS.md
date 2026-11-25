# Текущий статус разработки

**Срез:** 2026-11-26  
**Ветка:** `main` · **HEAD:** `0d9b17ac6b42ceb11ac7c17430f6b6eb9dcae2b3`  
**Версия:** `1.16.0-SNAPSHOT` · **Последний официальный релиз:** `v1.15.8`

## Mainline

- PR #76, #77, #79 и #80 интегрированы по согласованной последовательности.
- PR #80 merge commit: `0d9b17ac6b42ceb11ac7c17430f6b6eb9dcae2b3`.
- Post-merge Build & Release run `45712784817`: success на этом SHA.
- Live Badges run `45712784819`: success.
- Paper 26.2 Watchtower для #80 прошёл на PR HEAD до merge; это изолированный CI runtime, не production-сервер.

## Открытые потоки

| Направление | Статус | Следующий контроль |
|---|---|---|
| Combat / burst settlement | PR #81 Draft; head `a61ccb383d19634f09e2ccb6b79c969ee3df8ca0` | Проверить ancestry/diff относительно merge #80; не синхронизировать ветку автоматически |
| Arena architecture | PR #72 Draft, не в main | Сверить спецификацию с актуальным main |
| Arena-01 CI | PR #73 Draft, не в main; CI на последнем HEAD не подтверждён | Повторный CI на актуальном HEAD после согласованной синхронизации |
| Combat Lab chain | PR #53–#58 | Не менять ветки, базы и содержимое без отдельного разрешения |
| Lifecycle ownership | Специализированные аудиты в docs/architecture | Закрывать конкретные ownership пробелы |
| Storage 1.16.x (target 1.16.0) | characters.yml и spec2-storage.yml пока раздельны | Спроектировать и проверить миграцию к yaml-single |
| Class Lock | Код в main | Live-проверка на конфигурации LuckPerms |
| Soul Tome | Цель replace | Проверить замену группы и rollback |
| RPG Core | Уровень 1–60 и PvE XP в main | Storage, specialization integration, release gates |

## Release gates серии 1.16.x

- **1.16.0 RPG Foundation:** миграция к `yaml-single`, сохранность данных, интеграционные проверки основы прогрессии и подтверждение официального артефакта.
- **1.16.1 Class System:** live-проверка и окончательная валидация Class Lock на реальной конфигурации LuckPerms.
- **1.16.4 Soul Tome & Equipment:** завершение и проверка механики Soul Tome `replace` и интеграции экипировки.

Каждый патч проходит собственные CI и применимые runtime-проверки. См. [ROADMAP](ROADMAP.md) и [Runbook](RUNBOOK.md).

## Правило обновления

После значимого merge/CI указывать commit SHA, PR state, точный CI run и conclusion, среду runtime (Watchtower CI или реальный сервер), блокеры и следующее разрешённое действие. Не переносить статус PR в mainline до merge и не писать PASS без подтверждения run + SHA.
