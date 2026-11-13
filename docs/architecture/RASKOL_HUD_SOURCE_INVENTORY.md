# RaskolHUD — Source Inventory (HUD-A)

## Статус

- Проект: RaskolClasses
- Ветка: `refactor/a03-foundation-lifecycle`
- Этап: HUD-A — Source Inventory
- Результат: source inventory выполнен по текущей ветке на момент аудита
- Изменения игровых исходников: не выполнялись
- Изменения `main`: не выполнялись

## 1. Проверенные файлы и версии blob

| Файл | Blob SHA | Размер / строки |
|---|---|---:|
| `hud/HudService.java` | `f86aa3e50ff355ffa403e35565657914e4c08e04` | 12,954 bytes / 321 lines |
| `hud/BossBarService.java` | `39d486d8dab1f9b8b4876d0eaca6c81e2e963ddd` | 8,978 bytes / 241 lines |
| `hud/HpBarService.java` | `0ebd1f89b326284144521a2023bb9e203a8a2e29` | 29,945 bytes / 639 lines |
| `RaskolClasses.java` | `f667bf9297238bb8ca7996c3b54ddef367fc54f0` | 33,941 bytes / 885 lines |
| `config/RaskolConfig.java` | `aec90b0e3750e3499748744bc4c2912cc08e6fcc` | 1,180 lines |
| `resources/config.yml` | `acb73f1dcb7303fa889ad017ee9428cc0b453406` | 2,592 lines |

Также проверены startup/reload/shutdown участки `RaskolClasses`, HUD-конфигурация и дерево тестов. В `src/test/java/dev/raskol/classes` отдельного пакета `hud` на момент аудита нет.

## 2. Фактическое распределение ответственности

### 2.1. HudService — ActionBar ресурсный HUD

Файл: `src/main/java/dev/raskol/classes/hud/HudService.java`

Основные методы:
- `applyConfig()` — строки 74–79;
- `start()` — 81–92;
- `toggle(Player)` / `isVisible(Player)` — 94–102;
- `tickFor(Player)` — 105–176;
- rendering и DoT helpers — 178–319.

Фактическое поведение:
- периодическая задача перебирает всех online players;
- период по умолчанию — 10 ticks (`hud.update-period-ticks`);
- строит ActionBar с класс-специфичным ресурсным индикатором;
- показывает активные DoT с иконкой, стаками и оставшимся временем;
- использует class themes, Adventure Components и кэши;
- опционально создаёт particle aura при полном ресурсе;
- пропускает игроков без класса, spectator (если включено) и игроков с metadata `vanished`/`disappeared`;
- хранит per-player overrides, frame keys, spark positions и last resource values.

Зависимости чтения:
- `ClassProvider`;
- `ResourceService`;
- `CombatService.dots()/DotService`;
- `RaskolConfig`;
- Bukkit Player/World/metadata и scheduler.

Владение:
- `HudService.start()` возвращает BukkitTask;
- сам сервис task не сохраняет и не отменяет;
- task добавляется в общий `RaskolClasses.activeTasks`.

Наблюдения:
- у HudService нет собственного Listener и нет явного quit cleanup;
- per-player maps не имеют обнаруженного пути удаления по PlayerQuit;
- скрытый игрок и игрок без класса просто пропускаются, без очистки его render state;
- `applyConfig()` обновляет enabled flag и очищает только render caches, но не меняет состояние/период задачи;
- метод `toggle()` хранит индивидуальный override, но отдельная команда/GUI, вызывающая его, в проверенных startup/command участках не обнаружена.

### 2.2. BossBarService — отдельный рендер активных эффектов

Файл: `src/main/java/dev/raskol/classes/hud/BossBarService.java`

Основные методы:
- `start()` — строки 47–55;
- `applyConfig()` — 58–60;
- `shutdown()` — 63–73;
- `tick()` — 76–180;
- `cleanupPlayer()` / `cleanupAll()` — 217–240.

Фактическое поведение:
- отдельный BossBar для каждого отображаемого EffectType игрока;
- период по умолчанию — 5 ticks (`hud.boss-bar.tick-period`);
- фильтрует эффекты без displayName и короткие эффекты;
- по умолчанию показывает не более двух эффектов с длительностью от 8 секунд;
- сортирует кандидатов по оставшемуся времени;
- формирует название/цвет с учётом класса и ClassTheme;
- удаляет bars при исчезновении эффекта, отключении настройки, отсутствии класса и выходе игрока.

Владение:
- BossBarService хранит собственный BukkitTask;
- хранит per-player `Map<EffectType, BossBar>`;
- `shutdown()` отменяет task, удаляет все bars и очищает registry.

Наблюдения:
- `applyConfig()` безусловно делает shutdown → start; нет явного idempotent guard от повторного start;
- проверяет `HudService.isVisible(player)`, то есть видимость одного ActionBar HUD влияет и на effect BossBars;
- это не target frame и не универсальный HUD renderer: текущая ответственность — только активные эффекты;
- отдельного listener на quit нет, но offline игроки очищаются периодическим tick;
- отдельного общего арбитра BossBar с другими возможными владельцами сервера нет.

### 2.3. HpBarService — не просто HUD: HP runtime, healing и persistence

Файл: `src/main/java/dev/raskol/classes/hud/HpBarService.java`

Основные методы:
- HP query facade — строки 115–129;
- healing application — 131–144;
- display config helpers — 147–207;
- `start()` — 210–212;
- `saveAll()` — 218–222;
- `tick()` — 225–255;
- deferred restore / max HP / save / flush / STR regen — 257–440;
- hearts scaling и unified ActionBar — 441–570;
- PlayerJoin/Quit/Respawn handlers — 584–638.

Фактические обязанности:
1. **HP domain/application behavior**
   - `heal(...)` проверяет anti-heal, публикует CustomHealEvent и вызывает AttributeService.
   - предоставляет `formulaMaxHp`, `carrierMaxHp`, `scale`, `currentFormulaHp`.
   - эти методы используются боевыми/ability/installations/placeholder/selftest участками, то есть это внутренняя игровая интеграционная поверхность, а не только HUD.
2. **HP runtime**
   - применяет max-health attribute modifier;
   - восстанавливает сохранённый HP ratio после join с учётом позднего определения класса;
   - выполняет STR regeneration с учётом combat и anti-heal;
   - синхронизирует vanilla hearts scaling.
3. **HP persistence**
   - владеет `health.yml`, `SafeStorage`, `DirtyStorageState`;
   - делает dirty snapshot и отправляет его в общий `AsyncStorageWriter`;
   - `saveAll()` вызывается при выключении.
4. **Presentation**
   - при `hp-display.mode != vanilla` отправляет unified ActionBar с HP и ресурсом;
   - при `vanilla` управляет vanilla hearts scaling, а ресурсный ActionBar передаётся HudService.

Владение:
- task возвращается из `start()` и регистрируется в `activeTasks`;
- Listener регистрируется в `registerGameplayListeners()`;
- health.yml и flush логика принадлежат HpBarService.

**Архитектурный вывод:** HpBarService нельзя переносить целиком в RaskolHUD и нельзя переименовывать/удалять его публичные методы в рамках HUD-рефакторинга. Сначала нужно отделить HP runtime/domain/persistence от HP presentation, сохранив совместимую facade для существующих callers.

## 3. Bootstrap, reload и shutdown

### Startup

В `RaskolClasses.java`:
- создание HudService и BossBarService: `initializeCoreServices()`, примерно строки 300–310;
- создание HpBarService после AttributeService/HpAttributeSync: примерно строки 340–365;
- регистрация HpBarService Listener: `registerGameplayListeners()`, примерно строки 380–410;
- запуск HUD tasks: `startRuntimeTasks()`, строки 423–439.

Текущие правила запуска:
- HudService task запускается только если одновременно `hud.enabled=true` и `hp-display.mode=vanilla`;
- HpBarService task запускается всегда;
- BossBarService task запускается всегда, а настройка `hud.boss-bar.enabled` проверяется внутри tick.

### Reload

`reloadPlugin()`:
- вызывает `hud.applyConfig()`;
- вызывает `bossBars.applyConfig()`;
- не перезапускает/не останавливает HpBarService task;
- не пересчитывает условие запуска HudService task, которое было применено только при startup.

**HUD-001 подтверждён по source:** после reload смена `hud.enabled` или `hp-display.mode` не приводит к согласованному пересмотру запуска HudService. Поскольку HudService не владеет task и `applyConfig()` не управляет им, возможны отсутствующий HUD или два конкурирующих ActionBar producer при переходе между режимами. Это source-level finding; live-воспроизведение не выполнялось.

### Shutdown

`shutdownRuntime()`:
- сначала закрывает общие runtime owners и останавливает sweep;
- отменяет общий `activeTasks`;
- затем вызывает `hpBarService.saveAll()`;
- вызывает `bossBars.shutdown()`;
- HudService не имеет отдельного cleanup для per-player maps и не удаляет явно текущий ActionBar;
- Bukkit автоматически снимает зарегистрированные plugin listeners при отключении, но отдельного HudService listener нет.

## 4. Конфигурационные контракты

Текущие ключи в `src/main/resources/config.yml`:

### `hud.*`
- `hud.enabled` — default true;
- `hud.update-period-ticks` — default 10;
- `hud.skip-spectator` — default true;
- `hud.full-resource-aura` — default true;
- `hud.debug-skip` — default false;
- `hud.boss-bar.enabled` — default true;
- `hud.boss-bar.min-duration-seconds` — default 8;
- `hud.boss-bar.max-visible` — default 2;
- `hud.boss-bar.tick-period` — default 5;
- `hud.boss-bar.format` / `format-infinite`.

### `hp-display.*`
- `mode` — default `actionbar`; значение `vanilla` переключает ресурсный ActionBar на HudService;
- `gauge`, `gauge-length`, `hearts-scale`;
- `update-period-ticks` — default 10;
- `colors.*`, `gradient.*`, `regen-spark.*`, `format`.

### `messages.hud.*`
- DoT format, separator, stacks templates and school icons.

### `storage.health-flush-seconds`
- default 60 seconds; относится к HP persistence, не к частоте HUD rendering.

Конфигурация распределена между `RaskolConfig` и прямыми чтениями `plugin.getConfig()` внутри HudService/HpBarService. Это нужно учитывать при проектировании immutable config snapshot и reload validation. Нельзя менять ключи без migration/compatibility tests.

## 5. Внешние и внутренние call sites

### HudService
- `RaskolClasses` создаёт его, запускает task и предоставляет `getHud()`;
- `BossBarService` использует `getHud().isVisible(player)`;
- в просмотренных startup/command участках не найдено caller для `HudService.toggle()`.

### HpBarService
Его API используется не только отображением. Проверенные call-site примеры:
- `DamageCaps`: formula/carrier max HP;
- `CombatService`: HP fraction для combat calculations;
- `WarriorAbilities`, `PriestAbilities`, `MageAbilities`: max HP / scale;
- `WarlockAbilities`, `WarlockPassives`, `RogueAbilities`: healing;
- `LightWardHandler`: healing;
- `PlaceholderSnapshotService`: HP и max HP placeholders;
- `SelftestRunner`: self-test;
- `RaskolClasses`: lifecycle и shutdown save.

Это подтверждает необходимость совместимой HP facade/port. Полный исчерпывающий список call sites должен быть повторно собран при implementation PR, поскольку GitHub code search индексирует default branch, а не выбранную feature branch.

### BossBarService
- создаётся, запускается, перезагружается и останавливается из RaskolClasses;
- напрямую обращается к HudService для проверки видимости.

## 6. Найденные проблемы и архитектурные риски

| ID | Приоритет | Наблюдение | Последствие |
|---|---|---|---|
| HUD-A-01 | High | HpBarService объединяет HP mechanics, persistence и rendering (639 lines) | Нельзя считать его чистым presentation module; высокий риск при переносе |
| HUD-A-02 | High | HudService task не принадлежит HudService; applyConfig не управляет task lifecycle | Некорректное применение runtime reload |
| HUD-A-03 | High | HpBarService и HudService могут быть двумя ActionBar producers при несогласованном reload режима | Конфликт/перезапись ActionBar |
| HUD-A-04 | Medium | HudService per-player maps не имеют quit cleanup | Накопление stale UUID state после выхода |
| HUD-A-05 | Medium | HudService пропускает hidden/no-class игроков без очистки per-player render state | Сохраняется устаревшее состояние/кэш до возможного повторного входа |
| HUD-A-06 | Medium | BossBarService напрямую зависит от HudService visibility | Смешаны независимые настройки effect widget и resource HUD |
| HUD-A-07 | Medium | Task ownership и lifecycle разнесены между root, HudService, BossBarService и HpBarService | Сложнее обеспечить reload/disable/recovery invariants |
| HUD-A-08 | Medium | Конфиг HUD читается из нескольких мест и разными способами | Риск частично применённой конфигурации и дрейфа defaults |
| HUD-A-09 | Low | В текущем test tree нет отдельного hud test package | Нет изолированной проверки HUD lifecycle/render contracts |
| HUD-A-10 | Medium | BossBar registry не координируется с другими владельцами BossBar на сервере | Возможны конфликты UX/лимитов и конкурирующие полосы |

Приоритеты — архитектурная оценка, не оценка подтверждённых production incidents.

## 7. Целевые границы после аудита

Рекомендуемые seams:

1. **Health application/query facade** — остаётся владельцем HP mechanics и предоставляет узкий read model для HUD. Healing и HP calculations не принадлежат HUD.
2. **Health persistence owner** — отдельная ответственность, сохраняющая текущий формат `health.yml`, dirty-generation semantics и общий AsyncStorageWriter.
3. **PlayerHudSnapshotProvider** — собирает read-only данные из character/resource/health/effect/target/combat источников. Не предоставляет HUD прямые сервис-локаторы.
4. **HudCoordinator** — владеет dirty/coalescing/refresh policy и lifecycle HUD.
5. **Widget renderers** — преобразуют snapshot в конкретный presentation output.
6. **Channel arbitration** — единый владелец ActionBar/BossBar/Scoreboard output, который не позволяет независимым виджетам бесконтрольно перезаписывать один канал.
7. **Player session cleanup** — удаляет все per-player HUD state и визуальные handles на quit/world transitions, где это требуется.

Не создавать общий Renderer interface только ради одного адаптера. Для каждого канала сначала определить реальные различающиеся реализации и требования.

## 8. Рекомендуемый следующий этап

HUD-A source inventory завершён. Следующий шаг — **HUD-B.1: Health & Resource Read Contracts**:

- отделить минимальные read-only данные от HP mechanics;
- определить snapshot value types и семантику отсутствующих данных;
- определить владельцев resource/character/effect/target state;
- собрать полный список callers на актуальной feature branch перед изменениями;
- не менять YAML, PDC, heal semantics, health.yml и публичные compatibility facades.

После этого — HUD-B.2: определить `PlayerHudSnapshot`, revision consistency и widget contracts. Реализацию task ownership отложить до HUD-C.
