# RaskolHUD — Data Contracts & Snapshot Design (HUD-B.1)

## Статус

- Этап: HUD-B.1 — Health / Resource / Character Read Contracts
- Статус: проектирование read contracts; runtime-код не добавлялся
- Ветка: `refactor/a03-foundation-lifecycle`
- Целевой релиз: RaskolClasses 1.16.0
- Платформа: Paper 26.2 / Java 25

Этот документ уточняет источники данных и контракты чтения для RaskolHUD. Он не утверждает окончательный публичный API RaskolClasses и не создаёт новые обязательства для внешних плагинов.

## 1. Архитектурный принцип

RaskolHUD не обращается к игровым сервисам напрямую из каждого виджета и не получает изменяемые игровые объекты. Для одного игрока HUD получает единый immutable read snapshot.

Поток:

```text
Character / Class / Specialization ─┐
Health / Attributes ────────────────┤
Resources ──────────────────────────┤
Effects / DoT / Combat ─────────────┼─> HudSnapshotProvider
Targeting ─────────────────────────┤          |
Equipment / Cooldowns (later) ─────┘          v
                                      PlayerHudSnapshot
                                              |
                                         HudCoordinator
                                              |
                                      Widget Presentation
                                              |
                                      Paper Render Adapters
```

Игровые модули остаются владельцами своего состояния. HUD — потребитель read-only проекций.

## 2. Предлагаемые контракты чтения

Контракты должны быть узкими и ориентированными на HUD. Не следует передавать `RaskolClasses`, глобальные service locators или полные сервисы в виджеты.

Предварительный набор:

- `CharacterHudReadPort` — класс, специализация, уровень и прогресс персонажа;
- `HealthHudReadPort` — текущее/максимальное HP в согласованных игровых единицах;
- `ResourceHudReadPort` — идентификатор, подпись, символ, текущее значение и потолок ресурса;
- `AttributeHudReadPort` — STR/AGI/INT и другие характеристики, если они включены в HUD;
- `EffectHudReadPort` — временные эффекты и их сроки;
- `DotHudReadPort` — DoT-эффекты, наложенные на игрока;
- `CombatHudReadPort` — состояние боевого окна;
- `TargetHudReadPort` — целевая сущность и её разрешённые к отображению данные;
- `HudProfileProvider` — тема, включённые виджеты и персональные настройки.

Это проектные seams, а не требование создавать интерфейс на каждый существующий класс немедленно. Сначала проверяется реальная вариативность и ownership. Допускается один concrete provider, который агрегирует несколько read ports.

## 3. PlayerHudSnapshot

Предлагаемая логическая структура:

```java
record PlayerHudSnapshot(
    UUID playerId,
    long revision,
    long capturedAtMillis,
    CharacterView character,
    HealthView health,
    ResourceView resource,
    AttributeView attributes,
    TargetView target,
    List<EffectView> effects,
    List<DotView> dots,
    CombatView combat,
    HudProfileView profile
) {}
```

Все вложенные view types также immutable. Коллекции копируются при создании снимка. В snapshot не допускаются Bukkit Player/Entity/World, mutable service objects, живые Map/List из игровых сервисов или ссылки на внутренние кэши.

### 3.1. CharacterView

Поля:
- classId и classDisplayName;
- specializationId и specializationRole (могут отсутствовать);
- characterLevel;
- currentExperience;
- experienceToNextLevel либо другой явно определённый progression measure;
- состояние доступности данных.

Важно: текущий `CharacterLevelService` рассчитывает уровень на основе AuraSkills и fallback-конфигурации. Этот источник будет заменён в 1.16.0. RaskolHUD не должен зависеть от `CharacterLevelService` или `SkillLevelProvider` напрямую. Для HUD нужен новый Character read contract, реализация которого будет предоставлена RPG Core.

Текущий `ClassProvider.getClassOf(Player)` может вернуть null как при отсутствии класса, так и при неразрешённом источнике. До появления более точного результата разрешения snapshot не должен выдумывать причину: статус следует обозначать как unknown/unavailable, если адаптер не может доказать отсутствие класса.

### 3.2. HealthView

Поля:
- currentFormulaHp;
- maxFormulaHp;
- normalizedRatio (0..1, вычисляется с защитой от некорректного max);
- при необходимости carrier/vanilla values как отдельные поля, только если конкретный renderer действительно использует их.

Источник данных: `AttributeService` / текущая HP read facade.

Текущий `HpBarService` нельзя превращать в HUD-owned модуль целиком. Его `heal()`, HP queries, max-health modifier, deferred restore, STR regeneration и `health.yml` persistence используются игровыми системами. На первом этапе существующая совместимая facade остаётся доступной callers. Для HUD выделяется только read-only проекция.

### 3.3. ResourceView

Поля:
- resourceId;
- displayName;
- symbol/icon key;
- current;
- effectiveMax;
- normalizedRatio (0..1).

Источник: `ResourceService`.

`ResourceService.stateOf(UUID)` возвращает изменяемый `ResourceState`. HUD обязан немедленно скопировать необходимые примитивные значения в snapshot и не хранить ссылку на `ResourceState`. Потолок ресурса зависит не только от базового 100, но и от специализации/экипировки; snapshot использует фактический `effectiveMax`, а не константу.

### 3.4. AttributeView

Поля:
- STR;
- AGI;
- INT;
- при необходимости отдельные публичные производные показатели после согласования.

Источник: `AttributeService.value(UUID, AttributeType)`.

Текущий AttributeService кэширует рассчитанные значения по серверному tick и учитывает уровень, модификаторы и экипировку. HUD не должен повторно рассчитывать формулы атрибутов. Snapshot получает уже рассчитанные значения.

### 3.5. EffectView и DotView

EffectView:
- stable effect id;
- отображаемое имя/icon key;
- expiry timestamp либо remaining duration;
- one-shot/indefinite flag;
- stacks, если источник их предоставляет.

DotView:
- stable DoT id;
- school;
- stacks;
- expiry timestamp;
- owner UUID только если он нужен для утверждённого отображения.

`ActiveEffectManager.getActiveEffects()` формирует отдельную Map-копию, но это не публичный immutable snapshot contract. Провайдер должен скопировать данные в собственные value objects.

`DotService.activeDotsOf()` возвращает `List.copyOf`, однако элементы `DotInstance` mutable. Провайдер должен копировать поля каждого экземпляра, а не сохранять сами DotInstance.

Текущий ActionBar показывает DoT, наложенные на самого игрока. Это поведение следует сохранить при переносе, пока отдельное продуктовое решение не изменит его.

### 3.6. CombatView

Минимальный набор:
- inCombat;
- capturedAtMillis / combat-window expiry, если доступно и семантически корректно.

Не помещать сюда кулдауны способностей, готовность проков или контрольные эффекты без отдельных источников истины. Эти данные принадлежат соответствующим подсистемам и могут появиться отдельными view types.

### 3.7. TargetView

Предварительные поля:
- target entity UUID;
- entity kind/category;
- display name;
- current/max health, если разрешено и доступно;
- distance (опционально);
- target state: NONE / AVAILABLE / UNAVAILABLE.

В текущем коде не обнаружен единый владелец выбранной игроком цели. `Player.getTargetEntity()` используется как ray-target внутри отдельных способностей, но это не эквивалент устойчивой MMORPG target selection. Не использовать его автоматически как окончательную семантику target frame.

До реализации target widget требуется отдельное продуктовое решение: рамка должна показывать существо под прицелом или последнюю явно выбранную/зафиксированную цель. Эти режимы различаются по поведению и UX.

## 4. Согласованность и поток выполнения

- Bukkit/Paper read adapters выполняют обращения к игровому миру на допустимом серверном потоке.
- Первичная реализация snapshot capture выполняется синхронно на main thread; не передавать Bukkit API в async executor.
- `revision` — монотонный номер опубликованного HUD snapshot конкретного игрока, а не обещание общей revision всех доменных сервисов.
- `capturedAtMillis` — момент завершения чтения snapshot.
- Сначала все значения копируются в локальные immutable value types, затем публикуется целый snapshot.
- При отсутствии источника поле получает явный unavailable/unknown state; ноль используется только как реальное значение.
- Отложенные/асинхронные результаты должны проверять актуальность revision перед публикацией, чтобы старый кадр не перезаписал новый.

Не требуется вводить distributed transaction между доменами: на первом этапе согласованность обеспечивается чтением на серверном потоке и атомарной публикацией одного immutable snapshot.

## 5. Refresh policy

Источники данных неодинаковы:

- HP/resources/character могут меняться часто;
- эффекты и DoT имеют expiry;
- имя цели/здоровье цели может меняться независимо от состояния игрока;
- уведомления имеют ограниченный срок жизни.

Поэтому HUD не должен запускать один глобальный цикл, который каждый тик пересчитывает всё для всех игроков.

Целевой подход:
1. События/изменения помечают соответствующие player snapshots dirty.
2. Частые события объединяются (coalescing).
3. Один capture строит новый snapshot игрока.
4. Виджеты сравнивают только нужные поля и формируют patch.
5. Для источников без событий используется ограниченный fallback refresh.
6. Target frame может иметь отдельный refresh cadence, если будет выбран crosshair-target режим.

Текущие интервалы 10 ticks для HudService/HP display и 5 ticks для BossBarService — исторические значения, а не автоматически утверждённые частоты будущего HUD.

## 6. Что нельзя связывать напрямую

- Не связывать CharacterView с текущим AuraSkills-backed CharacterLevelService.
- Не передавать ResourceState в виджеты.
- Не передавать DotInstance и изменяемые effect maps в snapshot.
- Не передавать Bukkit Entity в долговременное состояние HUD.
- Не давать виджетам вызывать `heal`, `consume`, `add`, `purchase`, `reconcile` или другие mutation methods.
- Не делать HpBarService владельцем всей HUD-системы.
- Не привязывать внутренние контракты к PlaceholderAPI.
- Не использовать `RaskolClasses` как service locator внутри каждого widget.

## 7. Зависимости от будущей RPG Core 1.16.0

HUD должен интегрироваться через стабильные query contracts, которые будут реализованы новыми системами:
- Character progression 1–60;
- XP и прогресс следующего уровня;
- class lock;
- specialization v160;
- STR/AGI/INT;
- equipment/gear stats;
- talents;
- class resources.

HUD не должен заранее зависеть от внутренней модели хранения YAML, миграций профиля или реализации XP curve. Его задача — отображать read model, опубликованный RPG Core.

## 8. Результаты HUD-B.1

- Подтверждены источники для health, resources, attributes, effects и DoT.
- Установлено, что Character level source будет заменён в 1.16.0; нужна независимая query seam.
- Установлено, что current target selection contract отсутствует в проверенных исходниках.
- Зафиксировано требование копировать mutable ResourceState, DotInstance и effect maps в immutable view types.
- Runtime-код, YAML, PDC и существующие API не изменялись.

## 9. Следующие действия

1. **HUD-B.2 — Snapshot Schema:** утвердить точные value types и правила unavailable/null.
2. **HUD-B.3 — Widget Contracts:** определить вход/выход виджетов и channel ownership.
3. **HUD-C — Runtime Ownership:** после согласования snapshot и widget contracts спроектировать HudCoordinator и его TaskScope.
4. Перед implementation — повторно собрать полный call-site inventory на feature branch и зафиксировать compatibility tests.

