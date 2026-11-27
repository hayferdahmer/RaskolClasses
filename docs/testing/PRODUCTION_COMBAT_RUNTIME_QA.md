# Production Combat Runtime QA

© 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

## Purpose and isolation

The Watchtower workflow starts an isolated Paper 26.2 server with the freshly built RaskolClasses artifact. A temporary dependent QA plugin is compiled and installed only in this CI server; it is never packaged into or published with RaskolClasses.

A pinned headless client uses Minecraft Java 26.2 protocol 776 to complete the offline login/configuration handshake and create a real Bukkit Player. The plugin obtains the initialized CombatService from RaskolClasses and uses this same Player for the Path B and Path A combat matrices and the Warlock integration probe.

## Suppression scope probe

Before the combat checks, the helper performs two synchronous Paper damage calls against isolated, non-player living entities:

1. A damage call wrapped in `CombatService.beginSuppress()/endSuppress()`.
2. A normal damage call with no suppression scope.

A MONITOR listener observes both events after RaskolClasses has registered its listeners.

Pass conditions:

- The suppressed marker remains visible at MONITOR after the RaskolClasses NORMAL and HIGHEST listeners have processed the same event.
- The suppressed event is not cancelled by the RaskolClasses suppression/CC guard path.
- The ordinary event does not observe a stale suppression marker after the first scope exits.
- The probe emits `[RASKOL-COMBAT-QA] SUPPRESS_SCOPE_PASS`.

The suppression probe runs once per server start.

## Matrix order and shared lifecycle

The checks run sequentially against the same connected Player and share one CombatService instance and temporary in-memory configuration overrides:

1. Path B runs first.
2. After `BURST_CANCELLATION_MATRIX_PASS`, the Warlock Black Mass nested-context probe runs.
3. Only after `BLACK_MASS_NESTED_CONTEXT_PASS` does Path A start.
4. One final cleanup restores the original in-memory combat limits and Warlock configuration, restores the player's previous class-cache entry, removes spawned entities and disconnects the test Player. The same cleanup path is used after a failure at any stage.

The harness does not save temporary test limits or Warlock settings to the server configuration file.

## Path B — real damage-channel matrix

The helper runs hybrid Path B hits against the real Bukkit Player. The seven scenarios are:

1. Cancel the physical `ENTITY_ATTACK` event while allowing the magic event.
2. Cancel the magic event while allowing the physical event.
3. Cancel both channel events.
4. During the outer physical event, synchronously execute a nested hybrid hit, then cancel only the outer physical event.
5. Increase the physical Paper event damage and verify that the shared reservation expands to the actual applied defensive-pool delta.
6. Reduce the physical Paper event damage and verify that the unused reservation is released.
7. Give the Player 100 absorption points, apply a physical hit that is fully absorbed, and verify that the reservation equals the consumed absorption while health remains unchanged.

Before every Path B and Path A scenario, the harness explicitly clears only the connected test Player's previous burst-ledger entries. This keeps each assertion independent while the 60-second test window remains long enough for reservation settlement; neither matrix relies on time-based expiry to isolate scenarios. Nested reservations created within one scenario are preserved until that scenario's assertions finish.

For each scenario the harness checks:

- The expected physical and magic Paper events were observed.
- The intended event(s), and only those events, were cancelled where cancellation is part of the scenario.
- For increase/reduction scenarios, the final physical event damage differs from its initial value in the expected direction.
- The Player's actual defensive-pool delta (health + absorption) is measured after the synchronous Path B call returns.
- The remaining burst ledger amount equals the actual applied defensive-pool delta converted to formula units.
- The absorption-only scenario proves that fully absorbed damage is attributed even when the Player's health does not change.
- The nested scenario preserves the nested hit's reservation when the outer event is cancelled.

The nested scenario specifically exercises `DamageAttribution`: a nested call's defensive-pool delta must not be counted again as part of its parent call.

The final markers are `[RASKOL-COMBAT-QA] PATH_B_SETTLEMENT_MATRIX_PASS` and `[RASKOL-COMBAT-QA] BURST_CANCELLATION_MATRIX_PASS`. Any failed assertion emits `BURST_CANCELLATION_MATRIX_FAIL`.

## Warlock Black Mass — nested source-context probe

After Path B passes, the helper temporarily resolves the connected test Player as WARLOCK, enables Black Mass and sets recoil to zero in memory. It performs a real hybrid-independent damage call that synchronously triggers a nested CombatService hit against the same living target.

The probe verifies:

- Exactly two real `EntityDamageByEntityEvent` instances are observed.
- Both events retain the Warlock player's ability-source UUID, including after the nested call returns.
- The expected lifesteal is calculated from both final MONITOR event damage contributions.
- The player's actual health increase matches the expected lifesteal within the dedicated `0.001` health-point tolerance.
- CombatService reports applied damage and the probe emits `[RASKOL-COMBAT-QA] BLACK_MASS_NESTED_CONTEXT_PASS`.

The original class-cache entry and Warlock settings are restored by the shared cleanup path.

## Path A — event-settlement matrix

After Path B and the Warlock probe pass, the helper dispatches four synthetic Player-target `EntityDamageEvent` instances synchronously through Paper's PluginManager. This exercises the production NORMAL/HIGHEST/MONITOR listener pipeline and the event-bound reservation settlement ledger. It does not rely on NMS applying damage to the Player's health.

A dependent QA listener changes or cancels the outer event at HIGHEST, after the production NORMAL reservation has been created.

The four scenarios are:

1. An external listener decreases damage; final ledger usage must match the reduced event amount.
2. An external listener increases damage; the complete overage must be represented in the ledger and diagnosed.
3. A nested event succeeds while the outer event is cancelled; only the outer reservation is rolled back.
4. Nested and outer events both succeed while the outer amount is modified; each event's reservation settles independently.

The helper asserts each scenario's event identity, cancellation state, final damage and exact expected reservation total. The increased-damage case must emit the required burst overage diagnostic.

The final marker is `[RASKOL-COMBAT-QA] PATH_A_SETTLEMENT_MATRIX_PASS`. Any failed assertion emits `PATH_A_SETTLEMENT_MATRIX_FAIL`.

## Workflow acceptance criteria

The Watchtower job accepts the runtime only when all of the following are present in the same server log:

- `SUPPRESS_SCOPE_PASS`
- Seven `BURST_SCENARIO_PASS` records, `PATH_B_SETTLEMENT_MATRIX_PASS` and `BURST_CANCELLATION_MATRIX_PASS`
- `BLACK_MASS_NESTED_CONTEXT_PASS`
- Four `PATH_A_SETTLEMENT_SCENARIO_PASS` records and `PATH_A_SETTLEMENT_MATRIX_PASS`
- The Path A increased-damage overage diagnostic

The isolated runtime overrides only three in-memory combat settings for the duration of the QA run: `combat.burst-window-pct` and `combat.max-single-hit-pct` are raised to `1000.0`, and `combat.burst-window-seconds` is raised to `60.0`. The longer test-only window prevents a synchronous Paper event round-trip from expiring its reservation before settlement; it does not alter the production window or any damage calculation. The harness captures and restores all three original values during its shared cleanup path. No production configuration file or balance value is changed.

### Numerical tolerances

The runtime harness keeps independent comparison tolerances for different measurements:

- Burst reservation versus applied damage in formula units: absolute tolerance `0.05` (`BURST_RESERVATION_TOLERANCE`). This preserves the existing reservation comparison threshold and does not change combat calculations.
- Black Mass lifesteal versus the sum of the two real MONITOR event contributions in player health points: absolute tolerance `0.001` (`BLACK_MASS_HEAL_TOLERANCE`). This tighter threshold detects meaningful attribution/healing regressions while allowing minor Bukkit health-value rounding.

These tolerances apply only to QA assertions; no production formula, passive percentage, or combat balance value is modified.

## Safety and limitations

- The helper and headless client run only in CI.
- The spawned zombies are removed during cleanup.
- The test Player is disconnected after all checks or after a failure.
- The QA plugin and protocol client are never included in the production artifact.
- Path B validates real Paper damage calls, defensive-pool deltas (health + absorption) and burst settlement. Path A validates event-listener settlement. The Black Mass probe validates nested ability-source attribution and lifesteal. Together they do not claim full formula correctness, PvP balance, or full production combat parity.
- This is an isolated GitHub Actions Paper 26.2 runtime, not a test performed on the production RASKOL server.
