# Changelog

Формат — [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
версионирование — [Semantic Versioning](https://semver.org/lang/ru/).
Все даты — релизы на сервере «РАСКОЛ | ДВЕ КОРОНЫ».

## [1.13.0] — 2026-10-03

### Added — система контроля (CC) и убывающей отдачи (DR)
- Модель CC: 9 типов (STUN, ROOT, SILENCE, DISARM, FEAR, CHARM, SLOW, BLIND,
  KNOCKBACK) и 6 категорий DR (STUN, FEAR, SILENCE, ROOT, SLOW, BLIND).
- DR-ядро: окно 15 с, множители [1.0, 0.5, 0.25, 0.0] → 4-й CC в окне = иммунитет;
  стек сбрасывается после паузы; смерть/выход снимают CC и сбрасывают DR.
- ccResist по классам (Воин 20%, Охотник/Разбойник/Жрец 15%, Маг/Чернокнижник 10%)
  + бонус чернокнижнику при Скверне ≥ 75; кап cc.resist-cap 0.60.
- ccPower чернокнижника при Скверне ≥ 75 (+20% длительности накладываемого CC).
- Иммунитеты: теги BOSS/MINION_ELITE/MINION + явные ENTITY_WITHER/ENTITY_ENDER_DRAGON;
  CHARM на игроков запрещён по умолчанию.
- Запреты действий (CCGuard): атаки/хотбар/блоки/сущности/зелья под STUN;
  атаки и предметы под FEAR; оружие под DISARM (кулак разрешён); промах 50% под BLIND.
- Гейт каста (CastGuard): STUN/SILENCE/FEAR блокируют каст до списания ресурса
  и кулдауна; мгновенные способности под SILENCE разрешены.
- Прерывание каналов (CastChannels): soul_rift обрывается interruptible-CC.
- Обёртка ванили (VanillaCCWrapper): Slowness→SLOW, Blindness→BLIND, Weakness→SILENCE
  через DR; молоко и /effect clear снимают CC, но НЕ сбрасывают DR.
- breaksOnDamage: ROOT/FEAR снимаются уроном ≥ 5% formula-maxHP (пути A и B).
- Туман слепоты: BLIND накладывает ванильный BLINDNESS без партиклей-эмиттеров.
- HUD CC-строка после DoT-строки (иконки messages.cc.hud-icons.*), O1 dirty-rendering.
- CCFeedback: звуки/партиклы применения, снятия, резиста, иммунитета, DR-иммунитета;
  тик-партиклы активного CC раз в 10 тиков.
- Команды /rc cc: list, status, clear, test, reset (пермиссия raskolclasses.admin.cc).
- Секция «Контроль/DR-стеки» в /rc debug <player>.
- CcSanity: валидация диапазонов cc.* и ссылочной целостности иммунитетов.
- Selftest: чеки 76–90 (15 новых headless-проверок CC/DR).

### Changed
- WarlockAbilities.soulRift: канал регистрируется в CastChannels (прерывание CC).
- CombatService.dealDamage и VanillaDamageListener: хук CCService.breakOnDamage.
- AbilityRegistry.castOn: CC-гейт каста до антискпа/кулдаунов/ресурса.
- HudService: ключ кадра включает CC-снапшот; иконки CC по умолчанию.
- RaskolCommand: регистрация CcSub, «cc» в ROOT_SUBS и справке.
- Версия артефакта и плагина: 1.11.4 → 1.13.0 (pom.xml, plugin.yml).

### Fixed
- CCGuard: удалена черновая строка-артефакт, ломавшая компиляцию модуля.
- VanillaCCWrapper: event.getType() → event.getModifiedType() (Bukkit API 1.21.4).
- CCService: снятие SLOW-модификатора через getModifiers()+removeModifier(mod)
  (removeModifier(NamespacedKey) отсутствует в части сборок Paper).
- Удалён дубликат-черновик CcSubcommand.java (замещён CcSub.java).

### Docs
- CHANGELOG.md создан; docs/RUNBOOK-CC.md — операторский раздел «Контроль и DR»;
  README.md актуализирован (бейджи 1.13.0/90-90, секции школ/DoT/CC, команды, установка).

## [1.12.7] — 2026-10-01
- Баланс-прогон матрицы 6×6 с DoT: burning 4.0→3.5, poison_passive 2.5→2.0, chilled 2.0→1.5.
- Selftest-чек 75: sanity матрицы (чистота, диагональ [10,60], средняя [15,25]).

## [1.12.6] — 2026-09-30
- HUD: DoT-строка после ресурсного бара (иконки школ, стеки, секунды).
- Миграция пассивки poisoned_blades с ванильного POISON на DotService (poison_passive).

## [1.12.5] — 2026-09-29
- Реестр dots.* (burning/poison/bleed/chilled + пассивные варианты).
- Средовые триггеры: вода/пушистый снег гасят FIRE, огонь/лава плавят FROST.
- Очищение жреца: хилы снимают DoT школ NATURE и SHADOW.
- Тик-VFX школ для DoT (vfx.dot.*).

## [1.12.4] — 2026-09-28
- DoT-ядро combat/dot/: DotDef/DotInstance/DotMath/DotService, тик 1 с, атрибуция,
  стеки, кап суммарного DoT-DPS combat.dot-dps-cap-pct 6%.

## [1.12.3] — 2026-09-27
- Школы в способностях: ключ school: у всех 30, cast-контекст ThreadLocal,
  иммунитеты/множители школ в пути B.
- VFX-стандарт чернокнижника на всех 6 китах (cast/impact/execute/expire).

## [1.12.2] — 2026-09-26
- Стихийный резист-слой (elemental), pen-трейты gear/талантов/спек,
  живая проводка pen+elemental в пути A/B; mitigation-cap 0.90.

## [1.12.1] — 2026-09-25
- Митигация с пробитием (flat→pct), иммунитеты/уязвимости EntityType, Penetration-клампы.
- Фикс ключа DamageCause: FREEZING → FREEZE.

## [1.12.0] — 2026-09-24
- Каркас школ урона: School (8 школ → 3 канала), SchoolProfile, SchoolConfig, schools.*.

## [1.11.4] — 2026-09-20
- Рефакторинг P1–P5, эксплойт-фиксы S1–S5/F1–F10, per-class kits/*.yml, selftest 48.

## [1.11.0–1.11.3] — 2026-09-15…18
- Чернокнижник: спек-трейты, игнор маг-резиста ≤25% HP, soulbound/релок фолианта.

## [1.10.0–1.10.4] — 2026-09-10…14
- Скрытый класс Чернокнижник: Скверна, Пентаграмма, Фолиант Душ, sorcery-прогрессия.

## [1.9.0–1.9.3] — 2026-09-05…09
- Таланты (21 очко), боевое окно, план B (carrier/formula/scale), HpPool.

## [1.8.0–1.8.1] — 2026-09-03…04
- Уровень персонажа top-N, фракционный гейт canHit.

## [1.7.0–1.7.6] — 2026-08-28…09-02
- Атрибуты, WP/SP/HPow, анти-ваншот, киты шести классов, TTK-харнесс.

## [1.4.0–1.6.15] — 2026-08-10…27
- Становление плагина: роутер /rc, Книга, HUD, резисты, инсталляции, фолиант-дроп.
