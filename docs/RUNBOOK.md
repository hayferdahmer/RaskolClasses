## 7. Конфиг-карта 1.14.10 (Sprint 7: тестируемость + декомпозиция)

### 7.1. ConfigReads — единая точка чтения (аудит 1-01)

Все классы-читатели конфига (`CombatService`, `VanillaDamageListener`, `DamageCaps`,
`AbstractKit`, `AttributeService`, `PowerService`, `AvoidanceService`, `SchoolImmunity`,
`InstallationService`, `Spec2RoleListener`) делегируют в `dev.raskol.classes.config.ConfigReads`:
- `finiteDouble(cfg, path, def)` — NaN/Infinity → def
- `finiteInt(cfg, path, def)` — отсутствует → def
- `stringOr(cfg, path, def)` — null → def
- `stringNonEmptyOr(cfg, path, def)` — null/пустая строка → def
- `boolOr(cfg, path, def)` — отсутствует → def

**Поведение:** правка логики чтения (диапазон, логирование) — одна строка в `ConfigReads`,
а не 10 правок в 10 классах.

### 7.2. Reload-поведение стоп-листа (аудит 5-05, волна 12A)

**До 1.14.12:** `spec2.disabled-nodes` читался из конфига на каждый вызов `nodeDisabled()` —
правка в файле применялась мгновенно, но с аллокацией `HashSet` на каждый вызов (hot-путь
отрисовки книги).

**С 1.14.12:** стоп-лист — иммутабельный снапшот (`Set.copyOf`), строится лениво на первом
чтении и пересобирается `refreshDisabledCache()` на старте и `/rc reload`.

**Практика:** после правки `spec2.disabled-nodes` в `config.yml` выполнить `/rc reload` —
книга сразу увидит новый стоп-лист. Без reload узел остаётся серым (ожидаемое поведение,
задокументировано в javadoc `Spec2Service.disabledSet()`).

### 7.3. Rate-limit тяжёлых команд (аудит 7-04, волна 12B)

**Команды с rate-limit 10 с per-sender:**
- `/rc reload` — перезагрузка конфига (тяжёлая операция: пересборка снапшотов, reconcile
  всех онлайн-игроков, валидация VFX)
- `/rc selftest` — запуск 122 headless-чеков (CPU-интенсивно)

**Ключ rate-limit:** UUID игрока или `console:<name>` — разные игроки не блокируют друг
друга, консоль не блокирует игроков. Первое выполнение всегда разрешено.

**Поведение при спам-клике:** повторный вызов в течение 10 с возвращает сообщение
«Команда /rc reload доступна не чаще раза в 10 секунд» (серый текст, не ошибка).

### 7.4. Security: проверка raskolclasses.menu (аудит 7-02, волна 12B)

**До 1.14.12:** ветка `/rc menu` проверяла только `instanceof Player`, нода
`raskolclasses.menu` из `plugin.yml` не проверялась — любой игрок мог открыть книгу.

**С 1.14.12:** ветка `menu` проверяет `raskolclasses.menu` (default: true для группы `citizen`).
Игрок с отозванной нодой получает «Недостаточно прав».

### 7.5. Security: isSafeName перед dispatchCommand (аудит 7-01, волна 12B)

**До 1.14.12:** `FoliantService.grantSorcerySkill()` подставлял `player.getName()` в
fallback-команду из `foliant.sorcery-grant-command` без проверки — ник с пробелами/спецсимволами
мог быть использован для инъекции в консольную команду.

**С 1.14.12:** fallback-команда проверяет `isSafeName(player.getName())` (regex `^[A-Za-z0-9_]{3,16}$`)
перед `dispatchCommand`. Небезопасный ник отклоняется с severe-логом; API-путь через
LuckPermsProvider не затрагивается (LP API сам валидирует).

## 8. Регресс-матрица 1.14.10

| Аудит | ID | Статус | Волна |
| --- | --- | --- | --- |
| Единая точка чтения конфига | 1-01 | ✔ закрыт | 7.3 |
| Декомпозиция God Object | 4-01 | ✔ закрыт | 7.2 |
| Устранение клона critFeedback | 4-03 | ✔ закрыт | 7.2 |
| Hp.max вместо deprecated getMaxHealth | 3-02 | ✔ закрыт | 12A |
| Стоп-лист как кэш-snapshot | 5-05 | ✔ закрыт | 12A |
| isSafeName перед dispatchCommand | 7-01 | ✔ закрыт | 12B |
| Проверка raskolclasses.menu | 7-02 | ✔ закрыт | 12B |
| Rate-limit reload/selftest | 7-04 | ✔ закрыт | 12B |
| Узкие catch вместо Throwable/ignored | 6-05 | ✔ закрыт | 12B/12C |
| PDC после проверки типа сущности | 5-06 | ✔ закрыт | 12C |
| TTL-кэш плейсхолдеров | 5-07 | ✔ закрыт | 12C |
| Registry.SOUNDS вместо Sound.valueOf | 3-05 | ✔ закрыт | 12C |
| logger.warning с ключом конфига | 6-06 | ✔ закрыт | 12C |

## 9. Troubleshooting 1.14.10

### 9.1. Стоп-лист не обновляется после правки config.yml

**Симптом:** узел в `spec2.disabled-nodes` остаётся серым в книге после правки файла.

**Причина:** стоп-лист — снапшот (аудит 5-05), правка применяется только после reload.

**Решение:** `/rc reload` → книга сразу видит новый стоп-лист.

### 9.2. /rc reload отвечает «доступна не чаще раза в 10 секунд»

**Симптом:** повторный вызов `/rc reload` в течение 10 с возвращает серый текст.

**Причина:** rate-limit per-sender (аудит 7-04) защищает от спам-клика на main-thread.

**Решение:** подождать 10 с или вызвать из консоли (консольный sender не блокирует
игроков и наоборот).

### 9.3. Книга класса не открывается у игрока

**Симптом:** `/rc menu` возвращает «Недостаточно прав» у гражданина.

**Причина:** нода `raskolclasses.menu` отозвана (аудит 7-02).

**Решение:** `/lp user <ник> permission set raskolclasses.menu true` или проверить
parent-группу `citizen` (default: true).

### 9.4. Фолиант Душ не выдаёт sorcery через fallback-команду

**Симптом:** миграция в Чернокнижника проходит, но `foliant.sorcery-grant-command`
не выполняется; в логе `Foliant: небезопасный ник '...' — fallback-команда отклонена`.

**Причина:** ник игрока содержит пробелы/спецсимволы (аудит 7-01, `isSafeName`).

**Решение:**
- Если ник валиден (3–16 символов `[A-Za-z0-9_]`) — проверить `foliant.sorcery-grant-command`
  в `config.yml`.
- Если ник невалиден — использовать API-путь (AuraSkills установлен, LuckPermsProvider
  работает) или сменить ник игрока.

### 9.5. Битые цвета в HUD/actionbar

**Симптом:** в логе `HpBarService: битое значение цвета в ключе '...' = '...'`.

**Причина:** некорректный HEX-код в `hp-display.colors.*` или `hp-display.gradient.*`
(аудит 6-06).

**Решение:** проверить значения в `config.yml` — должны быть валидные HEX (`#RRGGBB`).

---

## 10. Предметы 1.15.0

- `items.yml` копируется из ресурсов JAR при первом запуске. Файл использует `schema-version: 1` и содержит настройки генерации, шаблоны, ручные `items` и `sets`.
- `item.enabled` включает ядро; `item.reconcile-ticks` задаёт период сверки экипировки; `item.starter-kit.enabled` по умолчанию `false`.
- После изменений выполните `/rc reload`. Структурная ошибка оставляет прежний реестр; неверные предметы отключаются с предупреждением.
- Предметы создаются через `ItemsApi.create(itemId, itemLevel, seed)` или `createFromTemplate(...)`. RaskolMobs отвечает за мобов, уровни, зоны и дроп; обратной зависимости нет.
- Снапшоты учитывают броню, основную руку и только предметы, объявленные для `OFF_HAND`. Требования не блокируют надевание, но блокируют бонусы.
- Аффиксы зажимаются в диапазонах текущего определения. Предметы без корректного PDC v1 и неизвестные ID инертны.
- При переходе с RaskolGear удалите его с сервера. Старый PDC не читается и не мигрируется; предметы нужно перевыдать.

### 10.1. Команды для администрирования

`/rc item list [страница]`, `/rc item info <id>`, `/rc item give <игрок> <id> [количество]`, `/rc item roll <игрок> <id> [seed]`, `/rc item browse`, `/rc item stats [игрок]`; `/rc set list`, `/rc set info <id>`, `/rc set active <игрок>`.

**Конец RUNBOOK 1.15.0.** Лицензия: RASKOL Proprietary License v1.0.
