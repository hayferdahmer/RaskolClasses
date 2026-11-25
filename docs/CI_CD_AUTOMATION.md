# RASKOL CI/CD — карта автоматизации

Этот документ описывает действующие GitHub Actions и целевое распределение ответственности.
Имена контуров едины, но каждый workflow остаётся отдельным исполняемым процессом.

## Действующие контуры

| Контур | Workflow | Ответственность |
|---|---|---|
| **RASKOL Forge** | `.github/workflows/build.yml` | Java 25, Maven Verify, Spotless/SpotBugs quality gate, JaCoCo, подготовка и публикация официального релиза |
| **RASKOL Sentinel** | `.github/workflows/rips-security-auditor.yml` | Детерминированный аудит исходников RIPS и Java-тесты целостности, подписи и защиты от подмены |
| **RASKOL Steward** | `.github/workflows/license-maintenance.yml` | Проверка и исправление лицензионных заголовков только в отдельной ветке и через Pull Request |
| **RASKOL Chronicle** | `.github/workflows/badges.yml` | Пересчёт JSON-бейджей и предложение изменений через Pull Request |

## Разрабатываемые и встроенные контуры

| Контур | Статус | Ответственность |
|---|---|---|
| **RASKOL Arena | Combat CI Guard** | Архитектура в PR #72 и Arena-01 в PR #73; оба Draft и не интегрированы в main на срезе 2026-11-26 | Целевой контур combat diff audit, report/schema validation, headless deterministic scenarios и статистических регрессий; Maven/quality gates принадлежат Forge, Paper runtime — Watchtower |
| **RASKOL Archivist** | Проектируется в PR #60; fail-closed SafeStorage уже включён в main через PR #62 | Аудит YAML-хранилищ, snapshots, резервные копии, восстановление и безопасная очистка |
| **RASKOL Watchtower** | Встроен в Forge; Paper 26.2 Runtime успешно проверен на Java 25 | Отдельный job скачивает стабильный Paper 26.2 через Fill API, проверяет SHA-256, устанавливает собранный JAR и проверяет реальное включение плагина на Java 25 |

## Состояние консолидации workflow

На актуальном main остаются ровно четыре исполняемых файла: build.yml, rips-security-auditor.yml, license-maintenance.yml и badges.yml.

В PR #59 удалены прежние formatting-bot.yml, license-bot.yml и spotless-format.yml: их необходимые функции перенесены в Forge, Steward или заменены обязательной проверкой форматирования. Старые записи запусков в GitHub Actions сохраняются как история и не означают, что удалённые workflow продолжают работать на main.

Ветки PR #45–58 и #60 были созданы до консолидации и пока содержат старые копии этих файлов. Их собственные push-события ещё могут запускать старую версию build.yml с именем RaskolClasses Build. При последовательном обновлении Combat-цепочки относительно актуальной базы эти устаревшие копии должны исчезнуть; не переносить их обратно в main.


## Правила безопасности и сопровождения

1. Ни один бот не коммитит напрямую в `main`.
2. Автоматические исправления оформляются отдельными PR с ограниченным списком изменяемых файлов.
3. Секрет подписи RIPS доступен только release/signing jobs через защищённое GitHub Environment.
4. Обычные build/test jobs не получают приватный ключ подписи.
5. Критические quality/security gates не используют `continue-on-error`.
6. Существующие combat PR #52–58 остаются отдельной последовательной цепочкой; их нельзя переставлять или сливать в обход базовых PR.
7. Любой новый workflow должен иметь явные минимальные permissions, timeout, concurrency policy и понятные job names.
8. Удалять workflow можно только после переноса его уникальных проверок и подтверждения отсутствия оставшихся ссылок на него.

## Проверки PR от ботов

GitHub не запускает обычные `pull_request`/`push` workflows для событий, созданных стандартным `GITHUB_TOKEN`. Поэтому Steward и Chronicle после создания или обновления PR явно вызывают `workflow_dispatch` для Forge и Sentinel на bot-ветке. Так сохраняется безопасный `GITHUB_TOKEN` без PAT, а обязательные проверки выполняются на том же commit, который предложен к слиянию. Если dispatch завершился ошибкой, PR нельзя считать проверенным.

## Целевая последовательность

1. **Завершено:** консолидация Forge + Sentinel и удаление дублирующих quality/maintenance workflows в PR #59.
2. **В работе:** завершить согласованную последовательность боевых PR #76 → #77 → #79 → #80; PR #81 проходит отдельное ревью и требует проверки актуальности относительно нового main. Не менять PR #53–#58 без отдельного согласования.
3. **Завершено:** Watchtower встроен в Forge; Paper 26.2 Runtime проходит на Java 25.
4. После fail-closed исправления SafeStorage в PR #62 продолжить реализацию Archivist без смены YAML-формата и без автоматического удаления пользовательских данных.


## RASKOL Arena | Combat CI Guard

Архитектурная спецификация находится в PR #72, Arena-01 — в PR #73. На срезе 2026-11-26 оба PR остаются Draft и не входят в main.

- Forge: общий Maven Build/Test, Spotless/SpotBugs и release pipeline.
- Arena: combat diff audit, report/schema contracts, validators, headless deterministic scenarios и statistical comparison после утверждения baseline.
- Watchtower: Paper 26.2 runtime QA.

Не добавлять Arena в required checks и не менять пять текущих required contexts без отдельного решения. Pending baseline не является эталоном. Все 20 talent nodes остаются disabled; таланты исключены из Arena-01. PR #54–#58 не менять, не rebase и не merge в рамках этой документационной работы.

Связанные документы: [ROADMAP](ROADMAP.md), [Architecture Overview](ARCHITECTURE.md), [PR #72](https://github.com/hayferdahmer/RaskolClasses/pull/72), [PR #73](https://github.com/hayferdahmer/RaskolClasses/pull/73).
