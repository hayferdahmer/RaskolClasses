# Architecture Overview

**Срез:** 2026-11-26 · **Платформа:** Paper 26.2 + Java 25 · **Целевая версия:** 1.16.0

Это карта ответственности, а не утверждение, что каждый целевой модуль уже существует как отдельный package или полностью реализован.

## Источники истины

- Production Java-код и конфигурация определяют фактическое поведение.
- Unit tests фиксируют локальные контракты и регрессии.
- Paper Watchtower проверяет JAR в изолированном Paper 26.2 CI runtime.
- `docs/architecture/` содержит специализированные решения, аудиты, инварианты и миграционные контракты.
- [ROADMAP](ROADMAP.md) — последовательность работ; [DECISIONS](DECISIONS.md) — журнал решений; [DEVELOPMENT_STATUS](DEVELOPMENT_STATUS.md) — текущий срез.
- Ни один документ не заменяет проверку кода и CI на конкретном SHA.

## Слои и ответственность

### Platform & Bootstrap
Загрузка плагина, проверка зависимостей, создание сервисов, регистрация listeners/commands, enable/disable и shutdown.

### Domain / RPG Core
Персонаж, класс, специализация, уровень, опыт, атрибуты, ресурсы, экипировка и прогрессия. Доменную логику по возможности отделять от Bukkit/Paper API.

### Combat Core
Единый production pipeline физических и магических каналов: source attribution, модификаторы, hit/crit, mitigation, caps, применение результата и post-hit эффекты. Не создавать параллельные resolver/attribution механизмы без отдельного решения.

### Ability / Effects / Resources
Проверка доступности способности, resource cost, cooldown/GCD, контекст применения, эффекты, DoT/AoE/projectile, procs и семантика cancellation. Вложенные события сохраняют контекст источника и закрывают scope по LIFO.

### Character Progression & Specialization
Профиль персонажа, XP, уровень, Class Lock, специализации, деревья талантов и квестовые награды. Источник уровня — собственная RPG-прогрессия, не AuraSkills и не ванильный XP.

### Equipment
Загрузка/валидация item templates, PDC/affix, агрегация статов, gear sets, weapon mastery и cadence. Headless/CI использует production definitions, а не вторую копию предметных формул.

### Persistence & Storage
Безопасное чтение/запись YAML, схемы, атомарные snapshots, backup/restore, миграции и fail-closed. Переход к `yaml-single` обязан сохранить профили и specialization state.

### HUD & Presentation
RaskolHUD публикует read-only snapshots и UI-контракты. HUD не источник истины для уровня, ресурсов, урона или состояния персонажа; его отказ не ломает доменную/боевую логику.

### Integrity & Security
RIPS проверяет подпись, build identity и защищённые файлы при старте и периодически в runtime. Приватный ключ подписи доступен только защищённым release jobs.

## RASKOL Arena — validation plane, не второй runtime

Целевая цепочка: **Production Combat Core → Combatant Snapshot/Builder → Equipment & Ability contracts → Deterministic Scenario Engine → Metrics/Reports → Baseline Comparator**.

Headless слой может иметь виртуальные часы, очередь событий, изолированные RNG streams и тестовых агентов, но формулы должны делегироваться production контрактам.

- Forge: общий Maven build/test и статический анализ.
- Arena: diff audit, report/schema validation, headless deterministic scenarios и statistical comparison.
- Watchtower: запуск JAR в Paper 26.2 runtime.

Headless PASS, unit PASS и Paper Runtime PASS — разные доказательства. PR #72 (спецификация) и #73 (Arena-01) на дату документа Draft и не интегрированы в main.

## Архитектурные инварианты

1. Один источник истины для каждого доменного значения.
2. Не дублировать production-формулы в HUD, Arena, harness или интеграциях.
3. Явно определять владельца каждого task, listener, callback, resource и async completion.
4. Persistent state имеет определённую семантику отказа, атомарности и восстановления.
5. Event contexts корректно поддерживают вложенность, отмену и исключения.
6. Временные reservations завершаются ровно один раз и сверяются с фактически применённым результатом.
7. Внешние интеграции не являются источником истины для ключевой прогрессии.
8. HUD и диагностика читают состояние через стабильные контракты.
9. Runtime shutdown прекращает принадлежащие плагину задачи и callbacks.
10. Изменение контракта сопровождается тестами и обновлением документации.

## Контрольный список новой подсистемы

До реализации определить владельца состояния, входные/выходные контракты, thread boundaries, ownership tasks/callback/resources, persistence impact, cancellation/timeout/exception semantics, unit/integration/runtime/Arena tests, диагностику и связанные документы/этап ROADMAP.

Компиляция сама по себе не означает готовность подсистемы.

## Связанные документы

- [ROADMAP](ROADMAP.md)
- [DECISIONS](DECISIONS.md)
- [DEVELOPMENT_STATUS](DEVELOPMENT_STATUS.md)
- [CI/CD Automation](CI_CD_AUTOMATION.md)
- [Runbook](RUNBOOK.md)
- [Architecture details](architecture/)
- [Testing contracts](testing/)
