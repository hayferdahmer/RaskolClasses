# Changelog

Формат: [Keep a Changelog](https://keepachangelog.com/ru-RU/1.1.0/).
Версионирование: семантическое. Внутренние волновые метки (1.14.1–1.14.3)
.shipped внутри релиза 1.14.4 и перечислены в его секции.
История НЕ переписывается задним числом; даты = даты тегов.

## [Unreleased] — Sprint 1 (аудит 1.14.6, к релизу 1.14.7)

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

[Unreleased]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.6...HEAD
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
