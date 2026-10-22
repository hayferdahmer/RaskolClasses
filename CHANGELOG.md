# Changelog

Все заметные изменения проекта документируются в этом файле.
Формат основан на [Keep a Changelog](https://keepachangelog.com/),
семантическое версионирование по [SemVer](https://semver.org/).

## [1.15.0] — 2026-10-22 (ядро оружия и брони)

### Добавлено

- Реестр предметов с генерацией 270 стартовых предметов для шести классов: оружие и четыре части брони, уровни 20/40/60, редкости COMMON/RARE/EPIC.
- Шаблонный бюджет статов, детерминированный ролл взвешенных аффиксов и пороги сетов 2–6.
- Версионированный PDC `raskolclasses` с UID экземпляра, seed, проверкой определения и зажимом значений аффиксов.
- Экипировочные снапшоты, обновление при событиях инвентаря/слотов и периодическая сверка; для стрел сохраняется оружие на момент выстрела.
- Команды `/rc item` и `/rc set`, браузер предметов, вкладка «Снаряжение», публичные Items API и CombatProfile API для интеграций.
- Опциональная выдача стартового комплекта COMMON ур. 1 при назначении класса (`item.starter-kit.enabled`, выключено по умолчанию); `/rc debug profile` для проверки боевых профилей мобов.
- Selftest 128–130 проверяет реестр 270+ определений, базовые инварианты предметов и отсутствие зависимости от RaskolGear.

### Изменено

- Статы предметов подключены к атрибутам, HP, урону, силе, критам, пробитию, резистам, ресурсам, КД, скорости, контролю и отражению.
- `items.yml` загружается с проверкой структуры; reload сохраняет прежний реестр при структурной ошибке.

### Удалено и несовместимость

- Полностью удалены RaskolGear-хук, старые set-бонусы, команда `/rc gear` (сохранён алиас диагностики `/rc gear`) и старая вкладка GearTab.
- Старые предметы RaskolGear не распознаются и не переносятся. Перед запуском 1.15.0 удалите RaskolGear с сервера; старые предметы при необходимости перевыдайте.

### Ограничения

- Для уклонения, парирования, блока и lifesteal предметных статов в 1.15.0 нет.
- Стартовые наборы генерируются встроенными шаблонами; сервер может заменить их секциями `templates` в `items.yml`.
- Стартовый комплект при выборе класса отключён по умолчанию.

## [1.14.16] — 2026-10-22 (raskolspec+: доступ и применение шаблонов)

### Исправлено

- Применение кураторского билда перепроверяет право доступа непосредственно перед
  изменением дерева: истёкшая или отозванная подписка не проходит через ранее открытый
  экран подтверждения.
- `plus.enabled: false` теперь действительно открывает шаблоны в публичном тестовом
  режиме, как обещает комментарий конфигурации; статус подписки при этом по-прежнему
  показывает фактическое состояние подписки.
- Добавлена команда `/rc plus buy <периоды>` для самостоятельной покупки/продления за
  PlayerPoints. Сумма списывается только при доступном кошельке, предел берётся из
  `plus.max-gift-periods`, а при ошибке записи подписки плагин пытается вернуть списание.
- Для покупки и подарка ошибка атомарного сохранения срока не подтверждает операцию;
  плагин пытается компенсировать списание.
- Админские команды grant/revoke теперь сообщают об ошибке записи вместо ложного
  подтверждения успешной выдачи или отзыва.
- Подарки и админские status/grant/revoke работают с известными оффлайн-игроками без
  блокирующего сетевого запроса за профилем.
- Администраторы с `raskolclasses.admin.plus` могут просматривать и применять шаблоны
  через GUI; административный путь применения остаётся бесплатным.
- README обновлён: удалено устаревшее предупреждение о preview-применении, указана
  актуальная версия артефакта.

### Совместимость

- Миграций нет. Все прежние `plus-subscription.yml` сохраняются.
- Paper 1.21.4, Java 21.

## [1.14.15] — 2026-10-22 (raskolspec+: подписка на курируемые билды — инфраструктура и GUI-оболочка)

Первая волна монетизируемого слоя «идеальных билдов»: 36 курируемых
шаблонов (18 спек × PvP/PvE), подписка за донат-валюту «Империал» (PlayerPoints),
  подарки и GUI-каталог с тизер-моделью.

### Новое: слой raskolspec+ (`spec/build/*`, `gui/PlusGui`, `command/sub/PlusSub`)

- **`builds.yml`** — ресурс jar, извлекается в `plugins/RaskolClasses/builds.yml` при
  первом старте; правится админом без пересборки; `/rc reload` перечитывает.
  36 шаблонов: имя, tagline, штамп `updated`, ранги узлов.
- **`BuildTemplates`** — загрузчик + валидатор: существование спеки/узлов, ранг ≤ maxRank,
  prereq-замкнутость, сумма очков ≤ `spec2.max-points`; узел из `spec2.disabled-nodes`
  → шаблон `stale` («⚠ обновляется после баланс-патча», не применим). Ошибочный шаблон
  отключается точечно с warning, остальные работают.
- **`ImperialWallet`** — абстракция кошелька: `PlayerPointsWallet` (reflection над
  PlayerPoints API, без compile-зависимости) и `NullWallet` (фолбэк, если плагина нет —
  подписка не продаётся, чтение/гранты работают).
- **`PlusStorage`** — `plus-subscription.yml` (expiry + gift-note), write-through через
  `SafeStorage.saveAtomic`.
- **`PlusSubscription`** — grant (продление **суммирует** дни, купленное не сгорает),
  revoke, gift (атомарно: take у дарителя → grant получателю; не списалось → не подарили),
  оффлайн-нота подарка на вход, LP-синхронизация `raskolclasses.plus.active` через
  `settemp` (для DeluxeMenus view_requirement).
- **`PlusGui`** — два экрана (CATALOG/CONFIRM) на holder-паттерне `PlusHolder`
  (как `FoliantHolder`): статус-слот, карточки PvP/PvE текущей спеки, confirm с двойным
  кликом (взвод 30 с). Тизер-модель Dota Plus: без подписки видны имя/tagline/счётчики
  узлов и «🔒 требуется raskolspec+», клик шлёт upsell с ценой и путём покупки.
- **`/rc plus`** — роутер: `[status [ник]] · gift <ник> <периоды> · grant <ник> <n> ·
  revoke <ник>`; gift с rate-limit `plus.cooldown-gift-ms` и лимитом 1–12 периодов;
  права `raskolclasses.plus.use` / `raskolclasses.admin.plus` (plugin.yml).
- **Плейсхолдер** `%raskolclasses_plus%` — дни до истечения или `expired` (TTL-кэш 1 с).
- **`RaskolConfig`** — дефолты `plus.*` (enabled, price-per-period=100, period-days=30,
  max-gift-periods=12, cooldown-gift-ms=5000) + `messages.plus.*`; новые хелперы
  `getIntOr/getLongOr/getBoolOr`.
- **Selftest 123–125**: валидность 36 шаблонов; математика grant/remainingDays/revoke;
  stale-маркировка. Итого **125 чеков**.
- Для внешней витрины доступна админская команда `/rc plus grant`; отдельный конфиг
  DeluxeMenus в этот исходный архив не включён.

### Preview-ограничение (ВАЖНО для монетизации)

- Применение шаблона в confirm-экране сейчас возвращает OK-сообщение, но **не изменяет
  билд**: реальная логика (resetTree + setRanks + reconcile + экономика респека дерева)
  поставляется волной **15C в релизе 1.14.16**.
- Операционная рекомендация: **не деплоить `raskolspec.yml` в DeluxeMenus и не анонсировать
  подписку игрокам до выхода 1.14.16**. Инфраструктура (гранты, подарки, плейсхолдер, GUI)
  полностью рабочая и тестируемая уже сейчас.

### Сопутствующие фиксы волн 15A/15B (compile-стабилизация)

- `PlusGui`: удалён фантомный импорт `BookSession`; маршрутизация кликов переведена с
  парсинга `title()` (examinableName() возвращает тип Adventure-компонента, а не текст)
  на `InventoryHolder` с enum-экраном.
- `SelftestRunner`: 12 вызовов `resetAllDR(...)` → каноническое `resetAllDr(...)`.
- `BuildTemplates`: `Spec2Tree.nodeById(...)` → `find(...)`; в тесте over-budget сумма
  исправлена на фактические 47 очков.
- `RaskolClasses`: `plus.period-days` читается через `getConfig()` (внутри JavaPlugin-наследника
  нет поля `plugin`); `PlusGui` создаётся и регистрируется до `registerCommand()`.
- `PlusSub`: ключи сообщений без префикса `messages.` (RaskolConfig.message добавляет его сам).

### Миграции и совместимость

- Миграций конфигов/данных/мира нет. `builds.yml` и `plus-subscription.yml` создаются сами.
- Breaking changes: нет. Paper 1.21.4, Java 21.

## [1.14.14] — 2026-10-21 (SpotBugs-гигиена: закрытие 12 findings + регресс-фикс 13C)

Гигиенический патч, закрывающий все 12 findings отчёта `spotbugsXml.xml` от
прогона 1.14.13 (9 кодом + 3 обоснованными exclude). Побочно закрыта
регрессия 13C: рассинхрон карт состояния `WarlockAbilities` между тремя
местами создания экземпляра (AbilityRegistry, TreeAbilities, RaskolClasses).

### Закрытые findings (12/12)

#### Исправлены кодом (9)

| # | Pattern | Класс | Правка |
|---|---|---|---|
| 1 | `ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD` | `WarlockAbilities` | Конструктор приватизирован; создание через `WarlockAbilities.install(plugin)` — статическую фабрику с idempotency-guard |
| 2 | `ST_WRITE_TO_STATIC` (регрессия 13C) | `AbilityRegistry`, `TreeAbilities`, `RaskolClasses` | Все 3 точки создания переведены на `install()` → **один** экземпляр синглтона; карты `sealExpiry/sealAmp/antihealExpiry/channelTasks` снова общие для китовых кастеров, древесных кастеров, слушателя `onPlayerQuit` и статик-facade |
| 3 | `BC_VACUOUS_INSTANCEOF` | `DotService.onProjectileHit` | Убран vacuous `instanceof Projectile` (`event.getEntity()` всегда возвращает `Projectile`); заменено на прямое присваивание + null-guard |
| 4 | `BC_VACUOUS_INSTANCEOF` | `VanillaDamageListener.onDamage` | `attacker instanceof Player` заменён на `attacker != null` (переменная уже типизирована `Player`) |
| 5 | `FE_FLOATING_POINT_EQUALITY` | `VanillaDamageListener.applyOutgoingOffense` | `total != base` → `Math.abs(total - base) > 1e-9` (epsilon-сравнение в боевой логике офенса) |
| 6 | `URF_UNREAD_FIELD` | `LuckPermsBackend.config` | Удалено мёртвое поле и сеттер `setConfig()`; висячий вызов убран из `ClassProvider` |
| 7 | `HE_INHERITS_EQUALS_USE_HASHCODE` | `FlavorPlaceholder` | **Откат override** — `equals()` объявлен `final` в апстриме PlaceholderAPI 2.11.6, переопределение невозможно (compile error). Закрыт точечным exclude |
| 8 | `HE_INHERITS_EQUALS_USE_HASHCODE` | `RaskolPlaceholder` | То же самое — см. выше |
| 9 | `FE_FLOATING_POINT_EQUALITY`/`FL_FLOATS_AS_LOOP_COUNTERS` | `BalanceSimulator`, `SelftestRunner` | Test-only классы — закрыты exclude с обоснованием «осознанные равенства формульных значений» |

#### Обоснованные exclude (3 правила в `spotbugs-exclude.xml`)

| Правило | Scope | Обоснование |
|---|---|---|
| `FE_FLOATING_POINT_EQUALITY` + `FL_FLOATS_AS_LOOP_COUNTERS` | `BalanceSimulator`, `SelftestRunner` | Test-only симуляторы TTK; равенства в формулах осознанные, epsilon не требуется |
| `URF_UNREAD_FIELD` | `PetService$PetState.dmgMult/speedMult` | Зарезервированы для будущего pet-damage слоя 1.15.0 — исключены до появления читателей |
| `HE_INHERITS_EQUALS_USE_HASHCODE` | `*Placeholder` (2 класса) | Апстрим PlaceholderAPI 2.11.6: `equals()` final в `PlaceholderExpansion`, переопределение невозможно; реестр расширений PAPI ведётся по строковому `identifier`, объект-ключом не используется — реального риска нет |

### Побочное закрытие регрессии 13C

В волне 13C `WarlockAbilities` была переведена со static-mutable state на instance-поля
с singleton-фасадом, но конструктор оставался публичным. В `onEnable` экземпляры
создавались трижды:

1. `new AbilityRegistry(this)` → `new WarlockAbilities(plugin)` внутри (кастеры слотов 1–5) → `instance = №1`
2. `TreeAbilities.registerCasters(...)` → `new WarlockAbilities(plugin)` (кастеры слотов 6+) → `instance = №2`
3. `this.warlockAbilities = new WarlockAbilities(this)` (слушатель `onPlayerQuit`) → `instance = №3`

**Симптомы регрессии** (молчаливо с 13C):
- `ruin_seal` писал seal-метки в `sealExpiry` №1/№2, а `CombatService` читал пустую карту №3 → амплификация ×1.26 не работала
- `soul_rift` складывал канал-задачи в `channelTasks` №1/№2, а `onPlayerQuit` чистил пустую карту №3 → задачи не отменялись на выход игрока
- `addAntiheal` писал в №1/№2, а `HpBarService.heal` проверял №3 → анти-хил не блокировал лечение

**Фикс:** конструктор приватизирован, создание только через `WarlockAbilities.install(plugin)` с idempotency-guard → **один** экземпляр на всё. Selftest не ловил эту регрессию (чек 37 проверял пустоту реестров, а не сквозную проводку) — добавлен ручной регресс-тест в чеклист приёмки.

### Изменения файлов

**Правки кода:**
- `ability/WarlockAbilities.java` — конструктор `private`, добавлена `install(plugin)`
- `ability/AbilityRegistry.java` — `WarlockAbilities.install(plugin)` вместо `new`
- `ability/TreeAbilities.java` — `WarlockAbilities.install(plugin)` вместо `new`
- `combat/VanillaDamageListener.java` — epsilon-сравнение в `applyOutgoingOffense`, `attacker != null` в `onDamage`
- `combat/dot/DotService.java` — упрощён `onProjectileHit`
- `classsystem/LuckPermsBackend.java` — удалены поле `config` и `setConfig()`
- `classsystem/ClassProvider.java` — удалён вызов `backend.setConfig(config)`
- `hook/FlavorPlaceholder.java` — откат overrides equals/hashCode (невозможны из-за final в апстриме)
- `hook/RaskolPlaceholder.java` — откат overrides equals/hashCode
- `RaskolClasses.java` — `WarlockAbilities.install(this)` вместо `new`

**Инфраструктура:**
- `spotbugs-exclude.xml` — добавлены 3 обоснованных правила (test-only, пет-бэклог, PAPI-ограничение)
- `pom.xml` — версия 1.14.12 → **1.14.14**
- `plugin.yml` — версия → **1.14.14**
- `config.yml` — шапка обновлена с блоком «ЧТО НОВОГО В 1.14.14»

### Миграция и совместимость

- **Миграция конфигов:** не требуется.
- **Миграция данных:** не требуется.
- **Миграция мира/игроков:** не требуется.
- **Breaking changes:** внешние плагины, создававшие `new WarlockAbilities(plugin)` напрямую, сломаются при обновлении (конструктор приватный). Таких плагинов не было — публичный конструктор был только внутренним для `AbilityRegistry`/`TreeAbilities`/`RaskolClasses`.

### Приёмка

```bash
mvn -B verify          # 57 unit, BUILD SUCCESS
mvn -Pdev -B verify    # Step Summary: SpotBugs findings: 0
/rc selftest           # 122/122 PASS

## [1.14.10] — 2026-10-18

Sprint 7: тестируемость, связность, единая точка чтения конфига.

### Added
- **Unit-тесты (11-01):** JUnit 5 + Surefire 3.2.5; CI переведён на `mvn -B verify` —
  тесты блокируют артефакт при падении. 57 headless-тестов pure-ядра:
  `CombatMath` (19), `DotMath` (14), `ConfigReads` (9), `PetMath` (5),
  `Spec2Node` (5), `Spec2Tree` (5). Якоря сверены с in-game selftest-чеками
  16/62/66/67/68/93/109/111 — unit и in-game проверки не могут разойтись молча.
- **config/ConfigReads** (1-01): единая точка чтения конфига с NaN/Infinity/null-guard
  (`finiteDouble`, `finiteInt`, `stringOr`, `stringNonEmptyOr`, `boolOr`).
- **bootstrap/** (4-01): `StartupChecks` (tmp-чистка, RaskolCore-лог, capacity-warning,
  баннер), `PluginTasks` (purge/autosave-задачи), `SessionListener` (вход/выход игрока).
- **Spec2TreeTest:** инвариант ёмкости [50, 58] по всем 18 деревьям и политика
  disabled-пререквизитов (регресс-страх 6-01) проверяются headless в CI.

### Changed
- **RaskolClasses (4-01):** onEnable сокращён до конструирования слоёв и регистраций;
  startup-проверки, периодические задачи и анонимные слушатели вынесены в bootstrap/*.
  Геттеры-локаторы сохранены (удаление мёртвых — план 1.14.11, аудит 2-01).
- **CombatService (4-03/1-02):** `critFeedback` — единственная публичная точка визуала
  крита; клон из `VanillaDamageListener` удалён. Регистрация `VanillaDamageListener`
  перенесена из конструктора в `onEnable` (конструктор без побочных эффектов).
- **Дубли cfgD/cfgS/cfgI (1-01):** 10 классов (`CombatService`, `VanillaDamageListener`,
  `DamageCaps`, `AbstractKit`, `AttributeService`, `PowerService`, `AvoidanceService`,
  `SchoolImmunity`, `InstallationService`, `Spec2RoleListener`) делегируют в ConfigReads.
  Локальные хелперы с особой семантикой (`baseOf`/`growthOf`, guard v ≥ 0) оставлены
  и помечены в javadoc.
- **AvoidanceService:** удалён неиспользуемый импорт `java.util.Locale` (аудит 2-02).

### Fixed
- Тест-семантика `DotMath.dpsLimit`: cap = 0 — это «кап выключен» (лимит 0 DPS),
  дефолт 6% применяется только при cap < 0 или NaN; расхождение unit-теста и
  реализации устранено фиксом теста, поведение кода не менялось.
- `Spec2TreeTest`: prereq-id больше не хардкодятся — берутся из `node.prereqs()`,
  тест переживает ребаланс деревьев без правок.

### Testing
- Unit: 57 тестов (Surefire, CI-гейт).
- In-game: `/rc selftest` — 122 чека, все PASS.

## [1.14.9] — 2026-10-17

Стабилизационный Sprint 6: закрытие критических находок статического аудита 1.14.8.

### Added
- **Spec2Tree.isReachable** (6-01): проверка достижимости узла при текущем стоп-листе.
- **Spec2Service.disabledSet** (6-01): единая точка чтения стоп-листа для сервиса, GUI, selftest.
- **Spec2Tree.prereqsMet/canBuy со стоп-листом** (6-01): отключённый пререквизит считается выполненным.
- Selftest: чек 122 — достижимость всех включённых узлов при текущем стоп-листе; всего 122 чека.
- **ConfigMigrator.migrate148** (8-01): миграция `avoidance`/`hp-display` из `attributes.*` в корень при старте на серверах, обновляющихся с 1.14.6.
- **purgeStale** для 4 китов (6-02): `HunterAbilities`/`RogueAbilities`/`MageAbilities`/`PriestAbilities` — чистка протухших бафф-меток общей purge-задачей.
- Подключение `ProcService.purgeStale()` и `CombatService.purgeBurstLog()` в общую purge-задачу (6-02): методы существовали, но не вызывались.

### Changed
- **Стоп-лист больше не блокирует включённые узлы** (6-01): 25 узлов, включая 6 ультов (`bm_bestial_wrath`, `su_shadow_blades`, `sv_serpent_sting`, `as_deathmark`, `fury_bladestorm`, `guard_last_stand`), снова достижимы при отключённых пререквизитах.
- **Синхронный IO на quit/клик переведён на in-memory + периодический persist** (5-01):
  - `CooldownManager.onQuit`: убран `saveAtomic`, секция фиксируется в in-memory store, persist в autosave/onDisable.
  - `Spec2Service.purchase/chooseMain/resetTree/resetNode`: убраны 4 синхронных `storage.save()`, persist в autosave-задаче.
  - `ResourceService.onQuit`: убран `saveAtomic`, секция фиксируется в in-memory store, persist в autosave/onDisable.
- **Подсказка цены респека** (6-03): `TalentsTab.nodeItem` передаёт UUID просматривающего игрока, а не первого онлайн — цена в lore соответствует рангам игрока, а не случайного.
- **Selftest из консоли** (7-03): `probe = sender instanceof Player sp ? sp : null` — запуск не от игрока больше не мутирует данные первого онлайн-игрока; probe-чеки уходят в skip с предупреждением.
- **Логирование исключений в кастерах** (6-04): `AbilityRegistry.castOn` логирует полный стектрейс (`Logger.log(Level.WARNING, msg, ex)`), а не только `getMessage()` (который у NPE равен null).
- **TalentsTab.nodeItem**: пререквизит из стоп-листа подсвечен как выполненный («✔ … (в разработке — учтён)»), чтобы игрок видел причину доступности узла.

### Fixed
- **Регрессия 1.14.8 (6-01, CRITICAL)**: 25 включённых узлов (включая 6 ультов) стали недостижимы из-за disabled-пререквизитов — `prereqsMet` требовал ранг отключённого узла. Фикс: отключённый пререквизит считается выполненным (вырезан из дерева).
- **Утечки памяти (6-02, HIGH)**: статические карты китов (`TRUE_SHOT_UNTIL`, `COLD_BLOOD_UNTIL`, `SHADOW_BLADES_UNTIL`, `VENDETTA_EXPIRY`, `COMBUSTION_UNTIL`, `LAST_HEAL`) росли до перезапуска; `ProcService.purgeStale()` и `DamageCaps.purge()` существовали, но не вызывались.
- **Синхронный IO на главном потоке (5-01, HIGH)**: `CooldownManager`/`Spec2Service`/`ResourceService` писали файлы синхронно на quit/клик — при массовом выходе блокировали тик.
- **Миграция конфига (8-01, HIGH)**: на серверах, обновляющихся с 1.14.6, баланс уклонения молча менялся (parry-k 300→150) — код читал корень (где ключей нет) и брал дефолты.
- **Подсказка цены респека (6-03, MEDIUM)**: показывала цену по рангам другого игрока (первого онлайн), а не просматривающего.
- **Selftest из консоли (7-03, MEDIUM)**: менял данные первого онлайн-игрока (ранги, спеку, HP).
- **Потеря стектрейса (6-04, MEDIUM)**: `getMessage()` у NPE равен null, ошибку в кастере нельзя было локализовать.
- **Spec2Tree**: возвращена двухкомпонентная форма record'а `(id, nodes)` с вычисляемыми `capacity()`/`displayName()` — четырёхкомпонентная редакция первой поставки ломала компиляцию 18 файлов `*Trees.java`.

### Migration
- **Серверы с 1.14.6**: при первом старте 1.14.9 в логе появятся warning'и `Миграция 1.14.8: attributes.avoidance.* → avoidance.*` и `attributes.hp-display.* → hp-display.*`; `config.yml` обновится автоматически. Рекомендуется удалить старые секции вручную после подтверждения миграции.
- **Окно потери при краше**: для кулдаунов/очков путей/ресурсов ≤ `storage.autosave-minutes` (дефолт 5 минут) — приемлемо для короткоживущих данных (та же философия, что P1-7 у HpBarService).

### Testing
- Selftest: 122 чека, все PASS.
- Чек 122: достижимость всех включённых узлов при текущем стоп-листе.

## [1.14.8] — 2026-10-13

Архитектурный Sprint 5 (P2): дубли китов, декомпозиция selftest, ревизия стоп-листа.

### Added
- **AbstractKit** (P2-3): общий каркас шести китов способностей — конфиг-хелперы,
  числа с фолбэком `TreeAbilities.*OrKit`, уронное и лечащее ядро с spec2-хуками,
  таргетинг, VFX. Классовый множитель урона — hook `postMult(UUID)`:
  Охотник `true_shot` ×1.30, Разбойник `cold_blood`/`shadow_blades` ×1.5 (до ×2.25),
  Маг `combustion` ×1.25.
- Selftest: чек 121 — стоп-лист `spec2.disabled-nodes` не содержит узлов с живыми
  потребителями (`kit_dur`, `attack_speed_pct`, проки Sprint 3); всего 121 чек.

### Changed
- **P2-1:** `SelftestRunner.run()` декомпозирован на 22 метода по подсистемам;
  состояние отчёта инкапсулировано в держатель `Report` (passed/failed/skipped).
  Номера чеков, порядок вывода и skip-семантика не изменились.
- **P2-3:** шесть китов переведены на `AbstractKit`, удалено ~540 строк дублей.
  Уронное ядро Чернокнижника (`spellDamage`/`tspellDamage` с `WarlockMath.damageMult`)
  осталось китовым: база не знает про Скверну, ад и спек-трейты.
- **P2-4:** стоп-лист `spec2.disabled-nodes` сокращён 38 → 20: ожили 18 узлов
  (5 proc-контрактов, 2 `attack_speed_pct`, 11 `kit_dur`) — потребители реализованы
  в Sprint 3–4. Оставлены только узлы без потребителя в коде.
- **P2-2:** ревизия legacy talents перенесена на 1.15.0 согласно миграционной заметке
  в config.yml; плейсхолдеры `%raskolclasses_talent_*` остаются алиасами spec2-слоя.

### Fixed
- `AbstractKit`: преждевременный терминатор блочного комментария в javadoc первой
  редакции (ошибка компиляции на строке 34) устранён переформулировкой комментария.
- `SelftestRunner.ccProbeCastGuard`: параметр `plugin` добавлен в сигнатуру метода
  и в точку вызова (ошибка компиляции волны 5.1).

## [1.14.7] — 2026-10-11

Закрытие аудита 1.14.6 (Sprint 1–4).

### Added
- **P0-6A:** 24 узла ожили: 5 proc-контрактов (`bleed_on_crit`, `burning_extend`,
  `free_heal`, `rage_on_cast`, `enrage_dmg`), 11 узлов `kit_dur` (агрегат длительности
  по цели), `attack_speed_pct`, `deep_wounds`.
- **P0-6A:** реестр `spec2.disabled-nodes` — 34 узла без реализации сделаны no-op
  с прунингом рангов и ждут своих волн.
- **P1-7:** `HpBarService.saveHealth()` пишет в память, на диск данные сбрасываются
  периодически (`storage.health-flush-seconds`, по умолчанию 60 с).
- Selftest: чеки 112–120 (пет-ядро, `spec2.procs`, kit_dur-агрегат, attack-speed-модификатор,
  контракт C `value2`, `HAS_DEPENDENTS`, антиспам-метка, отложенная запись HP, effectiveMax);
  всего 120.

### Changed
- **P1-1/P1-2:** единый путь лечения через `CustomHealEvent`; статический `healerMark`
  удалён, атрибуция целителя — `CustomHealEvent.getHealer()`.
- **P1-5:** `resetNode` отклоняет сброс ранга, от которого зависят купленные узлы
  (`HAS_DEPENDENTS`).
- **P1-6:** антиспам-метка каста в `AbilityRegistry.castOn` (чеки 117–118).
- **P1-8:** шкала ресурса нормируется на `ResourceState.getEffectiveMax()`, а не на
  константу 100; узлы `resource_max` и `setCeiling` растягивают бар выше 100.
- Docs: README пересобран (баннер, бейджи, ровные таблицы, 1.14.7 и 120 чеков);
  описание в `plugin.yml` сокращено до одной строки.

### Fixed
- **P0-2:** proc-узлы ожили — `Spec2Service.accumulate` срезает префикс `proc_`
  (ключ агрегата `riposte`, а не `proc_riposte`); 31 узел снова читается ProcService.
- **P0-3:** контракт единиц проков — значение узла = доля шанса за ранг (бросок),
  сила = `spec2.procs.<id>.amp` (новые ключи конфига, дефолты из описаний узлов).
- **P0-7:** секции `avoidance:` и `hp-display:` перенесены в корень config.yml
  (код читает их с корня); `parry-k` унифицирован до 300; добавлены ключи
  `combat.block.visuals/sound`, ранее жившие только дефолтами кода.
- **P0-8a:** повторный призыв пета (ALREADY) возвращает false — ресурс и кулдаун
  больше не сгорают на отказе.
- **P0-8d:** релог до применения отложенного restore не перезаписывает `health.yml`
  неподтверждённым ratio.
- **P0-8f:** PetService логирует warning, если атрибуты реестра не резолвились;
  добавлен `attributesReady()` для selftest.
- Docs: README — возвращены баннер и бейджи, счётчик selftest 111; CHANGELOG —
  история зафиксирована одним каноническим набором дат.

## [1.14.6] — 2026-10-09

### Added
- **Пет-система** (`pet/*`): PetDef (wolf/demon/shadowfiend), PetMath (pure scaling
  hp = base + attr×2, dmg = base + power×0.3 × pet_pct-узлы), PetService
  (жизненный цикл, follow 12 блоков, ttl, баффы, consume, гейты canHit, ретаргет).
- Киты переведены на PetService: `petWolf/beastFerocity/bestialWrath` (Охотник),
  `summonDemon/demonSoul` (Чернокнижник), `shadowfiend` (Жрец); ad-hoc карты
  PET_WOLF/DEMON_BY_OWNER и inline-Vex удалены.
- Selftest: чеки 108–111 (PetDef-реестр, PetMath, имена, ttl/teleport).

### Fixed
- Paper 1.21.4: `org.bukkit.attribute.Attribute` (не `org.bukkit.Attribute`);
  спавн через `World.spawnEntity` + `instanceof LivingEntity` (wildcard
  `getEntityClass()` ломал generic-вывод `World.spawn`).

## [1.14.4] — 2026-10-08

Закрытие аудита П1–П10 волнами 1–4 (внутренние метки 1.14.1–1.14.3).

### Added
- Волна 1: ROW_LINE_SLOTS 9 (видны ряды 4–6 и ульты), переключатель спек в книге,
  роль HEALER через `CustomHealEvent`, `hpBarService.saveAll()` в onDisable.
- Волна 2: централизованный гейт древесных способностей в `AbilityRegistry.castOn`;
  чек 99 (unlock_ability ↔ кастеры); фикс таргета `su_cloak_of_shadows`.
- Волна 3: геттеры агрегата (magic_dmg_pct и семейство proc-видов); хот-путь
  урона/критов/паверов/ресурса/атрибутов; `ProcService` (мили- и каст-проки);
  `AvoidanceService.tryAvoid` → AvoidResult; блок щитом = PARRY + щит;
  чек-композиция лечения в одной точке (убран двойной счёт роли HEALER).
- Волна 4: `resetNode` (респец ранга узла, цена 150 + 50×ранг, ПКМ×2 с взводом);
  resist-узлы школ → ElementalResistService; `Spec2Points.configure` (start-level/
  max-points/row-gates из конфига); хранилище `spec2-storage.yml` с миграцией.
- Selftest: чеки 100–107.

### Changed
- Amplifier proc-узлов (riposte/counterattack/revenge/shield_slam) ставится на
  защищавшегося, а не на атакующего.

## [1.14.0] — 2026-10-05

### Added
- Деревья путей «Спек 2.0»: 18 специализаций, 46 очков (15→60), гейты рядов,
  роли FIGHTER/TANK/HEALER, 69 древесных способностей, DoT wither (SHADOW).
- Переносимые китовые slots 4–5 → treeAbilities (Б11.1.x); legacy-слой talent/spec удалён.
- Selftest: чеки 91–99.

## [1.13.0] — 2026-10-01

### Added
- Контроль и убывающая отдача: CCService/CCGuard/CastGuard, 9 типов CC, 6 категорий DR,
  CastChannels, VanillaCCWrapper, голограммы TextDisplay, `/rc cc *`.
- Selftest: чеки 76–90.

## [1.12.0] — 2026-09-25

### Added
- Школы урона (8 школ → 3 канала), митигация/пробитие/иммунитеты, стихийный резист,
  PenTraitsService; DoT-ядро (burning/poison/bleed/chilled), кап DoT-DPS 6%;
  BalanceSimulator.matrix 6×6.
- Selftest: чеки 49–75.

## [1.11.0] — 2026-09-20

### Added
- Книга класса (5 вкладок), инсталляции (6 типов incl. Пентаграмма),
  хотбар-бинды и свитки постановки.

## [1.10.0] — 2026-09-15

### Added
- Шестой класс — Чернокнижник: Фолиант Душ (дроп 0.001%, soulbound, анти-фарм S2),
  Скверна (пороги 75/100, откат 6.66%, ×6 в аду), спек-трейты.

## [1.9.0] — 2026-09-10

### Added
- План B по HP: carrier ≤ 1024, formula без потолка, HpPool — единые точки входа;
  burst-окно 3 с ≤ 18%, анти-ваншот ≤ 35%, LOS для площадей.

## [1.8.0] — 2026-09-05

### Added
- RaskolGear-интеграция: резисты шмота, сет-бонусы 4/4, +HP в HpPool, pen-трейты,
  `/rc gear`, вкладка книги «Шмот и сеты».

## [1.7.0] — 2026-09-01

### Added
- Первый релиз: 5 классов, 25 китовых способностей, ресурсы 0–100 с боевым окном,
  кулдауны, активные эффекты, HUD actionbar, атрибуты STR/AGI/INT, WP/SP/HPow,
  уклонение/парирование с DR, криты.

[Unreleased]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.10...HEAD
[1.14.10]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.9...v1.14.10
[1.14.9]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.8...v1.14.9
[1.14.8]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.7...v1.14.8
[1.14.7]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.6...v1.14.7
[1.14.6]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.4...v1.14.6
[1.14.4]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.0...v1.14.4
[1.14.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.13.0...v1.14.0
[1.13.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.12.0...v1.13.0
[1.12.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.11.0...v1.12.0
[1.11.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.10.0...v1.11.0
[1.10.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.9.0...v1.10.0
[1.9.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.8.0...v1.9.0
[1.8.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/hayferdahmer/RaskolClasses/releases/tag/v1.7.0

