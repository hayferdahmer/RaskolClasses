# RaskolClasses

Активные способности пяти классов сервера «РАСКОЛ | ДВЕ КОРОНЫ».
Рантайм: Paper 26.2 (build ~112) · Java 21 · offline-mode + AuthMe.
Компиляция: paper-api 1.21.4-R0.1-SNAPSHOT (нижняя планка форвард-совместимости).

Текущая версия: **1.3.0** · [CHANGELOG](CHANGELOG.md)

## Владение и лицензия

© 2026 hayferdahmer. Все права защищены.
RASKOL Proprietary License v1.0 — см. [LICENSE](LICENSE).
Копирование, распространение и использование вне сервера «РАСКОЛ» без письменного
разрешения владельца запрещены.

## Что делает плагин

- Пять классов: Воин, Охотник, Жрец, Маг, Разбойник. Класс — из primary group
  LuckPerms (`class_*`).
- Ресурсы 0–100 с классовым регеном: Ярость, Концентрация, Свет, Мана, Энергия.
  У мага — тирный реген маны (3/4/5/6 в секунду по порогам 25/50/75).
- 21 активная способность (4–5 на класс) со стоимостью, кулдаунами и анлоками;
  кулдауны переживают релог (`cooldowns.yml`).
- 6 врождённых пассивок; все цифры — в конфиге.
- Тёмный тематический HUD в actionbar: градиент класса, шкала ресурса,
  braille-спиннер кулдаунов, аура полного ресурса.
- **Босс-бары длительных эффектов** (≥ 8 с, топ-2 одновременно, сегменты, отсчёт).
- **Свитки способностей:** `/rc bind <1-5>` — предмет, ПКМ = каст без команд.
- **Ready-notify:** звук и actionbar ««способность» — готова» при окончании длинных КД.
- «Принятие класса»: титр градиентом темы + партиклы + звук при смене класса
  (раз в сессию).
- Плейсхолдеры PlaceholderAPI: `%raskolclasses_class%`, `%raskolclasses_resource%`,
  `%raskolclasses_resource_max%`.
- Уровни AuraSkills открывают способности (graceful-degrade: без AuraSkills
  анлоки сняты).

## Требования

| Компонент | Статус | Примечание |
|---|---|---|
| Paper 1.21.4+ | обязателен | рантайм верифицирован на Paper 26.2 |
| Java 21 | обязателен | |
| LuckPerms | опционален | без него определение классов отключено (варнинг) |
| AuraSkills | опционален | без него уровневые требования сняты |
| PlaceholderAPI | опционален | без него плейсхолдеры не регистрируются |

## Команды и права

| Команда | Право | Описание |
|---|---|---|
| `/rc` | — | сводка класса и способностей |
| `/rc 1–5` | — | каст способности слота |
| `/rc menu` | — | GUI способностей |
| `/rc hud` | — | переключить HUD |
| `/rc bind <1-5>` | — | выдать свиток способности (ПКМ = каст) |
| `/rc reload` | `raskolclasses.admin` (default: op) | перезагрузить конфиг |
| `/rc debug [player]` | `raskolclasses.debug` (default: op) | диагностика: версия, класс, ресурс, КД, эффекты, уровень, HUD |

## Конфигурация

Весь баланс живёт в `config.yml`; отсутствующие ключи дописываются автоматически
(`copyDefaults`), правки админа не затираются.

- `classes.<CLASS>.resource-*` — реген и набор ресурса;
- `classes.<CLASS>.theme.*`, `cast-sound`, `cast-particle` — темы классов;
- `classes.<CLASS>.abilities.<id>.{unlock,cost,cooldown,name,description,duration}` — активки;
- `classes.<CLASS>.passives.<id>.*` — пассивки (chance, multiplier, threshold,
  cooldown-seconds, display-name, description);
- `classes.MAGE.regen-tier-{1..4}` — тиры регена маны (3/4/5/6 в секунду);
- `class-accept.{enabled,subtitle}` — титр принятия класса;
- `hud.*`, `hud.boss-bar.*`, `performance.*`, `performance.ready-notify.*`,
  `hotbar-bind.*`, `messages.*` — HUD, босс-бары, ready-notify, хотбар-бинд и локализация.

### Врождённые пассивки (1.2.0, описания с 1.3.0)

| Класс | Пассивка | Эффект (дефолт) |
|---|---|---|
| Воин | Казнь | 20% шанс ×3 урона по цели ≤20% HP; внутр. КД 6 с |
| Охотник | Хищник | +20% урона по целям ≥80% HP |
| Жрец | Благодать | получаемое лечение +15% |
| Маг | Пропитанный маной | мана ≥50 → −15% входящего урона |
| Разбойник | Мастер ядов | 30% Яд I на 2 с; внутр. КД 3 с |
| Разбойник | Садизм | +3 урона в спину; внутр. КД 2 с |

### Ребаланс воина (1.2.0)

| Способность | Цифры |
|---|---|
| Стальная кожа | −80% урона, 5 с (10/30/45) |
| Удар щитом | Slowness II + Blindness r4, 3 с + таунт (25/20/25) |
| Кровавое безумие | 4 с возвращает 20% урона агрессору (50/40/30) |
| Бог войны | Сила II + Сопротивление I, 8 с (75/100/300) |

### ×1.5 XP профильным деревьям (операторское, 1.3.0)

| Без кода, через LP-ноды (AuraSkills читает их нативно):
|---|---|
| /lp group class_warrior permission set auraskills.fighting.multiplier.50
| /lp group class_hunter permission set auraskills.archery.multiplier.50
| /lp group class_priest permission set auraskills.healing.multiplier.50
| /lp group class_mage permission set auraskills.sorcery.multiplier.50
| /lp group class_rogue permission set auraskills.agility.multiplier.50

Верификация: убить моба без ноды → с нодой, сравнить прирост fighting-XP.
Ожидаемый множитель: ≈×1.5 (семантика `50` = +50%). Если фактический множитель
отличается — скажи, подстроим значение под реальную семантику AuraSkills 2.3.12.

## Сборка и CI

GitHub Actions: `.github/workflows/build.yml` — `mvn -B clean package`
+ **deprecation-гейт**: ран красный при любом javac-варнинге.
Артефакт: `target/raskol-classes-<version>.jar`.

## Архитектурные правила

- V3: NMS и рефлексия во внутренние классы сервера запрещены; единственная
  рефлексия — graceful-degrade к AuraSkills в `SkillLevelProvider`.
- Запрещённые конструкции (исторические галлюцинации): `Component.Builder`,
  `Location#setPosition`, `EventSubscription#unregister`, `UserDataMutateEvent`,
  `Player#isVanished`, `JavaPlugin#getDescription`, `Sound.valueOf`.
