# RUNBOOK — RaskolClasses

Операторский справочник сервера «РАСКОЛ | ДВЕ КОРОНЫ».
Актуально для линии **1.12.1**. Всё, что тюнится без пересборки, помечено `/rc reload`.

---

## 0. МОДЕЛЬ ЗДОРОВЬЯ (план B, HpPool)

### 0.1 Формула
```
HP = base-hp + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus : 0) + gear-hp
Дефолты: base-hp 100, per-str 20, per-level 5, main-str-bonus 8. Воин L60/STR84 = 2560 HP.
```
### 0.2 Carrier / formula / scale
formula — боевой пул; carrier = min(formula, 1024) в ванильном max_health; scale = carrier/formula.
Урон/хилы/капы/реген/HUD — в formula-единицах.

### 0.3 Проверка «на живом»
`/rc debug` → maxHP formula; HUD → formula; `/attribute … base get` → carrier (1024 — норма);
selftest чеки 33–35.

### 0.4 Аварийный порядок при «странном HP»
1. `/version RaskolClasses` ≥ 1.12.1. 2. selftest 33–35. 3. WARNING ConfigValidator.
4. Sweep HpAttributeSync чинит рассинхрон ≤5 с; принудительно `/rc reload`. 5. Откат jar по I.1.

---

## I. АВАРИЙНЫЕ ПРОЦЕДУРЫ

### 1.1 Откат jar
`/stop` → `cp plugins/raskol-classes-X.Y.Z.jar{,.broken}` → предыдущий jar → старт →
`/rc debug` + `/rc selftest` → Issue с логами.

### 1.2 Восстановление хранилищ из `.bak`
`/stop`; `rm <file> <file>.tmp`; `mv <file>.bak <file>` для talents/cooldowns/spec-choices/
resources/health/**foliant-lock**; старт.

### 1.3 Гейты подсистем (`/rc reload`, рестарт не нужен)
| Проблема | Ключ | Выключатель |
|---|---|---|
| Таланты ломают бой | `talents.enabled` | `false` |
| Burst-окно режет легитим | `combat.burst-window-pct` | `0` |
| Анти-ваншот мешает ивенту | `combat.max-single-hit-pct` | `0` |
| AoE сквозь стены | `combat.aoe-los` | `false` |
| Резисты на арене | `resist.disabled-worlds` | `[мир]` |
| Откат чернокнижника лишний | `classes.WARLOCK.recoil-percent` (или `recoil.percent` в kits/warlock.yml) | `0` |
| Ад-имба чернокнижника | `nether-mult` (kits/warlock.yml) | `1.0` |
| Дроп фолианта нежелателен | `foliant.drop-enabled` | `false` |
| Релок-цикл слишком жёсткий | `foliant.relock.piglin-kills-no-chance` | уменьшить (тест: 5) |
| Анти-хил vs яблоки | `classes.WARLOCK.antiheal-strip-absorption` | `true` (жёстче) |
| HUD-полосы (вернуть ваниль) | `hp-display.mode` | `vanilla` (рестарт) |

### 1.4 Фолиант: инциденты и релок
- Случайное чтение: I.1.7 прошлого ранбука (LP-возврат класса, spec-choices/talents из .bak).
- Сброс релок-цикла вручную: `/stop` → правка `foliant-lock.yml` (locked: false) → старт.
- Просмотр состояния: `foliant-lock.yml` (out-kills/deaths/no-kills).

### 1.5 Экстренный сброс кулдаунов / талантов
Кулдауны: `rm cooldowns.yml` на стопе. Таланты: удалить блок `players.<uuid>.<specId>`
в talents.yml (или админ-кристалл в Книге ×2 ПКМ).

---

## II. ТЮНИНГ БЕЗ ПЕРЕСБОРКИ

### 2.0 Per-class конфиги kits/ (1.12.0, P5)
- `plugins/RaskolClasses/kits/<class>.yml` имеет приоритет над `classes.<CLASS>` в config.yml.
- Перечитывается на `/rc reload`; строка «Per-class конфиги: N/6» в `/rc health`.
- Сейчас поддерживаются ключи: resource.*, thresholds.*, recoil.*, nether-mult,
  lifesteal-cap, theme.*, cast.*, passives.*, abilities.* (name/description/unlock/cost/cooldown).
- base/coeff/drain/radius/self-cost/specs.* — пока только config.yml (переезд в 1.13.x).
- Дубли ключей в обоих местах → WARN при загрузке (источник правды — kits/).

### 2.1 Резисты и урон
`resist.*`, `damage-types.*`, `combat.*` (config.yml); спек-резисты `resist.specs.<id>.*`
(генерик для всех 12 спек, reconcile сам).

### 2.2 Атрибуты, HP, уровень персонажа
`attributes.*`, `character-level.*`; cap и top-n менять только между сезонами.

### 2.3 Киты и пассивки
Числа китов: `classes.<CLASS>.abilities.*` (или kits/); пассивки: `classes.<CLASS>.passives.*`
(или kits/). RUNBOOK-замки мультов заперты selftest-чеком 45 — правка числа = правка чека.

### 2.4 TTK-харнесс
`/rc debug simulate matrix [level]`; якорь `balance.target-ttk-seconds` (20 с), коридор ±30%.
**Внимание 1.12.0:** фиксы F1/F2/F4/F6 изменили DPS/выживаемость спек разбойника и воина —
ожидаются сдвиги строк ROGUE/WARRIOR; калибровка в 1.13.7.

### 2.5 Экономика талантов/респека
`talents.*`, `spec.respec-*`; формула цены заперта чеком 47.

---

## III. ЧЕК-ЛИСТ ПЕРЕД ИВЕНТОМ / ОСАДОЙ

1. `/version RaskolClasses` = 1.12.1; лог старта: баннер 5 путей, «Per-class конфиги: N/6»,
   `ConfigValidator: конфиг валиден`.
2. `/rc selftest` → **48/48 PASS** (SKIP 21/24/31/32 без онлайн-зонда допустимы).
3. `/rc health` → MSPT ≤ 50, purge ≤ 60 с.
4. Боевой чек: `/rc debug`, `/rc gear`, Книга → GEAR; чернокнижник: Скверна/SP 40 base/
   Чёрное Слово (−10% HP, +25 Скверны, КД 3 с).
5. Спеки-резисты: страж → `guardian +10 физ`; адский канал → `hell_channel +6 маг` в breakdown.
6. Конфиг под событие: PvP (`friendly-fire`, `pvp-cap`), ад-ивент (`nether-mult`),
   фолиант (`drop-chance-percent` в 0 на время ивента при желании).
7. Страховка: tar-бэкап plugins/RaskolClasses.
8. После: вернуть повседневный конфиг, `/rc reload`, selftest, лог инцидентов.

---

## IV. ОПЕРАТОРСКИЕ ЗАМЕТКИ 1.12.0 (F-фиксы)

| Спека | Было (баг) | Стало | Что увидят игроки |
|---|---|---|---|
| Ликвидатор | крит 100% | крит 15% ×1.5 | просадка DPS разбойника-ликвидатора |
| Трюкач | додж 100% | додж 15% | трюкач снова смертен |
| Ткач теней | хил мимо анти-хила | хил через HpBarService | под Расколом Души не лечится |
| Берсерк | по ярости ≥50 | по HP ≥60% | совпало с описанием |
| Аркана | ×1.15 урона | +1 мана/с | совпало с описанием |
| Стрелок | множитель по дистанции | крит стрелами 10% | совпало с описанием |
| Адский Канал | резист не применялся | +6% маг постоянно | новая выживаемость |

Жалобы «раньше было сильнее» по ликвидатору/трюкачу — это закрытие эксплойтов, не нерф:
проектовые числа всегда были 15%.

---

## V. УРОВЕНЬ ПЕРСОНАЖА, СПЕКИ, ТАЛАНТЫ (справка)

- charLevel = floor(среднее топ-5 скиллов AuraSkills из 10 деревьев), кап 60, фолбэк 40.
- Спека с 40 ур. профильного скилла, бесплатно, один раз; респец платный (250 + 10×уровень,
  дабл-арм 30 с); модификаторы старой спеки снимаются автоматически.
- Таланты: дерево активной спеки (11 деревьев), общий бюджет 21 очко к charLevel 60.
- Чернокнижник: спеки BLACK_MAGE/HELL_CHANNEL, дерево occult в Книге, прогрессия sorcery.

---

## VI. КОНТАКТЫ И ЭСКАЛАЦИЯ

Баг: GitHub Issues + `logs/latest.log` + `/rc selftest` + `/rc health`.
Крит: откат по I.1 + сообщение в чат (версия, время).
HP-вопрос: раздел 0. Фолиант-вопрос: I.4. Per-class конфиг: II.0.

---

## VII. ДОРОЖНАЯ КАРТА (после 1.12.1)

- 1.13.1–1.13.7: школы урона и DoT (School/SchoolProfile, penetration, DoT-контейнер,
  триггеры, иммунитеты, атрибуция, баланс-прогон матрицы с DoT).
- 1.14.x: BlueprintHook → рабочие рецепты чертежей; данж «Катакомбы Зари».
- Опционально: P5-доводка (переезд base/coeff/specs.* в kits/), per-class config для всех классов.
