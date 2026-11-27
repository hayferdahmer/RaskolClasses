# Production Combat Core — архитектура и план миграции

© 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

**Статус:** архитектурный проект; реализация не начата.
**Платформа:** Paper 26.2, Java 25.
**База аудита:** main `dbbccdee1def17de2df4e331e29173da04072f68`.
**Связанный контур:** RASKOL Arena v1 (отдельная архитектурная спецификация и CI-консолидация).
**Ключевое ограничение:** все 20 talent nodes остаются выключенными. Этот документ не разрешает их активацию.

## 1. Цель

Production Combat Core должен стать единственным источником боевых правил для реального Paper runtime и headless Arena. Arena не получает собственную копию формул, а использует те же production-компоненты, чистые доменные функции и контракты событий.

Цель не в переписывании всей боевой системы одним PR. Нужна поэтапная декомпозиция существующего боевого кода с сохранением публичного API, текущего игрового поведения и совместимости с Paper 26.2.

Основные свойства целевой системы:

1. **Единая семантика:** один набор правил урона, лечения, сопротивлений, критов, контроля, ресурсов и эффектов.
2. **Явная атрибуция:** каждое боевое событие несёт проверяемый источник и причинную цепочку.
3. **Детерминируемость:** расчёты можно повторить с теми же входами и seed.
4. **Изолируемость:** доменные правила тестируются без поднятия Minecraft/Paper.
5. **Адаптируемость:** Paper отвечает за события, сущности, scheduler и применение результата; домен не зависит от Bukkit.
6. **Мигрируемость:** старые API и listener paths переводятся постепенно, без одномоментной замены.

## 2. Фактическая отправная точка main

Аудит выполнен по дереву и исходникам main на SHA, указанном выше. Ниже перечислены существующие production-компоненты, а не проектируемые имена.

### 2.1. Боевой контур

- `combat/CombatService.java` — 797 строк. Фасад/Listener и одновременно крупная часть пути нанесения урона, включая custom damage path, PvP/friendly-fire gate, множители, crit, resist/penetration, caps, фактическое применение и обратные эффекты.
- `combat/VanillaDamageListener.java` — 548 строк. Отдельный Paper listener для vanilla damage path; содержит собственную обработку event, source/target, melee/ranged и модификаторов.
- `combat/DamageProfile.java` — immutable record из трёх каналов: physical, magic, trueDamage.
- `combat/CombatMath.java` — чистые статические операции sanitize/effective resist/mitigation/cap.
- `combat/ResistService.java` — сопротивления игроков, модификаторы с длительностью, cache и breakdown.
- `combat/AvoidanceService.java`, `DamageCaps.java`, `ProcService.java`, `WeaponMastery.java` — отдельные части боевой логики.
- `combat/school/*` — школы, иммунитеты, penetration traits и mitigation.
- `combat/dot/DotService.java` — 543 строки; Paper listener, хранилище активных DoT, применение и периодическая обработка объединены в одном сервисе.
- `cc/CCService.java` — 693 строки; применение CC, DR, длительности, иммунитеты, runtime state и Bukkit эффекты.
- `pet/PetService.java` — 643 строки; lifecycle питомца, Paper entity events, target selection, runtime tick и часть боевого взаимодействия.

### 2.2. Способности, характеристики и экипировка

- `ability/AbilityRegistry.java` — 653 строки; registry/lookup кастеров, gates, resource consumption/refund, антиспам, вызов caster, cooldown и proc callback находятся в одном классе.
- `AbilityDef` — конфигурационное описание способности, включая school.
- Классовые реализации способностей расположены в `ability/*Abilities.java`; часть эффектов непосредственно использует Bukkit/Paper.
- `attribute/AttributeService.java`, `PowerService.java`, `Hp.java`, `HpPool.java` — характеристики и HP.
- `item/service/ItemStatAggregator.java`, `ItemRegistry.java`, `SetBonusService.java`, `item/model/*` — определения предметов и агрегация характеристик.
- `combat/WeaponMastery.java` — текущая production-таблица mastery.
- `spec/service/Spec2EffectsApplier.java` и `Spec2Service.java` — применение эффектов специализации и доступ к значениям/узлам.

### 2.3. API и тесты

- `api/combat/CombatProfile` и `CombatProfileRegistry` — профиль и registry для сущностей, в том числе RaskolMobs.
- В main уже есть unit tests для CombatMath, resist breakdown, DoT math, CC-adjacent/runtime owners, PetMath, ItemStatAggregator, SetBonusService, CombatProfile и др.
- Наличие unit tests для отдельных функций не подтверждает parity между двумя Paper damage paths и headless execution.
- Combat Lab PR #54–#58 содержат отдельные разработки headless snapshot/runner, equipment, cadence и частичной source attribution. Эти ветки остаются отдельными источниками аудита; этот документ не предполагает их merge или rebase.

### 2.4. Архитектурные проблемы, которые следует устранить

1. **Два входа урона.** Vanilla event path и custom ability path имеют разные входные точки и частично повторяющиеся вычисления. Их фактическая эквивалентность не доказана.
2. **Смешение уровней.** Расчёт, Bukkit event mutation, эффекты, обратные вызовы и presentation/runtime state сосуществуют в крупных сервисах.
3. **Неявный контекст.** В CombatService используются static ThreadLocal markers для suppress/reflect/source/school. Это хрупкий механизм передачи причинности между вложенными вызовами; его нельзя считать надёжным API контекста.
4. **Неполная атрибуция.** Player, pet, projectile, DoT, proc, reflect и ability должны сохранять owner, непосредственный actor и parent event. Нельзя полагаться только на Entity damager или UUID в отдельном маркере.
5. **Недетерминированный RNG.** В боевых сервисах есть прямое использование ThreadLocalRandom. Такой вызов мешает повторить бой в headless и сравнить его с runtime.
6. **Время связано с runtime.** Cooldown/DoT/CC/Pet используют wall clock, Bukkit ticks/tasks и собственное состояние. Симулятору нужны виртуальные часы, но не альтернативные правила длительности.
7. **Семантика результата неоднородна.** Нужно различать рассчитанный урон, запрошенный Bukkit damage, реально снятое HP, поглощение, overkill, отмену события и фактическое лечение.
8. **Каст не является единым доменным действием.** AbilityRegistry смешивает проверки, списание ресурса, антиспам, исполнение и cooldown. Необходимо зафиксировать транзакционную семантику ошибок и отмены.
9. **Состояние не всегда snapshot-friendly.** Часть сервисов читает данные напрямую из Bukkit entity, plugin singleton или config в момент расчёта.

Эти пункты являются архитектурными рисками и целями аудита. Перед изменением каждого конкретного пути нужно зафиксировать его фактическое поведение тестами.

## 3. Целевая архитектура

### 3.1. Слои

```text
Paper 26.2 Adapter Layer
  ├─ Damage / Heal / Projectile / Entity Event Adapters
  ├─ Entity & Inventory Snapshot Providers
  ├─ Bukkit Effect Applier
  └─ Paper Clock / Scheduler / RNG Adapter
             │
             ▼
Combat Application Layer
  ├─ Combat Orchestrator
  ├─ Ability Cast Coordinator
  ├─ Event / Proc Dispatcher
  └─ Combat Session Lifecycle
             │
             ▼
Production Combat Domain (Paper-free)
  ├─ Combatant State & Snapshot
  ├─ Source Attribution / Event Context
  ├─ Damage & Healing Resolver
  ├─ Stats / Equipment / Mastery
  ├─ Ability & Effect Rules
  ├─ Resist / Penetration / Avoidance / CC
  ├─ DoT / Pet / Projectile rules
  └─ Deterministic Clock & Random Ports
             │
             ▼
Ports
  ├─ Combatant Read Port
  ├─ Resource Reservation Port
  ├─ Cooldown Port
  ├─ Effect Application Port
  ├─ Event Publication Port
  └─ Clock / Random Port

Arena Headless Adapter ─────┘
  uses the same Domain and Application contracts,
  with virtual clock, seeded RNG and in-memory ports.
```

Arena — второй адаптер к Production Combat Domain, а не второй Combat Domain.

### 3.2. Границы модулей

| Модуль | Ответственность | Не должен делать |
|---|---|---|
| combat-domain | Чистые правила и immutable входы/выходы | Импортировать Bukkit/Paper, читать plugin singleton |
| combat-application | Координация одного боевого действия и его шагов | Самостоятельно дублировать формулы |
| combat-paper | Конвертация Paper events/entities в доменные запросы и применение результатов | Содержать альтернативную формулу |
| combat-simulation | Virtual clock, seeded RNG, агенты и сценарии | Иметь отдельную реализацию урона/критов/резистов |
| combat-reporting | Event trace, metrics, report schema и baseline comparison | Влиять на игровой результат |

Физическое разделение по Maven modules не является первым шагом. Сначала нужно достичь package/API boundaries внутри существующего проекта; модульное разделение допустимо позднее, когда зависимости стабилизированы.

## 4. Канонические доменные контракты

Ниже приведены концептуальные контракты. Это не существующие классы и не утверждённые Java-сигнатуры. Конкретные типы и поля должны пройти отдельный API review.

### 4.1. CombatantSnapshot

Неизменяемый снимок участника на момент действия:

- стабильный combatant ID и entity kind;
- owner ID (если это pet/summon/projectile);
- class, specialization, character level;
- effective attributes и derived stats;
- current/max health и resource snapshot;
- resistance/penetration/avoidance profile;
- equipment IDs, item data revision, weapon type, mastery;
- active buffs/debuffs/CC/DR;
- enabled ability definitions;
- talent state как отдельная версия данных со статусом каждого узла.

Snapshot фиксирует входные данные для одного разрешённого расчёта. Нельзя читать Bukkit entity повторно посреди одного resolver pipeline.

### 4.2. CombatSourceContext

Каждое событие должно содержать явную причинную атрибуцию:

- `actorId` — непосредственный исполнитель (например, pet);
- `ownerId` — владелец pet/summon/projectile;
- `creditId` — субъект, которому засчитывается результат;
- `abilityId` / `effectId` / `weaponId`;
- `sourceKind` — melee, spell, projectile, pet, dot, proc, reflect, environment;
- `parentEventId` и `correlationId`;
- `depth` / proc chain metadata для защиты от циклов.

Контекст передаётся аргументом. Static ThreadLocal может быть временным compatibility bridge, но не целевой механизм и не должен быть нужен доменной логике.

### 4.3. DamageRequest / DamageResult

Запрос должен включать immutable snapshots атакующего и цели, DamageProfile, source context, тип/школу, flags и идентификатор RNG stream.

Результат должен раздельно описывать:

- исходные каналы;
- outgoing modifiers;
- hit/avoidance/block outcome;
- critical outcome;
- penetration и mitigation;
- capped amount;
- requested/applicable damage;
- actual health delta (если его уже применил runtime adapter);
- отмену/иммунитет/причину отказа;
- список post-hit effects, которые разрешено выполнить.

Чистый resolver возвращает расчётный результат. Только Paper adapter знает, сколько HP реально снято после Bukkit event и внешних плагинов. Не называть расчётный урон фактическим.

### 4.4. HealingRequest / HealingResult

Лечение — отдельный контракт, а не отрицательный урон. Он должен различать:

- raw healing;
- outgoing/incoming modifiers;
- absorb/anti-heal;
- effective healing;
- overheal;
- actual health delta;
- source/credit attribution.

Семантика `CustomHealEvent`, `EntityRegainHealthEvent` и фактического прироста HP должна быть сопоставлена до миграции.

### 4.5. CombatEvent

Все значимые действия публикуют immutable event record с ID, tick/time, source context, target, outcome, причинной связью и schema version. Event trace нужен для диагностики и воспроизведения, но не должен становиться вторым источником игрового состояния.

## 5. Порядок обработки действия

Ниже — целевая последовательность, а не утверждение, что текущие listener paths уже работают именно так. На этапе аудита требуется построить таблицу соответствия текущего кода каждому шагу.

1. **Ingress:** Paper event или ability command преобразуется в Combat Action.
2. **Eligibility:** участники валидны, target допустим, PvP/friendly-fire/faction/zone/auth gates пройдены.
3. **Ability validation:** класс/spec/unlock, silence/CC, cooldown/GCD, range/LOS и стоимость ресурса.
4. **Reservation:** ресурс резервируется с определённой семантикой commit/refund.
5. **Source attribution:** фиксируется actor/owner/credit/ability/parent chain.
6. **Snapshot:** состояние участников и релевантные определения фиксируются для действия.
7. **Base calculation:** базовый урон/лечение/эффект.
8. **Outgoing modifiers:** атрибуты, оружие, mastery, экипировка, set/affix, специализация, разрешённые эффекты.
9. **Hit resolution:** dodge/parry/block и прочие исходы попадания.
10. **Critical resolution:** крит и multiplier по определённому каналу.
11. **Mitigation:** armor/resist, penetration, school modifiers, vulnerability/immunity.
12. **Caps:** damage caps, anti-burst/anti-one-shot и другие лимиты.
13. **Apply:** Paper adapter применяет результат; отменённые внешним событием действия не считаются фактическим уроном.
14. **Post-hit:** разрешённые on-hit, resource generation, lifesteal, DoT, proc и CC effects.
15. **Publish:** результат, telemetry и causal trace.

Порядок внутри outgoing/mitigation этапов должен быть зафиксирован отдельной формульной спецификацией. Нельзя переставлять множители только ради удобства архитектуры: это может менять баланс.

## 6. Единая модель времени и случайности

### 6.1. Clock

Домен получает время через порт:

- Paper adapter использует server tick/monotonic runtime source;
- Arena adapter использует VirtualClock;
- все длительности имеют явную единицу и семантику: ticks или milliseconds;
- перевод между единицами происходит только в адаптере/типизированном helper;
- wall-clock timestamp используется для отчётности, а не для внутриигрового порядка событий.

Нельзя смешивать `System.currentTimeMillis()`, Bukkit scheduler tick и виртуальное время в одном доменном правиле.

### 6.2. Random

Все боевые rolls проходят через Random Port с именованными потоками:

- `hit`, `crit`, `proc`, `avoidance`, `pet`, `loot-independent-combat` и т. п.;
- stream seed строится из root seed + scenario + combatant + stream ID;
- порядок вызовов одного RNG stream не должен зависеть от логирования или UI;
- Paper production использует нормальный runtime RNG adapter;
- Arena использует seeded deterministic adapter.

Это обеспечивает воспроизводимость, не заставляя production бой использовать фиксированный seed.

## 7. Владение правилами и состоянием

- **Stats:** Attribute/Power services и ItemStatAggregator остаются источниками данных до завершения миграции. Domain получает вычисленный snapshot, а не вызывает их сам.
- **Equipment:** ItemRegistry/ItemDefinition/SetBonus/Affix и WeaponMastery должны поставлять один версионируемый EquipmentSnapshot.
- **Spec:** Spec2Service остаётся источником прогресса и unlock state; эффекты специализации переводятся в доменные modifiers с чётким источником.
- **Abilities:** AbilityDefinition описывает данные; AbilityCastCoordinator отвечает за gate/reservation/cooldown; отдельные AbilityEffect handlers описывают действие.
- **Cooldown/resource:** хранение и персистентность остаются за существующими сервисами/портами; правила потребления и refund должны стать едиными.
- **Resists/avoidance/CC:** чистые resolver functions отделяются от Paper effects и lifecycle listeners.
- **DoT:** DotDefinition + DotState + DotTickResolver; Paper scheduler лишь подаёт события тика. DoT не должен автоматически повторять proc/on-hit исходного удара.
- **Pets:** PetCombatant имеет собственный actor ID и owner/credit; PetService управляет сущностью и lifecycle, но боевой результат проходит через общий resolver.
- **Projectiles:** projectile хранит immutable source context при создании; hit event восстанавливает его без потери owner/ability/weapon.
- **Procs/reflect:** вложенные действия получают parent event и ограниченную глубину; отражение не создаёт бесконечную рекурсию.

## 8. Таланты: реестр, статус и строгий запрет активации

В проекте зафиксированы 20 talent nodes. На всём протяжении архитектурной и начальной миграционной работы:

- все 20 узлов остаются `disabled`;
- ни один узел не становится доступным через default config, fallback или migration;
- наличие consumer/handler/test не означает, что узел включён;
- тесты могут проверять disabled behavior и формулы изолированно;
- ни один новый consumer не должен автоматически активировать узел;
- все проверки статуса должны fail closed при неизвестном/невалидном состоянии.

Для каждого узла нужен реестр со следующими полями: node ID, class/spec, rank, prerequisites, intended effect, production consumer, source attribution, test coverage, runtime coverage, balance status, enabled=false. До включения отдельного talent node требуются: formula review, unit tests, event-level tests, headless scenario, Paper runtime test, balance baseline comparison и отдельное явное решение.

Активация талантов не входит в Production Combat Core architecture PR и не может быть выполнена автоматически последующим рефакторингом.

## 9. Миграция без big-bang rewrite

### Phase 0 — Behavioral inventory (отдельная подготовительная работа)

- Нарисовать фактический call graph VanillaDamageListener → CombatService → services → Bukkit event/application.
- Нарисовать call graph AbilityRegistry → caster → CombatService/CC/DoT/Pet/Resource/Cooldown.
- Сопоставить формулы vanilla и custom paths.
- Инвентаризировать все ThreadLocal markers, ThreadLocalRandom, direct clock reads и nested damage calls.
- Для каждого пути зафиксировать порядок, event priority, cancellation semantics и actual health delta.
- Составить таблицу: production class → ответственность → тесты → target component → compatibility adapter.

**Выход:** проверяемая матрица поведения и список несовместимостей. Никакого production refactor на этом этапе.

### Phase 1 — Domain contracts

- Ввести immutable source context, combatant snapshot, damage/heal request/result и random/clock ports.
- Реализовать только pure adapters вокруг уже существующих формул.
- Не менять значения конфигурации и порядок формул.
- Добавить golden tests, фиксирующие старые результаты на граничных значениях.

**Выход:** контракты и тесты, но runtime ещё использует прежний pipeline.

### Phase 2 — One resolver, two adapters

- Перевести сначала один ограниченный damage slice (например, физический прямой удар без proc/DoT/CC) на общий resolver.
- Vanilla и custom пути вызывают один и тот же resolver.
- Старый путь остаётся compatibility adapter, не параллельным источником формул.
- Сравнивать старый и новый результаты в shadow mode без двойного применения урона.

**Выход:** подтверждённый parity на ограниченном slice и возможность отката.

### Phase 3 — Full damage and attribution

- Подключить магию, resist/penetration, crit, avoidance, caps, shields, ranged/projectile, pets, reflect и proc chains.
- Перевести ThreadLocal markers на явный context.
- Зафиксировать cancellation и actual damage semantics.

**Выход:** единый production damage path с полной source attribution.

### Phase 4 — Ability/effects lifecycle

- Разделить AbilityRegistry на definition registry, cast coordinator и effect handlers.
- Унифицировать resource reservation/refund, cooldown/GCD и CC cast gates.
- Перевести DoT/CC на domain state + Paper scheduler adapter.
- Уточнить heal semantics и lifesteal.

**Выход:** abilities/effects используют общие контракты и виртуальное время.

### Phase 5 — Equipment, specialization, pets and talents

- Перевести equipment/stat/spec aggregation в immutable snapshots.
- Уточнить weapon cadence/mastery parity.
- Pet и projectile attribution проходят через общий event context.
- Все 20 talents остаются disabled; разрешается только тестирование их isolated contracts.

**Выход:** snapshots пригодны для runtime и headless без изменения игровых unlock states.

### Phase 6 — Arena parity and baseline readiness

- Подключить Arena к production resolver и snapshots.
- Сравнить одинаковые сценарии на Paper и headless.
- Утвердить scenario matrix, seeds, report schema и tolerance.
- Только после ручной проверки — создать approved baseline.
- Статистический gate не включать при pending baseline.

**Выход:** доказанный headless/runtime parity и готовность к отдельному balance workflow.

## 10. Матрица обязательных проверок

| Область | Unit/domain | Paper event | Headless | Parity |
|---|---|---|---|---|
| Physical/magic/true damage | Формулы, границы, sanitize | Vanilla + custom event | Да | Да |
| Resist/penetration/school | Cap, stacking, immunity | Реальные attributes/events | Да | Да |
| Crit/avoidance/block | RNG streams и edge cases | Equipment/shield interactions | Да | Да |
| Equipment/mastery/cadence | Aggregation и eligibility | Inventory/equipment changes | Да | Да |
| Ability/resource/cooldown | Reservation/refund/gates | Cast lifecycle/cancel | Да | Да |
| Heal/lifesteal | Effective vs overheal | Regain/custom heal events | Да | Да |
| DoT/projectile | Tick math и source | ProjectileHit + scheduler | Да | Да |
| CC/DR | Duration/stack/window | Potion/effect lifecycle | Да | Да |
| Pet/proc/reflect | Causal graph/cycle limits | Entity events/owner logout | Да | Да |
| Talent contracts | 20 disabled-state checks | Disabled runtime behavior | Disabled-state only | Не активировать |

Для каждой строки тесты должны включать normal case, boundary case, cancelled action, invalid source/target и повторяемый seed там, где есть случайность.

## 11. Acceptance criteria архитектуры

Архитектура считается готовой к реализации только если:

1. Для двух существующих damage paths есть точная карта текущего поведения и формульных расхождений.
2. Для каждого source kind определены actor/owner/credit/parent semantics.
3. Damage и heal имеют разные контракты с отдельным effective/actual result.
4. Clock и RNG ports покрывают все боевые random/time calls.
5. Указан владелец каждого правила и состояния.
6. Определена последовательность миграции и compatibility strategy.
7. Есть golden tests/parity plan до изменения runtime.
8. Arena использует production contracts, не повторяет формулы.
9. Никакой шаг не требует включить все 20 talents.
10. Не меняются баланс-конфиги и публичное поведение в архитектурном PR.

## 12. Явно вне рамок

- Реализация новых боевых формул или балансировка классов.
- Включение любого из 20 талантов.
- Изменение уровней unlock, specialization tree или class lock.
- Изменение предметов, сетов, weapon stats и cooldown numbers.
- Переписывание всех способностей и питомцев.
- Массовый rebase, смена base branch или merge PR #54–#58.
- Включение Arena в required checks до отдельного подтверждения стабильности.
- Создание/утверждение baseline из синтетических или неполных отчётов.

## 13. Предлагаемая последовательность PR после архитектурного согласования

1. **Production Combat Core — Behavioral Inventory:** только call graph, event order, source paths, formula parity matrix и тестовые пробелы.
2. **Combat Domain Contracts:** immutable snapshots/requests/results/context и ports; без переключения runtime.
3. **Damage Resolver Slice 1:** один ограниченный физический path, golden tests и shadow comparison.
4. **Unified Damage Pipeline:** постепенное подключение остальных каналов и attribution.
5. **Ability & Effect Lifecycle:** resource/cooldown/CC/DoT/heal.
6. **Equipment/Pet/Projectile Integration.**
7. **Arena Runtime/Headless Parity.**
8. **Talent-by-talent enablement review:** отдельные решения; не пакетное включение.

Каждый этап — отдельный PR с узкой областью изменений. Не объединять архитектуру, рефакторинг и балансировку в один PR.

---

**Архитектурное решение:** Production Combat Core строится как единый Paper-free combat domain с Paper и Arena adapters. Существующие сервисы мигрируют постепенно через compatibility boundaries. Пока parity не доказан, старые runtime paths сохраняются. Все 20 talent nodes остаются disabled.
