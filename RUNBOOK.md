# RUNBOOK — RaskolClasses

Операторский справочник сервера «РАСКОЛ | ДВЕ КОРОНЫ».
Актуально для линии 1.9.x. Всё, что тюнится без пересборки, помечено `/rc reload`.

---

## 1. АВАРИЙНЫЕ ПРОЦЕДУРЫ

### 1.1 Откат jar на предыдущую версию
1. `/stop` (graceful) — плагины сохранят состояния в `onDisable`.
2. `cp plugins/RaskolClasses/raskol-classes-X.Y.Z.jar{,.broken}`.
3. Положить предыдущий jar (GitHub Releases или архив куратора).
4. Старт → `/rc debug` → сверить строку «Версия:».
5. Issue в репозиторий: лог `logs/latest.log` + что сломалось.

### 1.2 Восстановление хранилищ из `.bak`
1. `/stop`; `ls -la plugins/RaskolClasses/*.yml*`.
2. Удалить битый текущий и `.tmp`: `rm talents.yml talents.yml.tmp`.
3. `mv talents.yml.bak talents.yml` (то же для `cooldowns.yml`,
   `spec-choices.yml`, `resources.yml`, `health.yml`).
4. Старт: плагин сам пишет WARNING/SEVERE о фолбэке; если и `.bak` бит — старт с
   пустыми данными и SEVERE: восстанавливаем из внешнего бэкапа хостинга.

### 1.3 Отключение подсистем гейтами (всё — `/rc reload`, рестарт не нужен)
| Проблема | Ключ | Значение-выключатель |
|---|---|---|
| Таланты ломают бой | `talents.enabled` | `false` |
| Уклонение/парирование ломают бой | `avoidance.enabled` | `false` |
| Burst-окно режет легитимный урон | `combat.burst-window-pct` | `0` |
| Анти-ваншот мешает ивенту | `combat.max-single-hit-pct` | `0` |
| AoE сквозь стены | `combat.aoe-los` | `false` |
| Friendly-fire нужен на арене | `combat.friendly-fire` | `true` |
| Реген HP от СИЛЫ лишний | `attributes.hp.regen-per-str` | `0` |
| Криты/визуал крита | `attributes.crit.visuals` | `false` |
| Звуковой спам в замесе | `performance.sound-budget-per-tick` | `0` |
| Инсталляции на ивенте | `installations.max-global` | `0` |
| HUD-полосы | `hp-display.mode` | `vanilla` (рестарт) |
| Резисты на арене | `resist.disabled-worlds` | `[имя_мира]` |
| Откат к прогрессии 1.7.x | `attributes.level-source` | `class-skill` |

### 1.4 Экстренный сброс кулдаунов
`/stop` → `rm plugins/RaskolClasses/cooldowns.yml` (`.bak` останется страховкой) → старт.

### 1.5 Экстренный сброс талантов игрока
Команд талантов нет (UI только в Книге). Аварийный путь:
1. `/stop` (или вывести игрока в оффлайн).
2. В `talents.yml` удалить список `players.<uuid>.<specId>` (или весь блок игрока).
3. Старт; если игрок онлайн — достаточно `/rc reload` (reconcile всех онлайн).
Админский бесплатный сброс своего дерева: Книга → TALENTS → кристалл ×2
(при праве `raskolclasses.admin` плата не списывается).

---

## 2. ТЮНИНГ БАЛАНСА БЕЗ ПЕРЕСБОРКИ (`/rc reload`)

### 2.1 Резисты и урон
- `resist.cap`, `resist.pvp-cap`, `resist.classes.*`, `resist.grants.*`,
  `resist.specs.*` — диапазоны 0..100, проверяет ConfigValidator.
- `damage-types.vanilla-map.*` — тип ванильной причины (physical|magic|true).
- `damage-types.env-lethal-scale` / `env-lethal` — летальность среды × maxHP/20.
- `combat.true-damage-cap-per-hit` — предохранитель чистого урона.
- `combat.max-single-hit-pct` + `cap-exempt-causes` — анти-ваншот и исключения.
- `combat.burst-window-seconds` / `burst-window-pct` — окно бурста (3 с / 18%).

### 2.2 Атрибуты, HP, реген, уровень персонажа
- `attributes.classes.*` — базы/рост/основной атрибут класса.
- `attributes.hp.base-hp` (100) и `attributes.hp.per-str` (20): HP = base + STR×per-str.
- `attributes.hp.regen-per-str` (0.025), `regen-combat-factor` (0.35), `regen-cap-pct` (1.5).
- `attributes.level-source`: `character` (дефолт) | `class-skill` | `vanilla`.
- `character-level.top-n` (5), `fallback` (40), список `skills`;
  `attributes.level-cap` (60). **Менять cap и top-n только между сезонами:**
  от этого зависят очки талантов и сохранённые билды.

### 2.3 HUD, VFX, avoidance
- `hp-display.*`: mode/gauge/gradient/regen-spark/colors — палитра и компоновка.
- `vfx.*`: звуки/партиклы кастов и проков; опечатка = WARNING + тишина, не краш.
- `avoidance.*`: dodge-k/parry-k/dodge-mult/soft-cap/dr-factor/hard-cap/углы/стаггер.

### 2.4 Экономика талантов (1.9.0)
- `talents.enabled`, `start-level` (40), `points-per-level` (1), `max-points` (21),
  `reset-base` (500), `reset-per-point` (25).
- Инвариант сезона: **полное дерево = 21 очко = charLevel 60**. Менять
  `max-points`/`points-per-level` внутри сезона = рассинхрон купленных деревьев.

### 2.5 TTK-харнесс
- `/rc debug simulate matrix [level]` — матрица 5×5; якорь
  `balance.target-ttk-seconds` (20 с), коридор подсветки ±30%.
- Тюнинг китов: `classes.<CLASS>.abilities.<id>.base` / `.coeff` / `.cooldown`;
  талантные `kit_mult`/`kit_base` складываются поверх автоматически.
- После правок — прогон матрицы и зеркал; журнал `combat.debug-damage` для разбора.

### 2.6 Хранилища
- `storage.autosave-minutes` (5): при краше теряется ≤ N минут кулдаунов/ресурсов;
  спек-выборы и таланты не теряются (пишутся на каждом изменении).

---

## 3. ЧЕК-ЛИСТ ОПЕРАТОРА ПЕРЕД ИВЕНТОМ / ОСАДОЙ

1. `/rc selftest` → 28/28 PASS (из консоли допустимо 27/28 + SKIP чек 21/24).
2. `/rc health` → MSPT ≤ 50 (зелёная), purge ≤ 60 с.
3. Боевой чек на 3–5 игроках: `/rc debug` — атрибуты, резисты, симулятор, таланты.
4. Конфиг под тип события: PvP-ивент (`friendly-fire: true`, `disabled-worlds`
   арены, `pvp-cap` при необходимости); PvE-ивент (`max-global` выше);
   творческий (`block-casts-in-creative: false`).
5. Страховка: снапшот панели или `tar -czf backup-pre-event-*.tar.gz plugins/RaskolClasses ...`.
6. Во время ивента: `/rc health` каждые 5–10 мин; при MSPT > 50 — `/spark profiler`
   60 с; жалобы «не бьёт/не лечит» → `/rc debug` на репрезентативной цели.
7. После: вернуть повседневный конфиг, `/rc reload`, `/rc selftest`, лог инцидентов.

---

## 4. КОНТАКТЫ И ЭСКАЛАЦИЯ
- Баг в патче: GitHub Issues + `logs/latest.log` + вывод `/rc selftest` и `/rc health`.
- Критический инцидент: откат по 1.1 + сообщение в рабочий чат (версия, время).
- Конфиг-вопрос: раздел 2 этого ранбука + `/rc debug` на живой цели.

---

## 5. УРОВЕНЬ ПЕРСОНАЖА И АТРИБУТЫ (1.8.0)

- **Уровень персонажа:** floor(среднее топ-N скиллов AuraSkills), кап
  `attributes.level-cap` (60). Пример: healing 99 при остальных низких даёт
  топ-5 ≈ 47, а не 99 — одиночное дерево не раздувает статы.
- **Атрибуты:** STR/AGI/INT = base(class) + growth(class)×charLevel + модификаторы
  (спек-пассивки, таланты source `talents`, эффекты).
- **HP:** `HP = base-hp + STR × per-str` (дефолт 100 + STR×20). Воин 40 ур. ≈ 1300,
  жрец ≈ 520, маг ≈ 420 (при charLevel 40).
- **Реген HP:** STR × regen-per-str HP/с вне боя; в бою × regen-combat-factor;
  кап regen-cap-pct от maxHP/с. Идёт прямым setHealth БЕЗ события RegainHealth —
  не спамит ресурс жреца и проки лечения (осознанно).
- **Среда:** FALL/DROWNING/SUFFOCATION/STARVATION = тип TRUE + масштаб × maxHP/20 —
  падение и утопление летальны при любом пуле. Protection/Feather Falling
  применяются после масштаба.
- **HUD:** одна строка actionbar `❬ ❤ полоса числа ❭ ❬ эмблема полоса числа ❭`,
  градиенты по теме класса, искра регена; сердца = один ряд (healthScale 20).

## 6. ПРОИЗВОДНЫЕ СТАТЫ, АНТИ-ВАНШОТ, BURST-ОКНО (1.7.1/1.7.6)

- **PowerService:** WP = base-wp + STR×str-to-wp + AGI×agi-to-wp;
  SP = base-sp + INT×int-to-sp; HPow = base-hpow + INT×int-to-hpow.
  Референс 40 ур.: Воин WP 133, Охотник 104, Разбойник 104, Маг SP 120,
  Жрец SP 115 / HPow 109.
- **Базовый урон:** ванильный удар/стрела = ванильное оружие + WP × basic-coeff
  (0.35); магические причины — через SP × basic-coeff-magic.
- **Анти-ваншот:** одиночный.hit ≤ `max-single-hit-pct`% maxHP после резистов/критов;
  исключения — `cap-exempt-causes` (среда) и `allowOverCap` execute-финишеров.
- **Burst-окно:** суммарный урон по игроку за `burst-window-seconds` ≤
  `burst-window-pct`% maxHP; среда и execute не учитываются. Режет связки
  «веер+пронзающий» и бурст-открытия; sustained-ДПС не трогает.

## 7. СПЕКИ И ТАЛАНТЫ (1.7.5 / 1.9.0)

- **Спека = пассивная идентичность:** выбор с 40 уровня профильного скилла,
  резисты/проки постоянны; респец платный (base 250 + 10×уровень) с двойным
  подтверждением 30 с; модификаторы старой спеки снимаются при респеце.
- **Таланты:** дерево активной спеки в Книге (вкладка TALENTS). Очки =
  clamp(charLevel − 39, 0, 21). Покупка: клик по узлу (серверная валидация:
  спека → дерево → тир-гейт → пререквизиты → стоимость). Сброс: кристалл ×2 ПКМ.
- **Reconcile** (применение модификаторов source `talents`): join, покупка, сброс,
  смена спеки, `/rc reload`. Рассинхрон «куплено ≠ применено» невозможен дольше
  одного события.
- **Диагностика:** `/rc debug` строка «Таланты: N потрачено / M заработано»;
  `%raskolclasses_talent_points%`, `%raskolclasses_talents%` в TAB/скорборде.
- **Аварийный сброс:** раздел 1.5 (правка talents.yml + reload).

## 8. БАЛАНС-ЯКОРЯ И ДОРОЖНАЯ КАРТА (1.9.0)

- **TTK равных:** 15–25 с; зеркала 40 ур. в коридоре 14–26 (исключение —
  жрец-зеркало heal-war ≥ 30 с, осознанно).
- **Полное дерево талантов = 21 очко = charLevel 60** — прогрессия талантов
  привязана к ширине прокачки, а не к гринду одного дерева.
- **Высокие avoid/резист-числа «со шмотом»** — линия 1.10.x (кастомная броня,
  Oraxen): таланты и атрибуты дают базу, сет-слой — вершину билда.
- **Патчи линии:** 1.9.1 — таланты в TTK-харнессе (флаг «полное дерево») +
  баланс-патчи плейтеста; 1.10.x — броня/сет-слой; далее — кланы/сезоны по
  мастер-плану сервера.
