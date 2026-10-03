# Changelog

Формат — [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/),
версионирование — [Semantic Versioning](https://semver.org/lang/ru/).
Все даты — релизы на сервере «РАСКОЛ | ДВЕ КОРОНЫ».

## [1.14.0] — 2026-10-04 · «РАСКОЛ | Спек 2.0: деревья путей»

### Добавлено
- **Деревья путей (Spec 2.0)** — новая система прогрессии:
  - 18 специализаций (3 на каждый из 6 классов):
    - Воин: `arms` / `fury` / `guard`
    - Охотник: `marksmanship` / `survival` / `beastmaster`
    - Жрец: `discipline` / `holy` / `shadow`
    - Маг: `arcane` / `fire` / `frost`
    - Разбойник: `assassination` / `outlaw` / `subtlety`
    - Чернокнижник: `affliction` / `destruction` / `demonology`
  - Ёмкость дерева 50–58 рангов, 20 узлов каждое, 6 рядов с гейтами очков
    внутри дерева: `[0, 5, 10, 15, 20, 30]` (ульт = ряд 6 @ 30 очков)
  - Общий бюджет очков: 46 (с 15 до 60 уровня персонажа, 1 очко за уровень);
    закрыть дерево математически нельзя
  - Выбор основной спеки один раз с 15 уровня через Книгу класса, даёт роль:
    - `FIGHTER` — +2% урона
    - `TANK` — +5% ccResist и +5% получаемого лечения
    - `HEALER` — +5% исходящего лечения
  - 11 категорий эффектов узлов: `attr`, `resist`, `hp_pct`, `regen`,
    `avoid`, `pen_phys_pct`, `pen_magic_pct`, `dot_dur/dot_stacks/dot_mult`,
    `kit_base/kit_mult/kit_cd/cd`, `proc`, `unlock_ability`, `ultimate`
  - Per-tree хранение рангов: `spec2-storage.yml` с разделами по `treeId`
  - Респис дерева путей: 250 + 10×потрачено в дереве монет (конфиг
    `spec2.respec-base-cost`, `spec2.respec-per-level`), возврат очков в общий пул
- **Новые способности** (unlock-узлы деревьев):
  - Воин: `whirlwind_slash`, `mortal_strike`, `bloodthirst`, `rampage`,
    `concussive_blow` (STUN), `shield_bash` (STUN), `taunt`, `bladestorm` (ульт),
    `last_stand` (ульт)
  - Охотник: `aimed_shot`, `silencing_shot`, `chimera_shot`, `true_shot`,
    `poison_shot`, `explosive_trap`, `black_arrow`, `wyvern_sting`, `readiness`,
    `serpent_sting` (ульт), `intimidation`, `beast_ferocity`, `bestial_wrath` (ульт)
  - Жрец: `purge`, `pain_suppression`, `spirit_shell` (ульт), `flash_heal`,
    `lightwell`, `divine_hymn` (ульт), `withering_touch`, `mind_flay`,
    `shadowfiend`, `wrath_heaven` (ульт — перенос из кита)
  - Маг: `arcane_missiles`, `counterspell`, `presence_of_mind`, `scorch`,
    `flamestrike`, `combustion`, `pyroblast` (ульт), `frostbolt`, `blizzard`,
    `ice_barrier`, `ice_lance_shatter` (ульт), `zeus_wrath` (ульт — перенос)
  - Разбойник: `poison_burst`, `cold_blood`, `vendetta`, `envenom`, `deathmark`
    (ульт), `pistol_shot`, `blade_flurry`, `adrenaline_rush`, `killing_spree`,
    `between_the_eyes` (ульт), `backstab`, `shadowstep`, `preparation`,
    `hemorrhage`, `shadow_blades` (ульт)
  - Чернокнижник: `withering`, `soul_siphon`, `soul_harvest` (ульт), `immolate`,
    `chaos_bolt`, `conflagrate`, `dreadfire`, `summon_demon`, `demonic_pact`,
    `demon_soul` (ульт)
- **Новый DoT `wither`** (школа `SHADOW`): 2.5 dps, 6 с, ×3 стека;
  партикл `SCULK_SOUL`; конфиг `dots.wither`
- **Роли в selftest**: чеки 91–94 валидируют enum, строгий `fromId`, реестр
  деревьев, ульт/unlock-инварианты
- **Passive-пассивка мага**: `mana_soaked` (файл `MagePassives.java` воссоздан,
  ранее был мёртв)

### Изменено
- **Киты всех 6 классов** читают `baseBonus/coeffMult` из `Spec2Service`
  (legacy `TalentService` удалён)
- **Resist-трейты чернокнижника** (`affliction`/`destruction`/`demonology`)
  используют ключи, унифицированные с enum и конфигом (ошибочный `witchcraft` убран)
- **Реген ресурсов** полностью из spec2-агрегата (узлы `regen`); ARCANE-реген маны
  теперь через дерево `arcane`, а не через legacy `SpecRegistry`
- **Кулдауны способностей** учитывают оба spec2-множителя:
  - `cooldownMult` (процент, kind `cd`) — мультипликативный
  - `cooldownSecBonus` (секунды, kind `kit_cd`) — аддитивный вычет
- **Фолиант душ**: гейты `SPEC_CHOSEN`/`TALENTS_SPENT` читают spec2-слой
- **PlaceholderAPI** (`%raskolclasses_spec%`, `talent_points`, `talents`) —
  из spec2-хранилища
- **DebugSub** `/rc debug`: спека из `spec2Service.mainSpec`, добавлены поля
  очков spec2
- **PassportChangeListener**: при смене паспорта CLASS спека сбрасывается, если
  её класс не совпадает с новым

### Удалено
- Legacy-слой талантов: `TalentModel`, `TalentService`, `TalentsRegistry`,
  `TalentsStorage`
- Legacy-слой спеков: `SpecRegistry`, `SpecService`, `SpecStorage`,
  `SpecEffects`, `SpecPassives`, `SpecListener`, `SpecToken`, `SpecLegacyReset`,
  `SpecMath`, `SpecEconomy`
- Конфиг `specs.yml` (заменён на `spec2-storage.yml` + статический реестр)
- Legacy-константы enum `Spec`: `GUARDIAN`, `TRACKER`, `BERSERKER`, `MARKSMAN`,
  `ASSASSIN`, `BEAST_MASTER`, `SHADOWWEAVER`, `BLACK_MAGE`, `HELL_CHANNEL`,
  `WITCHCRAFT` и др.

### Исправлено
- **Красная рана selftest 60**: pen-трейты без контента возвращают 0 через
  spec2-агрегат (раньше падали на null `SpecRegistry`)
- **Спека-свитки** полностью упразднены (выбор только через Книгу); `ScrollSanitizer`
  больше не ссылается на `SpecToken`
- **Рассинхрон id чернокнижника**: ключ `affliction` консистентен в enum,
  `SpecRoles`, `Spec2Service.treesOf`, `WarlockAbilities`, конфиге `classes.WARLOCK.specs.*`

### Миграция
- **Для игроков**: старые спеки автоматически сброшены (`SpecLegacyReset` удалён,
  сброс происходит через `FoliantService`-гейт `SPEC_CHOSEN` при первом входе);
  очки талантов возвращены (40→46 бюджет); выбор новой спеки — через
  `/rc menu` → «Специализации» с 15 уровня
- **Для операторов**: конфиг-ключи переехали:
  - `talents.*` → `spec2.*`
  - `spec.*` (старые) → удалены
  - `spec2.role-passives.{FIGHTER|TANK|HEALER}.*` — новые бонусы ролей
  - `dots.wither` — новый блок для DoT чернокнижника/жреца-тьмы

### Совместимость
- **Paper 1.21+**, **Java 21** (без изменений)
- **Зависимости**: LuckPerms, PlaceholderAPI (опц.), Vault (опц.), AuraSkills
  (опц.), RaskolCore 1.3.0+ (опц.) — как в 1.13.0
- **selftest**: 94 чека PASS (было 90 в 1.13.0; +4 спеков-spec2)
- **Обратная совместимость конфигов**: 1.14.0 читает `config.yml` 1.13.0,
  но новые секции (`spec2.*`, `dots.wither`) требуют ручного добавления

## [1.13.0] — 2026-09-20 · «РАСКОЛ | ДВЕ КОРОНЫ: Контроль»

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
