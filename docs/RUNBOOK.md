# RUNBOOK — RaskolClasses 1.14.4

Операторский справочник: сохранения, миграции, респецы, композиция лечения,
проки, конфиг-карта, регресс-матрица, troubleshooting. Лицензия: RASKOL
Proprietary License v1.0.

## 1. Старт/стоп и сохранения

- Автосейв каждые `storage.autosave-minutes` (дефолт 5): cooldowns.yml,
  resources.yml, spec2-storage.yml.
- `onDisable` порядок: hpSync.stopSweep → blueprint unregister →
  WarlockAbilities.cancelAllChannelTasks → отмена задач → passport unregister →
  resources.saveAll → **hpBarService.saveAll** (1.14.1) → spec2Storage.save →
  installations.shutdown → bossBars.shutdown → cooldowns save/clear →
  classProvider.shutdown → effects.clear.
- После краха HP восстанавливается из `health.yml` (ratio от formula-maxHP).

## 2. Spec2-хранилище и миграция (1.14.4, П10)

- Файл: `plugins/RaskolClasses/spec2-storage.yml` (схема v2:
  `players.<uuid>.main`, `players.<uuid>.ranks.<treeId>.<nodeId>`).
- Миграция: при старте, если есть `spec2.yml` и нет `spec2-storage.yml`,
  выполняется `renameTo`. Лог: `spec2: migrated spec2.yml → spec2-storage.yml`.
- Откат на 1.14.0: переименовать файл обратно вручную (`spec2-storage.yml` →
  `spec2.yml`) ПОСЛЕ замены jar.
- Бэкапы: `SafeStorage` пишет `.bak` перед атомарной заменой; ручная копия —
  стоп сервера → копия файла → старт.
- Прунинг: reconcile удаляет ранги узлов, нарушающих гейты рядов/пререквизиты,
  с warning-логом `spec2: ранги дерева … прорежены …`.

## 3. Респецы (1.14.4, П7)

| Операция | Где | Цена | Возврат |
| --- | --- | --- | --- |
| Сброс ВСЕГО дерева | Книга → «Деревья путей» → кристалл, ПКМ ×2 (30 с) | `spec2.respec-base-cost` (250) + `spec2.respec-per-level` (10) × потрачено в дереве | все очки дерева в общий пул |
| Сброс 1 ранга узла | Книга → ПКМ по купленному узлу → ПКМ повторно (30 с) | `spec2.node-respec-base` (150) + `spec2.node-respec-per-rank` (50) × текущий ранг | 1 очко в общий пул |

- `raskolclasses.admin` — оба респеца бесплатно (free-путь).
- Без Vault-экономики оба возвращают `NO_ECONOMY` (респец отключён, очки целы).
- Эскалация узлового респеца линейна по рангу: снять 5/5 = 400, снять 1/5 = 200.
- Взвод узла сбрасывается: ЛКМ по любому узлу, листание рядов, смена спеки,
  клик по кристаллу, выход из книги.

## 4. Композиция лечения (1.14.1/1.14.3, П3)

Порядок множителей на `CustomHealEvent` (кит-хилы через HpBarService.heal):
1. Исходящие: `× (1 + heal_out_pct/100)` (узлы discipline/holy/arms) →
   `× (1 + spec2.role-passives.HEALER.heal-mult)` если роль владельца HEALER.
2. Входящие (цель-игрок): `× (1 + heal_received_pct/100 + [TANK: role-passives.TANK.heal-received])`.
Ванильные `EntityRegainHealthEvent` (регены, зелья): входящие множители те же;
исходящие heal_out/HEALER применяются только при установленном маркере хилера
(PriestAbilities ставит перед кит-хилом). Анти-хил (mortal_strike, soul_rift,
Пентаграмма) режет оба пути через `WarlockAbilities.isAntihealed`.

## 5. Proc-узлы: справочник триггеров (1.14.3–1.14.4)

| Kind | Триггер | Эффект |
| --- | --- | --- |
| proc_riposte | PARRY без щита | +X% к следующему удару защищавшегося, 8 с |
| proc_counterattack | DODGE | +X% к следующему удару уклонившегося, 8 с |
| proc_revenge | блок щитом (PARRY+щит) | +X% к следующему удару блокировавшего, 8 с |
| proc_shield_slam | блок щитом | шанс X%: ×1.5 к следующему удару, 8 с |
| proc_second_wind | урон при HP<35% | мгновенный хил 8% formula-maxHP, КД 30 с |
| proc_bleed_on_crit | крит мили | наложить bleed |
| proc_apply_poison | крит мили, шанс | наложить poison |
| proc_poison_extend / proc_burning_extend | крит мили / крит магии | освежить соответствующий DoT |
| proc_vendetta_refresh | крит | продлить вендетту на цели до 10 с |
| proc_double_strike | успешный мили-урон | шанс X% повторного удара (без рекурсии) |
| proc_expose | любой успешный урон | цель +X% входящего урона, 6 с |
| proc_crit_bonus / proc_savage / proc_headshot | крит | множитель crit-урона |
| proc_stealth_bonus | удар в INVISIBILITY | +X% урона |
| proc_stealth_extend | каст shadow_cloak | +X с к длительности |
| proc_reflect_magic | полученный маг-урон | отразить X% (может быть уклонён) |
| proc_undodgeable | крит мили | следующий удар атакующего не уклоняется |
| proc_armor_pen | удар в INVISIBILITY | игнор X% резиста цели |

Состояния proc-слоя чистятся purge-задачей (`performance.purge-interval-ticks`).

## 6. Конфиг-карта 1.14.4 (новые/изменённые ключи)

```yaml
spec2:
  start-level: 15        # читается Spec2Points.configure (П9)
  max-points: 46         # читается (П9)
  row-gates: [0,5,10,15,20,30]  # читается (П9)
  node-respec-base: 150  # НОВОЕ (П7)
  node-respec-per-rank: 50  # НОВОЕ (П7)
combat:
  block:
    visuals: true        # НОВОЕ: звук/фидбек блока щитом (3B)
    sound: ITEM_SHIELD_BLOCK
