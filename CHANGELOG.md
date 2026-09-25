# CHANGELOG — RaskolClasses

Формат: [версия] — дата — имя. Секции Added / Changed / Fixed / Removed / Reverted / Validate.
Линия 1.9.x активна; 1.8.x закрыта с 1.8.1; 1.7.x и 1.6.x заморожены.

## [1.9.3.2] — 2026-09-25 · «Хотфикс: декей ярости воина вне боя»

### Fixed
- **Воин: ярость не падала вне боя.** `ResourceService.tick()` передавал отрицательный
  rate (−5/с) в `ResourceState.add()`, который по контракту игнорирует всё ≤ 0
  (регресс-замок чека 30: списание только через consume). Введён знаковый путь
  `ResourceState.tickDelta(delta)` для реген-тика; семантика `add()`/`consume()` не тронута.
  Баг был невидим с 1.6.x: у остальных классов rate положительный.

### Added
- Selftest чек 36: tickDelta (−5 декей, +5 набор, клампы 0/100) — регресс-замок ярости.

### Validate
- `/rc selftest` → 36/36 PASS.
- Живая проверка: воин бьёт мобов → окно 5 с → ярость −5/с до 0; в бою не падает.

## [1.9.3.1] — 2026-09-25 · «Хотфикс: версия, старт GearHook, Книга»

### Fixed
- `plugin.yml` version не подставлялся (`${project.version}` в логах и PAPI):
  в pom добавлен resource-filtering ТОЛЬКО для plugin.yml.
- GearHook печатал «RaskolGear: не найден» на старте из-за циклического softdepend
  (RaskolClasses ↔ RaskolGear): стартовый лог теперь по presence, реальная активация —
  на `PluginEnableEvent` с прогревом кэша онлайн-игроков; деактивация на `PluginDisableEvent`.
- Книга класса открывалась пустой: на сервер попал стаб из обрезанного батча.
  Восстановлена полная Книга (5 вкладок, рабочие клики, корректный holder).

### Added
- Вкладка Книги GEAR (слот 50): оружие, 4 слота брони, статус сетов (N/4), статы шмота.
- `/rc gear [player]`: детальная экипировка и активные сеты.

### Changed
- Версия 1.9.3.1; баннер и PAPI печатают реальную версию.

## [1.9.3] — 2026-09-24 · «Виртуальный пул HP + интеграция RaskolGear + редизайн Книги»

### Added
- **План B (виртуальный пул HP):** ванильный max_health = носитель ≤ 1024; боевой пул =
  формула. Единый масштаб scale = carrier/formula; урон/хилы/капы/HUD в формульных единицах.
  Потолок 1024 больше не ограничивает HP (воин L60 = 2560).
- **GearHook:** чтение статов шмота RaskolGear из PDC (резисты/HP/сеты/шипы);
  +HP шмота входит в формулу maxHp; резисты шмота применяет сам RaskolGear (без дубля).
- **SetBonusService:** сет-бонусы 4/4 как модификаторы ResistService (source `set-bonus-*`).
- **BlueprintHook:** softdepend-хук RaskolEnchant (задел под чертежи/рецепты).
- CombatService: пропуск надбавки WP/SP для оружия с PDC-тегом WEAPON (нет двойного скейла).
- Книга класса: мрачная строгая дизайн-система (чёрная рамка, навигация 45–50,
  деструктив в 40, единый лор-шаблон, состояния предметов).
- ConfigValidator: проверки attributes.hp.*; FxService: алиасы битых звуков
  (ENTITY_WOLF_HOWL→ENTITY_WOLF_AMBIENT, BLOCK_SNOW_BLOCK_BREAK→BLOCK_SNOW_BREAK).
- Selftest чеки 33–35 (scale/healFormula/targetCarrier).
- Мрачный стартовый баннер (ASCII «CLASSES», автор, версия).

### Changed
- Полная формула HP: base + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus);
  ключи per-level/main-str-bonus живые.
- CharacterLevelService: фолбэк без AuraSkills = character-level.fallback (не ванильный XP).
- HpAttributeSync: base = min(formula, 1024); учёт чужих модификаторов (gear) в carrier.

### Fixed
- Воин упирался в 1024 HP при формульных 2560.
- fenrirBlood/execute-пороги считались от ванильного max (20/1024), а не от пула.
- Игроки без AuraSkills получали level=0 → HP только от STR.

### Validate
- `/rc selftest` → 35/35 PASS; `/rc debug` воин L60/STR84 → maxHP 2560; HUD 2560/2560.

## [1.9.2] — 2026-09-16 · «Интеграция с RaskolCore 1.3.0 + эксплойт-свип»

### Added
- PassportChangeListener: мгновенный reconcile на Reason.CLASS/FACTION из RaskolCore.
- EconomyHook: первично RaskolCoreAPI.economy(), фолбэк Vault.
- Глобальный бюджет талантов spentGlobal(); респец не печатает очки.
- Reconcile-валидация talents.yml (прунинг битых узлов с WARNING).
- Rate-limit покупок/сброса талантов; фарм-гейты ресурса (себя/союзник).
- Selftest чеки 31–32. Стартовая проверка версии RaskolCore (warn-only).

### Changed
- ClassBook: инфо-предмет очков = «общий бюджет персонажа»; обработка RATE_LIMITED.

### Fixed
- YAML config.yml (строка 186): пробел после `base-hpow:` перед `{`.

### Validate
- `/rc selftest` → 32/32 PASS; живые эксплойт-проверки.

## [1.9.1] — 2026-09-15 · «Стабилизация: боевое окно, регресс-замки, валидатор»

### Added
- Selftest чеки 29–30 (боевое окно, семантика consume).
- ConfigValidator: talents.*, character-level.*, combat.burst-*, frost_rune.*.

### Changed
- ResourceService.markCombat() на нанёсшем и получившем урон — боевое окно ожило.

### Removed
- ResourceState.allowGainEvent()/lastGainEventMillis — мёртвый код.

### Validate
- `/rc selftest` → 30/30 PASS; живая проверка боевого окна.

## [1.9.0] — 2026-09-11 · «Дерево талантов спеки (Книга класса)»

### Added
- TalentsRegistry (10 деревьев × 9 узлов), TalentService (очки/валидация/reconcile),
  TalentsStorage, вкладка TALENTS в Книге, PAPI-плейсхолдеры талантов,
  selftest чеки 22–24, секция talents в config.

### Changed
- Кит-хелперы учитывают baseBonus/coeffMult; кулдауны ×cooldownMult;
  avoidance += avoid-бонусы; реген += regen-бонус; проки += procBonus.
- Применение способностей/инсталляций — только свитками; /rc 1–7 удалены.

### Fixed
- fix6–fix12: consume-баг ресурса; ScrollCooldownTask v12 (durability-bar);
  FxService резолв звуков; BindListener точечные DENY; InstallToken/InstallationService.

### Validate
- `/rc selftest` → 28/28 PASS; плейтест мага.

## [1.8.1] — 2026-09-11 · «Стабилизационная серия перед 1.9.0»
### Fixed
- S1–S7: фракционный гейт ванили, canHit в 9 абилках, Towny-гейт блинка,
  PAPI level/char_level, selftest canHit, health-ratio кламп, healer-маркер.

## [1.8.0] — 2026-09-10 · «Уровень персонажа: прогрессия от ширины прокачки»
### Added
- CharacterLevelService (топ-N скиллов, кап 60); config character-level; selftest 19–20.
### Changed
- attributes.level-source: character (дефолт); рубильник отката class-skill/vanilla.

## [1.7.6] — 2026-09-10 · «TTK-харнесс и баланс-пакет 1.7.6.1–1.7.6.3»
### Added
- BalanceSimulator (simulate / matrix); burst-окно 3 с / 18%.
### Changed
- dodge-mult 0.5; parry-k 300; resist WARRIOR physical 18; coeff-подъём против танков.

## [1.7.5] — 2026-09-10 · «Спеки = пассивная идентичность»
### Removed
- Активки спеков; свитки спеков сгорают на join.

## [1.7.4] — 2026-09-10 · «Кит Жреца (HPow-хилы)»
## [1.7.3] — 2026-09-10 · «Киты: Маг и Разбойник»
## [1.7.2] — 2026-09-10 · «Киты: Воин и Охотник»
## [1.7.1] — 2026-09-10 · «Производные статы WP/SP/HPow и анти-ваншот»
## [1.7.0] — 2026-09-10 · «Фундамент атрибутов и боевая физика»

## [1.6.15] — 2026-09-08 · «Заморозка ветки 1.6.x»
## [1.6.0–1.6.14] — 2026-09-07…10 · резюме линии
## [1.5.10] — 2026-09-08 · «Заморозка ветки 1.5.x»
## [1.5.0–1.5.9] — 2026-09-02…08 · резюме линии
## [1.4.0] — 2026-09-02 · «Специализации и Короны»
## [1.3.2] и ранее — история разработки
