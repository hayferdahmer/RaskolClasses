# Архитектурный аудит 20 отключённых боевых узлов v1.16.0

**Статус:** архитектурный аудит / проект решений. Не является разрешением включить узлы.  
**Платформа:** Paper 26.2, Java 25.  
**Базовая ветка:** `feat/v1.16-remaining-talent-handlers` (PR #49).  
**Правило безопасности:** все 20 узлов остаются в `spec2.disabled-nodes`, пока обработчик, тесты, баланс и runtime smoke не пройдут отдельный gate.

## 1. Результат аудита в двух словах

Сейчас нельзя считать, что все 20 эффектов имеют рабочие игровые механизмы. Реестр талантов хранит декларации эффектов, `Spec2Service` агрегирует часть их значений, но наличие числа в агрегате не доказывает, что оно применяется к боевому событию.

В проверенной ветке подтверждены:
- общая агрегация talent-эффектов и proc-шансов/силы в `Spec2Service`;
- отдельные карты для `kit_extra_hits`, `kit_extra_arrows`, `kit_attr_agi`;
- первые потребители дополнительных ударов/стрел/ловкости в PR #48 (узлы по-прежнему отключены);
- шесть pet-эффектов в PR #49: `bm_wild_bond`, `bm_cobra_reflexes`, `bm_spirit_bond`, `bm_go_for_the_throat`, `bm_primal_fury`, `bm_pack_leader`;
- существующий общий боевой контур с проверкой допустимости попадания `CombatService.canHit`, отдельными melee/ranged-путями и ограничителями урона в конфигурации.

Не подтверждены как готовые полноценные механизмы: комбо-поинты разбойника, отдельный ranged-crit pipeline, ability-specific penetration, trap damage/cooldown, offhand-only damage, pet attack speed, pet frenzy и групповой pet-бафф. Для них нельзя подменять механику похожим, но семантически неверным эффектом (например, скорость передвижения вместо скорости атаки).

## 2. Общие контракты реализации

### 2.1 Единицы и агрегация

1. Проценты хранятся как процентные пункты: `5.0` означает +5%, а не коэффициент 0.05.
2. Proc-шанс в декларации `proc_*` — доля 0..1 за ранг; суммируется по рангам и ограничивается сверху 1.0. `value2` — сила эффекта за ранг; сумма рангов имеет приоритет над конфигурационным fallback.
3. Любое числовое значение проверяется на `isFinite`; NaN/Infinity и отрицательные значения не должны усиливать персонажа.
4. Каждый handler читает эффект из одного агрегата, не пересчитывает таланты самостоятельно и не создаёт второй параллельный источник правды.
5. Весь дополнительный урон проходит через общий боевой контур: friendly-fire / `canHit`, cap одного удара, burst window, DoT cap, подавление рекурсивных proc-ов и корректная атрибуция источника.
6. Процентные бонусы одной категории складываются, если это явно указано ниже; независимые мультипликаторы перемножаются только там, где это осознанная механика. Не допускается случайное двойное применение одного бонуса в событии и при создании сущности.

### 2.2 Ранговая кривая

Для линейного пассивного эффекта базовая модель:
`bonus(rank) = min(rank × perRank, hardCap)`.

Для вероятностного эффекта:
`chance(rank) = min(rank × chancePerRank, chanceCap)`.

Для эффектов с длительностью/перезарядкой:
`duration(rank) = min(baseDuration + rank × durationPerRank, durationCap)`;  
`cooldown(rank) = max(minCooldown, baseCooldown × (1 - reductionPct(rank)))`.

Это **форма формул**, а не утверждение о финальных числах. Конкретные коэффициенты ниже — безопасные стартовые гипотезы для симуляции, а не принятый live-баланс. Если описание существующего узла задаёт другие значения, до балансировки сохраняем его игровое намерение, но проверяем его в модели.

### 2.3 Глобальные предохранители

- Никакой talent-proc не может рекурсивно запускать сам себя или цепочку других талантов без явного разрешения.
- Proc chance: 0–100%; один триггер на одно исходное событие.
- Дополнительный урон от таланта считается частью исходного удара для anti-burst контроля либо получает отдельный общий лимит; нельзя обходить `max-single-hit-pct` и `burst-window-pct`.
- Пет не может атаковать цель, которую владелец не может легально атаковать.
- Питомец не должен получать бесконечные/самоподдерживающиеся лечения и баффы.
- Атака по союзнику, декоративной сущности, armor stand, неподходящей цели или уже умершей сущности не должна запускать proc.
- Все временные состояния имеют expiry и очищаются при смерти, смене мира, выходе, despawn и выключении плагина.
- Нельзя включать talent node, если в текущей сборке нет runtime consumer и интеграционного теста.

## 3. Матрица аудита всех 20 узлов

Обозначения: **PR48/49** — обработчик уже добавлен в соответствующей ветке; **нет** — механизм ещё не подтверждён; **частично** — есть код, но нужна проверка полного event pipeline и edge cases.

| # | Узел / дерево | Заявленный эффект | Реальный механизм и текущее состояние | Проект формулы / ограничения | Решение |
|---:|---|---|---|---|---|
| 1 | `as_seal_fate` / Assassination | Критический удар даёт комбо-поинт с шансом | Нужен полноценный ресурс combo points: накопление на владельце, cap, расход конкретными Rogue-способностями, UI/restore/cleanup. В проверенном контуре такой общий ресурсный контракт не подтверждён. Нельзя заменить обычной яростью/энергией. | `chance = min(rank × 0.30, 0.90)`; +1 point за успешный крит, максимум 5 points; не более одного point за один hit; combo points принадлежат конкретной цели или должны иметь чёткое правило переноса. Таймаут вне боя 10–15 с — выбрать после проверки Rogue kit. | **P0: сначала спроектировать ресурс и потребителей**, затем proc. Узел выключен. |
| 2 | `bm_wild_bond` / Beastmaster | Пет повторяет часть урона хозяина | PR #49: handler повторяет 10% рассчитанного урона через pet; есть защита рекурсии и `canHit`. Нужно проверить, не обходит ли повторный урон общий cap/атрибуцию/сопротивления и корректно ли работает при projectile/AoE/DoT. | `petCopy = min(ownerFinalDamage × 0.10 × rank, perEventCap)`; один proc на исходное damage event; не копировать отражение, собственный pet damage, DoT ticks и повторный proc. Дополнительный урон входит в burst budget владельца. | **PR49 partial**. Нужны event matrix + интеграционные тесты и лимит. |
| 3 | `bm_bestial_discipline` / Beastmaster | Скорость атаки питомца | Нет подтверждённого отдельного pet attack-speed consumer. `MOVEMENT_SPEED` в PetService — скорость передвижения и не эквивалентна частоте ударов. | Предпочтительно контролировать период между разрешёнными pet attack events: `interval = max(minInterval, baseInterval × (1 - min(rank × 0.04, 0.16)))`. Не ускорять AI tick напрямую; учитывать серверный tick rate и target reacquisition. | **P0: нужен собственный attack scheduler / verified attack event**. Не использовать movement speed. |
| 4 | `bm_ferocious_inspiration` / Beastmaster | Крит питомца даёт групповой бонус урона | Нет подтверждённого стабильного party/ally contract для определения получателей и их боевого урона. Нельзя считать всех игроков вокруг союзниками. | Proc на pet crit; радиус/группа определяются существующей faction/party API; `allyDamageBonus = min(rank × 1%, 5%)`, длительность 4 с, refresh без stack; владелец включён один раз; исключить PvP-союзников/врагов по политике friendly fire. | **P0: сначала определить источник party/faction truth**. До этого узел выключен. |
| 5 | `bm_cobra_reflexes` / Beastmaster | Пет уклоняется от атаки | PR #49: входящий урон pet dodge через `pet_dodge_pct`. Проверить приоритет event cancellation, projectile, AoE, environmental damage и логирование фактического dodge. | `dodgeChance = min(rank × configuredChance, 20%)`; срабатывает только на direct hostile damage, не на void/fall/fire/DoT/environmental; один roll на один hit; dodge отменяет урон полностью и не запускает damage procs. | **PR49 implemented, needs integration review**. |
| 6 | `bm_spirit_bond` / Beastmaster | Лечение владельца лечит активного пета | PR #49: доля healing event передаётся активному pet. Проверить, что берётся фактически восстановленное HP, а не requested heal; не дублируется при custom heal + Bukkit regain event. | `petHeal = actualOwnerHeal × min(rank × 0.25, 0.50)`; ограничить missing pet HP; не запускать обратный heal владельца; игнорировать absorption/overheal. | **PR49 implemented, actual-heal semantics must be verified**. |
| 7 | `bm_go_for_the_throat` / Beastmaster | Крит питомца усиливается | PR #49: pet crit multiplier 1.5x → 2.0x через proc. Проверить единый порядок с pet physical bonus и общий damage cap. | Базовый pet crit = 1.5x; talent добавляет до +0.5x по рангу, hard cap 2.0x; proc/roll не должен выполняться дважды на одном ударе. | **PR49 implemented**. Проверить математику и распределение crit в симуляторе. |
| 8 | `bm_primal_fury` / Beastmaster | Физический урон питомца | PR #49: `pet_phys_dmg_pct` применяется при попадании, а не только на summon. Проверить, что pet damage и обычный pet damage bonus не умножаются дважды. | `petDamage = basePetDamage × (1 + min(rank × 0.05, 0.20))`; сначала базовая сила/статы, затем единый суммарный pet bonus, затем crit, после чего общие mitigation/caps. | **PR49 implemented**. Нужны regression tests на уже призванного питомца. |
| 9 | `bm_frenzy` / Beastmaster | Крит питомца временно ускоряет его атаки | Нет подтверждённого attack-speed hook; не реализовывать через movement speed или potion Haste без проверки его влияния на AI сущности. | На pet crit: шанс до 100% (проверить исходную декларацию), buff attack interval -10% за rank, max -30%, 3 с; refresh duration, не stack; внутренний ICD 1 с для защиты от цепочки критов. | **P0: зависит от #3**. Реализовать после attack scheduler. |
| 10 | `bm_pack_leader` / Beastmaster | Критический шанс питомца | PR #49: rank-scaled `pet_crit_pct`. Требуется проверить, что crit roll встроен ровно в один pet damage path, а не только в один тип pet entity. | `petCritChance = min(rank × 2%, 20%)`; единый RNG на финальном pet hit; crit multiplier по базовому контракту 1.5x, усиление — отдельный talent. | **PR49 implemented**. Нужен coverage всех pet archetypes и damage sources. |
| 11 | `mm_eagle_eye` / Marksmanship | Дальний крит | В дереве объявлен `crit_ranged_pct`; общий melee crit существует, но отдельный ranged crit path не подтверждён. Нужно определить, как идентифицировать стрелы игрока, ability projectile и кастомный ranged hit, не затрагивая melee. | `rangedCritChance = min(baseRangedCrit + rank × 1%, hardCap)`; сначала проверить базовый crit pipeline и не складывать один бонус одновременно в общую и ranged-ветку. Кап итогового шанса — 35% как стартовый тестовый предел, не live-утверждение. | **P0: построить единый ranged attribution/crit pipeline**. |
| 12 | `mm_sniper_training` / Marksmanship | Дополнительный дальний крит | Тот же отсутствующий ranged pipeline; узел имеет тот же тип эффекта, что и Eagle Eye. Не должно быть двух разных реализаций одного crit stat. | Общий агрегат: `rangedCritPct = eagleEyeRank × 1% + sniperRank × declaredPerRank`; общий cap; если это условный бонус (дистанция/неподвижность), условие должно быть явно задано и протестировано, а не выведено из названия. | **P0: согласовать семантику узла с его описанием**; один consumer для обоих узлов. |
| 13 | `mm_piercing_shots` / Marksmanship | Пробитие физической защиты только у aimed_shot | Декларация `kit_pen_phys` с целью `aimed_shot`; агрегат ability-specific penetration не подтверждён как готовый consumer в общем damage pipeline. | `effectiveArmor = armor × (1 - min(rank × 0.10, 0.30))` только для source abilityId=aimed_shot; не снижать общий armor target; применить до mitigation, после определения physical damage; penetration не может сделать armor отрицательной. | **PRIORITY P1: добавить abilityId/source context в damage request**, затем handler. |
| 14 | `mm_rapid_fire` / Marksmanship | Дополнительные стрелы для arrow_fan | PR #48: карта `kit_extra_arrows` и потребитель для `arrow_fan` добавлены. Проверить целое округление, cap и суммарное число projectiles; не увеличивать damage каждой стрелы без явного дизайна. | `extraArrows = floor(rank × declaredExtra)`; максимум +2 к исходным 3 (5 всего); общий урон залпа нормировать: либо сохранять damage per arrow и ограничить total volley, либо делить исходный бюджет урона на число стрел. Выбрать один контракт до включения. | **PR48 wired, node remains disabled**. Balance decision required. |
| 15 | `sv_trap_mastery` / Survival | Урон ловушек | `trap_dmg_pct` задекларирован, но реальный trap registry / damage attribution consumer не подтверждён. Нужно найти все источники ловушек и единый источник истины для владельца/цели. | `trapDamage = baseTrapDamage × (1 + min(rank × 0.05, 0.25))`; применяется только к урону от зарегистрированной ловушки владельца; не усиливает чужие traps, environmental damage или повторный proc. | **P0: инвентаризация trap ability implementations**. |
| 16 | `sv_resourcefulness` / Survival | Сокращение перезарядки ловушек | `cd_trap_pct` агрегируется как cooldown effect, но не подтверждён consumer для всех trap abilities. | `cooldown = max(baseCooldown × 0.70, baseCooldown × (1 - min(rank × 0.05, 0.30)))`; применяется при постановке cooldown, не ретроактивно; floor 70% base; не затрагивать остальные способности. | **P1: после единого TrapCooldown API**. |
| 17 | `ol_dual_wield_specialization` / Outlaw | Урон атак off-hand | Нужна достоверная классификация offhand-hit. Ванильный event может не предоставлять надёжный hand/source для кастомного pipeline; нельзя бонусить все удары игрока. | `offhandDamage = offhandBase × (1 + min(rank × 0.05, 0.20))`; только hit, подтверждённый как offhand; main-hand, projectile, ability и pet исключить; общий damage cap остаётся. | **P0: сначала weapon-hand attribution contract**. |
| 18 | `fury_unstoppable_force` / Fury | Дополнительные удары Rampage | PR #48: `kit_extra_hits` для `rampage` wired. Проверить, что дополнительные удары не повторяют стоимость ресурса, cooldown, cast, CC и on-hit proc полностью без ограничений. | 3 базовых удара + до 2 дополнительных; total hit count cap 5; общий damage budget способности распределять по числу ударов (если intended DPS не должен расти линейно); on-hit procs ограничить одним roll на cast или отдельным proc budget. | **PR48 wired, node remains disabled**. Требуется damage-budget decision. |
| 19 | `guard_spell_ward` / Guardian | Усиление магического сопротивления для Balder Skin | `kit_resist_magic` задекларирован как ability-specific эффект; нужно проверить точный момент применения resistance, stacking с class/spec/global resist caps и PvP caps. | `balderMagicResistBonus = min(rank × declaredPct, 15 percentage points)`; итоговый резист проходит существующие `resist.cap` и `resist.pvp-cap`; не добавлять процент к уже capped value после cap. | **P1: подключить к единому ResistService query с ability context**. |
| 20 | `su_master_of_subtlety` / Subtlety | Ловкость для Shadow Dance | PR #48: `kit_attr_agi` map/consumer wired. Важно: не удваивать базовый +30 AGI Shadow Dance и не добавлять эффект на каст и каждый тик одновременно. | `bonusAgi = rank × declaredAgi`; применяется ровно один раз к атрибутному снапшоту/формуле способности на время buff; базовые +30 AGI учитываются отдельно; cap/длительность наследуются от Shadow Dance. | **PR48 wired, node remains disabled**. Нужен тест композиции базового эффекта и таланта. |

## 4. Обязательные общие механизмы, которых не хватает

### P0 — блокируют безопасную реализацию
1. **Damage source context.** Единый immutable context: attacker UUID, owner UUID (для pet), target, source type (melee/ranged/ability/pet/trap/DoT/reflection), abilityId, hand, projectile identity, original event id, PvP/PvE classification. Без него невозможно безопасно реализовать ranged crit, aimed_shot penetration, offhand, traps и pet copy.
2. **ComboPointResource.** Отдельный ресурс Rogue: generation, cap, spend, target binding/transfer policy, expiry, persistence policy, UI, cleanup. Не переиспользовать ResourceService, пока его контракт не расширен корректно.
3. **PetAttackController.** Единый измеримый attack event / interval control для всех pet types. Скорость движения и скорость атаки — разные параметры.
4. **Party/FactionResolver.** Авторитетный список союзников для group buff. Нельзя определять группу только радиусом или scoreboard name.
5. **TrapRegistry + TrapCooldown API.** Зарегистрированный owner/source, target eligibility, damage source, cooldown identity, despawn/cleanup.
6. **WeaponHand attribution.** Явное подтверждение main-hand/offhand для melee damage path.
7. **Ability-scoped stat query.** Единый query для abilityId-specific penetration, resistance и attribute bonuses, чтобы эффекты применялись в правильной фазе и только к указанной способности.

### P1 — нужны до включения соответствующих узлов
- единый ranged crit roll и ranged damage classification;
- общий damage budget для multi-hit/multi-projectile abilities;
- actual-heal value в едином healing event contract;
- единый порядок: base damage → stat/ability modifiers → crit → mitigation/penetration → global caps → final damage;
- централизованная политика proc recursion, proc ICD и event deduplication.

## 5. Порядок обработки урона — целевой контракт

Целевой pipeline для обычного боевого события:

1. Создать source context и проверить валидность attacker/owner/target.
2. Проверить PvP/PvE/friendly-fire и `canHit`.
3. Рассчитать базовый урон способности/оружия/пета.
4. Применить class/spec/talent modifiers, scoped строго по source/ability/hand.
5. Выполнить ровно один crit roll соответствующего типа (melee или ranged); затем применить crit multiplier.
6. Применить penetration и защиту цели в согласованном порядке.
7. Применить общие caps: single hit, burst window, DoT; дополнительные удары/стрелы должны быть включены в тот же budget.
8. Применить урон и сформировать immutable итог события.
9. Запустить разрешённые post-hit procs с dedupe и recursion guard.
10. Записать метрики Combat Lab: исходный урон, фактический урон, source, abilityId, crit, mitigation, talent contribution, seed/event id.

Это целевая схема для выравнивания текущих listeners/services, не утверждение, что весь текущий код уже следует ей.

## 6. Минимальный тестовый контракт для каждого узла

Для каждого node handler обязательны:
- ранг 0 отключает эффект;
- ранг 1 даёт ровно ожидаемую величину;
- максимальный ранг не превышает cap;
- NaN/Infinity/отрицательные конфигурационные значения безопасно нейтрализуются;
- другой abilityId/source/hand не получает бонус;
- PvP и PvE проходят правильные caps;
- proc не вызывает рекурсию и не срабатывает повторно на duplicate event;
- logout/death/world change/despawn очищает временное состояние;
- эффект действует на уже активного питомца/уже созданный projectile там, где это предусмотрено дизайном;
- reset талантов немедленно снимает эффект;
- сохранение/перезапуск не оставляют временные proc-баффы, если их persistence явно не предусмотрен.

### Матрица сценариев
- Уровни: 15 / 30 / 45 / 60.
- Ранги: 0 / 1 / промежуточный / максимум.
- Тип цели: игрок / обычный моб / босс / невалидная цель.
- Источник: melee / ranged / ability / pet / trap / DoT / reflection.
- Контекст: PvP / PvE / friendly-fire denied / no target / dead target.
- Временные состояния: normal / buff active / cooldown / expiry / logout / restart.

## 7. Порядок реализации

1. **Стабилизировать source context и damage pipeline.** Без него отдельные handlers будут повторно изобретать attribution и обходить caps.
2. **Закрыть pet subsystem:** сначала PetAttackController, затем bestial_discipline и frenzy; после этого ferocious_inspiration с подтверждённым Party/FactionResolver. Параллельно провести event-level review шести уже реализованных pet handlers.
3. **Ranged subsystem:** ranged attribution + crit; затем eagle_eye/sniper_training и aimed_shot penetration.
4. **Trap subsystem:** TrapRegistry + cooldown API; затем trap mastery/resourcefulness.
5. **Weapon hand:** offhand attribution, затем dual-wield bonus.
6. **Ability-specific effects:** довести multi-hit/projectile damage budget, magic resistance, agility. PR48 handlers не включать до прохождения соответствующих тестов.
7. **Rogue combo points:** отдельный ресурс и реальные consumers, затем Seal Fate.
8. После закрытия всех handlers — отдельный balance pass базовых способностей и рангов, Combat Lab statistical report и Paper 26.2 runtime smoke.

## 8. Gate включения узла

Узел разрешается убрать из `spec2.disabled-nodes` только если выполнены ВСЕ условия:
- runtime consumer есть и вызывается настоящим игровым событием;
- unit + integration tests покрывают формулу, cap, source filtering и edge cases;
- Combat Lab report сравнен с baseline для уровней/рангов/PvP/PvE;
- аномалии имеют воспроизводимый seed и диагностическую причину;
- Paper 26.2 + Java 25 smoke пройден;
- документированы финальные per-rank values, cap, ICD, длительность и stacking;
- review подтверждает отсутствие обхода friendly-fire, damage caps и proc recursion.

**Ни один узел не включается этим документом.** Все предложенные числовые пределы являются стартовыми гипотезами для моделирования; окончательные значения принимаются только после результатов симуляции и runtime playtest.

## 9. Исходные технические опоры

- `src/main/java/dev/raskol/classes/spec/service/Spec2Service.java` — агрегат талантов, proc chance/amp и ability-scoped maps.
- `src/main/java/dev/raskol/classes/combat/ProcService.java` — существующий контракт proc chance/value2 и часть боевых proc consumers.
- `src/main/java/dev/raskol/classes/pet/PetService.java` — pet lifecycle и PR #49 pet handlers.
- `src/main/java/dev/raskol/classes/pet/PetTalentMath.java` — ограниченная математика pet chance/damage.
- `src/main/java/dev/raskol/classes/ability/TreeAbilities.java` — базовые и rank-aware параметры способностей.
- `src/main/resources/config.yml` — глобальные damage/burst/DoT caps и disabled node list.
- `docs/testing/COMBAT_SIMULATION_AND_SMOKE_QA.md` — целевая методика Combat Lab и Paper runtime smoke.

---
**Итог:** аудит фиксирует 20 узлов по одному контракту, отделяет уже добавленный код от отсутствующих игровых механизмов и задаёт безопасный порядок реализации. Следующий кодовый этап — не включение талантов, а общий source context/damage attribution и pet attack controller.

## 10. Обновление статуса после PR #58 (2026-11-23)

Этот раздел уточняет матрицу выше по фактическому коду PR #58. Он имеет приоритет над прежними пометками «не подтверждено» для перечисленных ниже consumers, но не снимает gate включения талантов.

### Новые подтверждённые consumers

- **Общий source context — частично закрыт.** `AbilityRegistry` задаёт вложенно-безопасный ability ID на время синхронного каста; `CombatService` принимает явный `DamageSourceContext`, восстанавливает вложенный контекст вокруг Bukkit damage event и отклоняет контекст с чужим target UUID. Это не полный контракт projectile identity / weapon hand / vanilla event ID из раздела 4.
- **`mm_eagle_eye` и `mm_sniper_training` — ranged crit consumer добавлен.** Есть отдельные `attributes.crit.ranged-base`, `ranged-per-agi`, `ranged-cap`, item stat `crit-ranged`, общий talent aggregate `crit_ranged_pct`; ванильные projectile hits и явно классифицированные ranged ability IDs используют этот шанс. Базовая формула сейчас использует AGI; коэффициенты по умолчанию повторяют прежние физические crit defaults и должны быть откалиброваны до live-включения.
- **`mm_piercing_shots` — ability-scoped penetration consumer добавлен.** Бонус действует только на физический mitigation path `aimed_shot`; он не является общим снижением защиты цели.
- **`guard_spell_ward` — consumer добавлен.** Магическая часть временного модификатора `balder_skin` получает rank bonus; caps и срок действия остаются в `ResistService`.
- **`sv_trap_mastery` и `sv_resourcefulness` — consumers добавлены для `explosive_trap`.** Первый увеличивает урон, второй уменьшает cooldown с жёстким пределом 90%. Универсальный TrapRegistry всё ещё отсутствует.
- **PR #48 consumers перепроверены:** `mm_rapid_fire` → `arrow_fan`, `fury_unstoppable_force` → `rampage`, `su_master_of_subtlety` → `shadow_dance` имеют runtime consumers, но остаются выключенными до решений по damage budget и интеграционных тестов.

### Уточнения по питомцам

- Proc-эффекты `proc_pet_copy`, `proc_pet_heal`, `proc_pet_crit_bonus` хранятся долями 0..1; перед процентным roll они теперь конвертируются в 0..100. Прямые процентные статы `pet_dodge_pct`, `pet_crit_pct` и `pet_phys_dmg_pct` остаются в процентных пунктах.
- Бонусы урона питомца применяются на `LOW` до стандартного mitigation listener. Базовый attack damage сущности не содержит повторно тот же `pet_dmg_pct`, поэтому снятие временного баффа не должно удваивать процент питомца.
- Vanilla damage attribution распознаёт активного питомца и его владельца для PvP/friendly-fire решения, но не добавляет владельцу обычный weapon-power / weapon-mastery bonus к атаке питомца.
- Это исправляет порядок и единицы для части PR #49, но не закрывает фактический heal amount в Spirit Bond, полное покрытие pet archetypes и интеграционную event matrix.

### Проверки текущего среза

- Java 25 Build: 832 tests, 0 failures, 0 errors.
- Combat CI Guard: success; Spotless/SpotBugs gate, package coverage, unarmed control report и equipment-aware report validation прошли.
- Equipment-aware matrix: 18 специализаций, 1,530 направленных сценариев × 1,200 повторов. Это по-прежнему auto-attack/equipment calibration, не ability/talent-aware baseline.

### Остаточные блокеры

- `PetAttackController` с измеримым attack interval для питомцев; затем `bm_bestial_discipline` и `bm_frenzy`.
- Стабильный `Party/FactionResolver` для `bm_ferocious_inspiration`.
- Отдельный Rogue combo-point resource и consumers для `as_seal_fate`.
- Надёжная weapon-hand attribution для `ol_dual_wield_specialization`.
- Actual-heal semantics и полная event matrix для уже добавленных pet handlers.
- Live Paper 26.2 runtime smoke и Combat Lab модель активных способностей/талантов.

**Все 20 talent nodes остаются disabled.** Добавление consumer не является разрешением на включение: каждый узел всё ещё должен пройти формульные, event-level, balance и Paper runtime gates.
