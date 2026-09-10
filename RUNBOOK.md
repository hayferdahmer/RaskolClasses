# RUNBOOK — RaskolClasses

Операторский справочник сервера «РАСКОЛ | ДВЕ КОРОНЫ».
Актуально для линии 1.7.x. Всё, что тюнится без пересборки, помечено `/rc reload`.

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
2. Удалить битый текущий и `.tmp`: `rm cooldowns.yml cooldowns.yml.tmp`.
3. `mv cooldowns.yml.bak cooldowns.yml` (то же для `spec-choices.yml`).
4. Старт: плагин сам пишет WARNING/SEVERE о фолбэке; если и `.bak` бит — старт с пустыми
   данными и SEVERE: восстанавливаем из внешнего бэкапа хостинга.

### 1.3 Отключение подсистем гейтами (всё — `/rc reload`, рестарт не нужен)
| Проблема | Ключ | Значение-выключатель |
|---|---|---|
| Уклонение/парирование ломают бой | `avoidance.enabled` | `false` |
| AoE сквозь стены | `combat.aoe-los` | `false` |
| Friendly-fire нужен на арене | `combat.friendly-fire` | `true` |
| Реген HP от СИЛЫ лишний | `attributes.hp.regen-per-str` | `0` |
| Криты/визуал крита | `attributes.crit.visuals` | `false` |
| Офенс-бонусы STR/INT | `attributes.offense.str-to-physical` / `int-to-magic` | `false` |
| Звуковой спам в замесе | `performance.sound-budget-per-tick` | `0` |
| Инсталляции на ивенте | `installations.max-global` | `0` |
| HUD-полосы | `hp-display.mode` | `vanilla` (рестарт) |
| Резисты на арене | `resist.disabled-worlds` | `[имя_мира]` |

### 1.4 Экстренный сброс кулдаунов
`/stop` → `rm plugins/RaskolClasses/cooldowns.yml` (`.bak` останется страховкой) → старт.

---

## 2. ТЮНИНГ БАЛАНСА БЕЗ ПЕРЕСБОРКИ (`/rc reload`)

### 2.1 Резисты и урон
- `resist.cap`, `resist.pvp-cap`, `resist.classes.*`, `resist.grants.*`, `resist.specs.*` —
  диапазоны 0..100, проверяет ConfigValidator (WARNING с путём ключа).
- `damage-types.vanilla-map.*` — тип ванильной причины (physical|magic|true).
- `damage-types.env-lethal-scale` / `env-lethal` — летальность среды × maxHP/20.
- `combat.true-damage-cap-per-hit` — предохранитель чистого урона.

### 2.2 Атрибуты, HP, реген (1.7.0)
- `attributes.classes.*` — базы/рост/основной атрибут класса.
- `attributes.hp.base-hp` (100) и `attributes.hp.per-str` (20): HP = base + STR×per-str.
- `attributes.hp.regen-per-str` (0.025), `regen-combat-factor` (0.35), `regen-cap-pct` (1.5).
- `attributes.level-source`: `class-skill` | `vanilla`.

### 2.3 HUD и VFX
- `hp-display.*`: mode/gauge/gradient/regen-spark/colors — палитра и компоновка полос.
- `vfx.*`: звуки/партиклы кастов и проков; опечатка = WARNING + тишина, не краш.
- `avoidance.*`: dodge-k/parry-k/soft-cap/dr-factor/hard-cap/углы/стаггер/теги/звуки.

### 2.4 Хранилища
- `storage.autosave-minutes` (5): при краше теряется ≤ N минут кулдаунов; спек-выборы не теряются.

---

## 3. ЧЕК-ЛИСТ ОПЕРАТОРА ПЕРЕД ИВЕНТОМ / ОСАДОЙ

1. `/rc selftest` → 14/14 PASS (из консоли допустимо 13/14 + SKIP первой).
2. `/rc health` → MSPT ≤ 50 (зелёная), размеры карт разумные, purge ≤ 60 с.
3. Боевой чек на 3–5 игроках: `/rc debug` — резисты, симулятор, атрибуты, максHP.
4. Конфиг под тип события: PvP-ивент (`friendly-fire: true`, `disabled-worlds` арены,
   `pvp-cap` при необходимости); PvE-ивент (`max-global` выше); творческий (`block-casts-in-creative: false`).
5. Страховка: снапшот панели или `tar -czf backup-pre-event-*.tar.gz plugins/RaskolClasses ...`.
6. Во время ивента: `/rc health` каждые 5–10 мин; при MSPT > 50 — `/spark profiler` 60 с;
   жалобы «не бьёт/не лечит» → `/rc debug` на репрезентативной цели.
7. После: вернуть повседневный конфиг, `/rc reload`, `/rc selftest`, лог инцидентов.

---

## 4. КОНТАКТЫ И ЭСКАЛАЦИЯ
- Баг в патче: GitHub Issues + `logs/latest.log` + вывод `/rc selftest` и `/rc health`.
- Критический инцидент: откат по 1.1 + сообщение в рабочий чат (версия, время).
- Конфиг-вопрос: раздел 2 этого ранбука + `/rc debug` на живой цели.

---

## 5. АТРИБУТЫ, HP, РЕГЕН (1.7.0)

- **Атрибуты:** STR/AGI/INT = base(class) + growth(class)×PlayerLevel + модификаторы
  (спек-пассивки, эффекты). PlayerLevel = уровень профильного скилла AuraSkills
  (`attributes.level-source`), фолбэк vanilla.
- **HP:** `HP = base-hp + STR × per-str` (дефолт 100 + STR×20). Воин 40 ур. ≈ 1300,
  жрец ≈ 520, маг ≈ 420. Классовый разрыв = сколько STR реально набрано.
- **Реген HP:** STR × regen-per-str HP/с вне боя; в бою × regen-combat-factor;
  кап regen-cap-pct от maxHP/с. Идёт прямым setHealth БЕЗ события RegainHealth —
  не спамит ресурс жреца и проки лечения (осознанно).
- **Среда:** FALL/DROWNING/SUFFOCATION/STARVATION = тип TRUE (игнорируют резисты и
  dodge/parry) + масштаб × maxHP/20 для игроков — падение и утопление летальны
  при любом пуле. Контрплей: Protection/Feather Falling применяются после масштаба.
- **HUD:** одна строка actionbar `❬ ❤ полоса числа ❭ ❬ эмблема полоса числа ❭`,
  градиенты по теме класса, искра регена; сердца = один ряд (healthScale 20).

## 6. УКЛОНЕНИЕ, ПАРИРОВАНИЕ, ОФЕНС, КРИТЫ (1.7.0)

- **Уклонение:** гипербола 100×AGI/(AGI+dodge-k), любой угол, только физ-атаки;
  у AGI-основных сверху + половина потерянного парирования (agi-main-dodge-refund).
- **Парирование:** гипербола 100×STR/(STR+parry-k); требует мили/щит в руке;
  полное — фронт ≤ front-angle; микро (agi-main-parry-micro) — любой угол, кроме спины;
  контратака: атакующий-игрок теряет attack-speed (стаггер), моб — Slowness + knockback.
- **DR:** сумма dodge+parry до soft-cap полная, свыше — каждый процент за dr-factor,
  жёсткий предел hard-cap; делится пропорционально.
- **Офенс:** плоские добавки STR+level → физ, INT+level → маг (гейты в `attributes.offense`);
  применяются ДО резистов цели; path B (способности) и path A (ванильные удары).
- **Криты:** мили = base + AGI×per-agi (кап melee-cap), маг = (base + INT×per-int)×spell-main-mult
  для INT-основных (кап spell-cap); множители урона melee-mult/spell-mult; визуал-теги.

## 7. БАЛАНС-ЯКОРЯ И ДОРОЖНАЯ КАРТА (решения владельца, 1.7.0)

- **TTK равных:** 15–25 с — ориентир тюнинга; проверяется симулятором в 1.7.6.
- **Анти-ваншот:** `combat.max-single-hit-pct = 35` (мягкий режим: «не жёсткий»);
  одиночный.hit ≤ 35% max HP цели; исключение — способности с тегом `execute`
  (сверх капа только по цели ниже порога). Вводится кодом в 1.7.1.
- **Темы имён способностей** (киты 1.7.2–1.7.4): Воин — нордика; Разбойник —
  средневековый реализм; Маг — греческая мифология; Жрец — католика/паладинство;
  Охотник — средневековье других вселенных. Опора на WoW, но своё и без кринжа.
- **Спеки:** активки убираем в 1.7.5 (спека = пассивная идентичность);
  дерево талантов (16 узлов, выбор 8, очки на 10/15/30/45/50/65/80/100, лево/право) — 1.8.0.
- **Лимит патчей снят:** линия 1.7.x идёт до критериев: TTK в бюджете, нет ваншотов,
  жрец жизнеспособен (хилы масштабируются), все способности масштабируются ×2.5
  между 10 и 80 ур., selftest зелёный, exploit-лист закрыт.
- **Патчи линии:** 1.7.1 производные статы (WP/SP/HPow) + базовый урон + анти-ваншот;
  1.7.2 киты Воин+Охотник; 1.7.3 киты Маг+Разбойник; 1.7.4 Жрец (масштабные хилы);
  1.7.5 спеки-пассивки + миграция; 1.7.6 баланс-харнесс TTK; 1.7.7 exploit-свип;
  1.7.8+ плейтест-правки; 1.8.0 таланты.
