# CHANGELOG — RaskolClasses

Формат: [версия] — дата — имя. Секции Added / Changed / Fixed / Removed / Validate / Decisions.
Линия 1.9.x активна; 1.8.x закрыта фиксацией 1.8.1; 1.7.x и 1.6.x заморожены.

## [1.9.0] — 2026-09-11 · «Дерево талантов спеки (Книга класса)»

### Added
- **TalentsRegistry:** статический каталог 10 деревьев × 9 узлов (2 ветки A/B, 4 тира,
  капстоуны и ульт); эффекты узлов: attr / resist / kit_base / kit_mult / cd / regen / avoid / proc.
- **TalentService:** очки (earned = clamp(charLevel − start-level + 1, 0, max-points));
  покупка с ПОЛНОЙ серверной валидацией (спека → узел из дерева активной спеки →
  тир-гейт → пререквизиты → стоимость); сброс платный (reset-base + reset-per-point ×
  потрачено) или бесплатный для raskolclasses.admin; reconcile постоянных модификаторов
  (source "talents") и кэш-карт хот-пути (baseBonus/coeffMult/cooldownMult/procBonus/
  avoidBonus/regenBonus).
- **TalentsStorage:** talents.yml через SafeStorage (атомарно, .bak, фолбэк).
- **ClassBook, вкладка TALENTS** (таб-слот 46): сетка 9 узлов (2/6/10/12/14/16/20/24/31),
  инфо-предмет очков (4), кристалл сброса (40) с двойным ПКМ-подтверждением 30 с;
  покупка кликом; состояния узлов (куплен/доступен/тир/пререквизит/нет очков).
- **RaskolPlaceholder:** %raskolclasses_talent_points%, %raskolclasses_talents%,
  %raskolclasses_spec%, %raskolclasses_spec_id%.
- **Selftest чеки 22–24:** экономика очков, стоимость полного дерева = 21,
  reconcile-цикл (покупка → reconcile → откат без рассинхрона). Итого 28 чеков.
- **config:** секция talents (enabled / start-level 40 / points-per-level 1 /
  max-points 21 / reset-base 500 / reset-per-point 25); messages.book.tab.talents;
  messages.talents.reset.poor.

### Changed
- Управление талантами — ТОЛЬКО через Книгу класса (решение владельца): команды
  /rc talents нет; /rc 6 даёт справку и отсылает в Книгу; чужое дерево недоступно
  (Книга всегда открывается от себя).
- Кит-хелперы dmg/heal/grant учитывают baseBonus/coeffMult; AbilityRegistry умножает
  кулдаун на cooldownMult; AttributeService.effectiveAvoidance добавляет avoid-бонусы;
  ResourceService.tick добавляет regen-бонус; PassiveListener добавляет procBonus
  (казнь/хищник/яды/садизм/благодать) и маркер хилера для благодати и ресурса за лечение.

### Fixed
- RaskolPlaceholder: onRequest переписан на switch-expression (return switch + yield) —
  строковые тела кейсов в switch-statement не компилировались («not a statement»).
- ResourceService: маркер хилера (UUID) резолвится в Player через сервер перед
  getClassOf (фикс «UUID cannot be converted to Player»).

### Validate
- /rc selftest → 28/28 PASS.
- Книга → TALENTS: покупка T1 списывает очко и применяет бонус; клик по T3 без
  пререквизитов отклоняется; сброс кристаллом возвращает очки в пул.
- /papi parse <ник> %raskolclasses_talent_points% / %raskolclasses_talents% — живые.

### Decisions
- Полное дерево = 21 очко = кап на charLevel 60 (топ-5 скиллов): прогрессия талантов
  привязана к ширине прокачки, а не к гринду одного дерева.
- Ветки A/B не взаимоисключающие: разнообразие билдов держится пререквизитами и ценой.
- Кастомная броня/сет-слой (источник высоких avoid-чисел «со шмотом») — линия 1.10.x.

## [1.8.1] — 2026-09-11 · «Стабилизационная серия перед 1.9.0»

### Fixed
- S1: ванильный мили/стрелы по союзнику в wilderness отменялись только Towny —
  CombatService.onDamage отменяет союзнический урон при friendly-fire=false.
- S2: центральный фракционный гейт в CombatService.dealDamage (абилки не бьют союзников).
- S3: canHit-гейты в 9 однотargetных абилках 5 китов — дебаффы/поджог/яд не вешаются
  на союзника при отклонённом уроне; каст отклоняется до траты ресурса/КД.
- S4: TownyHook (reflection, fail closed) — «Шаг Гермеса» не блинкует в чужой клейм.
- S5: %raskolclasses_level% и char_level в PAPI.
- S6: selftest-чек canHit.
- S7: health-ratio клампится [0,1] при сохранении и загрузке (health.yml).
- ResourceService: healer-маркер для ресурса за лечение (жрец).

### Changed
- PassiveListener: procBonus талантов в казнь/хищник/яды/садизм/благодать.

## [1.8.0] — 2026-09-10 · «Уровень персонажа: прогрессия от ширины прокачки»

### Added
- CharacterLevelService: уровень = floor(среднее топ-N скиллов), кап level-cap 60;
  дисплей в /rc, /rc debug, Книге (лор атрибутов), PAPI.
- config: character-level (top-n / fallback / skills), attributes.level-cap.
- Selftest чеки 19–20 (topNAverage).

### Changed
- attributes.level-source: character (дефолт): атрибуты растут от сводного уровня;
  кейс 1.7.x закрыт (healing 99 ≠ INT 130/HP 992/dodge 30% без шмота).
- Анлоки способностей остаются на профильном скилле.

### Decisions
- Рубильник отката: level-source = class-skill | vanilla.

## [1.7.6] — 2026-09-10 · «TTK-харнесс и баланс-пакет 1.7.6.1–1.7.6.3»

### Added
- BalanceSimulator (/rc debug simulate [A] [B] [level], simulate matrix [level]):
  headless-дуэли на боевых формулах; матрица 5×5; якорь balance.target-ttk-seconds 20 с.
- Burst-окно: combat.burst-window-seconds 3.0 / burst-window-pct 18.0 (пол TTK 13–15 с).

### Changed (тюнинг)
- avoidance.dodge-mult 0.5; parry-k 300; resist WARRIOR physical 18;
  HUNTER resource-on-deal 4; coeff-подъём против танков (piercing 1.4, arrow_rain 0.9,
  fire_prometheus 1.2, boreas 1.0, zeus 2.0, blade_fan 0.8, strangle 0.9, wrath 1.6).

### Fixed
- simulate off-by-one; dodge-mult в effectiveAvoidance; burst-окно в симуляторе;
  визуал матрицы (самоподписанные ячейки).

## [1.7.5] — 2026-09-10 · «Спеки = пассивная идентичность»

### Removed
- Активки спеков; SpecActiveCaster/SpecBindListener; свитки спеков сгорают на join;
  вкладка SPECS без активок/свитков.

### Decisions
- Дерево талантов спеки отложено до 1.9.0.

## [1.7.4] — 2026-09-10 · «Кит Жреца: католика/паладинство (HPow-хилы)»
- 5 способностей от Силы исцеления; таргет-правила себя/союзника; refund при полном HP;
  старые id жреца удалены (свитки сжигает ScrollSanitizer).

## [1.7.3] — 2026-09-10 · «Киты: Маг и Разбойник (SP/WP-масштаб)»
- Маг (Греция): Прометей/Гермес/Борей/Афина/Зевс; Разбойник (средневековье):
  плащ/веер/удушение/яд/танец; все числа base + Power×coeff.

## [1.7.2] — 2026-09-10 · «Киты: Воин и Охотник (WP-масштаб)»
- Воин (нордика): Тир/Бальдр/берсерк/Фенрир/Рагнарёк; Охотник (Ведьмак/Skyrim):
  метка/ласточка/пронзающий/веер/дождь; LOS и фракционные фильтры AoE.

## [1.7.1] — 2026-09-10 · «Производные статы WP/SP/HPow и анти-ваншот»
- PowerService (WP/SP/HPow, pure-статики); анти-ваншот 35% maxHP с исключениями
  (среда, execute-финишеры allowOverCap).

## [1.7.0] — 2026-09-10 · «Фундамент атрибутов и боевая физика»
- STR/AGI/INT (base + growth×Level + модификаторы); HP = 100 + STR×20; Dota-HUD;
  STR-реген; летальность среды ×maxHP/20; уклонение/парирование (гиперболы, DR, углы,
  стаггер); криты мили/магии; selftest; хотфиксы 1.7.0.1–1.7.0.6.

## [1.6.15] — 2026-09-10 · «Заморозка ветки 1.6.x»
- Регресс-матрица 30 пунктов; тег v1.6.15; frozen.

## [1.6.0–1.6.14] — 2026-09-07…10 · резюме линии
- 1.6.14 RUNBOOK; 1.6.13 selftest; 1.6.12 /rc health; 1.6.11 LOS/NaN/ScrollSanitizer;
  1.6.10 SafeStorage; 1.6.9 резист-рубильники; 1.6.8 края/совместимость; 1.6.7 ConfigValidator;
  1.6.6 резисты в Книге; 1.6.5 PAPI-резисты; 1.6.4 симулятор урона; 1.6.3 производительность;
  1.6.2 фракционные таргеты; 1.6.1 жизненный цикл модификаторов; 1.6.0 система урона/резистов.

## [1.5.10] — 2026-09-08 · «Заморозка ветки 1.5.x»
- Регресс-матрица 25 пунктов; frozen.

## [1.5.0–1.5.9] — 2026-09-02…08 · резюме линии
- локализация; AuthGate/creative/края; звуковой бюджет/purge; полоса КД/proc-теги;
  живая Книга/анти-дубли; Книга класса; валидация vfx; защита инсталляций; целостность;
  FxService/трейлы/инсталляции.

## [1.4.0] — 2026-09-02 · «Специализации и Короны»
- Спеки (выбор с 40, платный респец), атрибутные бонусы, королевские вкусы (%raskolcrown_*%).

## [1.3.2] и ранее — история разработки
- База классов: ресурсы 0–100, активки слотов 1–5, свитки, HUD, босс-бары,
  ready-notify, AuraSkills ×1.5, чтение класса из паспорта RaskolCore (LP — фолбэк).
