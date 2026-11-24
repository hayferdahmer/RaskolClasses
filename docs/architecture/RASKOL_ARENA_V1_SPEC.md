# RASKOL Arena v1 — архитектурная спецификация

© 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

**Статус:** проект архитектуры; требует согласования до реализации Arena-01.  
**Платформа:** Paper 26.2, Java 25.  
**Связанные области:** RASKOL Forge, Sentinel/RIPS, Standard, Archivist, Watchtower.  
**Область аудита:** PR #50, #53–#58 и их фактические изменения workflow/Combat Lab.

## 1. Назначение и архитектурный принцип

RASKOL Arena — единый контур проверки боевых регрессий, воспроизводимых headless-сценариев, статистического баланса и реального Paper runtime.

Arena не является вторым боевым движком. Она должна использовать production-формулы и проверяемые production-контракты. Headless-модель может иметь собственные виртуальные часы, очередь событий, RNG и тестовых агентов, но не должна повторно реализовывать формулы урона, атрибутов, сопротивлений, мастерства и эффектов.

Headless PASS и Paper Runtime PASS — независимые результаты. Ни один из них не подменяет другой.

Arena не включает автоматически способности или таланты и не изменяет игровой баланс. Она только проверяет заданные сценарии и формирует доказательные отчёты.

## 2. Зафиксированные ограничения

- Java 25 и Paper 26.2.
- 6 классов и 18 специализаций.
- Существующие PR #54–#58 остаются исходным материалом; их базы, содержимое и состояние не изменять в рамках Arena-01.
- Не выполнять массовый rebase, смену base branch или merge PR #54–#58.
- Все 20 talent nodes остаются disabled до отдельных формульных, event-level, balance и runtime gates.
- Не менять игровые значения, конфиги баланса и экипировку в рамках консолидации CI.
- Forge остаётся владельцем Maven build/test и статического анализа.
- Watchtower остаётся владельцем реального Paper 26.2 runtime QA.
- Архитектурные и кодовые изменения проводить отдельными PR.

## 3. Аудит Combat Lab: подтверждённая картина

### 3.1. PR-цепочка

| PR | Содержание по изменённым файлам | Текущий статус на момент аудита |
|---|---|---|
| #50 | `.github/workflows/combat-ci.yml`, diff-аудитор, концепция headless/runtime QA | Open, Draft; отдельная ветка от `feat/v1.16-remaining-talent-handlers` |
| #53 | второй вариант combat workflow, JSON schema статистического отчёта, comparator, comparator tests, pending baseline | Open, Draft; база — #58 predecessor branch `feat/v1.16-combat-source-context` |
| #54 | headless combatant snapshot/duel engine/runner, resist integration, probe schema/validator и тесты | Open, Draft; база — #53 |
| #55 | equipment runner/snapshot, production item loader/validator integration, headless tests и equipment report validator | Open, Draft; база — #54 |
| #56 | weapon cadence, Paper cadence resolver, WeaponMastery integration, cadence tests | Open, Draft; база — #55 |
| #57 | 18 specialization-locked gear sets, item template expansion, equipment tests/report validation | Open, Draft; база — #56 |
| #58 | ability-scoped damage attribution, talent consumers, pet math/task runtime, ranged crit, tests и talent audit docs | Open, Draft; база — #57 |

Примечание: PR #50 имеет отдельную базу и не является предшественником последовательной цепочки #53–#58. Не считать номера PR доказательством линейной зависимости. Все PR были открыты и не объединены на момент аудита.

### 3.2. Реально подтверждённые артефакты

- #50: diff-based аудит боевых изменений и наличие сопровождающих тестов; документация разделяет headless QA и Paper runtime smoke.
- #53: версия JSON report schema 1.0; comparator с проверками метрик/CI95/совместимости; тесты comparator; baseline v1.16.0 со статусом pending. Comparator требует минимум 1200 итераций на сценарий и не разрешает сравнение с pending baseline.
- #54: headless snapshot/duel engine/runner; тесты `CombatLabDuelEngineTest`; отдельная probe schema, validator и validator tests.
- #55: equipment snapshot/runner; адаптация загрузчика и валидатора предметов для headless; тесты snapshot и загрузчика.
- #56: `WeaponCadence`, `PaperWeaponCadenceResolver`, интеграция WeaponMastery; unit tests cadence и обновлённая проверка equipment report.
- #57: 18 specialization-locked gear sets, item template expansion; расширенные tests engine/snapshot/loader и equipment report validation.
- #58: ability/source context scope, ranged crit routing, часть talent consumers и pet math/task abstractions; профильные unit tests и документы статуса 20 talent nodes.

### 3.3. Ограничения текущего доказательства

Наличие файла или unit test не подтверждает завершённость игровой функции. В частности:

- #53 задаёт контракт comparator, но baseline остаётся pending; полноценное сравнение реальных headless-отчётов не доказано.
- #54 добавляет отдельный probe report contract; его нужно согласовать с общим статистическим report, не удаляя ранний контракт до миграции.
- #55–#57 подтверждают equipment-aware путь и 18 комплектов, но не полную ability/talent-aware симуляцию.
- #56 содержит Paper cadence resolver, но это не доказывает паритет всего боевого event pipeline.
- #58 добавляет только часть consumers и source attribution. В документации #58 зафиксированы остаточные пробелы: pet attack controller, party/faction resolver, Rogue combo-point resource, offhand attribution, actual-heal semantics, универсальный TrapRegistry и полная event matrix.
- Все 20 talent nodes остаются disabled.
- Paper Runtime combat matrix не считается завершённой только на основании headless tests.

## 4. Аудит CI и решение о каноническом workflow

### 4.1. Текущее состояние

В main на момент аудита существует `.github/workflows/build.yml` с именем **RASKOL Forge | Build & Release**. В нём уже выполняются:

- `mvn -B verify` — Build & Test (Java 25);
- отдельный Maven quality job с `mvn -Pdev -B -DskipTests verify` — Spotless/SpotBugs и lint;
- Watchtower runtime job на Paper 26.2.

Файл `.github/workflows/combat-ci.yml` в main отсутствует.

PR #50 добавляет первый вариант `combat-ci.yml`. В нём выполняются `mvn -B verify` и `mvn -Pdev -B verify`, то есть повторяются Build/Test и quality gates Forge; второй вызов также не использует `-DskipTests`.

PR #53 меняет тот же workflow: добавляет unit tests diff-аудитора, comparator tests и validate-only проверку baseline. Он явно сообщает, что live statistical comparison остаётся PENDING до production headless runner и approved baseline.

В изменённых файлах PR #54 и #57, а также #58 присутствует `.github/workflows/combat-ci.yml`; PR #55 и #56 workflow непосредственно не меняют, но наследуют workflow из своих базовых веток. Это создаёт риск расхождения копий workflow между независимыми ветками.

### 4.2. Каноническое решение

Каноническим остаётся **один** `.github/workflows/combat-ci.yml`, принадлежащий RASKOL Arena.

Он НЕ должен повторять:
- Maven compile/package/test, уже принадлежащие Forge;
- Spotless/SpotBugs/lint, уже принадлежащие Forge;
- Paper startup/runtime smoke, уже принадлежащий Watchtower.

Arena отвечает за:
1. обнаружение боевых изменений и проверку соответствующих тестовых контрактов;
2. тесты собственных Python-инструментов и JSON contracts;
3. профильные combat unit tests, если они не дублируют обязательный общий Maven suite; предпочтительно использовать уже созданные результаты Forge либо отдельный узкий Maven test selection только при технической обоснованности;
4. headless smoke/regression runner после его интеграции;
5. валидацию report/schema и baseline contract;
6. statistical comparison только когда есть production-generated report и approved baseline;
7. понятный summary и воспроизводимые артефакты.

Forge остаётся источником истины для общего Maven Build/Test и Quality Gate. Watchtower остаётся источником истины для Paper runtime.

### 4.3. Required-check совместимость

Текущая политика main содержит пять required contexts:
- Build & Test (Java 25);
- Quality Gate (Spotless + SpotBugs);
- RASKOL Watchtower | Paper 26.2 Runtime;
- Integrity & Tamper Audit;
- Storage Integrity Tests (Java 25).

Новый Arena check должен иметь стабильное имя и запускаться на каждом PR в main, чтобы не оставаться Pending из-за path filters. Для небоевых PR он должен завершаться успешным явным результатом «combat changes not detected / not applicable», сохраняя обязательную проверку существования workflow.

До появления фактического и стабильного Arena context не добавлять его в required checks. Не менять пять действующих required contexts в рамках Arena-01.

### 4.4. Diff-аудитор

Существующий `tools/combat-ci/check_combat_diff.py` — основа, но его контракт нужно проверить на:
- PR merge-base и push before/after semantics;
- начальные/нулевые SHA;
- удаления/переименования;
- изменения конфигураций, item templates, talent definitions и ability registries;
- изменение production боевого кода без соответствующего теста;
- изменение тестов без боевого кода;
- корректный статус для небоевого PR.

Тесты самого аудитора обязательны. Проверка не должна требовать изменения тестового файла для каждого косметического изменения; необходима проверка покрытия соответствующего контракта, а не просто факт изменения любого теста.

## 5. Контракты входа и выхода

### 5.1. Входной контракт Arena

Каждый запуск получает:
- source commit SHA;
- base/merge-base SHA и список изменённых файлов;
- Java 25 / Paper target metadata;
- версию Arena runner и schema;
- hash конфигурации и предметных данных;
- профиль запуска;
- scenario set ID/version;
- root seed и параметры итераций;
- версию агента/стратегии;
- статус и идентификатор baseline.

Никакие параметры нельзя неявно брать из изменяемого глобального состояния во время одного прогона.

### 5.2. Выходной контракт

Целевой единый report должен содержать:
- schema version и engine version;
- profile (probe, equipment, PvP, PvE, runtime);
- source commit и hashes входных данных;
- scenario ID/version;
- seed и количество итераций;
- participants и их class/spec/level/equipment/talents;
- metrics с единицами измерения;
- sample count и CI95 method/interval там, где применимо;
- invariant violations;
- baseline ID и comparison result;
- reproducibility metadata;
- общий результат PASS/FAIL/BLOCKED/SKIPPED.

Существующие probe report и statistical report не удалять в Arena-01. Сначала документировать различия и определить версионированную миграцию к единой схеме. Equipment report validator также сохранить до миграции.

### 5.3. Baseline

Файл v1.16.0 с `status: pending` — корректная блокировка, а не утверждённый эталон. Нельзя создавать/обновлять baseline из результатов текущего PR автоматически. Approved baseline требует production runner, стабильного набора сценариев, повторяемости, ручной проверки аномалий и привязки к полному commit SHA.

## 6. Production Combat Core — целевая архитектура

Arena должна использовать единый production pipeline. Не создавать независимые Arena-версии формул.

Целевые модули:
- Combat Core: damage profile, mitigation, crit, penetration, avoidance, caps;
- Combatant Builder: immutable snapshot класса/специализации/уровня/характеристик/ресурсов;
- Equipment Engine: production item definitions, stat aggregation, sets, affixes, weapon mastery/cadence;
- Ability Engine: validation, resource cost, GCD/cooldown, effects, projectile/DoT/AoE;
- Talent Engine: node/rank/prerequisites/consumer/status;
- Pet Engine: independent combatant state, attack cadence, effects, owner attribution;
- Simulation Engine: virtual clock, deterministic event queue, isolated RNG streams;
- Scenario Engine: PvP/PvE/boss phases and agent strategies;
- Statistics/Regression: metrics, CI95, baseline comparison and reproducible reports.

Целевые Java API (эскизы, не существующие сигнатуры):

```java
public interface CombatResolver {
    DamageResult resolve(DamageRequest request);
}

public interface AbilityExecutor {
    AbilityExecutionResult execute(
        CombatantState caster,
        CombatTarget target,
        AbilityDefinition ability,
        SimulationContext context
    );
}

public interface CombatRandom {
    double nextDouble(String streamId);
    int nextInt(String streamId, int bound);
}
```

Фактические сигнатуры должны быть спроектированы после сопоставления существующих production классов и Combat Lab. Не создавать второй `DamageSourceContext` или параллельный resolver.

## 7. Канонический порядок обработки боевого события

Целевой порядок, подлежащий сверке с существующими Paper listener priorities и вызовами:

1. Preflight: участники, цель, дистанция, видимость, PvP/PvE/friendly-fire и иммунитеты.
2. Ability validation: класс/spec/rank, GCD, cooldown, ресурс.
3. Resource reservation и явно определённая семантика отмены.
4. Source attribution: attacker/owner/pet/projectile/ability/effect/proc parent.
5. Base effect calculation.
6. Outgoing modifiers: stats, equipment, mastery, talents, buffs.
7. Hit resolution: dodge/parry/block.
8. Critical resolution.
9. Mitigation: penetration, armor/resists, vulnerability/immunity.
10. Caps и anti-one-shot.
11. Apply result.
12. Post-hit effects: lifesteal, DoT, on-hit, procs, resource generation.
13. Telemetry и immutable event result.

Инварианты: бонус применяется ровно один раз; DoT не повторяет исходные on-hit эффекты без явного контракта; отражение не образует бесконечную цепь; proc depth ограничен; source attribution сохраняется при вложенных событиях.

## 8. Метрики и сценарии

Минимальные метрики:
- win/draw/timeout rate, TTK, remaining HP, deaths;
- total/mean/median/p10/p90/peak DPS;
- burst за 1/3/5 секунд;
- HPS, effective heal, overheal;
- damage taken, mitigation, dodge/parry, CC uptime;
- casts, cast success/cancel, cooldown waste, resource waste;
- proc observed/expected rate и contribution;
- pet damage/heal share, attack interval, uptime;
- source/type damage breakdown.

Доли публикуются с sample count и доверительным интервалом. Для сравнения используются одинаковые seeds и парное сравнение сценариев. Пороговые значения должны быть утверждены после baseline, а не выбраны произвольно.

Матрица уровней: 1, 14, 15, 30, 40, 45, 60. Все 18 специализаций. PvP: зеркальные и направленные пары, несколько стратегий. PvE: физический/магический/смешанный противник, резисты, иммунитеты, контроль, boss phases/adds. Экипировка: без предметов, базовая, полный комплект, сет-бонусы, аффиксы, недоступные предметы. Talent tests покрывают все 20 узлов, но не включают их.

## 9. CI профили

- **PR Quick:** diff audit, инструментальные tests, report/schema validation, быстрый headless smoke после появления runner. Каждый PR получает стабильный результат.
- **Combat Regression:** все 18 специализаций, контрольные уровни, оборудование/способности/таланты, несколько фиксированных seeds.
- **Nightly/Manual Full:** расширенная PvP/PvE матрица, длинные сценарии, несколько seeds, полные артефакты.
- **Release Candidate:** утверждённый baseline, полный headless regression и отдельный Paper Runtime QA через Watchtower.

Артефакты: JSON report, Markdown summary, CSV scenario results, baseline diff, reproduction seeds, event trace для воспроизводимых ошибок.

## 10. Этап Arena-01 — Combat CI Consolidation

Arena-01 ограничен CI, документацией контрактов и тестами инструментов. Не менять игровые боевые классы, конфиги, предметы, таланты или ветки PR #54–#58.

Обязательные задачи:
1. Сверить workflow #50 и #53 и все изменения workflow в #54–#58.
2. Создать один канонический workflow на основе проверенных возможностей обоих вариантов.
3. Удалить из Arena повтор Maven verify, Spotless/SpotBugs и Paper runtime.
4. Сохранить diff audit и добавить регрессионные тесты его edge cases.
5. Сохранить schema/comparator/baseline validation tests из #53.
6. Зафиксировать миграционный контракт probe/equipment/statistical reports.
7. Сделать workflow запускаемым на каждом PR в main без required-check Pending из-за path filters.
8. Добавить корректный no-combat-changes outcome.
9. Сформировать артефакты/summary без ложного сообщения о выполненном статистическом сравнении.
10. Убедиться, что общий набор Java tests продолжает запускаться Forge и что Arena не снижает покрытие.

Acceptance:
- один канонический `combat-ci.yml`;
- ни одного дублирующего Maven/Spotless/SpotBugs/Watchtower job;
- стабильный check на боевых и небоевых PR;
- tests diff auditor/comparator/schema validators проходят;
- pending baseline остаётся блокирующим для statistical comparison;
- ни один PR #54–#58 не изменён, не rebased, не переключён на другую базу и не merged;
- отчёт CI явно различает проверку контракта и фактический статистический прогон.

## 11. Последующие этапы

1. Arena-01 — Combat CI Consolidation.
2. Отдельный архитектурный PR — Production Combat Core contracts и parity plan.
3. Combatant/Equipment snapshots.
4. Ability Simulation.
5. Talent/Pet integration.
6. PvP/PvE scenario engine.
7. Statistical Balance Gate и утверждение baseline.
8. Paper Runtime integration и release readiness.

Параллельная реализация допускается только после утверждения контрактов и явных зависимостей. Нельзя начинать полноценную балансировку до подтверждения Production Combat Core.

## 12. Definition of Done для Arena v1

- Headless использует production combat formulas.
- Runtime/headless parity подтверждён на общих сценариях.
- Все 18 специализаций покрыты.
- Оборудование и 20 talent nodes имеют проверяемый статус; отключённые таланты не активируются.
- Abilities, pets, CC/DR, DoT, AoE и projectiles покрыты.
- PvP/PvE/boss сценарии воспроизводимы.
- Есть единый версионированный report и утверждённый baseline.
- Есть статистический gate с CI95 и понятными допусками.
- Paper Runtime QA завершён отдельно от headless.
- Forge/Arena/Watchtower не дублируют обязанности.
- Все артефакты позволяют воспроизвести отдельный бой.

---

**Решение для согласования:** этот документ фиксирует границы Arena-01 и целевую архитектуру Arena v1. После ревью документа можно создавать отдельный Arena-01 PR. До этого момента кодовая консолидация workflow не начинается.
