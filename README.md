<div align="center">

![RASKOL CLASSES — Две Короны](assets/banner.png)

# RASKOL CLASSES

**Боевое ядро сервера «РАСКОЛ | ДВЕ КОРОНЫ»**
*Пять путей. Одна война. Слабым здесь не место.*

[![Version](https://img.shields.io/badge/VERSION-1.9.3.2-8b0000?style=for-the-badge)](CHANGELOG.md)
[![Build](https://img.shields.io/github/actions/workflow/status/hayferdahmer/RaskolClasses/build.yml?branch=main&style=for-the-badge&label=BUILD&color=1a1a1a)](../../actions)
[![Java](https://img.shields.io/badge/JAVA-21-1a1a1a?style=for-the-badge&logo=openjdk&logoColor=b0b0b0)](#)
[![Paper](https://img.shields.io/badge/PAPER-1.21.4%2B-0b0b0b?style=for-the-badge)](#)
[![License](https://img.shields.io/badge/LICENSE-PROPRIETARY-000000?style=for-the-badge)](LICENSE)

> *«Рассвет даёт свет. Вальрадис даёт пламя.*
> *Оба дают смерть тем, кто выбрал неверно».*
> — Хроника Раскола, третья эпоха

</div>

---

## ☠ СУТЬ

RaskolClasses — не «плагин способностей». Это **боевая конституция** сервера:
пять классов с ресурсом и китами, специализации как пассивная идентичность,
дерево талантов, сводный уровень персонажа и полная боевая математика —
четыре вида урона, резисты, модификаторы, burst-окно, анти-ваншот и TTK-харнесс.

Всё, что чувствует игрок в бою, считается здесь. Всё, что видит админ,
тюнится конфигом **без пересборки**.

---

## ⚔ ПЯТЬ ПУТЕЙ

| | Класс | Ресурс | Главный атрибут | Роль в бою |
|---|---|---|---|---|
| ⚔ | **Воин** | Ярость *(−5/с вне боя, +10 за урон)* | STR | танк, execute-финишеры |
| ➳ | **Охотник** | Концентрация *(+5/с вне боя)* | AGI | дальний бой, контроль |
| ✚ | **Жрец** | Свет *(+2/с всегда)* | INT | лечение, щиты веры |
| ✦ | **Маг** | Мана *(тиры по порогам 25/50/75)* | INT | магический бурст, зоны |
| ☠ | **Разбойник** | Энергия *(+10/с)* | AGI | инвиз, яды, удар в спину |

Способности — слоты 1–5 (лестница 10/25/50/65/75), применяются **только свитками
в хотбаре**. Все числа китов = `base + Power × coeff` (WP / SP / HPow).

---

## 🩸 БОЕВАЯ МАТЕМАТИКА

### Здоровье (1.9.3, виртуальный пул)

```
HP = base-hp + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus : 0) + gear-hp
```

- Ванильный `max_health` — лишь **носитель пропорции** (потолок движка 1024).
- Реальный пул = формула: воин L60/STR84 держит **2560 HP**, и это не предел.
- Урон, хилы, капы и HUD работают в формульных единицах через `scale = carrier / formula`.
- Датпаки и оверрайды атрибутов **не требуются** — потолок снят архитектурно.

### Урон и защита

- **Типы:** physical / magic / hybrid / true; среда = true + масштаб × maxHP/20.
- **Резисты:** база класса + спека + таланты + шмот RaskolGear; кап 90 (PvP-кап отдельный).
- **Анти-ваншот:** одиночный.hit ≤ 35% maxHP (исключения: среда, execute-финишеры).
- **Burst-окно:** ≤ 18% maxHP за 3 секунды — связки и бурст-открытия не убивают мгновенно.
- **Уклонение/парирование:** гиперболы с убывающей отдачей, DR, углы фронт/спина, стаггер.
- **TTK-харнесс:** `/rc debug simulate [A] [B] [level]` и матрица 5×5; якорь 20 с, коридор ±30%.

---

## 🛡 RASKOLGEAR: СТАЛЬ, КОТОРАЯ СЧИТАЕТ

Интеграция с плагином снаряжения **без правок чужой репозиторий** — чтение PDC-тегов:

| Слой | Кто применяет в бою | Кто считает и показывает |
|---|---|---|
| Урон оружия (base + Power×coeff, крит, проки) | RaskolGear | RaskolGear |
| Резисты шмота и сет-бонусы 4/4 | RaskolGear | RaskolClasses (`/rc debug`, `/rc gear`, Книга) |
| +HP от шмота | RaskolClasses (входит в формулу HP) | RaskolClasses |
| Классовые резисты и таланты | RaskolClasses | RaskolClasses |
| Burst / анти-ваншот | RaskolClasses | RaskolClasses |

Двойного применения нет: исходящий офенс классовых статов пропускается для оружия
с тегом `WEAPON`, резисты шмота и класса стекаются мультипликативно.

---

## 📖 КНИГА КЛАССА

Единое мрачное окно управления персонажем — пять вкладок, строгая сетка,
чёрная рамка, состояния предметов без разнобоя:

![Книга класса](assets/book.png)

| Вкладка | Содержимое |
|---|---|
| Способности | 5 слотов + инсталляция; глоу готовых, КД, цена, свитки |
| Специализации | выбор с 40 ур., пассивки, отречение (двойное ПКМ, 30 с) |
| Класс и пассивки | атрибуты, резисты с breakdown, корона и титул |
| Таланты спеки | дерево 9 узлов: тиры, пререквизиты, капстоун, ульт; сброс кристаллом |
| Шмот и сеты | оружие, 4 слота брони, статус сетов N/4, сводка статов |

Управление талантами и спеками — **только здесь**. Команд нет: решение владельца.

---

## 🧩 КОМАНДЫ И ПРАВА

| Команда | Право | Назначение |
|---|---|---|
| `/rc` | — | сводка: класс, уровень персонажа, ресурс, резисты |
| `/rc menu` | — | Книга класса |
| `/rc gear [player]` | `raskolclasses.debug` | экипировка, сеты, статы шмота |
| `/rc debug [player]` | `raskolclasses.debug` | атрибуты, резисты, симулятор урона, таланты |
| `/rc debug simulate …` | `raskolclasses.debug` | headless-дуэль / матрица TTK |
| `/rc health` | `raskolclasses.debug` | MSPT/TPS, purge, аптайм |
| `/rc selftest` | `raskolclasses.debug` | 36 headless-чеков формул |
| `/rc reload` | `raskolclasses.admin` | перезагрузка конфига + reconcile |

---

## 📡 ПЛЕЙСХОЛДЕРЫ (PlaceholderAPI)

<details>
<summary><b>%raskolclasses_*% и %raskolcrown_*% — развернуть</b></summary>

```
%raskolclasses_class%        %raskolclasses_class_id%
%raskolclasses_level%        %raskolclasses_char_level%     %raskolclasses_skill_level%
%raskolclasses_resource%     %raskolclasses_resource_max%
%raskolclasses_hp%           %raskolclasses_hp_max%
%raskolclasses_phys_resist%  %raskolclasses_magic_resist%
%raskolclasses_dodge%        %raskolclasses_parry%
%raskolclasses_crit_melee%   %raskolclasses_crit_spell%
%raskolclasses_spec%         %raskolclasses_spec_id%
%raskolclasses_talent_points%  %raskolclasses_talents%
%raskolcrown_*%              — короны, титулы, ауры
```

</details>

---

## ⚙ УСТАНОВКА

1. `mvn -B clean package` → `target/raskol-classes-1.9.3.2.jar`
2. jar в `plugins/`, рестарт сервера
3. `config.yml` создаётся автоматически; правки — через `/rc reload`
   (кроме `hp-display.mode`)
4. Softdepend (каждый опционален, деградация graceful): LuckPerms, AuraSkills,
   PlaceholderAPI, RaskolCore, Towny, Vault, **RaskolGear**, AuthMe, Essentials, packetevents
5. Проверка: `/rc selftest` → **36/36 PASS**

Хранилища: `cooldowns.yml`, `spec-choices.yml`, `talents.yml`, `resources.yml`,
`health.yml` — атомарная запись, `.bak`-копии, автосейв каждые `storage.autosave-minutes`.

---

## 🧪 КАЧЕСТВО

- **36 headless-чеков** `/rc selftest`: формулы, DR, криты, капы, TTK-санити,
  экономика талантов, reconcile-циклы, план B (scale/heal/carrier), декэй ярости.
- **Регресс-матрица 30 пунктов** в RUNBOOK — полный прогон перед любым хотфиксом.
- CI: workflow «RaskolClasses Build» на каждый пуш; красный ран = блок релиза.
- NaN-гарды, SafeStorage с фолбэком, санитизация устаревших свитков, rate-limit GUI.

---

## 📚 ДОКУМЕНТАЦИЯ

- [`RUNBOOK.md`](RUNBOOK.md) — операторский справочник: аварии, гейты, тюнинг, чек-листы
- [`CHANGELOG.md`](CHANGELOG.md) — история версий (Added/Changed/Fixed/Validate)

---

## ⚖ ЛИЦЕНЗИЯ

**RASKOL Proprietary License v1.0** © 2026 hayferdahmer.
Использование разрешено только на сервере «РАСКОЛ | ДВЕ КОРОНЫ».
Копирование, редистрибуция и продажа — только с письменного разрешения владельца.
См. [`LICENSE`](LICENSE).

---

<div align="center">

**РАСКОЛ · ДВЕ КОРОНЫ**
*Рассвет судит днём. Вальрадис — ночью.*

</div>
