# RUNBOOK — RaskolClasses

Операторский справочник сервера «РАСКОЛ | ДВЕ КОРОНЫ».
Актуально для линии **1.9.3.2**. Всё, что тюнится без пересборки, помечено `/rc reload`.
Формат разделов — римская нумерация, как в README; формат записей — Added/Fixed-нейтральный:
только процедуры, ключи, проверки.

---

## 0. МОДЕЛЬ ЗДОРОВЬЯ (1.9.3, план B: виртуальный пул)

### 0.1 Формула
```
HP = base-hp + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus : 0) + gear-hp
Дефолты: base-hp 100, per-str 20, per-level 5, main-str-bonus 8.
Воин L60/STR84 без шмота = 100 + 1680 + 300 + 480 = 2560 HP.
```
Ключи `attributes.hp.per-level` и `main-str-bonus` — ЖИВЫЕ с 1.9.3 (тюнинг без пересборки).
`gear-hp` приходит из RaskolGear (PDC `raskolgear:hp_bonus`) через GearHook.

### 0.2 Carrier / formula / scale
- **formula** — реальный боевой пул (не ограничен ничем).
- **carrier** — ванильный `max_health` = `min(formula, 1024)` (предел движка).
  Выставляется HpAttributeSync на join/respawn/reload/invalidate + sweep каждые 5 с.
- **scale** = carrier / formula. Урон, хилы, капы, реген и HUD работают в formula-единицах;
  на границе с ванилью умножаются на scale. Burst-лог хранится в formula-единицах.

### 0.3 Проверка «на живом игроке» (что нормально, а что баг)
| Команда | Ожидание (воин L60/STR84) | Комментарий |
|---|---|---|
| `/rc debug <ник>` | `maxHP 2560` | formula |
| HUD actionbar | `…/2560` | formula |
| `/attribute <ник> minecraft:max_health base get` | `1024` | carrier — **это норма**, не баг |
| `/rc selftest` чеки 33–35 | PASS | scale / healFormula / targetCarrier |

### 0.4 Персист здоровья
`health.yml` хранит ДОЛЮ от formula ([0,1], кламп при сохранении и загрузке — S7).
На join: доля × formula → ×scale → в ванильное здоровье. Без записи — полный пул.

### 0.5 Datapack больше не нужен
План B снимает потолок 1024 без оверрайда реестра. Если на сервере осталась папка
`world/datapacks/raskol_hp` — **удалить** и рестартануть: она бесполезна и путает диагностику.

### 0.6 Аварийный порядок при «странном HP»
1. Сверь jar: `/version RaskolClasses` ≥ 1.9.3 (иначе HUD показывает carrier-числа).
2. `/rc selftest` → чеки 33–35.
3. Проверь `attributes.hp.*` в конфиге (NaN/отрицательные ловит ConfigValidator).
4. Sweep HpAttributeSync чинит рассинхрон сам за ≤5 с; принудительно — `/rc reload`.
5. Гейта у плана B нет (это ядро): аварийное отключение = откат jar по I.1.

---

## I. АВАРИЙНЫЕ ПРОЦЕДУРЫ

### 1.1 Откат jar на предыдущую версию
1. `/stop` (graceful) — плагины сохранят состояния в `onDisable`.
2. `cp plugins/raskol-classes-X.Y.Z.jar{,.broken}`.
3. Положить предыдущий jar (GitHub Releases или архив куратора).
4. Старт → `/rc debug` → сверить строку «Версия» и maxHP.
5. Issue в репозиторий: `logs/latest.log` + вывод `/rc selftest` и `/rc health`.

### 1.2 Восстановление хранилищ из `.bak`
1. `/stop`; `ls -la plugins/RaskolClasses/*.yml*`.
2. Удалить битый текущий и `.tmp`: `rm talents.yml talents.yml.tmp`.
3. `mv <file>.bak <file>` для: `talents.yml`, `cooldowns.yml`, `spec-choices.yml`,
   `resources.yml`, `health.yml`.
4. Старт: плагин сам пишет WARNING/SEVERE о фолбэке; если и `.bak` бит — старт с пустыми
   данными и SEVERE: восстанавливаем из внешнего бэкапа хостинга.

### 1.3 Отключение подсистем гейтами (всё — `/rc reload`, рестарт не нужен)
| Проблема | Ключ | Значение-выключатель |
|---|---|---|
| Таланты ломают бой | `talents.enabled` | `false` |
| Burst-окно режет легитимный урон | `combat.burst-window-pct` | `0` |
| Анти-ваншот мешает ивенту | `combat.max-single-hit-pct` | `0` |
| AoE сквозь стены | `combat.aoe-los` | `false` |
| Friendly-fire нужен на арене | `combat.friendly-fire` | `true` |
| Реген HP от СИЛЫ лишний | `attributes.hp.regen-per-str` | `0` |
| Level-надбавка HP лишняя | `attributes.hp.per-level` | `0` |
| STR-main надбавка HP лишняя | `attributes.hp.main-str-bonus` | `0` |
| Криты/визуал крита | `attributes.crit.visuals` | `false` |
| Звуковой спам в замесе | `performance.sound-budget-per-tick` | `0` |
| Инсталляции на ивенте | `installations.max-global` | `0` |
| HUD-полосы (вернуть ваниль) | `hp-display.mode` | `vanilla` (рестарт) |
| Резисты на арене | `resist.disabled-worlds` | `[имя_мира]` |
| Откат к прогрессии 1.7.x | `attributes.level-source` | `class-skill` |
| RaskolGear ломает бой | — | вынуть jar RaskolGear (хук деградирует gracefully) |

### 1.4 Экстренный сброс кулдаунов
`/stop` → `rm plugins/RaskolClasses/cooldowns.yml` (`.bak` останется страховкой) → старт.

### 1.5 Экстренный сброс талантов игрока
Команд талантов нет (UI только в Книге). Аварийный путь:
1. `/stop` (или вывести игрока в оффлайн).
2. В `talents.yml` удалить список `players.<uuid>.<specId>` (или весь блок игрока).
3. Старт; если игрок онлайн — достаточно `/rc reload` (reconcile всех онлайн).
Админский бесплатный сброс своего дерева: Книга → TALENTS → кристалл ×2 ПКМ
(при праве `raskolclasses.admin` плата не списывается).

### 1.6 Ярость воина «не падает» (баг 1.9.3.2)
Если после деплоя ≥1.9.3.2 ярость всё же висит:
1. Проверь боевое окно: 5 с после последнего урона (нанесённого ИЛИ полученного).
2. Ключ `classes.WARRIOR.resource-regen` = `-5.0` (декэй идёт через `tickDelta`).
3. `/rc selftest` чек 36 — регресс-замок именно этого бага.

---

## II. ТЮНИНГ БАЛАНСА БЕЗ ПЕРЕСБОРКИ (`/rc reload`)

### 2.1 Резисты и урон
- `resist.cap`, `resist.pvp-cap`, `resist.classes.*`, `resist.grants.*`, `resist.specs.*` —
  диапазоны 0..100, проверяет ConfigValidator.
- `damage-types.vanilla-map.*` — тип ванильной причины (physical|magic|true).
- `damage-types.env-lethal-scale` / `env-lethal` — летальность среды × carrier/20.
- `combat.true-damage-cap-per-hit`, `combat.max-single-hit-pct` + `cap-exempt-causes`,
  `combat.burst-window-seconds` / `burst-window-pct`.

### 2.2 Атрибуты, HP, реген, уровень персонажа
- `attributes.classes.*` — базы/рост/основной атрибут класса.
- `attributes.hp.*` — вся формула 0.1, включая живые `per-level` и `main-str-bonus`.
- `attributes.level-source`: `character` (дефолт) | `class-skill` | `vanilla`.
- `character-level.top-n` (5), `fallback` (40), список `skills`; `attributes.level-cap` (60).
  **Менять cap и top-n только между сезонами:** от этого зависят очки талантов и билды.
- Фолбэк без AuraSkills = `character-level.fallback` (40), НЕ ванильный XP (фикс 1.9.3).

### 2.3 HUD, VFX, avoidance
- `hp-display.*`: mode/gauge/gradient/regen-spark/colors — палитра и компоновка формульных чисел.
- `vfx.*`: звуки/партиклы кастов и проков; опечатка = WARNING + тишина, не краш.
  Алиасы 1.9.3: `ENTITY_WOLF_HOWL→ENTITY_WOLF_AMBIENT`, `BLOCK_SNOW_BLOCK_BREAK→BLOCK_SNOW_BREAK`.
- `avoidance.*`: dodge-k/parry-k/dodge-mult/soft-cap/dr-factor/hard-cap/углы/стаггер.

### 2.4 Экономика талантов (1.9.0)
- `talents.enabled`, `start-level` (40), `points-per-level` (1), `max-points` (21),
  `reset-base` (500), `reset-per-point` (25).
- Инвариант сезона: **полное дерево = 21 очко = charLevel 60**. Очки — ОБЩИЙ бюджет
  персонажа (spentGlobal по всем деревьям); респец очки не печатает.

### 2.5 TTK-харнесс
- `/rc debug simulate matrix [level]` — матрица 5×5; якорь `balance.target-ttk-seconds` (20 с),
  коридор подсветки ±30%.
- Тюнинг китов: `classes.<CLASS>.abilities.<id>.base` / `.coeff` / `.cooldown`;
  талантные `kit_mult`/`kit_base` складываются поверх автоматически.
- **После правок HP-формулы TTK сдвинется ВВЕРХ** — прогони матрицу и при необходимости
  подними base/coeff уронных абилок. Журнал разбора: `combat.debug-damage`.

### 2.6 Хранилища
- `storage.autosave-minutes` (5): при краше теряется ≤ N минут кулдаунов/ресурсов;
  спек-выборы и таланты не теряются (пишутся на каждом изменении).

### 2.7 Gear и сеты (RaskolGear)
- Числа шмота и сет-бонусов живут в **конфиге RaskolGear** (`armor.*`, `weapons.*`);
  RaskolClasses читает их на лету (GearHook/SetBonusService) — правка там не требует
  нашего reload, достаточно релога игрока или `/rc reload` у нас для прогревания кэша.
- Резисты шмота в бою применяет сам RaskolGear; наши классовые резисты применяются ВСЕГДА
  (стек мультипликативный, дубля нет).WP/SP-надбавка нашего офенса пропускается, если в руке
  оружие с PDC-тегом `raskolgear:gear_type=WEAPON` (иначе двойной скейл).
- Сет 4/4 вешает модификатор резистов `source=set-bonus-<CLASS>-<RARITY>` — виден в
  `/rc debug` breakdown и в Книге (вкладка RESIST/GEAR).

---

## III. ЧЕК-ЛИСТ ОПЕРАТОРА ПЕРЕД ИВЕНТОМ / ОСАДОЙ

1. `/version RaskolClasses` = ожидаемая версия релиза.
2. Лог старта: `RaskolGear: хук активирован…` (или «найден… активируется после включения» —
   норма при циклическом softdepend), `RaskolEnchant: …`, `ConfigValidator: конфиг валиден…`.
   Строка `Базы резистов … 0/0` на старте — **косметика** (онлайн-игроков ещё нет);
   в игре проверяй `/rc debug`.
3. `/rc selftest` → **36/36 PASS** (из консоли допустимы SKIP-чеки 21/24/31/32 без онлайн-зонда).
4. `/rc health` → MSPT ≤ 50 (зелёная), purge ≤ 60 с.
5. Боевой чек на 3–5 игроках: `/rc debug` (атрибуты, резисты, gear-строка, симулятор),
   `/rc gear` (сеты N/4), Книга → GEAR.
6. Конфиг под тип события: PvP-ивент (`friendly-fire: true`, `disabled-worlds` арены,
   `pvp-cap` при необходимости); PvE-ивент (`max-global` выше); творческий
   (`block-casts-in-creative: false`).
7. Страховка: снапшот панели или `tar -czf backup-pre-event-*.tar.gz plugins/RaskolClasses …`.
8. Во время ивента: `/rc health` каждые 5–10 мин; при MSPT > 50 — `/spark profiler` 60 с;
   жалобы «не бьёт/не лечит» → `/rc debug` на репрезентативной цели + сверка formula/carrier (0.3).
9. После: вернуть повседневный конфиг, `/rc reload`, `/rc selftest`, лог инцидентов.

---

## IV. КОНТАКТЫ И ЭСКАЛАЦИЯ

- Баг в патче: GitHub Issues + `logs/latest.log` + вывод `/rc selftest` и `/rc health`.
- Критический инцидент: откат по I.1 + сообщение в рабочий чат (версия, время).
- Конфиг-вопрос: раздел II + `/rc debug` на живой цели.
- HP-вопрос: раздел 0 (formula/carrier/scale) — не путать carrier 1024 с багом.

---

## V. УРОВЕНЬ ПЕРСОНАЖА И АТРИБУТЫ

- **Уровень персонажа:** floor(среднее топ-N скиллов AuraSkills), кап `attributes.level-cap` (60).
  Фолбэк без AuraSkills = `character-level.fallback` (40). Пример: healing 99 при остальных
  низких даёт топ-5 ≈ 47, а не 99 — одиночное дерево не раздувает статы.
- **Атрибуты:** STR/AGI/INT = base(class) + growth(class)×charLevel + модификаторы
  (спек-пассивки, таланты source `talents`, сеты source `set-bonus-*`, эффекты, руна).
- **Реген HP:** STR × regen-per-str HP/с вне боя; в бою × regen-combat-factor;
  кап regen-cap-pct от maxHP/с. Идёт в formula-единицах, применяется через scale.
- **Среда:** FALL/DROWNING/SUFFOCATION/STARVATION = тип TRUE + масштаб × carrier/20 —
  падение и утопление летальны при любом пуле. Protection/Feather Falling — после масштаба.
- **HUD:** одна строка actionbar `❬ ❤ полоса числа ❭ ❬ эмблема полоса числа ❭` в formula-числах;
  градиенты по теме класса, искра регена; сердца = один ряд (healthScale 20).

---

## VI. ПРОИЗВОДНЫЕ СТАТЫ, АНТИ-ВАНШОТ, BURST-ОКНО

- **PowerService:** WP = base-wp + STR×str-to-wp + AGI×agi-to-wp; SP = base-sp + INT×int-to-sp;
  HPow = base-hpow + INT×int-to-hpow. Референс 40 ур.: Воин WP 133, Охотник 104,
  Разбойник 104, Маг SP 120, Жрец SP 115 / HPow 109.
- **Базовый урон:** ванильный удар/стрела = ванильное оружие + WP × basic-coeff (0.35);
  магические причины — через SP × basic-coeff-magic. Для оружия RaskolGear эта надбавка
  пропускается (урон считает WeaponDamageListener).
- **Анти-ваншот:** путь A — кап в carrier-единицах (≤35% carrier); путь B — в formula
  (≤35% formula); исключения — `cap-exempt-causes` (среда) и `allowOverCap` execute-финишеров.
- **Burst-окно:** суммарный урон за `burst-window-seconds` ≤ `burst-window-pct`% formula;
  лог в formula-единицах для обоих путей; среда и execute не учитываются.

---

## VII. СПЕКИ И ТАЛАНТЫ

- **Спека = пассивная идентичность:** выбор с 40 уровня профильного скилла; резисты/проки
  постоянны; респец платный (base 250 + 10×уровень) с двойным подтверждением 30 с;
  модификаторы старой спеки снимаются при респеце.
- **Таланты:** дерево активной спеки в Книге (вкладка TALENTS). Очки =
  clamp(charLevel − 39, 0, 21), бюджет общий. Покупка кликом, сброс кристаллом ×2 ПКМ.
- **Reconcile:** join, покупка, сброс, смена спеки, `/rc reload`, PassportChange (Core 1.3.0).
  Рассинхрон «куплено ≠ применено» невозможен дольше одного события.
- **Аварийный сброс:** I.5.

---

## VIII. КНИГА КЛАССА И КОМАНДЫ

- **Каркас Книги (54 слота):** row0 — рамка + эмблема (4); row1-4 — контент; row5 —
  навигация: 45 Способности, 46 Спеки, 47 Класс, 48 Таланты, 49 Закрыть, 50 Шмот.
  Деструктив (респец / сброс талантов) ВСЕГДА в слоте 40. Активная вкладка = glow + §6.
- **Вкладка GEAR:** оружие в руке, 4 слота брони, статы шмота, сеты N/4 (✔/✘). Информационная.
- **Семантика кликов:** ЛКМ = основное (каст/выбор/покупка), ПКМ = вторичное (свиток/инфо),
  двойное ПКМ + 30 с = деструктив. Shift-клики не используются.
- **Команды:** `/rc`, `/rc menu`, `/rc gear [player]`, `/rc debug [player|simulate …|matrix …]`,
  `/rc health`, `/rc selftest`, `/rc reload`. Цифровых подкоманд нет (свитки в хотбаре).

---

## IX. БАЛАНС-ЯКОРЯ И ДОРОЖНАЯ КАРТА

- **TTK равных:** 15–25 с; зеркала 40 ур. в коридоре 14–26 (исключение — жрец-зеркало
  heal-war ≥ 30 с, осознанно).
- **Полное дерево талантов = 21 очко = charLevel 60.**
- **1.9.3.x закрыло:** потолок HP (план B), gear-хук и сеты, вкладку GEAR, декэй ярости,
  версию из pom, старт-лог хуков, полную Книгу.
- **Дальше:** 1.9.4 — баланс по данным матрицы после HP-реформы; достройка BlueprintHook
  (чертежи/крафт RaskolEnchant → рецепты); 1.10.x — глубина сет-слоя (Oraxen-сеты,
  reflect-механики поверх шипов дикобраза); документация README/RUNBOOK синхронно с релизом.
