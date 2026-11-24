# © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
# SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

# RASKOL — политика обязательных GitHub checks для main

## Статус

Документ описывает целевую конфигурацию защиты `main`. Это не подтверждение того, что правила уже применены в настройках GitHub.

На момент аудита:
- ветка `main` возвращает `protected: false`;
- доступный ruleset защищает release tags, но не ветку `main`;
- интеграция GitHub не имеет прав на чтение/изменение Branch Protection;
- PR #66, #67 и #68 должны оставаться отдельными review-единицами и не объединяются этим документом.

## 1. Обязательные проверки

После включения соответствующих workflow и проверки bot-процессов для каждого PR в `main` должны требоваться ровно следующие пять контекстов:

| Required check | Workflow | Зачем обязателен |
|---|---|---|
| `Build & Test (Java 25)` | RASKOL Forge | Компиляция, unit tests, Maven verify и license gate |
| `Quality Gate (Spotless + SpotBugs)` | RASKOL Forge | Форматирование и статический анализ |
| `RASKOL Watchtower | Paper 26.2 Runtime` | RASKOL Forge | Проверка реального запуска плагина на целевой Paper |
| `Integrity & Tamper Audit` | RASKOL Sentinel | RIPS source audit и Java integrity/security tests |
| `Storage Integrity Tests (Java 25)` | RASKOL Archivist | SafeStorage, миграции, writer lifecycle и архитектурные инварианты |

Имена необходимо выбирать в GitHub Ruleset из фактически появившихся check runs, а не вводить по памяти. Источник каждого required check — GitHub Actions.

### Не делать обязательными

- `RIPS Official Signing Check` — штатно пропускается в обычных PR; относится к release/signing flow.
- `Signed Release Publication` — публикация по тегу, не PR gate.
- `RASKOL Chronicle | Live Badges` — обновление метаданных через PR, не проверка качества кода.
- `RASKOL Steward | License Maintenance` — ручной maintenance workflow, а не gate для каждого PR.

Пропуск релизных jobs не должен блокировать обычный PR.

## 2. Условия, при которых check всегда появляется

Required check должен запускаться для каждого PR в `main`. Иначе GitHub может оставить обязательный контекст в состоянии Pending, когда workflow не был запущен из-за path filter.

Поэтому:
- Forge запускается для всех PR в `main`;
- Sentinel должен запускаться для всех PR в `main`, а не только для PR, меняющих заранее перечисленные RIPS-файлы;
- Archivist должен запускаться для всех PR в `main`; push на `main` может оставаться path-filtered;
- release-only jobs остаются условными и не входят в required set.

Archivist — короткая целевая матрица тестов. Её запуск на всех PR даёт единый стабильный check context, пригодный для защиты ветки.

## 3. PR, созданные через GITHUB_TOKEN

PR, созданные Chronicle и Steward через `GITHUB_TOKEN`, не запускают обычные `pull_request` workflows автоматически. Поэтому оба workflow должны вручную dispatch-ить все три проверочных workflow на head-ветку созданного PR:

1. `build.yml`
2. `rips-security-auditor.yml`
3. `archivist-storage.yml`

Dispatch должен использовать именно head branch PR. Само принятие команды dispatch не является успешной проверкой: merge разрешается только после завершения всех пяти required checks на том же head SHA.

## 4. Рекомендуемые настройки ruleset для main

Создать отдельный активный ruleset, нацеленный только на `refs/heads/main`.

- Require a pull request before merging: **ON**.
- Required approvals: **0 на первом этапе**, чтобы не заблокировать единственного maintainer; пересмотреть после появления второго reviewer.
- Require status checks to pass: **ON**, пять контекстов из раздела 1.
- Require branches to be up to date before merging: **ON** после подтверждения стабильности всех пяти workflows.
- Block force pushes: **ON**.
- Restrict deletions: **ON**.
- Require conversation resolution: **ON**.
- Require signed commits: **OFF** на первом этапе; подпись release artifacts уже отдельно контролирует RIPS.
- Required deployments: **не задавать**, пока нет отдельного deployment environment и approval workflow.
- Bypass list: **пустой** для обычной работы. Аварийный обход, если он когда-либо понадобится, должен быть ограничен владельцами и документирован отдельно.

Не добавлять обязательные reviewers или CODEOWNERS до появления реально назначаемых reviewer-ов.

## 5. Безопасный порядок включения

1. Проверить и отдельно разрешить слияние PR #66: он устраняет дублирующие feature-branch push runs Forge/Sentinel.
2. Проверить PR #68: Archivist должен запускаться на каждом PR, а все storage tests и отчёты должны завершаться успешно.
3. Проверить PR #69: Sentinel запускается на каждом PR; Chronicle и Steward dispatch-ят Archivist вместе с Forge и Sentinel.
4. После слияния workflow-изменений создать тестовые PR Chronicle и Steward и дождаться всех пяти required checks на одном head SHA.
5. Только после успешной проверки bot flows включить ruleset для `main`.
6. Проверить отрицательный сценарий: PR с намеренно failing check не должен быть доступен к merge.
7. Проверить положительный сценарий: PR с пятью успешными checks может быть объединён обычным способом.

Не включать ruleset, пока хотя бы один из пяти контекстов не доказал, что появляется на обычном PR и на bot-generated PR.

## 6. Границы полномочий

На момент аудита GitHub integration получает отказ при чтении Branch Protection и не может безопасно применить ruleset. Поэтому данный этап готовит проверенную конфигурацию и необходимые workflow-изменения, но не утверждает, что защита `main` уже включена.

Применение правил выполняется владельцем репозитория в GitHub Settings → Rules → Rulesets (или Branch protection rules). После применения необходимо повторно прочитать настройки и проверить фактические required contexts через GitHub API.

Ни один из PR #66–#69 не объединяется автоматически. Для слияния каждого требуется отдельное разрешение владельца репозитория.
