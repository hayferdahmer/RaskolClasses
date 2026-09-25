# CHANGELOG — RaskolClasses

Формат: [версия] — дата — имя. Секции Added / Changed / Fixed / Removed / Reverted / Validate.
Линия 1.10.x активна; 1.9.x закрыта с 1.9.3.2; 1.8.x и ниже заморожены.

## [1.10.0] — 2026-09-25 · «Чернокнижник: шестой путь Раскола»

### Added
- **Класс WARLOCK (Чернокнижник)** — скрытый шестой класс: переход только из MAGE/PRIEST
  через «Фолиант Раскола» (уровень персонажа 40+, необратимо).
- **Ресурс «Скверна»** (0–100): событийный рост (+6 урон, +3 получение, +10 смерть врага
  в r10, +12 «Голод Скверны»), декэй −4/с вне боя; порог 75 «Распахнутая страница» (+20% урона),
  порог 100 «Переполнение» (тик 1% maxHP/с себе, набор заблокирован до <85).
- **Кит (5 способностей, power=SP):** Чёрное Слово (дрейн 66.6%), Печать Погибели
  (+26% входящего урона, 66.6 с, неснимаемо), Голод Скверны (AoE r6 + дрейн + Скверна),
  Небытие (диспел положительных эффектов + урон за каждый), Раскол Души (канал 2.5 с,
  зона r8, анти-хил 6 с, взрыв до +66.6% missing-HP).
- **Пассивка «Чёрная Месса»:** 6.66% lifesteal со способностей; 6.66% полученного урона
  возвращается чистым уроном всем в r8 (фракционный гейт на игроков-союзников).
- **Цена силы:** откат 6.66% от нанесённого урона (true-урон себе; предохранители:
  кап 30% maxHP за каст, не ниже 1 HP); ад (NETHER) ×6 ко всему киту (`nether-mult`).
- **Спеки:** Чёрный Маг (урон/диспел) и Адский Канал (контроль/выживаемость);
  общее дерево талантов `occult` (9 узлов, 21 очко, ульт «Последняя Страница»).
- **Инсталляция «Круг Хулы»:** зона r6 25 с — врагам маг-урон + анти-хил,
  владельцу +3 Скверны/с; в аду урон ×6.
- **FoliantService:** предмет-ключ (PDC-тег, WRITABLE_BOOK), GUI двойного подтверждения
  (holder-маркер, без парсинга заголовков), гейты: класс, уровень 40, спека сброшена,
  таланты сброшены, AuthGate; миграция: LP-группа class_* → class_warlock, очистка
  модификаторов/резистов/ресурса/пассивок, сгорание инсталляций и рун (removeAllOf),
  грант occult=10 через AuraSkills API (reflection, graceful).
- **Команда:** `/rc foliant give <ник>` (admin) + таб-комплит.
- **Баланс-харнесс:** WARLOCK в BalanceSimulator (атрибуты 4/4/14, рост 0.2/0.3/1.4,
  WP 5 / SP 40 / HPow 0, дрейн, откат, амплификация печати, множитель Скверны ≥75);
  матрица TTK стала 6×6.
- **VFX:** адская тема чернокнижника — Warden (heartbeat/roar/sonic), Elder Guardian
  (curse), Evoker (prepare_attack), Vex (charge/death); партиклы SCULK_CHARGE,
  SCULK_SOUL, SHRIEK, SOUL, SONIC_BOOM.
- **Конфиг:** секция classes.WARLOCK (ресурс, пороги, откат, ад, lifesteal-cap),
  резисты 10/26, тема #1B0022/#9B30FF/☾, титулы корон «Тень на Рассвете» /
  «Голос Раскола», messages.foliant.*, messages.book.resource.warlock,
  installations.heresy_circle.*, vfx-блок, character-level.skills += occult.
- **Selftest:** чеки 37–40 (реестры печати/анти-хила + 9 кодов гейтов; sanity-диапазоны
  конфига WARLOCK; дуэль WARLOCK без падений; матрица 6×6). Итого 40 чеков.

### Changed
- Книга класса: эмблема WARLOCK = WRITABLE_BOOK, правила Скверны в лоре, описание
  Круга Хулы; баннер-баннер-бар: BarColor.PURPLE; матрица /rc debug — короткое имя «чернокн.».
- Баннер репозитория и README: шестой класс в строке путей (☾ Чернокнижник).

### Fixed
- Каскад exhaustiveness после добавления WARLOCK/HERESY_CIRCLE/новых Spec: покрыты
  switch-выражения в RaskolConfig (9), ClassBook (3), HpBarService, RaskolCommand,
  BossBarService, BalanceSimulator (3 switch + 4 Map.of), InstallToken, ResistService (2),
  AttributeService (3), PowerService (3).
- ResourceService: удалён внутренний класс-паразит Location (тенил org.bukkit.Location),
  добавлен импорт LivingEntity; on-kill считает дистанцию через distanceSquared.
- WarlockAbilities: печать перенесена из несуществующего ResistService.addModifier
  в собственный статический реестр (sealAmplifyOf); партиклы — напрямую world.spawnParticle
  (у FxService нет spawnParticles); итерация getNearbyEntities через instanceof LivingEntity;
  анти-хил проверяется и в ванильных RegainHealth (CombatService), и в нашем хил-пайплайне
  (HpBarService.heal).
- CombatService: амплификация «Печати Погибели» применяется в обоих путях урона
  (A: event-урон универсально, B: phys/magic части) до капов; откат чернокнижника —
  после применения урона, в formula-единицах через scale.

### Validate
- `/rc selftest` → 40/40 PASS.
- `/rc debug simulate matrix 40` → 6×6, строка/колонка WARLOCK без NaN/∞-аномалий.
- Живой сценарий перехода: маг 40+ без спеки и талантов → фолиант → GUI → подтверждение
  → class_warlock в LP, occult=10, Книга = ☾, ресурс «Скверна», инсталляции мага сгорели.
- Отказные пути: воин / маг<40 / с спекой / с талантами / уже чернокнижник — красные сообщения.

## [1.9.3.2] — 2026-09-25 · «Хотфикс: декэй ярости воина вне боя»

### Fixed
- **Воин: ярость не падала вне боя.** `ResourceService.tick()` передавал отрицательный
  rate (−5/с) в `ResourceState.add()`, который игнорирует отрицательные числа
  (регресс-замок чека 30: списание только через consume). Декэй молча отбрасывался
  каждый тик → ярость зависала на значении после боя.
- Введён `ResourceState.tickDelta()` — знаковый путь реген-тика (реген И декэй)
  с клампом [0,100]. Семантика `add()`/`consume()` не тронута (замок чека 30 цел).

### Added
- Selftest чек 36: tickDelta (декэй −5, набор +5, клампы 0/100). Итого 36 чеков.

### Validate
- `/rc selftest` → 36/36 PASS.
- Живая проверка: воин бьёт мобов → окно 5 с → ярость −5/с до 0; в бою не падает.

## [1.9.3.1] — 2026-09-25 · «Хотфикс: версия, старт GearHook, Книга»

### Fixed
- `plugin.yml` версия не подставлялась (`${project.version}` в логах и PAPI):
  в pom добавлен resource-filtering ТОЛЬКО для plugin.yml.
- GearHook печатал «RaskolGear: не найден» на старте из-за циклического softdepend
  (RaskolClasses↔RaskolGear): лог по presence, активация на PluginEnableEvent
  с прогревом кэша онлайн, деактивация на PluginDisableEvent.
- Книга класса открывалась пустой: на сервере лежал стаб из обрезанного батча.
  Восстановлена полная Книга (5 вкладок, рабочие клики, корректный holder).

### Added
- Книга: вкладка GEAR (оружие, 4 слота брони, статус сетов N/4, статы шмота).
- `/rc gear [player]` — детальная экипировка и сеты.

### Changed
- Версия 1.9.3.1.

## [1.9.3] — 2026-09-24 · «Виртуальный пул HP + RaskolGear + редизайн Книги»

### Added
- **План B (виртуальный пул HP):** ванильный max_health = носитель ≤1024,
  боевой пул = формула; scale = carrier/formula; урон/хилы/капы/HUD в формульных
  единицах. Потолок 1024 больше не ограничивает HP (datapack не нужен).
- **HpAttributeSync:** base = min(formula,1024) на join/respawn/reload/invalidate + sweep 5 с.
- **GearHook (RaskolGear):** чтение статов шмота из PDC (резисты/HP/сеты/шипы),
  +HP шмота входит в формулу maxHp; исходящий офенс WP/SP пропускается для оружия
  с тегом WEAPON (без двойного скейла).
- **SetBonusService:** сеты 4/4 → модификаторы резистов source `set-bonus-*`.
- **BlueprintHook:** softdepend RaskolEnchant (каркас под чертежи/рецепты).
- Редизайн Книги класса: мрачная строгая система (чёрная рамка, навигация 45–50,
  деструктив в 40, единый лор-шаблон, состояния предметов).
- Мрачный стартовый баннер в консоли (CLASSES, 5 классов, автор, версия).
- ConfigValidator: проверки attributes.hp.*; FxService: алиасы старых звуков.
- Selftest чеки 33–35 (scale/healFormula/targetCarrier). Итого 35 чеков.

### Changed
- Полная формула HP: base + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus).
- CharacterLevelService: фолбэк без AuraSkills = character-level.fallback (не vanilla XP).
- CombatService: applyEnvLethalScale и капы от carrier; burst-лог в effective-единицах.

### Fixed
- Воин упирался в 1024 HP при формульных 2560 (движковый кламп).
- fenrirBlood/ragnarok/хилы жреца считали от ванильного max (20/1024) — crash-guard и execute-пороги от формулы.
- Игроки без AuraSkills получали level=0 → HP только от STR.

### Validate
- `/rc debug` воин L60/STR84 → maxHP 2560; HUD `…/2560`.
- `/rc selftest` → 35/35 PASS.

## [1.9.2] — 2026-09-16 · «Интеграция с RaskolCore 1.3.0 + эксплойт-свип»

### Added
- PassportChangeListener: мгновенный reconcile на Reason.CLASS/FACTION.
- EconomyHook: первично RaskolCoreAPI.economy(), фолбэк Vault.
- Глобальный бюджет талантов spentGlobal(); респец не печатает очки.
- Reconcile-валидация talents.yml (прунинг битых узлов с WARNING).
- Rate-limit покупок/сброса талантов.
- Фарм-гейты ресурса (себя/союзник не фармят ярость/концентрацию).
- Selftest чеки 31–32. Итого 32 чека.
- Стартовая проверка версии RaskolCore (warn-only).

### Changed
- ClassBook: инфо-предмет очков = «общий бюджет персонажа»; обработка RATE_LIMITED.

### Fixed
- YAML config.yml (строка 186): пробел после `base-hpow:` перед `{`.

### Validate
- `/rc selftest` → 32/32 PASS; живые эксплойт-проверки.

## [1.9.1] — 2026-09-15 · «Стабилизация: боевое окно, регресс-замки, валидатор»

### Added
- Selftest чеки 29–30 (боевое окно, семантика consume). Итого 30 чеков.
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
