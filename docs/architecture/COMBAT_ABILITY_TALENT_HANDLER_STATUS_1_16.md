# Статус runtime-обработчиков способностей и талантов 1.16.0

Документ фиксирует только фактически подключённые runtime-эффекты в ветке PR #58. Он не является разрешением включать таланты или утверждением о балансе.

## Подключено в текущем срезе

- **Ability ID context:** `AbilityRegistry` оборачивает синхронное выполнение каста в восстанавливаемый контекст ID способности. Вложенные вызовы и исключения должны восстанавливать предыдущий контекст.
- **Damage source context:** `CombatService` может принимать явный `DamageSourceContext`, а применяемое Bukkit-событие урона получает этот контекст на время синхронного вызова. Контекст, привязанный к другой цели, отклоняется.
- **Marksmanship — `mm_piercing_shots`:** `kit_pen_phys` применяется к физическому сопротивлению только для объявленной способности `aimed_shot`. Применение учитывает и player damage path, и CombatProfile цели.
- **Guard — `guard_spell_ward`:** `kit_resist_magic` добавляется к магической части временного защитного модификатора способности `balder_skin`. Срок действия и PvP/PvE caps остаются ответственностью `ResistService`.
- **Survival — `sv_trap_mastery`:** бонус увеличивает урон `explosive_trap` поверх существующего production multiplier.
- **Survival — `sv_resourcefulness`:** бонус уменьшает cooldown `explosive_trap`; снижение ограничено 90%.

## Регрессионные проверки

- `AbilityRegistryContextTest`: вложенный ability ID корректно восстанавливает внешний контекст.
- `DamageContextScopeTest`: проверяет вложенные ability/source scopes и очистку контекста после исключения.
- `Spec2KitEffectAggregationTest`: проверяет ранговую агрегацию penetration, magic ward, trap damage и trap cooldown.

## Что пока не реализовано

- Полная атрибуция vanilla projectile damage, конкретной руки оружия и offhand damage.
- Отдельные ranged critical chance consumers.
- Контроллер реальной частоты атак питомца, pet frenzy и корректный party/ally resolver для групповых баффов.
- Combo-point resource и его consumers для Rogue.
- Универсальный TrapRegistry; текущая обработка cooldown относится к единственной зарегистрированной ловушке `explosive_trap`.
- Полная интеграция активных способностей, талантов, питомцев, контроля, ресурсов и PvE в Combat Lab.

## Статус включения

Все 20 боевых talent nodes остаются **disabled**. До включения необходимы реализация оставшихся runtime consumers, проверка всех путей урона, полный набор тестов и live Paper 26.2 QA. Этот срез не формирует baseline баланса и не должен использоваться как доказательство PvP/PvE-равновесия.
