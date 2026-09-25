<div align="center">

<img src="https://capsule-render.vercel.app/api?type=slice&color=0:050505,45:140000,80:2e0000,100:520000&height=170&section=header&animation=fadeIn"/>

# 𝕽𝕬𝕾𝕶𝕺𝕷 𝕮𝕷𝕬𝕾𝕾𝕰𝕾

† Пять путей · Одна война · Без пощады †

<p>
  <img src="https://img.shields.io/badge/RELEASE-1.9.3.2-520000?style=for-the-badge&labelColor=0a0a0a"/>
  <img src="https://img.shields.io/badge/PAPER-1.21.4%2B-1c1c1c?style=for-the-badge&labelColor=0a0a0a&color=3d3d3d"/>
  <img src="https://img.shields.io/badge/JAVA-21-1c1c1c?style=for-the-badge&labelColor=0a0a0a&color=3d3d3d"/>
  <img src="https://img.shields.io/badge/SELFTEST-36%2F36-3d0000?style=for-the-badge&labelColor=0a0a0a&color=520000"/>
  <img src="https://img.shields.io/badge/LICENSE-PROPRIETARY-000000?style=for-the-badge&labelColor=0a0a0a&color=2a2a2a"/>
</p>

Боевой слой сервера **«РАСКОЛ | ДВЕ КОРОНЫ»**<br/>
<sub>Классы · Специализации · Таланты · Инсталляции · Боевая математика · TTK-харнесс</sub>

</div>

---

## ⚔ Пять путей

| Сигил | Класс | Ресурс | Главный атрибут | Роль в бою |
|:---:|---|---|:---:|---|
| ⚔ | **Воин** | Ярость *(−5/с вне боя)* | `STR` | Передовая линия, execute-финишеры |
| ➳ | **Охотник** | Концентрация *(+5/с вне боя)* | `AGI` | Дистанционное давление, метки |
| ✚ | **Жрец** | Свет *(+2/с всегда)* | `INT` | Лечение, щиты, кары |
| ✦ | **Маг** | Мана *(тиры по порогам 25/50/75)* | `INT` | Бурст, контроль, зоны |
| ☠ | **Разбойник** | Энергия *(+10/с)* | `AGI` | Скрытность, яды, удар в спину |

Ресурс — **0…100**. Боевое окно 5 с разделяет режимы «в бою» / «вне боя»;
правила регена индивидуальны для каждого класса.

---

## † Модель здоровья (1.9.3)

```
HP = base-hp + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus : 0) + gear-hp
```

- **Виртуальный пул:** ванильный `max_health` — носитель-пропорция (≤ 1024, предел
  движка). Реальный пул = формула; урон, хилы, капы и HUD работают в формульных
  единицах через единый `scale = carrier / formula`. Датапак не нужен.
- Воин L20 · STR 36 → **1080 HP**; воин L60 · STR 84 → **2560 HP** (проверено `/rc debug`).
- `+HP` от брони RaskolGear входит в формулу автоматически.

---

## ✠ Модули

| Модуль | Назначение |
|---|---|
| Способности (слоты 1–5) | Свитки в хотбар, КД-полоса прочностью, `base + Power×coeff` (WP/SP/HPow) |
| Специализации | Пассивная идентичность с 40 ур.; резисты и проки постоянны; платный респец с двойным подтверждением 30 с |
| Дерево талантов | 10 деревьев × 9 узлов (ветви A/B, 4 тира, капстоун, ульт); управление только в Книге класса |
| Уровень персонажа | floor(среднее топ-5 скиллов AuraSkills), кап 60; прогрессия от ширины прокачки |
| Инсталляции | Мины, варды, руна-зона: TTL, лимиты 2/200, килл-кредит владельцу, гейты зон |
| Короны | Рассвет / Вальрадис: титулы классов и партикл-ауры `%raskolcrown_*%` |
| Бой | 4 типа урона, резисты, модификаторы, burst-окно (3 с ≤ 18%), анти-ваншот (≤ 35%), LOS для AoE, летальность среды |
| TTK-харнесс | `/rc debug simulate`, матрица 5×5, якорь `balance.target-ttk-seconds` 20 с ±30% |
| RaskolGear-хук | Статы шмота из PDC: резисты, HP, сеты 4/4, шипы — без правок чужого плагина |
| Надёжность | Атомарные сейвы с `.bak` и автофолбэком, автосейв 5 мин, ConfigValidator, NaN-гарды, 36 headless-чеков |

---

## ☠ Команды

| Команда | Право | Назначение |
|---|---|---|
| `/rc` | — | Сводка: класс, уровень персонажа, ресурс, резисты, корона, спека |
| `/rc menu` | — | Книга класса: способности · спеки · класс · таланты · шмот |
| `/rc gear [player]` | `debug` | Экипировка, статы шмота, активные сеты N/4 |
| `/rc debug [player]` | `debug` | Атрибуты, резисты, симулятор урона, таланты, gear-строка |
| `/rc debug simulate [A] [B] [lvl]` | `debug` | Headless-дуэль на боевых формулах |
| `/rc debug simulate matrix [lvl]` | `debug` | Матрица 5×5 TTK |
| `/rc health` | `debug` | MSPT/TPS, purge, аптайм, Fx-счётчики |
| `/rc selftest` | `debug` | 36 headless-чеков формул |
| `/rc reload` | `admin` | Перезагрузка конфига + reconcile талантов и шмота |

Способности и инсталляции применяются **только свитками в хотбаре** —
цифровых подкоманд нет (решение владельца).

---

## ⸸ Плейсхолдеры (PlaceholderAPI)

```
%raskolclasses_class%        %raskolclasses_class_id%      %raskolclasses_level%
%raskolclasses_char_level%   %raskolclasses_skill_level%   %raskolclasses_resource%
%raskolclasses_resource_max% %raskolclasses_hp%            %raskolclasses_hp_max%
%raskolclasses_phys_resist%  %raskolclasses_magic_resist%  %raskolclasses_dodge%
%raskolclasses_parry%        %raskolclasses_crit_melee%    %raskolclasses_crit_spell%
%raskolclasses_spec%         %raskolclasses_spec_id%       %raskolclasses_talent_points%
%raskolclasses_talents%      %raskolcrown_*%
```

---

## ✦ RaskolGear: разделение ответственности

Хук читает статы шмота из PDC (`raskolgear:*`) и не дублирует чужую логику:

| Что | Кто применяет в бою | Кто считает и показывает |
|---|---|---|
| Урон оружия (`base + Power×coeff`, крит, проки) | RaskolGear | — |
| Резисты шмота и сет-бонусы 4/4 | RaskolGear | RaskolClasses: `/rc gear`, вкладка GEAR, debug |
| `+HP` шмота | RaskolClasses (входит в формулу `maxHp`) | HUD, `/rc debug` |
| Классовые резисты (воин 18/12 и т.д.) | RaskolClasses | ResistService, breakdown в Книге |
| Burst / single-hit cap | RaskolClasses | CombatService |
| Исходящий офенс WP/SP | RaskolClasses — **пропускается**, если в руке оружие RaskolGear | — |

Циклический softdepend разруливается через `PluginEnableEvent`: хук активируется,
когда RaskolGear включился позже нас.

---

## ⚙ Установка

1. `mvn -B clean package` → `target/raskol-classes-1.9.3.2.jar`
2. jar в `plugins/`, рестарт сервера
3. `config.yml` создаётся автоматически; правки применяются через `/rc reload`
   (кроме `hp-display.mode`)
4. Хранилища: `cooldowns.yml`, `spec-choices.yml`, `talents.yml`, `resources.yml`,
   `health.yml` — атомарная запись, `.bak`-копии, автосейв каждые 5 мин
5. `/rc selftest` → **36/36 PASS**

<details>
<summary><b>Softdepend — каждый опционален, деградация graceful</b></summary>

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
<summary><b>Регресс-матрица: 36 чеков selftest + живой прогон</b></summary>

Порядок прогона перед любым хотфиксом и релизом.

- 1–14: формулы dodge / parry / DR / split / углы
- 15–16: WP / SP / HPow и анти-ваншот
- 17–18: TTK-санити и матрица
- 19–20: сводный уровень top-N
- 21: фракционный гейт canHit
- 22–24: экономика талантов и reconcile-цикл
- 29–30: боевое окно и семантика consume
- 31–32: глобальный бюджет очков и прунинг битых узлов
- 33–35: план B — scale / healFormula / targetCarrier
- 36: декэй ярости воина вне боя (tickDelta)

</details>

---

## ❦ Документация

- [`RUNBOOK.md`](RUNBOOK.md) — аварии, гейты, тюнинг без пересборки, чек-лист оператора
- [`CHANGELOG.md`](CHANGELOG.md) — история версий (Added / Changed / Fixed / Removed / Validate)
- [`LICENSE`](LICENSE) — RASKOL Proprietary License v1.0

---

## ⸸ Лицензия

**RASKOL Proprietary License v1.0** © 2026 hayferdahmer.
Использование разрешено только на сервере «РАСКОЛ | ДВЕ КОРОНЫ».
Копирование, редистрибуция и продажа — только с письменного разрешения владельца.

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=slice&color=0:520000,45:2e0000,80:140000,100:050505&height=120&section=footer&flip=true&animation=fadeIn"/>

<sub>© 2026 hayferdahmer · RASKOL · ДВЕ КОРОНЫ</sub>

</div>
