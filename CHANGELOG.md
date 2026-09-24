# CHANGELOG — RaskolClasses

Формат: [версия] — дата — имя. Секции Added / Changed / Fixed / Removed / Reverted / Validate.
Линия 1.9.x активна; 1.8.x закрыта с 1.8.1; 1.7.x и 1.6.x заморожены.

## [1.9.3] — 2026-09-24 · «Снятие потолка HP + синхронизация max_health»

### Added
- **HpAttributeSync:** синхронизация ванильного `MAX_HEALTH` с формулой
  AttributeService на join/respawn/reload/invalidate + sweep каждые 5 с.
  HUD, бой, хилы и плагины-партнёры видят одно и то же HP.
- **Полная формула HP:** `base + STR×per-str + level×per-level + (STR-main ?
  level×main-str-bonus)`. Ключи `attributes.hp.per-level` (5) и
  `main-str-bonus` (8) стали ЖИВЫМИ (ранее мёртвые).
- **Datapack `raskol_hp`** (серверный): оверрайд `minecraft:max_health`
  (max_value 1 000 000) — снимает движковый потолок 1024.
- **Crash-guard китов:** `effectiveMaxHp = min(формула, ванильный getMaxHealth)`
  в fenrirBlood/ragnarok — `setHealth` больше не бросает IllegalArgumentException.
- `RaskolConfig`: addDefault для `attributes.hp.per-level` и `main-str-bonus`.

### Changed
- **CharacterLevelService:** фолбэк без AuraSkills = `character-level.fallback`
  (40), а НЕ `player.getLevel()` (ванильный XP, часто 0). Устранён провал HP
  к «100 + STR×20» у игроков без скилл-системы.
- **CombatService.applyEnvLethalScale:** масштаб среды от формульного HP,
  а не от ванильного MAX_HEALTH.
- **WarriorAbilities:** fenrirBlood/ragnarok читают HP из AttributeService
  (через effectiveMaxHp), а не из ванильного атрибута.
- **AttributeMath:** каноническая 7-арг формула maxHp; legacy 6-arg делегирует
  в неё (baseHp=100) — убран молчаливый хардкод.

### Fixed
- Воин упирался в 1024 HP (движковый кламп max_health) при формульных 2560.
- `fenrirBlood` мог бросить IllegalArgumentException при formula > vanilla max.
- `ragnarok` execute-порог считался от ванильного max (20/1024), а не от пула.
- Игроки без AuraSkills получали level=0 → HP только от STR.

### Validate
- `/rc debug` воин L60/STR84 → `maxHP 2560`.
- `/attribute <ник> minecraft:max_health` → 2560 при enabled `raskol_hp`.
- `/rc selftest` → 32/32 PASS.
- Fenrir Blood лечит до effective max без исключений (с datapack и без).

## [1.9.2] — 2026-09-16 · «Интеграция с RaskolCore 1.3.0 + эксплойт-свип»

### Added
- **PassportChangeListener:** мгновенный reconcile талантов/резистов/боевых кэшей
  на Reason.CLASS/FACTION из RaskolCore — закрыто 5-секундное окно эксплойта.
- **EconomyHook:** первично `RaskolCoreAPI.economy()` (контракт Core), фолбэк Vault.
- **Глобальный бюджет талантов:** `spentGlobal()` по ВСЕМ деревьям; респец не печатает очки.
- **Reconcile-валидация talents.yml:** битые узлы удаляются сервером с WARNING.
- **Rate-limit покупок/сброса талантов.**
- **Фарм-гейты ресурса:** урон по себе/союзнику не фармит ярость/концентрацию.
- **Selftest чеки 31–32.** Итого 32 чека.
- **Стартовая проверка RaskolCore** (warn-only).

### Changed
- ClassBook: инфо-предмет очков = «общий бюджет персонажа»; обработка RATE_LIMITED.

### Fixed
- YAML `config.yml` (строка 186): пробел после `base-hpow:` перед `{`.

### Validate
- `/rc selftest` → 32/32 PASS; живые эксплойт-проверки.

## [1.9.1] — 2026-09-15 · «Стабилизация: боевое окно, регресс-замки, валидатор»

### Added
- Selftest чеки 29–30 (боевое окно, семантика consume). Итого 30 чеков.
- ConfigValidator: talents.*, character-level.*, combat.burst-*, frost_rune.*.

### Changed
- ResourceService.markCombat() на нанёсшем и получившем урон — боевое окно ожило.

### Removed
- ResourceState.allowGainEvent()/lastGainEventMillis — мёртвый код.

### Validate
- `/rc selftest` → 30/30 PASS; живая проверка боевого окна.

## [1.9.0] — 2026-09-11 · «Дерево талантов спеки (Книга класса)»

### Added
- TalentsRegistry (10 деревьев × 9 узлов), TalentService (очки/валидация/reconcile),
  TalentsStorage, вкладка TALENTS в Книге, PAPI-плейсхолдеры талантов,
  selftest чеки 22–24, секция talents в config.

### Changed
- Кит-хелперы учитывают baseBonus/coeffMult; кулдауны ×cooldownMult;
  avoidance += avoid-бонусы; реген += regen-бонус; проки += procBonus.
- Применение способностей/инсталляций — только свитками; /rc 1–7 удалены.

### Fixed
- fix6–fix12: consume-баг ресурса; ScrollCooldownTask v12 (durability-bar);
  FxService резолв звуков; BindListener точечные DENY; InstallToken/InstallationService.

### Validate
- `/rc selftest` → 28/28 PASS; плейтест мага.

## [1.8.1] — 2026-09-11 · «Стабилизационная серия перед 1.9.0»
### Fixed
- S1–S7: фракционный гейт ванили, canHit в 9 абилках, Towny-гейт блинка,
  PAPI level/char_level, selftest canHit, health-ratio кламп, healer-маркер.

## [1.8.0] — 2026-09-10 · «Уровень персонажа: прогрессия от ширины прокачки»
### Added
- CharacterLevelService (топ-N скиллов, кап 60); config character-level; selftest 19–20.
### Changed
- attributes.level-source: character (дефолт); рубильник отката class-skill/vanilla.

## [1.7.6] — 2026-09-10 · «TTK-харнесс и баланс-пакет 1.7.6.1–1.7.6.3»
### Added
- BalanceSimulator (simulate / matrix); burst-окно 3 с / 18%.
### Changed
- dodge-mult 0.5; parry-k 300; resist WARRIOR physical 18; coeff-подъём против танков.

## [1.7.5] — 2026-09-10 · «Спеки = пассивная идентичность»
### Removed
- Активки спеков; свитки спеков сгорают на join.

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
