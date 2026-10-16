# Changelog

Формат: [Keep a Changelog](https://keepachangelog.com/ru-RU/1.1.0/).
Версионирование: семантическое. Внутренние волновые метки (1.14.1–1.14.3)
вышли внутри релиза 1.14.4 и перечислены в его секции.
История НЕ переписывается задним числом; даты = даты тегов.

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

[Unreleased]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.9...HEAD
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
