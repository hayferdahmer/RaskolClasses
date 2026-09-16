# CHANGELOG — RaskolClasses

Формат: [версия] — дата — имя. Секции Added / Changed / Fixed / Removed / Reverted / Validate.
Линия 1.9.x активна; 1.8.x закрыта с 1.8.1; 1.7.x и 1.6.x заморожены.

## [1.9.2] — 2026-09-16 · «Интеграция с RaskolCore 1.3.0 + эксплойт-свип»

### Added
- **PassportChangeListener:** мгновенный reconcile талантов/резистов/боевых кэшей
  на Reason.CLASS/FACTION из RaskolCore — закрыто 5-секундное окно эксплойта
  «сменил LP-группу → 5 секунд старых гейтов» (TTL кэша паспорта Core).
- **EconomyHook:** первично `RaskolCoreAPI.economy()` (контракт Core), фолбэк Vault
  напрямую. Лог «Economy: контракт RaskolCore подключён (RaskolCore:…)» при старте.
- **Глобальный бюджет талантов:** `spentGlobal()` суммирует потраченные очки по
  ВСЕМ деревьям игрока, а не по активной спеке. Респец больше не печатает
  новые очки: `availablePoints = earned − spentGlobal`.
- **Reconcile-валидация talents.yml:** неизвестные id узлов и узлы с нарушенными
  пререквизитами удаляются из хранилища самим сервером с WARNING.
- **Rate-limit покупок/сброса талантов:** не чаще `cast-click-cooldown-ms` мс
  на игрока — защита от пакет-спама кликами по GUI.
- **Фарм-гейты ресурса:** урон по себе и по союзнику (friendly-fire выкл) не даёт
  прибавок ярости/концентрации обеим сторонам; урон по мобам и врагам-игрокам —
  как раньше.
- **Selftest чеки 31–32:** глобальный бюджет очков + reconcile-прунинг битых
  узлов. Итого 32 чека.
- **Стартовая проверка RaskolCore:** warn-only, лог `RaskolCore: версия …`.
  Старый Core не ломает бой — гейты уходят в fail-open.

### Changed
- ClassBook: инфо-предмет очков помечает «Очки — общий бюджет персонажа (все
  деревья)». Обработка `RATE_LIMITED` в покупке и сбросе («Слишком часто:
  подожди мгновение и повтори»).

### Fixed
- YAML `config.yml` (строка 186): пробел после `base-hpow:` перед `{` — парсер
  больше не падает на маппинг без пробела.

### Validate
- `/rc selftest` → 32/32 PASS.
- Живые эксплойт-проверки:
  - Респец не печатает очки (после респеца A→B доступных очков = 21−2, не 21).
  - Ручная правка talents.yml с левым узлом самоуничтожается в логе WARNING.
  - Спарринг с однокоронником не фармит ярость.
  - Быстрые клики по узлам книги подряд → «Слишком часто».
- Смена LP-группы класса/фракции у онлайн-игрока → в логе Classes строка
  `PassportChange CLASS/FACTION: reconcile для <ник> …`.

## [1.9.1] — 2026-09-15 · «Стабилизация: боевое окно, регресс-замки, валидатор»

### Added
- Selftest чеки 29–30: боевое окно (markCombat/isInCombat) и семантика consume
  (регресс-замок бага «ресурс не списывается»: сверх отказа без изменений,
  точное обнуление, 0 бесплатна). Итого 30 чеков.
- ConfigValidator: проверки talents.* (start-level/points-per-level/max-points/
  reset-base/reset-per-point), character-level.* (top-n/fallback),
  combat.burst-* (window-seconds/window-pct), installations.frost_rune.*
  (radius/duration/cooldown/damage-*/slow-*/mage-*).

### Changed
- ResourceService: markCombat() вызывается на нанёсшем и получившем урон в
  EntityDamageByEntityEvent. Боевое окно наконец работает: воин теряет ярость
  −5/с ТОЛЬКО вне боя, охотник регенит концентрацию +5/с ТОЛЬКО вне боя
  (ранее markCombat не вызывался нигде и окно было мертво с 1.6.x).

### Removed
- ResourceState.allowGainEvent() и lastGainEventMillis — мёртвый код
  (кап «1 событие урона в секунду» живёт в ResourceService.lastGainMs;
  метод не вызывался нигде с 1.7.x). TECHDEBT-комментарий закрыт.

### Validate
- /rc selftest → 30/30 PASS.
- Живая проверка боевого окна: воин вне боя −5/с, после удара моба окно 5 с;
  охотник вне боя +5/с, после удара рост замирает на 5 с.
- ConfigValidator на старте: «конфиг валиден (resist/damage/talents/
  character-level/burst/frost_rune)».

## [1.9.0] — 2026-09-11 · «Дерево талантов спеки (Книга класса)»

### Added
- TalentsRegistry: статический каталог 10 деревьев × 9 узлов (2 ветки A/B, 4 тира,
  капстоуны и ульт); эффекты узлов: attr / resist / kit_base / kit_mult / cd /
  regen / avoid / proc.
- TalentService: очки (earned = clamp(charLevel − start-level + 1, 0, max-points));
  покупка с ПОЛНОЙ серверной валидацией (спека → узел из дерева активной спеки →
  тир-гейт → пререквизиты → стоимость); сброс платный (reset-base +
  reset-per-point × потрачено) или бесплатный для raskolclasses.admin; reconcile
  постоянных модификаторов (source "talents") и кэш-карт хот-пути
  (baseBonus/coeffMult/cooldownMult/procBonus/avoidBonus/regenBonus).
- TalentsStorage: talents.yml через SafeStorage (атомарно, .bak, фолбэк).
- ClassBook, вкладка TALENTS (таб-слот 46): сетка 9 узлов (2/6/10/12/14/16/20/24/31),
  инфо-предмет очков (4), кристалл сброса (40) с двойным ПКМ-подтверждением 30 с;
  покупка кликом; состояния узлов (куплен/доступен/тир/пререквизит/нет очков).
  Управление талантами — ТОЛЬКО через Книгу (решение владельца): команд талантов нет.
- RaskolPlaceholder: %raskolclasses_talent_points%, %raskolclasses_talents%,
  %raskolclasses_spec%, %raskolclasses_spec_id%.
- Selftest чеки 22–24: экономика очков, стоимость полного дерева = 21,
  reconcile-цикл (покупка → reconcile → откат без рассинхрона).
- config: секция talents (enabled/start-level 40/points-per-level 1/max-points 21/
  reset-base 500/reset-per-point 25); messages.book.tab.talents;
  messages.talents.reset.poor.

### Changed
- Кит-хелперы dmg/heal/grant учитывают baseBonus/coeffMult; AbilityRegistry умножает
  кулдаун на cooldownMult; AttributeService.effectiveAvoidance добавляет avoid-бонусы;
  ResourceService.tick добавляет regen-бонус; PassiveListener добавляет procBonus
  (казнь/хищник/яды/садизм/благодать) и маркер хилера для благодати и ресурса за лечение.
- Применение способностей и инсталляций — только свитками в хотбаре; /rc 1–7 удалены.

### Fixed (серия fix1–fix12 по итогам плейтеста)
- fix6: ResourceService.consume делегирует в ResourceState.consume — баг
  «ресурс не тратится» (add() игнорировал отрицательные числа).
- fix7–fix12: ScrollCooldownTask v12 — полоса прочности на свитке как КД
  (Damageable#setMaxDamage/setDamage, работает на аметисте); пакеты только на
  старте каста/готовности/переключении — рука не дёргается.
- FxService: резолв звуков через Sound.valueOf (NamespacedKey-лук-ап не находил
  ключи); заряженные снаряды = Snowball без взрыва + огненный трейл; Зевс —
  мгновенный урон + сцена молнии поверх.
- BindListener: точечные DENY вместо полного cancel; конвейер tryCast на всех путях.
- InstallToken.isInstallScroll для ScrollSanitizer; InstallationService:
  placeCooldownRemaining/placeCooldownTotalMillis для бара КД инсталляций.

### Validate
- /rc selftest → 28/28 PASS (до чеков 1.9.1).
- Плейтест мага: Прометей/Гермес/Борей/Афина/Зевс работают; КД-полоса на аметисте;
  мана списывается; руна-зона 8 блоков с нарастающим уроном/замедлением и
  баффом мага (+3 маны/с, ИНТ ×2).

## [1.8.1] — 2026-09-11 · «Стабилизационная серия перед 1.9.0»

### Fixed
- S1: ванильный мили/стрелы по союзнику в wilderness отменялись только Towny —
  CombatService.onDamage отменяет союзнический урон при friendly-fire=false.
- S2: центральный фракционный гейт в CombatService.dealDamage.
- S3: canHit-гейты в 9 однотargetных абилках 5 китов — дебаффы/поджог/яд не
  вешаются на союзника при отклонённом уроне; каст отклоняется до траты ресурса/КД.
- S4: TownyHook (reflection, fail closed) — «Шаг Гермеса» не блинкует в чужой клейм.
- S5: %raskolclasses_level% и char_level в PAPI.
- S6: selftest-чек canHit.
- S7: health-ratio клампится [0,1] при сохранении и загрузке (health.yml).
- ResourceService: healer-маркер для ресурса за лечение (жрец).

## [1.8.0] — 2026-09-10 · «Уровень персонажа: прогрессия от ширины прокачки»

### Added
- CharacterLevelService: уровень = floor(среднее топ-N скиллов AuraSkills),
  кап attributes.level-cap (60); дисплей в /rc, /rc debug, Книге, PAPI.
- config: character-level (top-n/fallback/skills), attributes.level-cap.
- Selftest чеки 19–20 (topNAverage).

### Changed
- attributes.level-source: character (дефолт): атрибуты растут от сводного уровня;
  анлоки способностей остаются на профильном скилле; рубильник отката class-skill/vanilla.

## [1.7.6] — 2026-09-10 · «TTK-харнесс и баланс-пакет 1.7.6.1–1.7.6.3»

### Added
- BalanceSimulator (/rc debug simulate [A] [B] [level], simulate matrix [level]):
  headless-дуэли на боевых формулах; матрица 5×5; якорь balance.target-ttk-seconds 20 с.
- Burst-окно: combat.burst-window-seconds 3.0 / burst-window-pct 18.0.

### Changed (тюнинг)
- avoidance.dodge-mult 0.5; parry-k 300; resist WARRIOR physical 18;
  HUNTER resource-on-deal 4; coeff-подъём против танков.

## [1.7.5] — 2026-09-10 · «Спеки = пассивная идентичность»

### Removed
- Активки спеков; SpecActiveCaster/SpecBindListener; свитки спеков сгорают на join.

### Decisions
- Дерево талантов спеки отложено до 1.9.0.

## [1.7.4] — 2026-09-10 · «Кит Жреца: католика/паладинство (HPow-хилы)»
- 5 способностей от Силы исцеления; таргет-правила себя/союзника; refund при полном HP.

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
- STR-реген; летальность среды × maxHP/20; уклонение/парирование (гиперболы, DR,
  углы, стаггер); криты мили/магии; selftest; хотфиксы 1.7.0.1–1.7.0.6.

## [1.6.15] — 2026-09-08 · «Заморозка ветки 1.6.x»
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
