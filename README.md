<p align="center">
  <img src="banner.svg" width="100%"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/1.13.0-8b0000?style=flat-square&label=release&labelColor=0a0a0a"/>
  <img src="https://img.shields.io/badge/1.21.4%2B-3a3a3a?style=flat-square&label=paper&labelColor=0a0a0a"/>
  <img src="https://img.shields.io/badge/21-3a3a3a?style=flat-square&label=java&labelColor=0a0a0a"/>
  <img src="https://img.shields.io/badge/90%2F90-b08d3e?style=flat-square&label=selftest&labelColor=0a0a0a"/>
  <img src="https://img.shields.io/badge/proprietary-000000?style=flat-square&label=license&labelColor=0a0a0a"/>
</p>

<p align="center">
  <sub>Боевой слой сервера «РАСКОЛ | ДВЕ КОРОНЫ»: шесть путей, две короны, одна война.</sub>
</p>

---

## I. ДВЕ КОРОНЫ

| Корона | Цвет и аура | Природа |
|---|---|---|
| **Рассвет** | золото · `END_ROD` | Свет, порядок, клятва |
| **Вальрадис** | багрянец · `SOUL_FIRE_FLAME` | Дракон, пепел, долг |

Корона даёт классу титул и партикл-ауру, видимую союзникам и врагам.

---

## II. КЛАССЫ И ТРАДИЦИИ

| Класс | Традиция | Ресурс | Атрибут | Роль |
|---|---|---|:---:|---|
| Воин | Нордика | Ярость, −5/с вне боя | STR | Передовая линия, execute-финишеры |
| Охотник | Путь ведьмака | Концентрация, +5/с вне боя | AGI | Дистанция, метки |
| Жрец | Католика | Свет, +2/с всегда | INT | Лечение, щиты, кары, очищение |
| Маг | Эллада | Мана, тиры по порогам | INT | Бурст, зоны, горение/охлаждение |
| Разбойник | Ночные гильдии | Энергия, +10/с | AGI | Скрытность, яды, кровотечение, спина |
| Чернокнижник | Фолиант Душ (скрытый) | Скверна, событийная | INT | Дрейн, диспел, анти-хил, амплификация |

Ресурс 0–100. Боевое окно 5 с разделяет режимы регена.

---

## III. ШЕСТОЙ ПУТЬ: ЧЕРНОКНИЖНИК

**Доступ:** том «Фолиант Душ. Том I» падает с шансом **0.001%** с пиглинов ада
(урон должен нанести игрок). Прочесть может Маг/Жрец 40+ без спеки и потраченных
талантов. Переход необратим: LP `class_warlock`, прогрессия — **sorcery** (грант 10).
**Анти-фарм (S2):** после выпадения тома шанс обнуляется до скрытого цикла
«≥1 моб вне ада + ≥1 смерть + ≥1000 пиглинов без шанса» (`foliant-lock.yml`).
**Soulbound (S3):** том не падает на смерть, не выбрасывается Q, наземный экземпляр
поднимает только владелец; продажа на аукционе/ChestShop НЕ блокируется.

**Скверна:** +9 урон / +5 получено / +15 убийство / +25 Чёрное Слово; вне боя −4/с до 0
(Адский Канал: −2/с). 75+ → урон ×1.2, ccResist +10%, ccPower +20%; 100 → тик 1% maxHP/с.
**Цена силы:** откат 6.66% (кап 30% maxHP, пол 1 HP); в аду кит ×6.
**Пентаграмма:** видимый круг r6 / 25 с (кольцо огней душ + звезда), визор-звук постановки,
эмбиент ада; врагам маг-урон + запрет лечения, владельцу +3 Скверны/с; в аду урон ×6.

---

## IV. ЗДОРОВЬЕ, ШКОЛЫ, DoT И КОНТРОЛЬ
HP = base-hp + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus : 0) + gear-hp

- План B: carrier ≤ 1024 (ванильный max_health), formula без потолка, scale = carrier/formula.
- HpPool (1.11.4): единые точки входа healFormula/currentFormulaHp/targetCarrier.
- Burst-окно 3 с ≤ 18%, анти-ваншот ≤ 35%, LOS для площадей, летальность среды.

### Школы урона (1.12.x)
8 школ (PHYSICAL, FIRE, FROST, NATURE, SHADOW, HOLY, ARCANE, TRUE) сворачиваются
в 3 канала защиты. Митигация: канал-резист → пробитие (flat→pct, кап 40%) →
стихийный резист (кап 60%) → кап поглощения 90%. Иммунитеты/уязвимости мобов
(`schools.entities.*`), множители школ, vanilla-school маппинг причин урона.

### DoT-ядро (1.12.4–1.12.7)
Реестр `dots.*`: burning (FIRE), poison (NATURE), bleed (PHYSICAL), chilled (FROST)
+ пассивные варианты. Стеки, атрибуция владельца, кап суммарного DoT-DPS 6% formula-maxHP.
Средовые триггеры: вода/пушистый снег гасят огонь, огонь/лава плавят лёд.
Очищение жреца снимает NATURE+SHADOW. Тик-VFX школ, DoT-строка в HUD.

### Контроль и убывающая отдача (1.13.0)
9 типов CC, 6 категорий DR: окно 15 с, множители [1.0, 0.5, 0.25, 0.0] →
4-й CC в окне = иммунитет. ccResist по классам + Скверна; ccPower чернокнижника.
Иммунитеты боссов и элиты; CHARM на игроков запрещён. Запреты действий под CC,
гейт каста, прерывание канала soul_rift. Обёртка ванильных зелий
(Slowness→SLOW, Blindness→BLIND, Weakness→SILENCE) через DR; молоко и
`/effect clear` снимают CC, но НЕ сбрасывают DR. breaksOnDamage: ROOT/FEAR
снимаются уроном ≥ 5% maxHP. HUD CC-строка, админка `/rc cc …`, секция в `/rc debug`.

| Класс | L40 | L60 |
|---|---:|---:|
| Воин | ≈ 1780 | ≈ 2560 |
| Маг / Жрец / Чернокнижник | ≈ 700 | ≈ 840 |

---

## V. МОДУЛИ И АРХИТЕКТУРА (линия 1.13.0)

| Слой | Структура |
|---|---|
| Способности | `ability/*Abilities` (по классу) + WarlockMath/WarlockFx (pure/визуал) |
| Пассивки | `passive/ClassPassive` + 5 файлов по классам; PassiveListener = диспетчер |
| Инсталляции | `install/InstallationHandler` + 6 обработчиков; InstallationService = реестр |
| Бой | CombatService-фасад + VanillaDamageListener + DamageCaps + CombatMath (pure) |
| Школы | `combat/school/*`: School, SchoolProfile, SchoolConfig, SchoolMitigation, SchoolImmunity, PenTraitsService, ElementalResistService |
| DoT | `combat/dot/*`: DotDef, DotInstance, DotMath (pure), DotService |
| Контроль | `cc/*`: CCType, DRCategory, DRState, CCInstance, CCService, CCGuard, CastGuard, CastChannels, CCFeedback, VanillaCCWrapper, CcSub, CcSanity |
| Атрибуты | AttributeService-фасад + HpPool (план B) + AttributeModifiers |
| Спеки | SpecService-фасад + SpecPassives + SpecEconomy + SpecMath (pure) |
| Книга | ClassBook-фасад + `gui/book/*Tab` (5 вкладок) |
| HUD | HudService (ресурс + DoT-строка + CC-строка, O1 dirty-rendering) |
| Команды | RaskolCommand-роутер + `command/sub/*Sub` (debug, gear, foliant, health, cc) |
| Конфиг | config.yml + per-class `kits/<class>.yml` (приоритет, фолбэк, /rc reload) |
| Тесты | `/rc selftest` — 90 headless-чеков (формулы, киты, школы, DoT, CC/DR) |

---

## VI. КОМАНДЫ

| Команда | Право | Назначение |
|---|---|---|
| `/rc` | — | Сводка игрока |
| `/rc menu` | — | Книга класса (5 вкладок) |
| `/rc gear [player]` | debug | Экипировка, статы шмота, сеты N/4 |
| `/rc debug [player]` | debug | Атрибуты, резисты, pen, стихии, CC/DR |
| `/rc debug simulate …` | debug | Дуэль и матрица TTK 6×6 |
| `/rc health` | debug | MSPT/TPS, purge, аптайм, per-class конфиги |
| `/rc selftest` | debug | 90 headless-чеков |
| `/rc cc list` | admin.cc | Таблица 9 типов CC и категорий DR |
| `/rc cc status <player>` | admin.cc | Активные CC + DR-стеки + таймер окна |
| `/rc cc clear <player>` | admin.cc | Снять все CC + сбросить DR |
| `/rc cc test <player> <type>` | admin.cc | Наложить тестовый CC |
| `/rc cc reset <player> <cat>` | admin.cc | Сбросить DR-стек категории |
| `/rc foliant give <ник>` | admin | Выдать Фолиант Душ (тест/резерв) |
| `/rc reload` | admin | Перезагрузка config.yml + kits/*.yml |

---

## VII. ПЛЕЙСХОЛДЕРЫ

%raskolclasses_class% %raskolclasses_level% %raskolclasses_hp%
%raskolclasses_hp_max% %raskolclasses_resource% %raskolclasses_phys_resist%
%raskolclasses_magic_resist% %raskolclasses_dodge% %raskolclasses_parry%
%raskolclasses_crit_melee% %raskolclasses_crit_spell% %raskolclasses_spec%
%raskolclasses_talent_points% %raskolclasses_talents% %raskolcrown_*%

---

## VIII. RASKOLGEAR

| Что | Применяет в бою | Считает и показывает |
|---|---|---|
| Урон оружия, крит, проки | RaskolGear | — |
| Резисты шмота, сет-бонусы 4/4 | RaskolGear | RaskolClasses: `/rc gear`, Книга |
| pen-трейты шмота (1.12.2) | RaskolClasses (PenTraitsService) | `/rc debug`, Книга |
| `+HP` шмота | RaskolClasses (HpPool) | HUD, `/rc debug` |
| Классовые/спек-резисты | RaskolClasses | ResistService + SpecPassives |
| Burst и single-hit cap | RaskolClasses | DamageCaps |

WARLOCK-сеты добавляются секциями `weapons.WARLOCK.*` / `armor.WARLOCK.*` в конфиг RaskolGear.

---

## IX. УСТАНОВКА

mvn -B clean package
cp target/raskol-classes-1.13.0.jar plugins/
restart
rc selftest → 90/90 PASS

<details>
<summary>Softdepend</summary>

LuckPerms, AuraSkills, PlaceholderAPI, RaskolCore, Towny, Vault, RaskolGear, AuthMe, Essentials, packetevents. Каждый опционален, деградация graceful.

</details>

<details>
<summary>Регресс-матрица selftest (90 чеков)</summary>

Атрибуты и бой 1–48, школы/DoT/pen/стихии 49–75, контроль и DR 76–90.
Полный прогон перед любым хотфиксом и релизом; чеки 82–84/90 требуют онлайн-зонда
(иначе SKIP без падения).

</details>

---

## X. ДОКУМЕНТАЦИЯ

- [`RUNBOOK.md`](RUNBOOK.md) — аварии, гейты, тюнинг без пересборки, операторские заметки
- [`docs/RUNBOOK-CC.md`](docs/RUNBOOK-CC.md) — контроль и DR: конфиг-карта, команды, troubleshooting
- [`CHANGELOG.md`](CHANGELOG.md) — история версий
- [`LICENSE`](LICENSE) — RASKOL Proprietary License v1.0

<p align="center">
  <sub>© 2026 hayferdahmer · RASKOL Proprietary License v1.0 · использование только на сервере «РАСКОЛ | ДВЕ КОРОНЫ»</sub>
</p>
