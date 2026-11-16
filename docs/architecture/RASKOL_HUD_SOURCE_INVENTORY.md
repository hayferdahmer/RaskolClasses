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


## 9. Актуализация source inventory после A-03 runtime ownership

**Эта секция supersedes устаревшие описания task ownership и reload из разделов 2–5 выше.** Первоначальные SHA ниже относятся к раннему HUD-A срезу; актуальные feature-branch blobs проверены повторно.

### 9.1. Актуальные исходники (feature branch)

| Файл | Текущий blob SHA | Строк |
|---|---|---:|
| `hud/HudService.java` | `aead6955344f34b37be4337a229eb3e4caf926b7` | 321 |
| `hud/BossBarService.java` | `2779fc779d3b5fd32d60012e092b7bef84c2e0b7` |  |
| `hud/HpBarService.java` | `d51a26e1583e20c8e82761941f785ecfb73496a3` |  |
| `RaskolClasses.java` | `3a57979e107939836aff76deedfdb357f0be7b4c` | 1260 |
| `config.yml` | `dcce8c9b8240d45acd2c3cfed0205ff1b8053242` | 2594 |
| `hud/runtime/HudRuntimeOwner.java` | `5e84d6920020c53311d35ef9afd72a1134431701` | 189 |
| `hud/runtime/BossBarRuntimeOwner.java` | `2ffab0424f2f4716bd845075aa126ca72685273d` | 138 |
| `hud/runtime/HpBarRuntimeOwner.java` | `e529d545a0e4e736549793d2e74795d6d3e91c8f` | 151 |
| `ability/CooldownManager.java` | `7a412ffb9593c604f1572d5aaa4ddbfaaf59319f` | 360 |
| `hook/PlaceholderSnapshotService.java` | `4713a57d452dec76a43aa608d8262b7214c68bba` | 127 |

### 9.2. Текущее распределение ownership

- `HudService` больше не планирует собственную задачу. Его `tick()` — один цикл рендеринга; периодическая задача и quit cleanup принадлежат `HudRuntimeOwner`.
- `BossBarService` больше не владеет scheduler task. `BossBarRuntimeOwner` владеет периодическим producer и вызывает `clearBars` при restart/close.
- `HpBarService` остаётся смешанным HP gameplay/persistence/presentation сервисом. Listener registration и периодический tick переданы `HpBarRuntimeOwner`; health.yml, dirty flush, healing, HP formulas и respawn callbacks остаются вне RaskolHUD ownership.
- `RaskolClasses` создаёт и запускает эти owners через composition root; shutdown закрывает их до финальных HP snapshots и до закрытия общего StorageWriter.
- `reconcileHudRuntime()` вызывается при reload и согласует ActionBar HUD с парой `hud.enabled && hp-display.mode == vanilla`. Это устраняет первоначальную проблему, когда `applyConfig()` менял только cache/config, но не task lifecycle.
- `PlaceholderSnapshotService` остаётся отдельным optional integration snapshot provider, не частью renderer pipeline.

### 9.3. Фактические ActionBar producers и открытый конфликт

На актуальной ветке обнаружены три независимых отправителя в один клиентский ActionBar channel:
1. `HudService` — class resource + DoT;
2. `HpBarService` — unified HP + resource, когда `hp-display.mode != vanilla`;
3. `CooldownManager` — transient ready notification.

Режимы `HudService` и `HpBarService` на startup/reload взаимоисключаются текущим gate, но `CooldownManager` по-прежнему отправляет ActionBar независимо. Это подтверждает необходимость будущего `HudChannelCoordinator`: временное уведомление должно вытеснять persistent frame по TTL, после чего persistent HUD должен восстанавливаться без гонки независимых writers.

### 9.4. Что ещё не реализовано

- Единого immutable `PlayerHudSnapshot` в runtime pipeline нет.
- Нет production `HudCoordinator` с dirty coalescing и bounded fallback refresh.
- Нет production `HudWidgetRegistry` и `HudChannelCoordinator`.
- `HudService`, `HpBarService` и `BossBarService` всё ещё читают игровые сервисы напрямую; текущие owners решают lifecycle, но не data/presentation separation.
- Target domain contracts/store/application service существуют в коде, но Paper interaction adapter и production bootstrap wiring не активированы.
- Scoreboard остаётся отложенным до решения о совместимости с внешними плагинами.

### 9.5. Актуальные риски

| ID | Статус | Уточнение |
|---|---|---|
| HUD-A-01 | Open | HpBarService всё ещё совмещает gameplay, persistence и presentation. |
| HUD-A-02 | Resolved at runtime-owner layer | HudRuntimeOwner управляет start/stop; reload вызывает reconcileHudRuntime. |
| HUD-A-03 | Partially contained | Основные persistent producers взаимоисключаются legacy mode gate; transient CooldownManager всё ещё пишет напрямую в ActionBar. |
| HUD-A-04 | Resolved for HudService cache | Quit cleanup делегируется HudRuntimeOwner. |
| HUD-A-05 | Open / re-audit | Нужно проверить очистку per-player cache при скрытии/потере класса, а не только при quit. |
| HUD-A-06 | Open | BossBarService всё ещё зависит от HudService.isVisible(player). |
| HUD-A-07 | Partially resolved | У legacy HUD producers появились отдельные owners; единого coordinator/channel owner пока нет. |
| HUD-A-08 | Open | Конфиг читается из RaskolConfig и напрямую из Bukkit config в нескольких сервисах. |
| HUD-A-09 | Partially resolved | Есть runtime-owner tests; snapshot/widget/channel contract tests ещё не реализованы. |
| HUD-A-10 | Open | Политика сосуществования с чужими BossBars/Scoreboard не принята. |

### 9.6. Следующий implementation slice

Следующий шаг — **HUD-C.2: immutable snapshot pipeline и coordinator core**. Он должен реализовать только Bukkit-independent value types/coordinator orchestration и deterministic tests; не подключать новые renderers и не менять существующий визуальный вывод. ActionBar arbitration — отдельный HUD-C.3 slice, после которого legacy senders мигрируют на единый publisher.


## 10. HUD-C.2 / HUD-C.3 — реализация snapshot и channel ownership (2026-11-16)

**Эта секция supersedes статусы разделов 9.4 и 9.6 выше.** Раздел 9 сохраняется как исторический source audit до реализации C.2/C.3.

### 10.1. Реализовано

- `hud/snapshot/PlayerHudSnapshot` и вложенные immutable views.
- `hud/application/HudCoordinator`: dirty coalescing, bounded refresh, revision publication и изоляция ошибок источника/виджета/publisher.
- `hud/channel/HudChannelCoordinator`: единый arbitration owner для ActionBar и HUD-owned BossBar.
- `hud/channel/HudChannelOutput`: Paper-independent output seam.
- `hud/runtime/PaperHudChannelOutput`: Paper adapter, проверяющий main thread и управляющий только созданными RaskolHUD BossBar handles.
- ActionBar priority: больший priority выигрывает; для равного priority более поздняя публикация выигрывает; внутри одного snapshot tie разрешается стабильным widget ID.
- Notifications хранятся отдельно от snapshot, обязаны иметь finite TTL и после expiry/cancel восстанавливают следующий доступный ActionBar frame.
- Несколько BossBar могут быть активны одновременно; идентификатор ownership — player UUID + widget ID.
- Scoreboard channel fail-fast disabled до утверждения внешней compatibility policy.

### 10.2. Границы текущей интеграции

- C.2/C.3 core и Paper output adapter реализованы, но ещё не подключены в `RaskolClasses` composition root.
- `HudService`, `HpBarService`, `CooldownManager` продолжают использовать прежний ActionBar path.
- `BossBarService` остаётся legacy owner; новые BossBar frames пока не подаются в Paper adapter из production pipeline.
- Поэтому этот этап не устраняет конкуренцию legacy writers на работающем сервере и не меняет текущий визуальный HUD.
- ActionBar ownership координируется внутри RaskolClasses; глобальная арбитрация с произвольными внешними плагинами не гарантируется клиентским ActionBar API.

### 10.3. Проверки и следующий slice

- GitHub Actions: **712 tests, 0 failures/errors/skipped**; SpotBugs 0; Spotless clean; license headers passed.
- Build/Analyze: [run 43954439931](https://github.com/hayferdahmer/RaskolClasses/actions/runs/43954439931).
- Spotless + final verification: [run 43954439872](https://github.com/hayferdahmer/RaskolClasses/actions/runs/43954439872).
- Следующий этап: **HUD-C.4 — Legacy Producer Migration & Runtime Wiring**. Перед отключением старых writers необходимо подключить один owner, перенести cooldown notifications на TTL API, сохранить legacy config semantics и добавить integration tests на reload/quit/disable.
- Live Paper 26.2 / Java 25 staging smoke test остаётся обязательным до включения нового pipeline на production.


## 11. HUD-C.4.1 — ActionBar migration и optional client mod

### 11.1. Подтверждённый production path

- `RaskolClasses` создаёт один `HudChannelCoordinator` + `PaperHudChannelOutput`.
- `HudService` публикует persistent class/resource/DoT frame (`legacy-class-resource`, priority 40).
- `HpBarService` публикует persistent HP/resource frame (`legacy-health-resource`, priority 50); его игровые расчёты и persistence не переносились.
- `CooldownManager` публикует transient ready notifications (priority 100, TTL 3 секунды).
- `HudRuntimeOwner` обслуживает и legacy HUD refresh, и expiry tick channel coordinator; второй periodic owner не создавался.
- Session cleanup очищает per-player channel state. Shutdown отменяет cooldown producers до очистки и закрытия coordinator.
- Production legacy producers используют coordinator, а не прямой `Player.sendActionBar`. Низкоуровневый Paper send выполняется в `PaperHudChannelOutput`.

### 11.2. Ограничения

- Snapshot `HudCoordinator` и production `HudSnapshotProvider` пока не подключены к composition root. Legacy frames — временный migration API.
- `BossBarService` публикует per-effect frames в `HudChannelCoordinator`; Paper BossBar handles принадлежат `PaperHudChannelOutput`.
- ActionBar arbitration координирует только producers RaskolClasses. Внешние плагины могут отправлять собственный ActionBar и перезаписывать его.
- Clearing ActionBar может очистить текущее сообщение канала независимо от того, какой плагин отправил его последним; глобальной ownership-механики Paper API не предоставляет.
- Клиентский мод — отдельный optional renderer. Сервер остаётся authoritative; handshake, versioned wire protocol и display-safe DTO будут спроектированы после просмотра исходников/контрактов отдельного mod repository. До этого сетевой протокол не реализуется.
- Отсутствие мода обязано оставлять vanilla fallback рабочим; Resource Pack optional и независим от клиентского мода.

### 11.3. Следующий slice

**Следующий этап — HUD-C.5: Production Snapshot Provider & Read Model Wiring.** Подключить реальные источники HP/resources/attributes/effects/DoT/target к `HudCoordinator`, сохранив typed availability states, bounded refresh, per-player revisions и текущий channel arbitration. Клиентский mod bridge проектировать отдельно после просмотра его исходников.


### 11.4. Verification

Финальный проверенный кодовый HEAD: `310ba30a5a90f7dde46202d54be4cb6fd84ab13f`.
- Maven verify: **717 tests, 0 failures/errors/skipped**; license headers passed.
- SpotBugs: **0 findings**.
- Spotless format + final branch verification: **SUCCESS**.
- [Build + Analyze run 43959893978](https://github.com/hayferdahmer/RaskolClasses/actions/runs/43959893978).
- [Spotless run 43959893936](https://github.com/hayferdahmer/RaskolClasses/actions/runs/43959893936).
- Live Paper 26.2 / Java 25 staging smoke test остаётся отдельным гейтом.


## 12. HUD-C.5 — Production Snapshot Provider & Read Model Wiring

### 12.1. Подключено

- Добавлен `PaperHudSnapshotProvider` — единственный Paper read adapter для immutable `PlayerHudSnapshot`. Он проверяет main thread и требует актуальную online-сессию.
- Provider копирует в snapshot class identity/display name, выбранную specialization/role, formula HP, effective resource, STR/AGI/INT, активные эффекты, DoT на самом игроке и combat flag.
- Все mutable источники копируются в immutable values: effect map → `EffectView`, mutable `DotInstance` → `DotView`, resource state → `ResourceSnapshot`.
- `ResourceService.readSnapshot` возвращает `Optional<ResourceSnapshot>`: не создаёт ResourceState, не меняет resource ceiling и не маскирует ещё не загруженное состояние нулём. Effective maximum учитывает актуальные specialization/item bonuses; отображаемое значение клампится только в read projection.
- `HudCoordinator` подключён к composition root и получает bounded round-robin fallback budget **64 players per HUD refresh cycle**. Capture/publish выполняется синхронно на Paper main thread; revision увеличивается только после успешной публикации.
- Session quit очищает snapshot/cache state через `HudCoordinator.onPlayerQuit`; shutdown закрывает snapshot coordinator до channel coordinator.
- Snapshot publication использует существующий `HudChannelCoordinator`. Пустой registry на этом этапе намеренный: `snapshotFrames` остаются отдельными от legacy persistent frames, поэтому C.5 не меняет текущую визуальную подачу и не стирает legacy ActionBar/BossBar.

### 12.2. Явные состояния, а не выдуманные значения

- Character class доступен, если ClassProvider его разрешил. Неизвестный класс остаётся `UNAVAILABLE`.
- Specialization/role доступны при валидной выбранной специализации; отсутствие выбора — `NOT_APPLICABLE`.
- Character level и XP пока `UNAVAILABLE`: текущий CharacterLevelService зависит от AuraSkills и не используется как будущий RPG Core read contract.
- Target пока `UNAVAILABLE(POLICY_PROVIDER_UNAVAILABLE)`: targeting application/Paper interaction runtime ещё не подключён к production root. Provider не подменяет Locked Target на crosshair target или на ложный `NONE`.
- Combat flag читается из combat window ResourceState; точное expiry timestamp пока unavailable, поскольку ResourceState не публикует last-combat timestamp.
- Theme ID берётся из class ID. `enabledWidgetIds` пока пустой набор без персонального HUD profile store; это не означает отключение всех будущих default widgets.

### 12.3. Границы C.5

- Widget registry пока пустой: snapshot read model уже собирается и хранится, но новые snapshot widgets ещё не создают presentation frames. Legacy producers продолжают работать через отдельный channel ownership path.
- Dirty coalescing API остаётся доступным, но production invalidation events пока не подключены; сейчас используется ограниченный round-robin fallback refresh.
- Никаких client-mod packets, handshake или wire DTO не добавлялось. Server read model остаётся общей основой для будущего vanilla и optional mod renderer.
- Live Paper 26.2 / Java 25 staging smoke test остаётся обязательным перед production rollout.
