<div align="center">

# ⚔ RASKOL CLASSES

**Боевые классы, таланты и боевая математика для Paper 1.21**
*Сервер «РАСКОЛ | ДВЕ КОРОНЫ»*

`v1.9.3` · `Java 21` · `Paper 1.21.4+` · `Proprietary`

</div>

---

## Обзор

RaskolClasses — боевой слой сервера: пять классов с ресурсами и активными
способностями, специализации как пассивная идентичность, дерево талантов спеки,
сводный уровень персонажа и полная боевая математика (четыре вида урона,
резисты, модификаторы, burst-окно, анти-ваншот, TTK-харнесс). Всё тюнится
конфигом без пересборки.

## Классы

| Класс | Ресурс | Главный атрибут | Роль |
|---|---|---|---|
| Воин | Ярость | STR | танк/ближний бой |
| Охотник | Концентрация | AGI | дальний бой |
| Разбойник | Энергия | AGI | контроль/бурст |
| Маг | Мана | INT | магический урон |
| Жрец | Свет | INT | лечение/щиты |

## HP-модель (1.9.3)

```
HP = base-hp + STR×per-str + level×per-level + (STR-main ? level×main-str-bonus : 0)
```
- Дефолты: 100 / 20 / 5 / 8 → воин L60·STR84 = **2560 HP**.
- Потолок ванильного `max_health` (1024) снят серверным datapack'ом `raskol_hp`.
- `HpAttributeSync` держит ванильный атрибут = формуле; crash-guard в китах.

## Модули

| Модуль | Назначение |
|---|---|
| Способности (слоты 1–5) | свитки в хотбар, КД-полоса, base + Power×coeff |
| Специализации | пассивная идентичность, резисты/проки постоянны |
| Таланты спеки | 10 деревьев × 9 узлов, только в Книге класса |
| Уровень персонажа | топ-N скиллов AuraSkills, кап 60 |
| Инсталляции | мины/варды, TTL, лимиты, килл-кредит |
| Короны | титулы и партикл-ауры `%raskolcrown_*%` |
| Бой | резисты, модификаторы, burst-окно, анти-ваншот, LOS |
| TTK-харнесс | `/rc debug simulate`, матрица 5×5, якорь 20 с |

## Команды

| Команда | Право | Назначение |
|---|---|---|
| `/rc` | — | сводка: класс, уровень, ресурс, резисты, корона, спека |
| `/rc menu` | — | Книга класса (способности / спеки / класс / таланты) |
| `/rc reload` | `raskolclasses.admin` | перезагрузка конфига + reconcile |
| `/rc debug [player]` | `raskolclasses.debug` | атрибуты, резисты, симулятор, таланты |
| `/rc debug simulate …` | `raskolclasses.debug` | headless-дуэль / матрица TTK |
| `/rc health` | `raskolclasses.debug` | MSPT/TPS, purge, аптайм |
| `/rc selftest` | `raskolclasses.debug` | 32 headless-проверки |

## PlaceholderAPI

```
%raskolclasses_class%  %raskolclasses_level%  %raskolclasses_hp%  %raskolclasses_hp_max%
%raskolclasses_phys_resist%  %raskolclasses_magic_resist%  %raskolclasses_dodge%  %raskolclasses_parry%
%raskolclasses_spec%  %raskolclasses_talent_points%  %raskolclasses_talents%  %raskolcrown_*%
```

## Установка

1. `mvn -B clean package` → `target/raskol-classes-1.9.3.jar`
2. jar в `plugins/`; рестарт
3. Datapack `raskol_hp` в `world/datapacks/` (снимает потолок 1024) — см. RUNBOOK §0
4. `/rc selftest` → 32/32 PASS

## Документация

- [`RUNBOOK.md`](RUNBOOK.md) — аварии, гейты, тюнинг, чек-лист оператора
- [`CHANGELOG.md`](CHANGELOG.md) — история версий

## Лицензия

**RASKOL Proprietary License v1.0** © 2026 hayferdahmer. См. [`LICENSE`](LICENSE).

---

<div align="center">

**РАСКОЛ · ДВЕ КОРОНЫ** — пять путей, одна война

</div>
