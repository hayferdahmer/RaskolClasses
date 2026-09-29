# CHANGELOG — RaskolClasses

Формат: [версия] — дата — имя. Секции Added / Changed / Fixed / Removed / Validate.
Линия 1.12.x активна (школы урона); 1.11.x и ниже заморожены.

## [1.12.2] — 2026-09-30 · «Школы: пробитие и стихийный слой (живая проводка)»

### Added
- Блок 1: ElementalResistService — стихийный слой резистов поверх канала
  (source-модификаторы по школам, кап schools.elemental.resist-cap, self-purge).
- Блок 2: pen-трейты RaskolGear в GearHook: PDC-ключи raskolgear:pen_phys_pct /
  pen_magic_pct / pen_<school>_pct (bronя + оружие), агрегация с клампом pen-pct-cap.
- Блок 3: PenTraitsService — агрегатор pen из gear + талантов (kind "pen") +
  спек (passive-ключи pen_* в specs.yml); clampSumPercent.
- Блок 4: живая проводка pen+elemental в путь A (VanillaDamageListener) и путь B
  (CombatService.dealDamage: phys→PHYSICAL, magic→ARCANE по legacy-соглашению);
  порядок flat→pct, слои мультипликативно, кап mitigation-cap.
- Блок 5: UX — строки «Пробитие» и «Стихии» в /rc debug и в Книге (вкладка CLASS);
  настоящий раздел CHANGELOG и RUNBOOK-секция VIII.
- Selftest чеки 55–62 (стихийный слой, gear-pen, pen-трейты, neutral-проводка).

### Changed
- schools.mitigation-cap: 0.80 → 0.90 (выравнивание с resist.cap: проводка нейтральна
  к 1.12.1 для танков с 90% резиста).
- schools.elemental.enabled: false → true (слой включён; без источников прозрачен).

### Validate
- /rc selftest → 62/62; TTK-матрица 6×6 без дельт против 1.12.1 (pen-контента нет).

## [1.12.1] — 2026-09-29 · «Школы: митигация, пробитие, иммунитеты»

### Added
- SchoolMitigation (порядок flat→pct, стихийный слой, mitigation-cap), Penetration
  (record, клампы), SchoolImmunity (schools.entities.*: immune/resistant/vulnerable).
- Проводка иммунитетов/уязвимостей и глобальных множителей школ в путь A.
- Selftest чеки 52–54.

### Fixed
- DamageCause FREEZE: ключи FREEZING в damage-types.vanilla-map и
  schools.vanilla-school никогда не матчились (в Bukkit константа FREEZE).

## [1.12.0] — 2026-09-28 · «Каркас школ урона»

### Added
- combat.school: School (8 школ → каналы PHYSICAL/MAGIC/TRUE), SchoolProfile
  (immutable, legacy-адаптеры fromLegacy/toLegacy, dominant), SchoolConfig
  (schools.*: enabled, multiplier, vanilla-school, резервные капы).
- Секция schools.* в config.yml; school-reorg пакета (переезд School*).
- Selftest чеки 49–51.

### Changed
- Поведение боя не менялось (множители 1.0, каркас без живой проводки).

## [1.11.4] — 2026-09-29 · «Сверхстабильность: рефакторинг P1–P5 + харденинг S/F/T»

### Added
- P1: пассивки по классам (ClassPassive/BaseClassPassive + 5 файлов); PassiveListener = диспетчер.
- P2: инсталляции по типам (InstallationHandler + 6 обработчиков); InstallationService = реестр;
  Пентаграмма v2; кулдаун только после успешной постановки.
- P3: боевое ядро разложено (VanillaDamageListener, DamageCaps, CombatMath); CombatService = фасад.
- P4a: WarlockMath + WarlockFx; чеки 41–43. Чеки 44–45: KitSanity.
- P4b: Книга → gui/book/* (5 таб-файлов + каркас).
- P4c: AttributeService → HpPool + AttributeModifiers.
- P4d: команды → command/sub/* (4 сабкоманды + роутер).
- P4e: spec-слой → SpecMath/SpecPassives/SpecEconomy; аудит F1–F10; чеки 46–47.
- P5: per-class конфиги kits/<class>.yml (KitConfigLoader/KitConfigCache); чек 48.
- Безопасность: S1 санитайзер ников; S2 релок-цикл фолианта; S3 soulbound;
  S4 рефлект без self-урона; S5 анти-хил на все причины RegainHealth.

### Fixed (эксплойты и семантика, F1–F10)
- F1/F2: ликвидатор/трюкач — 100% крит/додж → 15% (проценты читались как доли).
- F3: лифстил тенеплёта через HpBarService (уважает анти-хил).
- F4/F5/F6: берсерк по HP≥60%, аркана = +1 мана/с, стрелок = крит стрелами (по specs.yml).
- F7: +6% маг-резиста Адского Канала применяется (генерик resist.specs.* + reconcile).
- F9/F10: двойной тег благодати убран; reconcile самопланируется.
- T2/T3/T5: задачи канала отменяются; invalidate резист-кэша на смене сетов; чистка .tmp.

### Changed (баланс-влияние)
- DPS ликвидатора и выживаемость трюкача снижены до проектных 15%; семантики берсерка/
  arkаны/стрелка приведены к specs.yml. Калибровка матрицы — в 1.12.7.

### Removed
- SpecMenu.java, TrapVisual.java, install/Installation.java, dead-ветки precise/rageBurst.

### Validate
- /rc selftest → 48/48; CI зелёный; живой регресс китов/спек/инсталляций/фолианта/книги.

## [1.11.0] — 2026-09-25 · «Публичный релиз линии Чернокнижника»
## [1.10.4] — 2026-09-25 · «Чёрное Слово v2, Скверна пол 0, Пентаграмма»
## [1.10.3] — 2026-09-25 · «Прогрессия чернокнижника = sorcery»
## [1.10.2] — 2026-09-25 · «Дроп фолианта, LP-миграция без clear-перегрузок»
## [1.10.1] — 2026-09-25 · «Пол Скверны, plain-партиклы, self-кастеры»
## [1.10.0] — 2026-09-25 · «Чернокнижник: шестой путь Раскола»
## [1.9.3.2] — 2026-09-25 · «Хотфикс: декэй ярости воина вне боя»
## [1.9.3.1] — 2026-09-25 · «Хотфикс: версия, старт GearHook, Книга»
## [1.9.3] — 2026-09-24 · «Виртуальный пул HP + RaskolGear + редизайн Книги»
## [1.9.2] — 2026-09-16 · «Интеграция с RaskolCore 1.3.0 + эксплойт-свип»
## [1.9.1] — 2026-09-15 · «Стабилизация: боевое окно, регресс-замки, валидатор»
## [1.9.0] — 2026-09-11 · «Дерево талантов спеки (Книга класса)»
## [1.8.1] — 2026-09-11 · «Стабилизационная серия перед 1.9.0»
## [1.8.0] — 2026-09-10 · «Уровень персонажа: прогрессия от ширины прокачки»
## [1.7.6] — 2026-09-10 · «TTK-харнесс и баланс-пакет»
## [1.7.5] — 2026-09-10 · «Спеки = пассивная идентичность»
## [1.7.4] — 2026-09-10 · «Кит Жреца (HPow-хилы)»
## [1.7.3] — 2026-09-10 · «Киты: Маг и Разбойник»
## [1.7.2] — 2026-09-10 · «Киты: Воин и Охотник»
## [1.7.1] — 2026-09-10 · «Производные статы WP/SP/HPow и анти-ваншот»
## [1.7.0] — 2026-09-10 · «Фундамент атрибутов и боевая физика»
## [1.6.15] — 2026-09-08 · «Заморозка ветки 1.6.x»
## [1.6.0–1.6.14] — 2026-09-07…10 · резюме линии
## [1.5.10] — 2026-09-08 · «Заморозка ветки 1.5.x»
## [1.5.0–1.5.9] — 2026-09-02…08 · резюме линии
## [1.4.0] — 2026-09-02 · «Специализации и Короны»
## [1.3.2] и ранее — история разработки
