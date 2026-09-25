<div align="center">

<img src="https://capsule-render.vercel.app/api?type=venom&color=0:050505,30:140000,65:2e0000,100:520000&height=240&section=header&text=RASKOL%20CLASSES&fontSize=56&fontColor=c9c9c9&fontAlignY=36&desc=%E2%80%A0%20%D0%9F%D1%8F%D1%82%D1%8C%20%D0%BF%D1%83%D1%82%D0%B5%D0%B9%20%C2%B7%20%D0%9E%D0%B4%D0%BD%D0%B0%20%D0%B2%D0%BE%D0%B9%D0%BD%D0%B0%20%C2%B7%20%D0%91%D0%B5%D0%B7%20%D0%BF%D0%BE%D1%88%D0%B0%D0%B4%D1%8B%20%E2%80%A0&descAlignY=62&descFontColor=7a7a7a&animation=fadeIn"/>

<p>
  <img src="https://img.shields.io/badge/RELEASE-1.9.3.2-520000?style=for-the-badge&labelColor=0a0a0a"/>
  <img src="https://img.shields.io/badge/PAPER-1.21.4%2B-1c1c1c?style=for-the-badge&labelColor=0a0a0a&color=3d3d3d"/>
  <img src="https://img.shields.io/badge/JAVA-21-1c1c1c?style=for-the-badge&labelColor=0a0a0a&color=3d3d3d"/>
  <img src="https://img.shields.io/badge/SELFTEST-36%2F36-3d0000?style=for-the-badge&labelColor=0a0a0a&color=520000"/>
  <img src="https://img.shields.io/badge/LICENSE-PROPRIETARY-000000?style=for-the-badge&labelColor=0a0a0a&color=2a2a2a"/>
</p>

<p>
  <code>⸸</code>&nbsp; Боевой слой сервера **«РАСКОЛ | ДВЕ КОРОНЫ»** &nbsp;<code>⸸</code><br/>
  <sub>Классы · Специализации · Таланты · Инсталляции · Боевая математика · TTK-харнесс</sub>
</p>

<p><sub>✠──────────⚔──────────✠</sub></p>

> ☠ *«Героев здесь нет. Есть выжившие — и есть уже забытые.»*

</div>

---

## ⚔ ПЯТЬ ПУТЕЙ

| Сигил | Класс | Ресурс | Главный атрибут | Роль в бою |
|:---:|---|---|:---:|---|
| ⚔ | **Воин** | Ярость *(−5/с вне боя)* | `STR` | Передовая линия, execute-финишеры |
| ➳ | **Охотник** | Концентрация *(+5/с вне боя)* | `AGI` | Дистанционное давление, метки |
| ✚ | **Жрец** | Свет *(+2/с всегда)* | `INT` | Лечение, щиты, кары |
| ✦ | **Маг** | Мана *(тиры по порогам)* | `INT` | Бурст, контроль, зоны |
| ☠ | **Разбойник** | Энергия *(+10/с)* | `AGI` | Скрытность, яды, спина |

Ресурс — **0…100**, правила регена индивидуальны; боевое окно 5 с разделяет
«в бою» и «вне боя» для каждого класса по-своему.

<p align="center"><sub>─────────⸸─────────</sub></p>

## † МОДЕЛЬ ЗДОРОВЬЯ (1.9.3)

```
HP = base-hp + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus : 0) + gear-hp
```

- **Виртуальный пул:** ванильный `max_health` — лишь носитель-пропорция (≤ 1024,
  предел движка). Реальный пул = формула; урон, хилы, капы и HUD живут в
  формульных единицах через единый `scale = carrier / formula`.
- **Потолок 1024 снят без датапаков:** воин L60·STR84 держит **2560 HP**,
  HUD честно показывает `…/2560`.
- Воин L20·STR36 = 1080 HP; +35 HP от нагрудника RaskolGear входят в формулу.

| Класс | Референс L40 | Референс L60 |
|---|---:|---:|
| Воин (STR-main) | ≈ 1780 | ≈ 2560 |
| Жрец / Маг | ≈ 700 | ≈ 840 |

<p align="center"><sub>─────────⸸─────────</sub></p>

## ✠ МОДУЛИ

| Модуль | Назначение |
|---|---|
| **Способности (слоты 1–5)** | Свитки в хотбар, КД-полоса прочностью, `base + Power×coeff` (WP/SP/HPow) |
| **Специализации** | Пассивная идентичность с 40 ур.; резисты/проки постоянны; платный респец с двойным подтверждением |
| **Дерево талантов** | 10 деревьев × 9 узлов (ветви A/B, 4 тира, капстоун, ульт); только в Книге класса |
| **Уровень персонажа** | floor(среднее топ-5 скиллов AuraSkills), кап 60; ширина прокачки, а не гринд одного дерева |
| **Инсталляции** | Мины/варды/руна-зона: TTL, лимиты 2/200, килл-кредит, гейты зон |
| **Короны** | Рассвет / Вальрадис: титулы классов и партикл-ауры `%raskolcrown_*%` |
| **Бой** | 4 типа урона, резисты, модификаторы, burst-окно (3 с ≤ 18%), анти-ваншот (≤ 35%), LOS для AoE, летальность среды |
| **TTK-харнесс** | `/rc debug simulate`, матрица 5×5, якорь 20 с ±30% |
| **RaskolGear-хук** | Статы шмота из PDC: резисты/HP/сеты/шипы — без правок чужого плагина |
| **Надёжность** | Атомарные сейвы с `.bak`, автосейв, ConfigValidator, NaN-гарды, 36 headless-чеков |

<p align="center"><sub>─────────⸸─────────</sub></p>

## ☠ КОМАНДЫ

| Команда | Право | Назначение |
|---|---|---|
| `/rc` | — | Сводка: класс, уровень, ресурс, резисты, корона, спека |
| `/rc menu` | — | Книга класса: способности · спеки · класс · таланты · **шмот** |
| `/rc gear [player]` | `debug` | Экипировка, статы шмота, активные сеты N/4 |
| `/rc debug [player]` | `debug` | Атрибуты, резисты, симулятор урона, таланты, gear-строка |
| `/rc debug simulate [A] [B] [lvl]` | `debug` | Headless-дуэль на боевых формулах |
| `/rc debug simulate matrix [lvl]` | `debug` | Матрица 5×5 TTK |
| `/rc health` | `debug` | MSPT/TPS, purge, аптайм, Fx-счётчики |
| `/rc selftest` | `debug` | 36 headless-чеков формул |
| `/rc reload` | `admin` | Перезагрузка конфига + reconcile талантов и шмота |

Способности и инсталляции применяются **только свитками в хотбаре**: цифровых
подкоманд нет — осознанное решение владельца.

<p align="center"><sub>─────────⸸─────────</sub></p>

## ⸸ ПЛЕЙСХОЛДЕРЫ (PlaceholderAPI)

```
%raskolclasses_class%        %raskolclasses_class_id%      %raskolclasses_level%
%raskolclasses_char_level%   %raskolclasses_skill_level%   %raskolclasses_resource%
%raskolclasses_resource_max% %raskolclasses_hp%            %raskolclasses_hp_max%
%raskolclasses_phys_resist%  %raskolclasses_magic_resist%  %raskolclasses_dodge%
%raskolclasses_parry%        %raskolclasses_crit_melee%    %raskolclasses_crit_spell%
%raskolclasses_spec%         %raskolclasses_spec_id%       %raskolclasses_talent_points%
%raskolclasses_talents%      %raskolcrown_*%
```

<p align="center"><sub>─────────⸸─────────</sub></p>

## ✦ RASKOLGEAR: РАЗДЕЛЕНИЕ ОТВЕТСТВЕННОСТИ

Хук читает статы шмота из PDC (`raskolgear:*`) и **не дублирует** чужую логику:

| Что | Кто применяет в бою | Кто считает/показывает |
|---|---|---|
| Урон оружия (`base + Power×coeff`, крит, проки) | RaskolGear | — |
| Резисты шмота и сет-бонусы 4/4 | RaskolGear | RaskolClasses (дисплей, `/rc gear`, Книга) |
| `+HP` шмота | RaskolClasses (входит в формулу `maxHp`) | HUD, `/rc debug` |
| Классовые резисты (18/12…) | RaskolClasses | ResistService, breakdown в Книге |
| Burst / single-hit cap | RaskolClasses | CombatService |
| Исходящий офенс WP/SP | RaskolClasses — **пропускается**, если в руке оружие RaskolGear | — |

Циклический softdepend разруливается событием `PluginEnableEvent`: хук
активируется, когда RaskolGear включился позже нас.

<p align="center"><sub>─────────⸸─────────</sub></p>

## ⚙ УСТАНОВКА

1. `mvn -B clean package` → `target/raskol-classes-1.9.3.2.jar`
2. jar в `plugins/`, **рестарт** сервера
3. `config.yml` создаётся автоматически; правки — через `/rc reload`
   (кроме `hp-display.mode`)
4. Хранилища: `cooldowns.yml`, `spec-choices.yml`, `talents.yml`,
   `resources.yml`, `health.yml` — атомарная запись, `.bak`, автосейв 5 мин
5. `/rc selftest` → **36/36 PASS**

<details>
<summary><b>☩ Softdepend (каждый опционален, деградация graceful)</b></summary>

| Плагин | Зачем |
|---|---|
| LuckPerms | определение классов |
| AuraSkills | уровни скиллов и сводный уровень персонажа |
| PlaceholderAPI | плейсхолдеры |
| RaskolCore | паспорта классов, мгновенный reconcile |
| Towny | клеймы инсталляций, гейт блинка |
| AuthMe | гейт до логина |
| Vault / Economy | плата респеца и сброса талантов |
| RaskolGear | статы шмота, сеты, шипы |
| RaskolEnchant | чертежи и крафт (каркас BlueprintHook) |

</details>

<details>
<summary><b>☩ Регресс-матрица (36 чеков selftest + живой прогон)</b></summary>

Порядок прогона перед любым хотфиксом и релизом: диагностики (1–5),
бой (6–16), контент (17–30), план B и ресурсы (31–36).

- Формулы dodge/parry/DR/split/углы — чеки 1–14
- WP/SP/HPow и анти-ваншот — чеки 15–16
- TTK-санити и матрица — чеки 17–18
- Сводный уровень top-N — чеки 19–20
- Фракционный гейт canHit — чек 21
- Экономика талантов и reconcile — чеки 22–24
- Боевое окно и семантика consume — чеки 29–30
- Глобальный бюджет очков и прунинг — чеки 31–32
- План B: scale / healFormula / targetCarrier — чеки 33–35
- Декэй ярости воина (tickDelta) — чек 36

</details>

<p align="center"><sub>─────────⸸─────────</sub></p>

## ❦ ДОКУМЕНТАЦИЯ

- [`RUNBOOK.md`](RUNBOOK.md) — аварии, гейты, тюнинг без пересборки, чек-лист оператора
- [`CHANGELOG.md`](CHANGELOG.md) — история версий (Added/Changed/Fixed/Removed/Validate)
- [`LICENSE`](LICENSE) — RASKOL Proprietary License v1.0

<p align="center"><sub>─────────⸸─────────</sub></p>

## ⸸ ЛИЦЕНЗИЯ

**RASKOL Proprietary License v1.0** © 2026 hayferdahmer.
Использование разрешено только на сервере «РАСКОЛ | ДВЕ КОРОНЫ».
Копирование, редистрибуция и продажа — только с письменного разрешения владельца.

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=venom&color=0:520000,35:2e0000,70:140000,100:050505&height=150&section=footer&text=%C2%A9%202026%20hayferdahmer%20%C2%B7%20RASKOL&fontSize=22&fontColor=6f6f6f&fontAlignY=72&animation=fadeIn"/>

<sub>☠ &nbsp;Пять путей. Одна война. Без пощады.&nbsp; ☠</sub>

</div>
