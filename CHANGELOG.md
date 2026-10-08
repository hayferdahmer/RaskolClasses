# CHANGELOG — RaskolClasses

Формат: [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/).
Лицензия: RASKOL Proprietary License v1.0 (см. LICENSE).

## [1.14.4] — 2026-10-08

Закрытие аудита «мёртвых эффектов» и GUI: пункты П1–П10 волнами 1–4.
Selftest: 94 → **107 чеков**.

### Волна 1 (GUI / HEALER / ёмкости)
- **Добавлено**
  - `BookSlots.ROW_LINE_SLOTS` расширен 5 → 9: ряды 4–6 и ульты видны в книге (П1).
  - Переключатель спек в `TalentsTab` (main + 2 secondary): вторичные деревья
    можно прокачивать и сбрасывать (П1).
  - `event/CustomHealEvent` — кастомное лечение с атрибуцией целителя; роль
    HEALER (+5% исходящего лечения) реализована через него (П3).
  - `HpBarService.saveAll()` вызывается в `onDisable` — HP не теряется при краше.
- **Изменено**
  - Композиция лечения собрана в одной точке (`Spec2RoleListener.onCustomHeal`):
    исходящие = heal_out_pct × роль HEALER; входящие = heal_received_pct + роль
    TANK. Убран двойной счёт роли HEALER в `PriestAbilities`.
- **Проверено**
  - Ёмкости всех 18 деревьев уже в диапазоне 50–58 (П6 закрыт ранее, чек 93).

### Волна 2 (unlock-гейты)
- **Добавлено**
  - Централизованный гейт в `AbilityRegistry.castOn`: любой id из
    `treeCasterIds` требует `Spec2Service.hasUnlocked` — ульты больше не
    протекают даже при забытом `treeUnlocked` в ките (П2).
  - `AbilityRegistry.hasCaster(id)` / `treeCasterIds()` + selftest-чек 99:
    двунаправленное покрытие unlock_ability ↔ кастеры.
- **Исправлено**
  - `RogueTrees.su_cloak_of_shadows`: цель эффекта была
    `cloak_of_shadows_cleanse` (без кастера) → стала `cloak_of_shadows`.

### Волна 3 (мёртвые эффекты: 3A/3B/3C1/3C2)
- **Добавлено**
  - Геттеры агрегата: `magicDmgPercent`, семейство proc-геттеров
    (`hpPercent`, `resourceMaxBonus`, `blockPercent`, `moveSpeedPercent`,
    `healReceivedPercent`, `petHpPercent`, …) (3A/3B).
  - Хот-путь: phys/magic_dmg_pct, wp/sp/hpow_pct, crit_melee_pct, hp_pct,
    move_speed_pct, block_pct, resource_max/resource_regen_pct применены в
    `PowerService` / `CombatService` / `AttributeService` / `ResourceState` (3B).
  - `combat/ProcService` — фасад proc-узлов: riposte, counterattack, revenge,
    shield_slam, second_wind, bleed_on_crit, apply_poison, poison_extend,
    burning_extend, vendetta_refresh, double_strike, expose, crit_bonus,
    savage, headshot, stealth_bonus, stealth_extend, reflect_magic,
    undodgeable, armor_pen (3C1/3C2).
  - `AvoidanceService.tryAvoid` возвращает `AvoidResult {NONE, DODGE, PARRY}`;
    блок щитом = PARRY + щит (детерминированно, без несуществующего
    `EntityDamageByEntityEvent.isBlocked()`).
  - Amplifier-семантика исправлена: riposte/counterattack/revenge/shield_slam
    усиливают следующий удар **защищавшегося**.
- **Изменено**
  - `ResourceState.setCeiling` — расширяемый потолок ресурса (fury_rage_pool).

### Волна 4 (респец узла / гигиена spec2)
- **Добавлено**
  - `Spec2Service.resetNode` + `NodeResetResult`: респец одного ранга узла,
    цена `spec2.node-respec-base` (150) + `spec2.node-respec-per-rank` (50) ×
    текущий ранг; GUI: ПКМ по узлу → взвод 30 с → ПКМ-подтверждение (П7).
  - Узлы `resist` с таргетом-школой (nature/fire/frost/…) аккумулируются в
    `agg.elResist` и применяются через `ElementalResistService`
    (source `spec2_el`) (П8).
  - `Spec2Points.configure(plugin)`: start-level/max-points/row-gates читаются
    из `spec2.*` конфига с фолбэком 15/46/[0,5,10,15,20,30] (П9).
- **Изменено**
  - Хранилище: `spec2.yml` → `spec2-storage.yml` + одноразовая миграция
    переименованием при старте (П10).
- **Тесты**
  - Selftest +8 чеков: 100–107 (heal-агрегат, павер-пропорции, потолок
    ресурса, proc-нейтральность, боевые pct, resist-школа, resetNode,
    конфиг-точки).

## [1.14.0] — 2026-10-03
«Спек 2.0»: 18 специализаций (3 на класс), деревья 20 узлов / 6 рядов,
бюджет 46 очков, гейты рядов [0,5,10,15,20,30], роли FIGHTER/TANK/HEALER,
11 категорий эффектов, per-tree хранение рангов, респец дерева за монеты,
69 древесных способностей, DoT wither (SHADOW), пассивка мага mana_soaked,
удаление legacy-слоёв talent/spec и specs.yml. Selftest 94 чека.

## [1.13.0] — 2026-09-14
Контроль (CC) и убывающая отдача (DR): 9 типов CC, 6 категорий DR, окно 15 с,
множители [1.0, 0.5, 0.25, 0.0], ccResist/ccPower/ccReduction, гейты каста,
прерывание каналов (CastChannels), обёртка ванильных зелий, голограммы
TextDisplay, breaksOnDamage для ROOT/FEAR, `/rc cc *`.

## [1.12.0] — 2026-08-30
Школы урона (8 школ → 3 канала), митигация/пробитие/иммунитеты, стихийный
резист-слой, pen-трейты шмота, DoT-ядро (burning/poison/bleed/chilled),
средовые триггеры, HUD DoT-строка, баланс-матрица 6×6.

## [1.11.0] — 2026-08-12
Чернокнижник: стабилизация Скверны, спек-трейты, игнор маг-резиста ≤25% HP,
рефакторинг P1–P5, эксплойт-фиксы S1–S5/F1–F10, soulbound/релок Фолианта.

## [1.10.0] — 2026-07-28
Шестой класс — Чернокнижник: Фолиант Душ (дроп 0.001% с пиглинов), Скверна
(пороги 75/100, откат 6.66%, ×6 в аду), Пентаграмма-инсталляция, прогрессия
sorcery, анти-фарм S2.

## [1.9.0] — 2026-07-10
План B по HP: carrier ≤ 1024, formula без потолка, scale = carrier/formula;
HpPool — единые точки входа healFormula/currentFormulaHp/targetCarrier;
burst-окно 3 с ≤ 18%, анти-ваншот ≤ 35%, LOS для площадей.

## [1.8.0] — 2026-06-22
RaskolGear-интеграция: резисты шмота, сет-бонусы 4/4, +HP в HpPool,
pen-трейты, `/rc gear`, вкладка книги «Шмот и сеты».

## [1.7.0] — 2026-06-05
Киты шести классов, атрибуты STR/AGI/INT, WP/SP/HPow, уклонение/парирование,
криты, ресурсы 0–100 с боевым окном, Книга класса, HUD actionbar.
