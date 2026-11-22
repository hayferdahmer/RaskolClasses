# A-03-003.41 — Residual periodic scheduler ownership audit

## Scope

A source-backed scan of the lifecycle branch covered Bukkit scheduler entry points, `BukkitRunnable`, `CompletableFuture` completion handoffs, executor creation, and explicit thread creation. Candidate files were fetched from `refactor/a03-foundation-lifecycle` before classification; default-branch search results were used only as discovery hints.

## Finding

### P2 — ElementalResistService discarded a repeating BukkitTask handle

Before this slice, `ElementalResistService` scheduled `purgeExpired` from its constructor with a 600-tick repeating task and discarded the returned `BukkitTask`. The service had no explicit start/stop boundary, and construction of `CombatService` therefore created a background lifecycle side effect before the runtime task phase.

This was not an unbounded accumulation: one repeating task was created per plugin instance and Paper cancels plugin tasks on disable. The issue was missing explicit ownership and ordering in the plugin's own lifecycle.

### Resolution in A-03-003.41

- Removed scheduler access and the `RaskolClasses` dependency from the `ElementalResistService` constructor.
- Exposed `PURGE_INTERVAL_TICKS = 600` as the service's cadence contract.
- Registered a dedicated `PurgeRuntimeOwner` in `RaskolClasses.startRuntimeTasks()`, backed by the existing `PaperPurgeTaskScheduler` and `combat.elemental()::purgeExpired`.
- Closed that owner in `shutdownRuntime()` as an independent shutdown step.
- Reused the existing lifecycle owner and Paper adapter; no new scheduler abstraction was introduced.
- Preserved the original 600-tick initial delay and repeat interval, purge algorithm, and combat behavior.

## Existing ownership confirmed

The scan confirmed explicit owners or returned task handles for the following repeating/runtime paths:

- Global purge and autosave.
- Pet tick and orphan scan through `PetRuntimeOwner`.
- Resource tick, ScrollCooldown, ManaSoaked, DoT, CC, HP Attribute, HP Bar, HUD and BossBar.
- CrownFlavor and PlaceholderAPI runtime.
- Installation sweep plus Pentagram/Frost Rune handler tasks.
- FX aura, ambient and charged-projectile tasks.
- Cooldown notification tasks and Warlock channel tasks.
- Foliant lock flush and transition callbacks.
- RIPS scheduled executor through `RuntimeIntegrityRuntimeOwner`.

## Bounded one-shot classification

The following one-shot paths are finite and do not create unbounded repeating work. They remain plugin-scoped and are not moved into a generic task registry in this slice:

- Ability impact, glow, projectile pickup, trap and delayed AoE callbacks in Hunter, Warrior, Priest, Mage and Rogue ability implementations.
- Avoidance stagger expiry callback; the quit handler removes the persistent modifier immediately.
- TrailListener projectile trails; each task self-cancels for invalid projectile/world and stops after at most 200 ticks.
- Startup pet orphan scan; its task handle is returned to and registered by `PetRuntimeOwner`.

Two bounded player-session references remain candidates for a narrower follow-up: Warrior taunt's delayed glow cleanup retains its caster Player, and Avoidance stagger cleanup retains the old Player/AttributeInstance pair. Neither is a repeating-task leak; session re-resolution can be handled separately without introducing a shared registry.

SelftestRunner's short-lived worker threads are daemon threads bounded by a latch and a finite fixed iteration count; they are selftest execution, not plugin runtime producers.

## Deferred boundary

A-04 remains separate. The async purchase/gift/revoke, salvage, subscription notification, and storage completion handoffs still require explicit decisions for:

- Whether a result belongs to the initiating session or the UUID across reconnect.
- Behavior when the player disconnects before completion.
- Behavior when scheduling the main-thread continuation is rejected during shutdown.
- Whether callbacks must be drained, discarded, or reported before storage shutdown.

UUID substitution alone is not sufficient to define those semantics.

## Validation boundary

The implementation reuses the already-tested `PurgeRuntimeOwner` and `PaperPurgeTaskScheduler` contracts. CI for the source implementation and Spotless-formatted HEAD was triggered; live Paper 26.2 runtime acceptance remains a separate requirement. No merge to `main` is part of this slice.
