# Changelog

Формат ведётся по [Keep a Changelog](https://keepachangelog.com/ru-RU/1.1.0/).
Проект следует семантическому версионированию.

## [1.14.6] — 2026-10-09

### Added
- **Пет-система** (`dev.raskol.classes.pet`): единый сервис боевых петов вместо ad-hoc карт в китах.
  - `PetDef` — реестр определений (wolf / demon / shadowfiend), базы HP/DMG/ttl/шаблон имени/атрибут-теги.
  - `PetMath` — pure-математика scaling (hp = base + attr×2; dmg = base + power×0.3) × spec2-узлы `pet_hp_pct` / `pet_dmg_pct`, NaN-guard.
  - `PetService` — жизненный цикл: спавн через `World.spawnEntity` + явный cast к `LivingEntity`, PDC-метки `rc_pet_owner`/`rc_pet_id`, tick 20 т (ttl, follow >12 блоков с телепортом в safe spot, окончание баффов), смерть через `EntityDeathEvent`, дезспавн на `PlayerQuitEvent`.
- `PetService.buff(...)` — универсальный множитель dmgMult × speedMult × glow на N секунд; `PetService.consume(...)` — поглощение пета с VFX.
- Боевые гейты петов: `canHit` для союзников/владельца в `EntityDamageByEntityEvent`, `EntityTargetEvent` блокирует таргетинг союзников, ретаргет на цель владельца при ударе.
- Перевод `petWolf` / `beastFerocity` / `bestialWrath` (Охотник), `summonDemon` / `demonSoul` (Чернокнижник), `shadowfiend` (Жрец) на делегирование в `PetService`.

### Changed
- Удалены static-карты `PET_WOLF` (HunterAbilities) и `DEMON_BY_OWNER` (WarlockAbilities) — PetService теперь единственный источник истины.
- Удалён inline-спавн `Vex` с `setLimitedLifetime` в WarlockAbilities/PriestAbilities — ttl теперь в PetDef.
- `HunterAbilities.forgetPet()` оставлен как no-op для обратной совместимости.
- `WarlockAbilities.onPlayerQuit` больше не чистит карту демонов (это делает PetService.onOwnerQuit).
- Selftest расширен до **111 чеков**: 108 (PetDef-реестр), 109 (PetMath scaling), 110 (имена по шаблону), 111 (ttl/teleport pure-логика).

### Fixed
- Paper 1.21.4 совместимость: `org.bukkit.attribute.Attribute` (а не `org.bukkit.Attribute`); ключи реестра без префикса `generic.` (`max_health` / `attack_damage` / `movement_speed`).
- Спавн сущностей через `spawnEntity` + `instanceof LivingEntity` (wildcard `getEntityClass()` не проходил generic-вывод `World.spawn`).

## [1.14.4] — 2026-10-08

### Added
- Волна 4 аудита (П1–П10): `ElementalResistService` для resist-узлов spec2; `Spec2Service.resetNode` с возвратом очков; `Spec2Points.configure(this)` читает старт/макс/гейты рядов из конфига.
- `RaskolConfig.classDouble` / `classInt` / `classString` — per-class чтение через KitConfigLoader.

### Changed
- Spec2-очки: 14→0, 15→1, 40→26, 60→46, 99→46 (ёмкость дерева arms=51 — закрыть нельзя).
- Selftest: 105 (resist-узел → ElementalResistService), 106 (resetNode: spentGlobal −1), 107 (Spec2Points из конфига).

### Fixed
- Отложенный restore HP на join: `HpBarService` теперь дожидается загрузки класса LuckPerms (или таймаут 5 с) перед применением ratio — устранена геометрическая потеря HP между релогами.
- `pom.xml`: добавлена compile-зависимость `net.luckperms:api:5.4`; `<finalName>raskol-classes-${project.version}</finalName>` для совместимости с workflow.
- `plugin.yml`: `description` обновлён под 1.14.4 (респецы/spec2-storage); `version: ${project.version}` для автоподстановки.

## [1.14.3] — 2026-10-07

### Added
- Wave 3: heal-агрегат (heal_out_pct / heal_received_pct), павер-множители (wp/sp/hpow_pct), потолок ресурса (ResourceState.setCeiling с NaN-guard), боевые pct-агрегаты (phys/magic/block/crit/hp/move) + maxHp ×1.10 от hp_pct.

### Changed
- Ролевой пред-множитель HEALER убран из `applyHealWith` — теперь единой точкой в `Spec2RoleListener.onCustomHeal` (убрана двойная раскрутка 1.05×1.05).
- Кэш дельты max-health-модификатора в `HpBarService` (убран сетевой спам).

## [1.14.2] — 2026-10-06

### Added
- Волна 2: централизованный гейт древесных способностей в `AbilityRegistry.castOn` — любой `treeCasterId` требует `Spec2Service.hasUnlocked`, даже если метод кита забыл `treeUnlocked()`.

### Changed
- Selftest-чек 99: двунаправленное покрытие `unlock_ability ↔ кастеры` без дыр.

## [1.14.0] — 2026-10-05

### Added
- **Деревья путей «Спек 2.0»**: 18 деревьев × 3 специализации, 46 очков с 15 уровня, гейты рядов.
- Legacy-слой (talent/*, spec/Spec*, specs.yml) УДАЛЁН — единственная система спеков/талантов это spec2.
- 69 древесных способностей: 9 Воин, 14 Охотник, 16 Разбойник, 11 Маг, 9 Жрец, 10 Чернокнижник.
- Переносимые китовые (slots 4–5): `TransferableAbilities` реестр; `AbilityRegistry.getBySlotOrTree` для переходного периода.
- Универсальные фолбэк-хелперы `TreeAbilities.numberOrKit`/`intOrKit`/`stringOrKit`/`durationOrKit` (treeAbilities → abilities → код-дефолт).

### Changed
- Spec2 теперь единственный источник роли класса (`Spec2Service.mainSpec`).

## [1.13.0] — 2026-10-01

### Added
- CC-слой: `CCService`/`CCGuard`/`CastGuard`, 6 категорий DR, `VanillaCCWrapper`, `CastChannels` для прерывания каналов.
- Selftest 76–90 покрывает CC/DR.

## [1.12.3] — 2026-09-28

### Added
- Школы урона (PHYSICAL / FIRE / FROST / NATURE / SHADOW / HOLY / ARCANE / TRUE), `SchoolConfig`, `SchoolMitigation`, `SchoolImmunity`, `Penetration`, `PenTraitsService`, `ElementalResistService`.
- ThreadLocal cast-school контекст в `CombatService`.

## [1.12.0] — 2026-09-25

### Added
- DoT-слой: `DotService`/`DotMath`/`DotInstance`, 5 базовых DoT (burning/poison/bleed/chilled/wither), cap-DPS от maxHp, средовые триггеры.
- `BalanceSimulator.matrix` 6×6 для TTK-аудита.

## [1.11.0] — 2026-09-20

### Added
- Книга класса (ClassBook) с вкладками: статы, способности, деревья, инсталляция, фолиант.
- Инсталляции: 6 типов (знамя / ловушка / вард / руна / дым / пентаграмма), `InstallToken` для свитков постановки.

## [1.10.0] — 2026-09-15

### Added
- Кит Чернокнижника (5 базовых способностей) + ресурс «Скверна» с декаем −4/с вне боя.
- «Чёрное Слово» v2: 10% HP → +25 Скверны, без лечения.
- Виртуальный пул HP (AttributeService + HpBarService с планом B).

## [1.0.0] — 2026-09-01

### Added
- Первый публичный релиз: 5 классов (Воин / Охотник / Жрец / Маг / Разбойник), 25 китовых способностей, ресурсы, кулдауны, активные эффекты.

[1.14.6]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.4...v1.14.6
[1.14.4]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.3...v1.14.4
[1.14.3]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.2...v1.14.3
[1.14.2]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.0...v1.14.2
[1.14.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.13.0...v1.14.0
[1.13.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.12.3...v1.13.0
[1.12.3]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.12.0...v1.12.3
[1.12.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.11.0...v1.12.0
[1.11.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.10.0...v1.11.0
[1.10.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.0.0...v1.10.0
[1.0.0]: https://github.com/hayferdahmer/RaskolClasses/releases/tag/v1.0.0
