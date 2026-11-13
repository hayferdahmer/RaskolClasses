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



## 10. HUD-B.2 — Snapshot Schema

### 10.1. Общие правила

Предлагается один корневой `PlayerHudSnapshot` с отдельными value records для независимых групп данных. Состав не должен зависеть от конкретного способа рендеринга.

Корневые поля:
- `playerId`;
- `revision` — монотонный номер snapshot конкретного игрока;
- `capturedAtMillis`;
- `character`;
- `health`;
- `resource`;
- `attributes`;
- `target`;
- `effects`;
- `dots`;
- `combat`;
- `profile`.

Вложенные типы:
- `CharacterView`;
- `HealthView`;
- `ResourceView`;
- `AttributeView`;
- `TargetView`;
- `EffectView`;
- `DotView`;
- `CombatView`;
- `HudProfileView`.

### 10.2. Неизвестное значение не равно нулю

Вводится общий enum `DataStatus`:
- `AVAILABLE` — источник успешно предоставил данные;
- `NOT_APPLICABLE` — показатель неприменим к данному классу/состоянию;
- `UNAVAILABLE` — источник временно не смог предоставить данные.

Для target используется более точная семантика:
- `NONE` — цель успешно проверена, цели нет;
- `AVAILABLE` — цель есть;
- `UNAVAILABLE` — target source недоступен/не смог разрешить цель.

Не кодировать неизвестные значения нулём, пустой строкой или отрицательными числами. Значение доступно только при статусе AVAILABLE. Для необязательных чисел использовать явное отсутствие значения; не использовать NaN как протокол.

### 10.3. Коллекции и время

- Все коллекции в snapshot создаются defensive copy и публикуются как immutable.
- `EffectView` и `DotView` хранят primitive/value fields, а не ссылки на mutable EffectType maps или DotInstance.
- Внутреннее время срока действия хранится как absolute expiry timestamp; remaining duration вычисляется относительно `capturedAtMillis`, чтобы все виджеты кадра использовали один момент времени.
- Для бесконечного/one-shot эффекта используется явный флаг, а не искусственный большой remaining duration.
- UUID игрока/цели допустимы; Bukkit Entity/Player — нет.

### 10.4. Уведомления не являются частью постоянного snapshot

Level-up, смена специализации, получение важной награды и другие одноразовые уведомления должны идти через отдельный `HudNotificationService`/event queue с идентификатором, сроком жизни и адресатом.

Причина: если хранить уведомление как обычное поле snapshot, каждый refresh может повторно показать одно и то же событие. Notification delivery должна быть idempotent и иметь политику удаления/истечения.

## 11. HUD-B.3 — Widget Contracts и каналы

### 11.1. Widget contract

Виджет:
- имеет стабильный `HudWidgetId`;
- объявляет, какие поля snapshot ему нужны;
- получает immutable `PlayerHudSnapshot` и эффективный `HudProfileView`;
- возвращает presentation intent, а не вызывает Bukkit API;
- не читает игровые сервисы самостоятельно;
- не меняет игровые данные;
- может вернуть отсутствие вывода, если поле unavailable/not applicable или виджет скрыт.

Предварительная логическая форма:

```java
interface HudWidget {
    HudWidgetId id();
    WidgetFrame render(PlayerHudSnapshot snapshot, HudProfileView profile);
}
```

`WidgetFrame` содержит:
- widget id;
- channel;
- priority;
- immutable payload;
- optional expiry/animation metadata;
- visibility state.

Не создавать отдельный interface для каждого вида текста/иконки до появления реальной вариативности. Виджеты должны оставаться небольшими и cohesive.

### 11.2. Channel ownership

Вводится один владелец состояния вывода на игрока — `HudChannelCoordinator` (рабочее имя).

Его задачи:
- разрешать конкуренцию нескольких виджетов за ActionBar;
- управлять набором принадлежащих RaskolClasses BossBars;
- публиковать Scoreboard только через отдельную стратегию совместимости;
- очищать output и per-player handles при quit/disable;
- не допускать, чтобы два виджета независимо отправляли один и тот же канал без приоритета.

BossBar может иметь несколько одновременно видимых элементов, но каждый такой handle должен иметь стабильный widget/source id и управляться общим owner. Существующий BossBarService пока остаётся legacy owner до отдельного переноса.

Scoreboard не должен перезаписывать sidebar/teams другого плагина без согласованной политики. Если нельзя безопасно владеть sidebar, соответствующий widget должен быть отключён или использовать альтернативный channel.

### 11.3. Presentation pipeline

```text
PlayerHudSnapshot
       |
       v
HudWidgetRegistry
       |
       v
WidgetFrame[]
       |
       v
HudChannelCoordinator
       |
       +--> ActionBarRenderer
       +--> BossBarRenderer
       +--> ScoreboardRenderer
       +--> ResourcePack / Font Glyph adapter
       |
       v
Paper / Adventure API
```

Виджеты не должны сериализовать legacy strings или создавать Bukkit BossBar напрямую. Renderer adapter отвечает за конвертацию presentation payload в Paper API.

### 11.4. Независимость каналов

- ActionBar — единая строка/область, конкуренция решается приоритетом и коротким TTL.
- BossBar — несколько принадлежащих HUD полос, но единая регистрация/очистка.
- Scoreboard — условный канал с ограничениями совместимости.
- Resource pack/custom font — визуальные assets, а не самостоятельный произвольный экранный canvas. Его использование требует отдельной стратегии доставки, hash/version и fallback.
- Inventory GUI — только настройки/редактор профиля, не постоянный боевой вывод.

## 12. HUD-B.1/B.2/B.3 — статус и открытые решения

Проектная структура snapshot и widget contract описана. Продуктовые решения по Resource Pack и семантике Target Frame приняты; до реализации остаются:
- политика Scoreboard/BossBar конфликтов с внешними плагинами;
- конкретный набор class/spec-specific widgets для каждой из шести текущих классов и будущих специализаций.

Эти вопросы не блокируют завершение source inventory и базовых read contracts, но влияют на окончательный renderer/widget scope.


## 13. H-04 — Resource Pack optional contract

Пользователь утвердил политику `optional`.

### 13.1. Capability tiers

- **Baseline (без Resource Pack):** все критичные данные и уведомления доступны через стандартные Paper/Adventure presentation channels; используются текстовые/символьные fallback-представления без custom font glyph assumptions.
- **Enhanced (совместимый Resource Pack принят):** разрешены фирменные glyphs, иконки, декоративные рамки и дополнительные визуальные акценты.
- **Degraded (пакет отсутствует, отклонён, не загрузился или имеет неподдерживаемую версию):** Enhanced capabilities отключаются, Baseline продолжает работать. Ошибка пакета не отключает snapshot provider, widgets или весь HUD.

### 13.2. Data and rendering invariants

- Семантика виджета не зависит от наличия Resource Pack: меняется только presentation mapping.
- Нельзя кодировать единственное значение HP/resource/XP/target/effect только custom glyphs.
- Если иконка недоступна, renderer использует текстовую метку/числовое значение.
- Resource Pack status — presentation capability, а не доменное состояние персонажа.
- Принятие/отказ игрока не меняет игровой профиль, прогресс или права.
- Ошибка/отказ доставки не должна запускать бесконечный цикл prompt; частота повторного предложения требует отдельного UX решения.

### 13.3. Contracts still to define

До HUD-D определить отдельный ResourcePackCapabilityView или эквивалентный presentation capability contract со статусами NOT_OFFERED, PENDING, ACCEPTED, DECLINED, FAILED, OUTDATED. Не включать этот статус в CharacterView и не считать его gameplay data.

Точные hash/version negotiation, pack URL, server prompt policy и обновление пакета описываются в delivery/config contract. Значение optional означает, что отказ никогда не блокирует вход и не лишает игрока критически важных данных.

## 14. HUD-C — Runtime ownership design handoff

HUD-C реализует единый lifecycle owner, не смешивая его с HP persistence owner и не закрывая shared AsyncStorageWriter. Детальный lifecycle, ownership matrix, failure semantics, staged migration и тестовые критерии находятся в [RASKOL_HUD_RUNTIME_OWNERSHIP.md](RASKOL_HUD_RUNTIME_OWNERSHIP.md).


## 15. H-05 — Locked Target contract

Пользователь выбрал вариант 2: **last explicitly selected/locked target**.

### 15.1. Semantics

- `TargetView` отражает текущую явно зафиксированную цель игрока.
- Наведение камеры/прицела на другую сущность не меняет locked target.
- Цель меняется только явной командой/действием выбора либо явным снятием выбора.
- Выбор автоматически очищается при инвалидизации цели по правилам targeting domain: сущность удалена/недоступна, игрок offline или его игровая сессия завершена.
- Crosshair/raycast entity не подменяет locked target. В будущем она может быть отдельным preview-полем, не влияющим на selection state.

### 15.2. Ownership and read contract

- Selection state принадлежит отдельному targeting/application owner, не HUD и не renderer.
- HUD получает read-only `TargetHudReadPort` projection.
- Port должен различать `NONE` (успешно проверено, locked target отсутствует), `AVAILABLE` и `UNAVAILABLE`.
- `AVAILABLE` содержит только immutable identity/display/combat fields, разрешённые правилами мира и приватности; не содержит Bukkit Entity/Player.
- Target view привязана к snapshot `capturedAtMillis` и revision; устаревшая ссылка не должна отображаться после invalidation.
- Конкретный UX выбора цели (команда, GUI, иной серверный механизм) не утверждён. Не предполагать клиентский keybind или client mod.

### 15.3. Target contract test cases

- прицеливание на другую сущность не меняет выбранную цель;
- явный выбор переключает TargetView;
- явное снятие выбора переводит состояние в NONE;
- удаление/недоступность цели очищает selection;
- offline/quit очищает session-bound target state;
- provider failure возвращает UNAVAILABLE, а не NONE;
- snapshot не содержит Bukkit Entity/Player и не хранит mutable domain object.

## 16. HUD-C.1 — Runtime seams (design)

Первый implementation slice ограничивается нейтральными портами для scheduling, session events и output cleanup плюс deterministic test doubles. Он не подключает новые task/listener producers к `RaskolClasses`, не меняет `activeTasks` и не переключает legacy renderers. Контракты должны оставаться независимыми от Bukkit API; Bukkit adapters появятся отдельным шагом после тестирования.


## 17. HUD-C.1 — Initial runtime seams

В `dev.raskol.classes.hud.runtime` добавлены Bukkit-independent ports: `HudTaskScheduler`, `HudSessionEvents`, `HudRegistration` и `HudOutputCleanup`. Они фиксируют минимальные границы scheduling, quit subscriptions и очистки принадлежащего HUD вывода. Реальные Bukkit adapters, coordinator и тестовые doubles будут добавляться в следующих C.1 подэтапах вместе с потребителями, чтобы тесты проверяли рабочую оркестрацию.
