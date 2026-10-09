<p align="center">
  <img src="banner.svg" alt="RASKOL CLASSES — боевой слой сервера «РАСКОЛ | ДВЕ КОРОНЫ»" width="100%"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/1.14.6-8b0000?style=flat-square&label=release&labelColor=0a0a0a" alt="release 1.14.6"/>
  <img src="https://img.shields.io/badge/1.21.4%2B-3a3a3a?style=flat-square&label=paper&labelColor=0a0a0a" alt="paper 1.21.4+"/>
  <img src="https://img.shields.io/badge/21-3a3a3a?style=flat-square&label=java&labelColor=0a0a0a" alt="java 21"/>
  <img src="https://img.shields.io/badge/111%2F111-b08d3e?style=flat-square&label=selftest&labelColor=0a0a0a" alt="selftest 111/111"/>
  <img src="https://img.shields.io/badge/proprietary-000000?style=flat-square&label=license&labelColor=0a0a0a" alt="license proprietary"/>
</p>

<p align="center">
  <i>Боевой слой сервера «РАСКОЛ | ДВЕ КОРОНЫ»: шесть путей, две короны, одна война.</i>
</p>

<p align="center">
  <a href="#две-короны">Две короны</a> ·
  <a href="#классы-и-традиции">Классы</a> ·
  <a href="#шестой-путь">Шестой путь</a> ·
  <a href="#деревья-путей">Деревья путей</a> ·
  <a href="#боевые-системы">Боевые системы</a> ·
  <a href="#архитектура">Архитектура</a> ·
  <a href="#команды">Команды</a> ·
  <a href="#установка">Установка</a>
</p>

<br/>

<table align="center">
  <tr>
    <td align="center"><b>6</b><br/><sub>классов</sub></td>
    <td align="center"><b>18</b><br/><sub>специализаций</sub></td>
    <td align="center"><b>8</b><br/><sub>школ урона</sub></td>
    <td align="center"><b>9</b><br/><sub>типов контроля</sub></td>
    <td align="center"><b>5</b><br/><sub>видов DoT</sub></td>
    <td align="center"><b>111</b><br/><sub>headless-чеков</sub></td>
  </tr>
</table>

## Две короны

| Корона | Цвет и аура | Природа |
| --- | --- | --- |
| **Рассвет** | золото · `END_ROD` | Свет, порядок, клятва |
| **Вальрадис** | багрянец · `SOUL_FIRE_FLAME` | Дракон, пепел, долг |

Корона даёт классу титул и партикл-ауру, видимую союзникам и врагам.

## Классы и традиции

| Класс | Традиция | Ресурс | Атрибут | Роль |
| --- | --- | --- | :-: | --- |
| ⚔️ **Воин** | Нордика | Ярость, −5/с вне боя | STR | Передовая линия, execute-финишеры |
| 🏹 **Охотник** | Путь ведьмака | Концентрация, +5/с вне боя | AGI | Дистанция, метки, ловушки |
| ✨ **Жрец** | Католика | Свет, +2/с всегда | INT | Лечение, щиты, кары, очищение |
| 🔮 **Маг** | Эллада | Мана, тиры по порогам | INT | Бурст, зоны, горение/охлаждение |
| 🗡️ **Разбойник** | Ночные гильдии | Энергия, +10/с | AGI | Скрытность, яды, спина |
| 📖 **Чернокнижник** | Фолиант Душ (скрытый) | Скверна, событийная | INT | Дрейн, диспел, анти-хил, DoT |

Ресурс 0–100 (Воин с `fury_rage_pool` — до 100 + бонус узла). Боевое окно 5 с разделяет режимы регена.

## Шестой путь

Чернокнижник скрыт: путь открывается только через редкий том.

```mermaid
flowchart TD
    P["Пиглины ада<br/>урон должен нанести игрок"] -->|"0.001%"| T["Фолиант Душ. Том I<br/>soulbound"]
    T --> R{"Читает Маг или Жрец 40+<br/>без основной спеки<br/>и потраченных очков"}
    R -->|"да"| W["Чернокнижник<br/>LP class_warlock<br/>прогрессия sorcery, грант 10"]
    R -->|"нет"| N["Чтение недоступно"]
```

> [!WARNING]
> Переход необратим.

**Анти-фарм (S2).** После выпадения тома шанс обнуляется до скрытого цикла «≥1 моб вне ада + ≥1 смерть + ≥1000 пиглинов без шанса» (`foliant-lock.yml`).

**Soulbound (S3).** Том не падает на смерть, не выбрасывается клавишей Q, наземный экземпляр поднимает только владелец.

### Скверна

| Событие | Скверна |
| --- | --: |
| Нанесённый урон | +9 |
| Полученный урон | +5 |
| Убийство | +15 |
| Чёрное Слово | +25 |
| Вне боя | −4/с до 0 (Демонология: −2/с) |

- **75+:** урон ×1.2, ccResist +10%, ccPower +20%.
- **100:** тик 1% maxHP/с.
- **Цена силы:** откат 6.66% (кап 30% maxHP, пол 1 HP); в аду кит ×6.
- **Пентаграмма:** круг r6 / 25 с. Врагам маг-урон и запрет лечения, владельцу +3 Скверны/с; в аду урон ×6.

## Деревья путей

С 15 уровня каждый класс открывает **3 специализации** с деревом прогрессии (Спек 2.0): 20–21 узел, 6 рядов. Бюджет очков за карьеру — **46** (15→60, +1 за уровень). Старт, бюджет и гейты читаются из `spec2.start-level`, `spec2.max-points` и `spec2.row-gates`.

Ёмкость дерева не меньше бюджета, поэтому закрыть дерево целиком нельзя: выбор ветки остаётся осознанным билдом. Нижнюю границу контролируют `spec2.tree-capacity-min` и чек 93 selftest.

**Гейты рядов** считаются по очкам, вложенным внутрь самого дерева:

```mermaid
flowchart LR
    R1["Ряд 1<br/>0"] --> R2["Ряд 2<br/>5"] --> R3["Ряд 3<br/>10"] --> R4["Ряд 4<br/>15"] --> R5["Ряд 5<br/>20"] --> R6["Ряд 6 · ульт<br/>30"]
```

| Класс | Специализации и роли |
| --- | --- |
| ⚔️ Воин | Оружие `F` · Неистовство `F` · Защита `T` |
| 🏹 Охотник | Стрельба `F` · Выживание `F` · Повелитель зверей `F` |
| ✨ Жрец | Послушание `H` · Свет `H` · Тьма `F` |
| 🔮 Маг | Тайная магия `F` · Огонь `F` · Лёд `F` |
| 🗡️ Разбойник | Ликвидация `F` · Головорез `F` · Скрытность `F` |
| 📖 Чернокнижник | Колдовство `F` · Разрушение `F` · Демонология `F` |

| Роль | Эффект |
| --- | --- |
| `F` FIGHTER | +2% урона |
| `T` TANK | +5% ccResist и +5% получаемого лечения |
| `H` HEALER | +5% исходящего лечения |

**Типы узлов:** `passive_stat` · `passive_proc` · `unlock_ability` · `enhance_ability` · `ultimate` (ряд 6, 1/1). Ранги 1–5, стоимость ранга — 1 очко в любом из 3 деревьев класса, пререквизиты ранговые. Узлы `resist` принимают таргет-школу (`nature`, `fire`, …) и дают стихийный резист.

### Управление

1. `/rc menu` → **Специализации**: выбор основной спеки (один раз, бесплатно, с 15 уровня).
2. **Деревья путей**: ЛКМ по узлу даёт +1 ранг.
3. ПКМ по купленному узлу — респец одного ранга: взвод 30 с, затем подтверждение. Цена: `spec2.node-respec-base` + `spec2.node-respec-per-rank` × ранг.
4. Кристалл — платный сброс **всего** дерева с возвратом очков в общий пул.

Вкладка листает ряды ◀ ▶ и переключает спеки класса (main + 2 secondary).

## Боевые системы

<details open>
<summary><b>Здоровье</b></summary>

<br/>

HP = `base-hp` + STR × `per-str` + level × `per-level` + (STR-main ? level × `main-str-bonus` : 0) + `gear-hp`, далее × (1 + `hp_pct`/100) из узлов.

- **План B:** carrier ≤ 1024 (ванильный `max_health`), formula без потолка, scale = carrier / formula.
- **HpPool:** единые точки входа `healFormula`, `currentFormulaHp`, `targetCarrier`.
- **Защита от бурста:** окно 3 с ≤ 18%, анти-ваншот ≤ 35%, LOS для площадей, летальность среды.
- **Восстановление на входе (`pendingRestore`):** ratio из `health.yml` применяется после загрузки класса LuckPerms, релог в первые секунды не портит сохранённое HP.

| Класс | L40 | L60 |
| --- | --: | --: |
| Воин | ≈ 1780 | ≈ 2560 |
| Маг / Жрец / Чернокнижник | ≈ 700 | ≈ 840 |

</details>

<details>
<summary><b>Школы урона</b> · 1.12.x</summary>

<br/>

8 школ (`PHYSICAL`, `FIRE`, `FROST`, `NATURE`, `SHADOW`, `HOLY`, `ARCANE`, `TRUE`) сворачиваются в 3 канала защиты.

```mermaid
flowchart LR
    A["Урон<br/>8 школ"] --> B["Канал защиты<br/>3 канала"]
    B --> C["Резист канала"]
    C --> D["Пробитие<br/>flat → pct, кап 40%"]
    D --> E["Стихийный резист<br/>кап 60%"]
    E --> F["Кап поглощения<br/>90%"]
```

Иммунитеты и уязвимости мобов, множители школ, маппинг ванильных причин урона на школы.

</details>

<details>
<summary><b>DoT-ядро</b> · 1.12.4–1.14.0</summary>

<br/>

Реестр `dots.*`:

| DoT | Школа |
| --- | --- |
| `burning` | FIRE |
| `poison` | NATURE |
| `bleed` | PHYSICAL |
| `chilled` | FROST |
| `wither` | SHADOW |

Стеки, атрибуция владельца, кап суммарного DoT-DPS 6% formula-maxHP. Средовые триггеры: вода и пушистый снег гасят огонь, огонь и лава плавят лёд. Очищение жреца снимает NATURE + SHADOW. DoT-строка в HUD.

</details>

<details>
<summary><b>Контроль и убывающая отдача</b> · 1.13.0</summary>

<br/>

9 типов CC, 6 категорий DR. Окно 15 с, множители `[1.0, 0.5, 0.25, 0.0]`: четвёртый CC в окне даёт иммунитет.

- `ccResist` / `ccPower` / `ccReduction`: класс + Скверна + узлы деревьев + роль TANK.
- Гейт каста, прерывание каналов (`CastChannels`), обёртка ванильных зелий через DR, `breaksOnDamage` для ROOT/FEAR.
- Визуал: голограмма `TextDisplay` над целью.

</details>

<details>
<summary><b>Проки</b> · 1.14.3–1.14.6</summary>

<br/>

`combat/ProcService` — единый фасад proc-узлов.

> [!IMPORTANT]
> **Контракт единиц.** Значение узла — доля шанса за ранг (0.15 = 15%), бросок выполняется при событии. Сила эффекта берётся из `spec2.procs.<id>.amp`: riposte +50%, revenge +10%, headshot ×2, second_wind 8% HP, reflect_magic 20% и т. д.

Amplifier следующего удара ставится на защищавшегося (parry, dodge, block).

</details>

<details>
<summary><b>Пет-система</b> · 1.14.6</summary>

<br/>

`pet/PetService` + `PetDef` + `PetMath`:

| Пет | Владелец | Время жизни | Масштаб |
| --- | --- | --- | --- |
| `wolf` | Охотник | постоянный | STR / WP |
| `demon` | Чернокнижник | 12 с | INT / SP |
| `shadowfiend` | Жрец | 8 с | INT / SP |

- **Масштаб:** hp = base + attr × 2, dmg = base + power × 0.3, далее × узлы `pet_hp_pct` / `pet_dmg_pct`.
- **Правила:** 1 пет на владельца, следование 12 блоков, гейты `canHit`, ретаргет на цель владельца, дезспавн на смерть и выход.
- **Баффы:** `beast_ferocity`, `bestial_wrath`; ульт-поглощение `demon_soul`.

</details>

## Архитектура

Модули линии 1.14.6:

| Слой | Структура |
| --- | --- |
| Способности | `ability/*Abilities` (6 китов) + `WarlockMath` / `WarlockFx` (pure и визуал) |
| Пассивки | `ability/passive/ClassPassive` + 6 файлов по классам; `PassiveListener` — диспетчер |
| Инсталляции | `install/InstallationHandler` + 6 обработчиков; `InstallationService` — реестр |
| Бой | `CombatService`-фасад + `VanillaDamageListener` + `DamageCaps` + `CombatMath` (pure) |
| Школы | `combat/school/*`: `School`, `SchoolProfile`, `SchoolConfig`, `SchoolMitigation`, `SchoolImmunity`, `PenTraitsService`, `ElementalResistService` |
| DoT | `combat/dot/*`: `DotDef`, `DotInstance`, `DotMath` (pure), `DotService` |
| Контроль | `cc/*`: `CCType`, `DRCategory`, `DRState`, `CCInstance`, `CCService`, `CCGuard`, `CastGuard`, `CastChannels`, `CCFeedback`, `VanillaCCWrapper`, `CcSub`, `CcSanity` |
| Проки | `combat/ProcService`: amplifier, expose, undodgeable, stealth-состояния, crit-mult, dot-extend, vendetta refresh |
| Лечение | `event/CustomHealEvent` + `Spec2RoleListener` (композиция heal_out, HEALER, heal_received, TANK) |
| Петы | `pet/*`: `PetDef`, `PetMath` (pure), `PetService` (жизненный цикл, баффы, гейты) |
| **Спек 2.0** | `spec/*`: `Spec` (18), `SpecRole`, `SpecRoles`; `spec/model/*`; `spec/registry/trees/*Trees` (18 деревьев); `spec/storage/Spec2Storage` (`spec2-storage.yml`, миграция из `spec2.yml`); `spec/service/Spec2Service` + `Spec2EffectsApplier`; `spec/listen/Spec2RoleListener` |
| Атрибуты | `AttributeService`-фасад + `HpPool` (план B) + `AttributeModifiers` |
| Книга | `ClassBook`-фасад + `gui/book/*Tab` (5 вкладок: пагинация рядов, переключатель спек, ПКМ-респец узла) |
| HUD | `HudService` (ресурс + DoT-строка, dirty-rendering) |
| Команды | `RaskolCommand`-роутер + `command/sub/*Sub` (debug, gear, foliant, health, cc) |
| Конфиг | `config.yml` (avoidance и hp-display в корне с 1.14.6-fix) + per-class `kits/<class>.yml` |
| Тесты | `/rc selftest` — **111 headless-чеков** |

## Команды

| Команда | Права | Описание |
| --- | --- | --- |
| `/rc` | — | Сводка игрока |
| `/rc menu` | `raskolclasses.menu` | Книга класса (5 вкладок) |
| `/rc gear [player]` | `raskolclasses.debug` | Экипировка, статы шмота, сеты N/4 |
| `/rc debug [player]` | `raskolclasses.debug` | Атрибуты, резисты, pen, стихии, CC/DR, очки путей |
| `/rc debug simulate …` | `raskolclasses.debug` | Дуэль и матрица TTK 6×6 |
| `/rc health` | `raskolclasses.debug` | MSPT/TPS, purge, аптайм, per-class конфиги |
| `/rc selftest` | `raskolclasses.debug` | 111 headless-чеков |
| `/rc cc list\|status\|clear\|test\|reset` | `raskolclasses.admin.cc` | Админка контроля с DR |
| `/rc foliant give <ник>` | `raskolclasses.admin` | Выдать Фолиант Душ |
| `/rc reload` | `raskolclasses.admin` | Перезагрузка `config.yml` + `kits/*.yml` |

## Плейсхолдеры

```text
%raskolclasses_class%        %raskolclasses_level%         %raskolclasses_hp%
%raskolclasses_hp_max%       %raskolclasses_resource%      %raskolclasses_phys_resist%
%raskolclasses_magic_resist% %raskolclasses_dodge%         %raskolclasses_parry%
%raskolclasses_crit_melee%   %raskolclasses_crit_spell%    %raskolclasses_spec%
%raskolclasses_talent_points% %raskolclasses_talents%      %raskolcrown_*%
```

`spec`, `talent_points` и `talents` читают spec2-слой: основная спека, доступные очки, потрачено и заработано.

## RaskolGear

| Что | Применяет в бою | Считает и показывает |
| --- | --- | --- |
| Урон оружия, крит, проки | RaskolGear | — |
| Резисты шмота, сет-бонусы 4/4 | RaskolGear | RaskolClasses: `/rc gear`, Книга |
| pen-трейты шмота (1.12.2) | RaskolClasses (`PenTraitsService`) | `/rc debug`, Книга |
| `+HP` шмота | RaskolClasses (`HpPool`) | HUD, `/rc debug` |
| Классовые и спек-резисты | RaskolClasses | `ResistService` + spec2-агрегат |
| Burst и single-hit cap | RaskolClasses | `DamageCaps` |

WARLOCK-сеты добавляются секциями `weapons.WARLOCK.*` и `armor.WARLOCK.*` в конфиг RaskolGear.

## Установка

```bash
mvn -B clean package
cp target/raskol-classes-1.14.6.jar plugins/
# перезапуск сервера
/rc selftest   # ожидается 111/111 PASS
```

> [!NOTE]
> При первом старте 1.14.4+ выполняется миграция хранилища: `spec2.yml` → `spec2-storage.yml` (rename, содержимое не меняется). В логе появится строка `spec2: migrated …`.

**Softdepend:** `LuckPerms` · `AuraSkills` · `PlaceholderAPI` · `RaskolCore` · `Towny` · `Vault` · `RaskolGear` · `AuthMe` · `Essentials` · `packetevents`. Каждый опционален, деградация graceful.

<details>
<summary><b>Регресс-матрица selftest</b> · 111 чеков</summary>

<br/>

| Область | Чеки |
| --- | :-: |
| Атрибуты и бой | 1–21 |
| spec2-экономика | 22–24 |
| Ресурсы, план B | 29–36 |
| Чернокнижник, `WarlockMath`, sanity | 37–48 |
| Школы | 49–65 |
| DoT, баланс | 66–75 |
| CC / DR | 76–90 |
| spec2-модель | 91–94 |
| Переносимые slots 4–5 | 95–98 |
| unlock-покрытие | 99 |
| heal, паверы, ресурс, проки, боевые pct | 100–104 |
| resist-школа, `resetNode`, конфиг-точки | 105–107 |
| Пет-ядро | 108–111 |

Полный прогон — перед любым хотфиксом и релизом. Skip-чеки считаются отдельно и не входят в PASS-счётчик (с sprint 1).

</details>

## Документация

- [`RUNBOOK.md`](RUNBOOK.md) — аварии, гейты, тюнинг без пересборки, операторские заметки
- [`docs/RUNBOOK.md`](docs/RUNBOOK.md) — контроль и DR, проки, респецы, миграции: конфиг-карта, команды, troubleshooting
- [`CHANGELOG.md`](CHANGELOG.md) — история версий (не переписывается задним числом)
- [`LICENSE`](LICENSE) — RASKOL Proprietary License v1.0

<br/>

<p align="center">
  <sub>© 2026 hayferdahmer · RASKOL Proprietary License v1.0<br/>использование только на сервере «РАСКОЛ | ДВЕ КОРОНЫ»</sub>
</p>
