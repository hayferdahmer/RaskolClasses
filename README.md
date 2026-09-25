<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=venom&height=320&section=header&animation=twinkling&color=0:%23050505,20:%238B0000,40:%231B4D1B,60:%23101A6E,80:%232A0A3A,100:%23050505"/>
</p>

<p align="center">
  <img src="https://readme-typing-svg.demolab.com/?font=Cormorant+Gothic&size=56&color=%23C9C9C9&background=00000000&center=true&vCenter=true&width=1000&height=140&lines=RASKOL+CLASSES&repeat=true&pause=2500&duration=900"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/1.9.3.2-520000?style=flat-square&label=release&labelColor=0a0a0a"/>
  <img src="https://img.shields.io/badge/1.21.4%2B-3d3d3d?style=flat-square&label=paper&labelColor=0a0a0a"/>
  <img src="https://img.shields.io/badge/21-3d3d3d?style=flat-square&label=java&labelColor=0a0a0a"/>
  <img src="https://img.shields.io/badge/36%2F36-520000?style=flat-square&label=selftest&labelColor=0a0a0a"/>
</p>

<p align="center">
  <sub>Боевой слой сервера «РАСКОЛ | ДВЕ КОРОНЫ»: классы, специализации, таланты, инсталляции, боевая математика, TTK-харнесс.</sub>
</p>

---

## Классы

| Класс | Ресурс | Атрибут | Роль |
|---|---|:---:|---|
| Воин | Ярость, −5/с вне боя | STR | Передовая линия, execute-финишеры |
| Охотник | Концентрация, +5/с вне боя | AGI | Дистанционное давление, метки |
| Жрец | Свет, +2/с всегда | INT | Лечение, щиты, кары |
| Маг | Мана, тиры по порогам | INT | Бурст, контроль, зоны |
| Разбойник | Энергия, +10/с | AGI | Скрытность, яды, атака со спины |

Ресурс 0–100. Боевое окно 5 с разделяет режимы регена по классам.

---

## Здоровье

```
HP = base-hp + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus : 0) + gear-hp
```

- Ванильный `max_health` — носитель-пропорция (предел движка 1024).
- Боевой пул, урон, хилы, капы и HUD работают в формульных единицах через `scale = carrier / formula`.
- Потолок 1024 снят без датапаков: воин L60·STR84 = 2560 HP.

| Класс | L40 | L60 |
|---|---:|---:|
| Воин | ≈ 1780 | ≈ 2560 |
| Маг / Жрец | ≈ 700 | ≈ 840 |

---

## Модули

| Модуль | Назначение |
|---|---|
| Способности 1–5 | Свитки в хотбар, КД-полоса прочностью, `base + Power×coeff` |
| Специализации | Пассивная идентичность с 40 ур., платный респец с двойным подтверждением |
| Таланты | 10 деревьев × 9 узлов, только в Книге класса |
| Уровень персонажа | Среднее топ-5 скиллов AuraSkills, кап 60 |
| Инсталляции | Мины, варды, руна-зона; TTL, лимиты 2/200, килл-кредит |
| Короны | Рассвет / Вальрадис: титулы и партикл-ауры |
| Бой | 4 типа урона, резисты, burst-окно, анти-ваншот, LOS, летальность среды |
| TTK-харнесс | `/rc debug simulate`, матрица 5×5, якорь 20 с |
| RaskolGear-хук | Статы шмота из PDC без правок чужого плагина |
| Надёжность | Атомарные сейвы с `.bak`, автосейв, ConfigValidator, NaN-гарды |

---

## Команды

| Команда | Право | Назначение |
|---|---|---|
| `/rc` | — | Сводка игрока |
| `/rc menu` | — | Книга класса: способности, спеки, класс, таланты, шмот |
| `/rc gear [player]` | debug | Экипировка, статы шмота, сеты N/4 |
| `/rc debug [player]` | debug | Атрибуты, резисты, симулятор, таланты |
| `/rc debug simulate …` | debug | Дуэль и матрица TTK |
| `/rc health` | debug | MSPT/TPS, purge, аптайм |
| `/rc selftest` | debug | 36 headless-чеков формул |
| `/rc reload` | admin | Перезагрузка конфига, reconcile |

Способности и инсталляции применяются только свитками в хотбаре.

---

## Плейсхолдеры

```
%raskolclasses_class%        %raskolclasses_level%        %raskolclasses_hp%
%raskolclasses_hp_max%       %raskolclasses_resource%     %raskolclasses_phys_resist%
%raskolclasses_magic_resist% %raskolclasses_dodge%        %raskolclasses_parry%
%raskolclasses_crit_melee%   %raskolclasses_crit_spell%   %raskolclasses_spec%
%raskolclasses_talent_points%%raskolclasses_talents%      %raskolcrown_*%
```

---

## RaskolGear

| Что | Применяет в бою | Считает и показывает |
|---|---|---|
| Урон оружия, крит, проки | RaskolGear | — |
| Резисты шмота, сет-бонусы 4/4 | RaskolGear | RaskolClasses: `/rc gear`, Книга |
| `+HP` шмота | RaskolClasses, входит в `maxHp` | HUD, `/rc debug` |
| Классовые резисты | RaskolClasses | ResistService |
| Burst и single-hit cap | RaskolClasses | CombatService |
| Офенс WP/SP | Пропускается для оружия RaskolGear | — |

Циклический softdepend разрешается через `PluginEnableEvent`.

---

## Установка

1. `mvn -B clean package` → `target/raskol-classes-1.9.3.2.jar`
2. jar в `plugins/`, рестарт
3. Правки конфига — `/rc reload` (кроме `hp-display.mode`)
4. `/rc selftest` → 36/36

<details>
<summary>Softdepend</summary>

LuckPerms, AuraSkills, PlaceholderAPI, RaskolCore, Towny, AuthMe, Vault, RaskolGear, RaskolEnchant. Каждый опционален, деградация graceful.

</details>

<details>
<summary>Регресс-матрица</summary>

Диагностики 1–5, бой 6–16, контент 17–30, план B и ресурсы 31–36. Полный прогон перед любым хотфиксом и релизом.

</details>

---

## Документация

- [`RUNBOOK.md`](RUNBOOK.md) — аварии, гейты, тюнинг без пересборки
- [`CHANGELOG.md`](CHANGELOG.md) — история версий
- [`LICENSE`](LICENSE) — RASKOL Proprietary License v1.0, © 2026 hayferdahmer

<p align="center">
  <img src="https://capsule-render.vercel.app/api?type=venom&height=140&section=footer&color=0:%23050505,20:%232A0A3A,40:%23101A6E,60:%231B4D1B,80:%238B0000,100:%23050505"/>
</p>
