# RUNBOOK — RaskolClasses

Операторский справочник сервера «РАСКОЛ | ДВЕ КОРОНЫ».
Актуально для линии 1.9.x (релиз 1.9.3). Всё, что тюнится без пересборки, помечено `/rc reload`.

---

## 0. HP-МОДЕЛЬ 1.9.3 И ПОТОЛОК MAX_HEALTH (читать первым)

### 0.1 Формула HP
```
HP = base-hp + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus : 0)
Дефолты: base-hp 100, per-str 20, per-level 5, main-str-bonus 8.
Воин L60/STR84 = 100 + 1680 + 300 + 480 = 2560 HP.
```
Ключи `attributes.hp.per-level` и `main-str-bonus` — ЖИВЫЕ с 1.9.3 (тюнинг без пересборки).

### 0.2 Потолок ванильного max_health = 1024 (движок)
Minecraft клампует атрибут `minecraft:max_health` сверху до 1024. Формула может
дать 2560, но ванильный пул без datapack'а упрётся в 1024 (симптом: «1024/1024»
в HUD при «maxHP 2560» в `/rc debug`).

**Решение (серверное, без пересборки):** datapack `raskol_hp`:
```
world/datapacks/raskol_hp/pack.mcmeta        {"pack":{"pack_format":71,"description":"Raskol HP cap"}}
world/datapacks/raskol_hp/data/minecraft/attribute/max_health.json
                                             {"min_value":1.0,"max_value":1000000.0,"default":20.0}
```
После рестарта: `/datapack list` → `raskol_hp` enabled; `/attribute <ник> minecraft:max_health` → 2560.
Откат: удалить папку `raskol_hp` + рестарт (потолок вернётся к 1024, плагин продолжит работать).

### 0.3 HpAttributeSync (плагин, 1.9.3)
Синхронизирует ванильный MAX_HEALTH с формулой на join/respawn/reload/invalidate
+ sweep каждые 5 с. Без datapack'а sync упрётся в кламп 1024 — это нормально и
безопасно ( crash-guard в китах использует `min(формула, ванильный)` ).

### 0.4 Crash-guard китов
Все хилы/execute-пороги читают `effectiveMaxHp = min(формула, ванильный getMaxHealth)`,
поэтому `setHealth` никогда не бросает IllegalArgumentException ни с datapack'ом, ни без.

---

## 1. АВАРИЙНЫЕ ПРОЦЕДУРЫ

### 1.1 Откат jar на предыдущую версию
1. `/stop` (graceful) — плагины сохранят состояния в `onDisable`.
2. `cp plugins/RaskolClasses-1.9.3.jar{,.broken}`.
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
| Level-надбавка HP лишняя | `attributes.hp.per-level` | `0` |
| STR-main надбавка HP лишняя | `attributes.hp.main-str-bonus` | `0` |
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

### 1.6 HP «залип» на 1024 / не совпадает с `/rc debug`
1. `/datapack list` → есть ли `raskol_hp` enabled. Нет → см. 0.2 (создать/починить pack.mcmeta).
2._pack.mcmeta_ битый → в логе старта `JsonParseException ... pack metadata`:
   оставить ТОЛЬКО `pack_format` + `description` (см. 0.2), рестарт.
3. `/attribute <ник> minecraft:max_health` → base должен равняться формуле из `/rc debug`.
4. Если base меньше формулы и datapack enabled — проверить `pack_format` (71 → 61 → 48).

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
- `attributes.hp.base-hp` (100), `per-str` (20), `per-level` (5), `main-str-bonus` (8):
  HP = base + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus).
- `attributes.hp.regen-per-str` (0.025), `regen-combat-factor` (0.35), `regen-cap-pct` (1.5).
- `attributes.level-source`: `character` (дефолт) | `class-skill` | `vanilla`.
- `character-level.top-n` (5), `fallback` (40), список `skills`;
  `attributes.level-cap` (60). **Менять cap и top-n только между сезонами:**
  от этого зависят очки талантов и сохранённые билды.
- **Фолбэк 1.9.3:** при отсутствии AuraSkills уровень = `character-level.fallback`
  (40), а НЕ ванильный XP — иначе HP проваливается к «100 + STR×20».

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
- После правок HP-формулы (per-level/main-str-bonus) TTK сдвинется ВВЕРХ —
  прогони матрицу и при необходимости подними base/coeff уронных абилок.
- Журнал `combat.debug-damage` для разбора.

### 2.6 Хранилища
- `storage.autosave-minutes` (5): при краше теряется ≤ N минут кулдаунов/ресурсов;
  спек-выборы и таланты не теряются (пишутся на каждом изменении).

---

## 3. ЧЕК-ЛИСТ ОПЕРАТОРА ПЕРЕД ИВЕНТОМ / ОСАДОЙ

1. `/rc selftest` → 32/32 PASS (из консоли допустимо 31/32 + SKIP чек 21/24).
2. `/rc health` → MSPT ≤ 50 (зелёная), purge ≤ 60 с.
3. `/datapack list` → `raskol_hp` enabled; `/attribute` репрезентативного воина = формуле.
4. Боевой чек на 3–5 игроках: `/rc debug` — атрибуты, резисты, симулятор, таланты.
5. Конфиг под тип события: PvP-ивент (`friendly-fire: true`, `disabled-worlds`
   арены, `pvp-cap` при необходимости); PvE-ивент (`max-global` выше);
   творческий (`block-casts-in-creative: false`).
6. Страховка: снапшот панели или `tar -czf backup-pre-event-*.tar.gz plugins/RaskolClasses ...`.
7. Во время ивента: `/rc health` каждые 5–10 мин; при MSPT > 50 — `/spark profiler`
   60 с; жалобы «не бьёт/не лечит» → `/rc debug` на репрезентативной цели.
8. После: вернуть повседневный конфиг, `/rc reload`, `/rc selftest`, лог инцидентов.

---

## 4. КОНТАКТЫ И ЭСКАЛАЦИЯ
- Баг в патче: GitHub Issues + `logs/latest.log` + вывод `/rc selftest` и `/rc health`.
- Критический инцидент: откат по 1.1 + сообщение в рабочий чат (версия, время).
- Конфиг-вопрос: раздел 2 этого ранбука + `/rc debug` на живой цели.
- HP-вопрос: раздел 0 этого ранбука.

---

## 5. УРОВЕНЬ ПЕРСОНАЖА И АТРИБУТЫ (1.8.0 / 1.9.3)

- **Уровень персонажа:** floor(среднее топ-N скиллов AuraSkills), кап
  `attributes.level-cap` (60). Фолбэк без AuraSkills = `character-level.fallback` (40).
- **Атрибуты:** STR/AGI/INT = base(class) + growth(class)×charLevel + модификаторы
  (спек-пассивки, таланты source `talents`, эффекты).
- **HP:** формула 0.1. Воин L60 ≈ 2560, маг L60 ≈ 840 (стеклянный — идентичность).
- **Реген HP:** STR × regen-per-str HP/с вне боя; в бою × regen-combat-factor;
  кап regen-cap-pct от maxHP/с. Идёт прямым setHealth БЕЗ события RegainHealth.
- **Среда:** FALL/DROWNING/SUFFOCATION/STARVATION = тип TRUE + масштаб × maxHP/20 —
  падение и утопление летальны при любом пуле (с 1.9.3 масштаб от формулы, не от ванили).
- **HUD:** одна строка actionbar, градиенты по теме класса, искра регена;
  сердца = один ряд (healthScale 20).

## 6. ПРОИЗВОДНЫЕ СТАТЫ, АНТИ-ВАНШОТ, BURST-ОКНО (1.7.1/1.7.6)

- **PowerService:** WP = base-wp + STR×str-to-wp + AGI×agi-to-wp;
  SP = base-sp + INT×int-to-sp; HPow = base-hpow + INT×int-to-hpow.
- **Базовый урон:** ванильный удар/стрела = ванильное оружие + WP × basic-coeff (0.35).
- **Анти-ваншот:** одиночный.hit ≤ `max-single-hit-pct`% maxHP после резистов/критов;
  исключения — `cap-exempt-causes` (среда) и `allowOverCap` execute-финишеров.
- **Burst-окно:** суммарный урон за `burst-window-seconds` ≤ `burst-window-pct`% maxHP.

## 7. СПЕКИ И ТАЛАНТЫ (1.7.5 / 1.9.0)

- **Спека = пассивная идентичность:** выбор с 40 уровня профильного скилла;
  респец платный с двойным подтверждением 30 с.
- **Таланты:** дерево активной спеки в Книге (вкладка TALENTS). Очки =
  clamp(charLevel − 39, 0, 21). Покупка кликом, сброс кристаллом ×2 ПКМ.
- **Reconcile:** join, покупка, сброс, смена спеки, `/rc reload`.
- **Аварийный сброс:** раздел 1.5.

## 8. БАЛАНС-ЯКОРЯ И ДОРОЖНАЯ КАРТА

- **TTK равных:** 15–25 с; зеркала 40 ур. в коридоре 14–26.
- **Полное дерево талантов = 21 очко = charLevel 60.**
- **1.9.3:** снят потолок HP (datapack + HpAttributeSync + crash-guard).
- **1.10.x:** кастомная броня/сет-слой (Oraxen / RaskolGear) как источник высоких
  avoid/резист чисел «со шмотом».
