# A-03-003.26 — FX and Projectile Runtime Task Ownership Audit

## Статус и границы

- Репозиторий: `hayferdahmer/RaskolClasses`
- Ветка: `refactor/a03-foundation-lifecycle`
- Проверенный HEAD: `eb44274b30a127c12b42d24f5e9c8986f35f7d92`
- Платформа: Paper 26.2 / Java 25
- Тип работы: source-backed lifecycle audit; production-код и игровое поведение не меняются.
- Область: `FxService`, `TrailListener`, вызовы VFX из installation handlers, root registration/shutdown и global purge.

Этот срез проверяет конкретный набор event-triggered и временных VFX-задач. Он не закрывает общий repository-wide scan всех Java source и не является разрешением на активацию `BootstrapCoordinator`.

## 1. FxService — фактическое владение

`FxService` одновременно является Bukkit listener, владельцем VFX state и держателем нескольких Bukkit task handles:

- `chargedShots` + `trailTasks`: per-projectile repeating trail, initial delay 0, period 2 ticks.
- `auras`: per-player repeating task, initial delay 0, period 10 ticks; рядом ставится отдельный delayed expiration callback.
- `ambients`: per-effect repeating task, initial delay 0, period `max(1, periodTicks)`.
- `procVisualCd`: временные cooldown timestamps без Bukkit task.

### 1.1 Charged projectile trail

- `chargeProjectile` создаёт repeating task и сохраняет handle в `trailTasks`.
- Task прекращается, если entity отсутствует/мёртва или запись projectile больше не существует.
- `onProjectileHit` удаляет charged-shot state и отменяет соответствующий trail handle.
- `purgeStale` удаляет expired charged-shot entries и отменяет orphan trail handles.
- Отдельного `close/stopAll` для этих handles нет.

Вывод: normal hit и stale purge имеют cleanup path; явного FX shutdown boundary в самом сервисе и root нет.

### 1.2 Aura tasks

- `startAura` сначала вызывает `stopAura`, затем создаёт repeating task и сохраняет `AuraTask(task, expiresAt)`.
- Отдельный delayed expiration callback не сохраняет BukkitTask handle.
- Callback сравнивает текущую запись по identity с `scheduledTask`; устаревший callback не удаляет и не отменяет новую ауру.
- `stopAura` отменяет repeating handle и удаляет запись, но не может отменить уже поставленный expiration callback.
- `purgeStale` отменяет repeating handles для истёкших записей.

Вывод: подтверждённая защита от stale callback существует. Остаточный delayed callback после ручной остановки/замены является bounded no-op при несовпадении identity, но не имеет явной cancellation ownership.

### 1.3 Ambient loops

- `startAmbient` создаёт repeating task и сохраняет `AmbientTask` по UUID.
- Callback удаляет и отменяет собственную запись после expiry или при отсутствии world.
- `stopAmbient` отменяет конкретный handle.
- `purgeStale` дополнительно отменяет истёкшие записи.
- Frost Rune вызывает `stopAmbient` из `expire`.

Вывод: локальный lifecycle для активной инсталляции существует; общий FX shutdown отсутствует.

## 2. TrailListener — projectile trail tasks

- Listener регистрируется root напрямую в `registerGameplayListeners`.
- Для подходящего снаряда создаётся anonymous `BukkitRunnable` с периодом 4 ticks.
- Задача сама отменяется при death/invalid projectile, превышении `MAX_TRAIL_TICKS = 200` или отсутствии world.
- Handle не сохраняется, поэтому нет адресной отмены при projectile hit, quit shooter или plugin shutdown через owner.
- Paper/Bukkit отменяет plugin-owned scheduled tasks при disable; это platform cleanup, а не явный lifecycle contract приложения.

Вывод: task имеет естественную верхнюю границу жизни, но его создание и cancellation не представлены отдельным owner/port. Проверить, нужна ли адресная отмена на hit, следует отдельно; не менять поведение в рамках аудита.

## 3. Installation handlers — граница ответственности

Проверены `FrostRuneHandler`, `PentagramHandler` и `InstallationService`:

- Frost Rune хранит ring task в `RuneState`; `expire` отменяет ring task и останавливает ambient loop.
- Pentagram хранит redraw task по installation UUID; `expire` отменяет handle.
- `InstallationService.shutdown()` вызывает `expire` для каждой активной инсталляции до очистки registries.
- `InstallationRuntimeOwner` останавливает периодический sweep до `InstallationService.shutdown()` в root lifecycle.

Вывод: ring/redraw tasks принадлежат lifecycle конкретной installation. Их нельзя переносить в общий FX owner: они используют state конкретного handler и завершаются через domain `expire`.

## 4. Root lifecycle и global purge

- Root регистрирует `FxService` как listener.
- Root регистрирует `TrailListener` как отдельный listener.
- `PluginTasks.purge()` вызывает `FxService.purgeStale()`; orchestration периодического purge находится в `PurgeRuntimeOwner`.
- Root shutdown закрывает runtime owners и вызывает `InstallationService.shutdown()`, но явного `FxService.close()`/cleanup вызова не найдено.
- `FxService` не должен закрывать shared scheduler или отменять чужие installation tasks.
- Bukkit автоматически отменяет plugin tasks и unregisters listeners при disable, однако эта гарантия не заменяет explicit owner semantics и не очищает явно domain maps до выгрузки.

## 5. Findings и приоритет

### P1 — отсутствует явная FX shutdown boundary

`FxService` сохраняет repeating handles и mutable registries, но не имеет идемпотентного close/clear path, вызываемого root. При нормальном disable scheduler отменит задачи, однако прикладной контракт cleanup не выражен и не тестируется.

Рекомендация для отдельного implementation slice:
- добавить узкую FX runtime ownership boundary, не перенося игровые расчёты и VFX rendering;
- явно отменять charged trail, aura и ambient handles;
- очищать соответствующие runtime maps;
- не закрывать общий Bukkit scheduler;
- обеспечить idempotent close и безопасный вызов после частично успешного старта.

### P2 — неотменяемый aura expiration callback

Delayed callback в `startAura` не сохраняет handle. Identity guard защищает новую ауру от старого callback, поэтому подтверждённой порчи состояния нет. Вариант с отдельным expiration handle можно рассмотреть при реализации owner, если он не усложнит replacement semantics.

### P2 — projectile trail в TrailListener без handle ownership

Задача bounded 200 ticks и сама отменяется. Отдельный owner может улучшить явную отмену и тестируемость, но сначала требуется решить, нужен ли cancel на hit/disable и как сохранить текущие периоды/эффекты.

### Не finding — installation-owned VFX tasks

Frost Rune и Pentagram имеют domain cleanup через `expire`; `InstallationService.shutdown()` вызывает его для всех активных инсталляций. Не переносить их handles в FX runtime owner.

## 6. Решения по границам

- `FxService` остаётся владельцем VFX domain state и rendering behavior.
- Installation handlers остаются владельцами своих installation-scoped task handles.
- `TrailListener` рассматривается отдельно от `FxService`: он создаёт projectile trail tasks, но не делит charged-shot/aura/ambient state.
- Нельзя вводить единый глобальный owner для всех Bukkit tasks.
- В этом audit slice не менялись gameplay, визуальные эффекты, частоты, сроки жизни и публичные API.

## 7. Acceptance criteria

- [x] Проверены charged projectile trails и их completion/cancellation paths.
- [x] Проверены aura replacement, identity guard и expiration callback.
- [x] Проверены ambient loops и вызовы stop из Frost Rune.
- [x] Проверен TrailListener task lifetime и отсутствие сохранённого handle.
- [x] Проверены Frost Rune/Pentagram cleanup и InstallationService.shutdown.
- [x] Проверены root registration, shutdown и global purge call sites.
- [x] Зафиксировано решение не смешивать installation-scoped handles с общим FX ownership.
- [ ] Добавить focused tests для будущей FX shutdown boundary.
- [ ] Реализовать и подключить FX cleanup owner в отдельном implementation slice.
- [ ] Провести полный Build/Analyze/Spotless CI после implementation.

Срез `A-03-003.26` закрывает только аудит FX/projectile lifecycle. Полный repository-wide scan остаётся отдельным незакрытым gate.
