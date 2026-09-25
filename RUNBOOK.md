# RUNBOOK — RaskolClasses

Операторский справочник сервера «РАСКОЛ | ДВЕ КОРОНЫ».
Актуально для линии **1.11.0**. Всё, что тюнится без пересборки, помечено `/rc reload`.
Формат разделов — римская нумерация, как в README; записи — только процедуры, ключи, проверки.

---

## 0. МОДЕЛЬ ЗДОРОВЬЯ (план B: виртуальный пул)

### 0.1 Формула
```
HP = base-hp + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus : 0) + gear-hp
Дефолты: base-hp 100, per-str 20, per-level 5, main-str-bonus 8.
Воин L60/STR84 без шмота = 2560 HP.
```
Ключи `attributes.hp.per-level` и `main-str-bonus` — ЖИВЫЕ. `gear-hp` — из RaskolGear (PDC).

### 0.2 Carrier / formula / scale
- **formula** — реальный боевой пул; **carrier** = min(formula, 1024) в ванильном `max_health`;
  **scale** = carrier/formula. Урон/хилы/капы/реген/HUD — в formula-единицах.

### 0.3 Проверка «на живом игроке»
| Команда | Ожидание (воин L60/STR84) | Комментарий |
|---|---|---|
| `/rc debug <ник>` | `maxHP 2560` | formula |
| HUD actionbar | `…/2560` | formula |
| `/attribute <ник> minecraft:max_health base get` | `1024` | carrier — норма, не баг |
| `/rc selftest` чеки 33–35 | PASS | scale / healFormula / targetCarrier |

### 0.4 Персист здоровья
`health.yml` хранит ДОЛЮ от formula ([0,1] при сохранении и загрузке). Без записи — полный пул.

### 0.5 Datapack не нужен
Папку `world/datapacks/raskol_hp` удалить, если осталась.

### 0.6 Аварийный порядок при «странном HP»
1. `/version RaskolClasses` ≥ 1.9.3. 2. `/rc selftest` 33–35. 3. WARNING ConfigValidator.
4. Sweep HpAttributeSync чинит рассинхрон за ≤5 с; принудительно `/rc reload`. 5. Гейта нет: откат jar по I.1.

---

## I. АВАРИЙНЫЕ ПРОЦЕДУРЫ

### 1.1 Откат jar
1. `/stop`. 2. `cp plugins/raskol-classes-X.Y.Z.jar{,.broken}`. 3. Положить предыдущий jar.
4. Старт → `/rc debug` → сверить версию и maxHP. 5. Issue: `logs/latest.log` + `/rc selftest` + `/rc health`.

### 1.2 Восстановление хранилищ из `.bak`
1. `/stop`; 2. `rm <file> <file>.tmp`; 3. `mv <file>.bak <file>` для `talents.yml`, `cooldowns.yml`,
   `spec-choices.yml`, `resources.yml`, `health.yml`; 4. старт; при битом `.bak` — внешний бэкап хостинга.

### 1.3 Отключение подсистем гейтами (`/rc reload`, рестарт не нужен)
| Проблема | Ключ | Значение-выключатель |
|---|---|---|
| Таланты ломают бой | `talents.enabled` | `false` |
| Burst-окно режет легитимный урон | `combat.burst-window-pct` | `0` |
| Анти-ваншот мешает ивенту | `combat.max-single-hit-pct` | `0` |
| AoE сквозь стены | `combat.aoe-los` | `false` |
| Friendly-fire на арене | `combat.friendly-fire` | `true` |
| Реген HP от СИЛЫ лишний | `attributes.hp.regen-per-str` | `0` |
| Level-надбавка HP лишняя | `attributes.hp.per-level` | `0` |
| STR-main надбавка HP лишняя | `attributes.hp.main-str-bonus` | `0` |
| Криты/визуал крита | `attributes.crit.visuals` | `false` |
| Звуковой спам в замесе | `performance.sound-budget-per-tick` | `0` |
| Инсталляции на ивенте | `installations.max-global` | `0` |
| HUD-полосы (вернуть ваниль) | `hp-display.mode` | `vanilla` (рестарт) |
| Резисты на арене | `resist.disabled-worlds` | `[имя_мира]` |
| Откат к прогрессии 1.7.x | `attributes.level-source` | `class-skill` |
| RaskolGear ломает бой | — | вынуть jar RaskolGear |
| Откат чернокнижника лишний | `classes.WARLOCK.recoil-percent` | `0` |
| Ад-имба чернокнижника | `classes.WARLOCK.nether-mult` | `1.0` |
| Переполнение кусает | `classes.WARLOCK.threshold-overflow` | `999` |
| Скверна-буст урона | `classes.WARLOCK.threshold-open` | `999` |
| Дроп фолианта нежелателен | `foliant.drop-enabled` | `false` |
| Дроп фолианта шире/уже | `foliant.drop-mobs` | `[]` = все мобы ада / список типов |

### 1.4 Экстренный сброс кулдаунов
`/stop` → `rm plugins/RaskolClasses/cooldowns.yml` → старт.

### 1.5 Экстренный сброс талантов игрока
Команд талантов нет (UI в Книге). Аварийно: `/stop` → в `talents.yml` удалить блок
`players.<uuid>.<specId>` → старт (или `/rc reload`, если игрок онлайн).
Админский бесплатный сброс своего дерева: Книга → TALENTS → кристалл ×2 ПКМ.

### 1.6 Ярость воина «не падает» (замок — чек 36)
1. Боевое окно 5 с после последнего урона (нанесённого ИЛИ полученного).
2. `classes.WARRIOR.resource-regen` = `-5.0`. 3. `/rc selftest` чек 36.

### 1.7 Авария: фолиант прочитан не тем / случайно
1. Изъять оставшиеся тома (PDC-тег `raskol_foliant`).
2. Вернуть класс: `lp user <ник> parent remove class_warlock` → `parent add class_<прежний>`.
3. Спеку/таланты: восстановить `spec-choices.yml` и `talents.yml` из `.bak` ДО перехода
   (или дать пройти отречение/сброс бесплатно админ-кристаллом).
4. Sorcery не откатывать (это общий прогресс мага/чернокнижника).
5. Инсталляции-Пентаграммы сгорят по TTL 25 с сами.
6. Инцидент: `/rc foliant give` и строки `Foliant:` видны в логах.
Профилактика: гейты проверяются дважды (ПКМ и клик GUI); право `raskolclasses.admin` только у владельца.

---

## II. ТЮНИНГ БАЛАНСА БЕЗ ПЕРЕСБОРКИ (`/rc reload`)

### 2.1 Резисты и урон
`resist.cap/pvp-cap/classes.*/grants.*/specs.*`; `damage-types.vanilla-map.*`;
`damage-types.env-lethal-scale/env-lethal`; `combat.true-damage-cap-per-hit`,
`max-single-hit-pct` + `cap-exempt-causes`, `burst-window-seconds/pct`.

### 2.2 Атрибуты, HP, реген, уровень персонажа
`attributes.classes.*`; `attributes.hp.*`; `attributes.level-source`;
`character-level.top-n/fallback/skills`; `attributes.level-cap`.
**Менять cap и top-n только между сезонами.**

### 2.3 HUD, VFX, avoidance
`hp-display.*`; `vfx.*` (опечатка = WARNING + тишина); `avoidance.*`.

### 2.4 Экономика талантов
`talents.enabled/start-level/points-per-level/max-points/reset-base/reset-per-point`.
Инвариант сезона: полное дерево = 21 очко = charLevel 60; бюджет общий (spentGlobal).

### 2.5 TTK-харнесс
`/rc debug simulate matrix [level]` — матрица 6×6; якорь `balance.target-ttk-seconds` (20 с),
коридор ±30%. Тюнинг китов: `classes.<CLASS>.abilities.<id>.base/.coeff/.cooldown`.
**После правок HP-формулы TTK сдвинется ВВЕРХ** — прогони матрицу. Журнал: `combat.debug-damage`.

### 2.6 Хранилища
`storage.autosave-minutes` (5): при краше теряется ≤ N минут кулдаунов/ресурсов.

### 2.7 Gear и сеты (RaskolGear)
Числа шмота и сет-бонусов — в конфиге RaskolGear; RaskolClasses читает на лету.
Резисты шмота в бою применяет RaskolGear; классовые — всегда (стек мультипликативный).
WP/SP-надбавка пропускается для оружия с тегом `raskolgear:gear_type=WEAPON`.
Сет 4/4 вешает модификатор `source=set-bonus-<CLASS>-<RARITY>`.

### 2.8 Чернокнижник (1.10.x → 1.11.0)
- Ресурс: `classes.WARLOCK.resource-on-deal` (9), `resource-on-take` (5), `resource-on-kill` (15),
  `resource-decay-out-of-combat` (−4), пол = 0 (`resource-floor: 0`).
- Пороги: `threshold-open` (75, +20% урона), `threshold-overflow` (100, тик 1%/с).
- Цена: `recoil-percent` (6.66), `recoil-cap-pct` (30), `recoil-min-hp` (1).
- Ад: `nether-mult` (6.0) — миры с environment NETHER.
- Чёрное Слово v2: `abilities.black_word.cost` (0), `cooldown` (3), `self-cost-pct` (10),
  `corruption-gain` (25); лечения нет (ключа `drain` у слова нет).
- Голод Скверны: `drain` 0.666, `corruption-gain` 12.
- Пентаграмма: `installations.heresy_circle.radius/duration(25)/cooldown/damage-magic/
  corruption-per-sec`; визуал и звуки — `vfx.heresy_circle.*` (постановка ENTITY_WARDEN_EMERGE,
  эмбиент ENTITY_BLAZE_AMBIENT).
- Фолиант: `foliant.drop-enabled/drop-nether-only/drop-chance-percent(0.001)/drop-mobs`,
  тексты `foliant.item-name/gui-title/gui-yes/gui-no`.
- Прогрессия: профильный скилл = **sorcery** (как у мага); дерева occult в AuraSkills нет.

---

## III. ЧЕК-ЛИСТ ОПЕРАТОРА ПЕРЕД ИВЕНТОМ / ОСАДОЙ

1. `/version RaskolClasses` = 1.11.0.
2. Лог старта: баннер с **пятью** путями (чернокнижник скрыт); `Базы резистов` — пять классов;
   `RaskolGear: хук активирован…` (или «найден… активируется после включения» — норма);
   `ConfigValidator: конфиг валиден…`.
3. `/rc selftest` → **40/40 PASS** (SKIP 21/24/31/32 без онлайн-зонда допустимы).
4. `/rc health` → MSPT ≤ 50, purge ≤ 60 с.
5. Боевой чек на 3–5 игроках: `/rc debug`, `/rc gear`, Книга → GEAR.
   Чернокнижник (если есть): `/rc debug` → ресурс «Скверна», SP base 40; каст «Чёрного Слова» →
   HP −10% макс, Скверна +25 (+9 от урона), лечения нет.
6. Фолиант-чек (только при planned-выдаче): `/rc foliant give` тест-магу 40+ без спеки/талантов →
   GUI → «Отмена» (том цел) → повтор → «Прочесть» (класс сменился, LP `class_warlock`, sorcery=10).
7. Конфиг под тип события: PvP (`friendly-fire: true`, `disabled-worlds`, `pvp-cap`);
   PvE (`max-global` выше); творческий (`block-casts-in-creative: false`);
   ад-ивент (`nether-mult` при желании 2–3; `drop-chance-percent` при желании 0 на время ивента).
8. Страховка: `tar -czf backup-pre-event-*.tar.gz plugins/RaskolClasses …`.
9. Во время ивента: `/rc health` каждые 5–10 мин; при MSPT > 50 — `/spark profiler` 60 с;
   жалобы «не лечится вовсе» → проверить анти-хил Раскола Души (6 с, партиклы SCULK у цели).
10. После: вернуть повседневный конфиг, `/rc reload`, `/rc selftest`, лог инцидентов.

---

## IV. КОНТАКТЫ И ЭСКАЛАЦИЯ

- Баг в патче: GitHub Issues + `logs/latest.log` + `/rc selftest` + `/rc health`.
- Крит: откат по I.1 + сообщение в рабочий чат (версия, время).
- Конфиг-вопрос: раздел II + `/rc debug` на живой цели.
- HP-вопрос: раздел 0 (formula/carrier/scale) — carrier 1024 не баг.
- Фолиант-вопрос: раздел 1.7.

---

## V. УРОВЕНЬ ПЕРСОНАЖА И АТРИБУТЫ

- floor(среднее топ-5 скиллов AuraSkills из 10 деревьев), кап 60; фолбэк без AuraSkills = 40.
- Атрибуты: base(class) + growth(class)×charLevel + модификаторы (спек, таланты `talents`,
  сеты `set-bonus-*`, эффекты, руна `frost_rune_int`).
- Реген HP: STR × regen-per-str HP/с вне боя; в бою × regen-combat-factor; кап regen-cap-pct.
  Анти-хил Раскола Души его НЕ блокирует (решение дизайна).
- Среда: FALL/DROWNING/SUFFOCATION/STARVATION = TRUE + масштаб × carrier/20.
- HUD: actionbar `❬ ❤ полоса числа ❭ ❬ эмблема полоса числа ❭` в formula-числах; сердца = один ряд.

---

## VI. ПРОИЗВОДНЫЕ СТАТЫ, АНТИ-ВАНШОТ, BURST-ОКНО

- WP = base-wp + STR×1.5 + AGI×0.5; SP = base-sp + INT×1.5; HPow = base-hpow + INT×1.4.
  Референс 40 ур.: Воин WP 133, Охотник 104, Разбойник 104, Маг SP 120, Жрец SP 115 / HPow 109,
  Чернокнижник SP 130 (base 40).
- Базовый урон: ваниль + WP×0.35 (маг-причины: SP×0.35); для оружия RaskolGear пропускается.
- Анти-ваншот: путь A — кап в carrier (≤35%); путь B — в formula (≤35%); исключения —
  `cap-exempt-causes` и `allowOverCap` execute-финишеров.
- Burst-окно: суммарно за 3 с ≤ 18% formula; среда и execute вне окна.
- Амплификация «Печати Погибели» (+26%) — ПОСЛЕ резистов и ДО капов в обоих путях.

---

## VII. СПЕКИ И ТАЛАНТЫ

- Спека = пассивная идентичность с 40 ур. профильного скилла; респец платный
  (250 + 10×уровень) с двойным подтверждением 30 с; модификаторы старой спеки снимаются.
- Таланты: дерево активной спеки (11 деревьев, включая `occult`); очки = clamp(charLevel−39, 0, 21),
  бюджет общий; покупка ЛКМ, сброс кристаллом ×2 ПКМ.
- Reconcile: join, покупка, сброс, смена спеки, `/rc reload`, PassportChange (Core 1.3.0).
- Чернокнижник: спеки BLACK_MAGE / HELL_CHANNEL делят одно дерево `occult`; покупки хранятся
  раздельно по specId, бюджет общий.

---

## VIII. КНИГА КЛАССА И КОМАНДЫ

- Каркас (54 слота): row0 рамка + эмблема (4); row1-4 контент; row5 навигация
  45 Способности, 46 Спеки, 47 Класс, 48 Таланты, 49 Закрыть, 50 Шмот. Деструктив всегда 40.
- Вкладка GEAR информационная; эмблема WARLOCK = WRITABLE_BOOK.
- Клики: ЛКМ основное, ПКМ вторичное, двойное ПКМ + 30 с деструктив.
- Команды: `/rc`, `/rc menu`, `/rc gear [player]`, `/rc debug …`, `/rc health`, `/rc selftest`,
  `/rc foliant give <ник>` (admin), `/rc reload`. Цифровых подкоманд нет.

---

## IX. БАЛАНС-ЯКОРЯ И ДОРОЖНАЯ КАРТА

- TTK равных 15–25 с; зеркала 40 ур. в коридоре 14–26 с.
- Известные исключения матрицы: жрец-зеркало heal-war ≥ 30 с; чернокнижник-жрец: анти-хил
  выводит зеркало из коридора осознанно.
- Полное дерево талантов = 21 очко = charLevel 60 (включая `occult`).
- Закрыто в 1.10.x: шестой класс, Скверна, фолиант-дроп, Пентаграмма, ад-домен, 40 чеков, матрица 6×6.
- Дальше: 1.11.x — баланс по живым боям матрицы 6×6; достройка BlueprintHook (чертежи
  RaskolEnchant → рецепты); 1.12.x — глубина сет-слоя RaskolGear (Oraxen-сеты, reflect-механики).

---

## X. ЧЕРНОКНИЖНИК: ПАМЯТКА ОПЕРАТОРА (1.11.0)

### X.1 Жизненный цикл пути
1. Том падает с пиглинов ада: 0.001% (`foliant.drop-chance-percent`), белый список `drop-mobs`.
2. Маг/жрец 40+ без спеки и без потраченных талантов: ПКМ → гейты → GUI 27 слотов
   (11 «Прочесть страницу», 15 «Закрыть том»).
3. Подтверждение: том сгорает; LP `class_warlock`; sorcery=10; инсталляции сгорают;
   модификаторы/резисты/ресурс/пассивки очищаются; визуал page_turn + warden_roar + sculk.
4. Прогрессия: способности по sorcery 10/25/50/65/75 (как у мага), спека на sorcery 40.

### X.2 Что смотреть в `/rc debug` чернокнижника
- Ресурс «Скверна»: состояние <75 прикрыта / 75–99 раскрыта / 100 переполнена.
- SP база 40 (выше мага 30) → урон кита выше магического при том же INT.
- Резисты база 10/26; модификаторы `set-bonus-*` и `talents` поверх.
- Симулятор урона учитывает резисты, но НЕ откат/дрейн (они в бою, не в simulate).

### X.3 Частые жалобы и ответы
| Жалоба | Норма/действие |
|---|---|
| «Чёрное Слово ест здоровье» | Норма: плата 10% макс HP; предохранитель — не ниже 1 HP |
| «Слово не лечит» | Норма с 1.10.4: дрейн снят, вместо него +25 Скверны |
| «Меня нельзя вылечить» | Анти-хил «Раскола Души» 6 с; свой STR-реген работает |
| «Кастую и теряю HP» | Откат 6.66% — цена кита; кап 30% maxHP за каст |
| «В аду имба» | Дизайн-домен ×6 (`nether-mult`); гейт 1.3 при желании |
| «Скверна стоит на 100 и я горю» | Переполнение: тик прекратится ниже 85 |
| «Том не читается» | Гейты: класс, 40+, спека сброшена, таланты сброшены, AuthMe; сообщение подскажет |
| «Печать не снимается» | Дизайн: снимается только смертью (66.6 с либо таймаут) |
| «Пентаграмма не видна» | Проверь `vfx.heresy_circle.*` и партиклы SOUL_FIRE_FLAME/CRIMSON_SPORE (1.16+) |

### X.4 VFX-справка кита и Пентаграммы
Warden (heartbeat/roar/sonic/emerge), Elder Guardian (curse), Evoker (prepare_attack),
Vex (charge/death), Blaze (ambient Пентаграммы); партиклы SCULK_SOUL, SONIC_BOOM,
SOUL_FIRE_FLAME, CRIMSON_SPORE, WARPED_SPORE, SOUL, ASH. Битых ключей нет (проверено по enum 1.21.4).
