# Production Combat Runtime QA

© 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

## Purpose and isolation

The Watchtower workflow starts an isolated Paper 26.2 server with the freshly built RaskolClasses artifact. A temporary dependent QA plugin is compiled and installed only in this CI server; it is never packaged into or published with RaskolClasses.

A pinned headless client uses Minecraft Java 26.2 protocol 776 to complete the offline login/configuration handshake and create a real Bukkit Player. The plugin obtains the initialized CombatService from RaskolClasses and uses this same Player for both combat matrices.

## Suppression scope probe

Before the combat matrices, the helper performs two synchronous Paper damage calls against isolated, non-player living entities:

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

The two combat matrices run sequentially against the same connected Player and share one CombatService instance and one temporary in-memory configuration override:

1. Path B runs first.
2. Only after `BURST_CANCELLATION_MATRIX_PASS` does Path A start.
3. One final cleanup restores the original in-memory combat limits, removes the spawned source mob and disconnects the test Player. The same cleanup path is used after a failure in either matrix.

The harness does not save the temporary test limits to the server configuration file.

## Path B — real damage-channel matrix

The helper runs hybrid Path B hits against the real Bukkit Player. The four scenarios are:

1. Cancel the physical `ENTITY_ATTACK` event while allowing the magic event.
2. Cancel the magic event while allowing the physical event.
3. Cancel both channel events.
4. During the outer physical event, synchronously execute a nested hybrid hit, then cancel only the outer physical event.

For each scenario the harness checks:

- The expected physical and magic Paper events were observed.
- The intended event(s), and only those events, were cancelled.
- The Player's actual health delta is measured after the synchronous Path B call returns.
- The remaining burst ledger amount equals the actual applied health delta converted to formula units.
- The nested scenario preserves the nested hit's reservation when the outer event is cancelled.

The nested scenario specifically exercises `DamageAttribution`: a nested call's health delta must not be counted again as part of its parent call.

The final marker is `[RASKOL-COMBAT-QA] BURST_CANCELLATION_MATRIX_PASS`. Any failed assertion emits `BURST_CANCELLATION_MATRIX_FAIL`.

## Path A — event-settlement matrix

After Path B passes, the helper dispatches four synthetic Player-target `EntityDamageEvent` instances synchronously through Paper's PluginManager. This exercises the production NORMAL/HIGHEST/MONITOR listener pipeline and the event-bound reservation settlement ledger. It does not rely on NMS applying damage to the Player's health.

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
- Four `BURST_SCENARIO_PASS` records and `BURST_CANCELLATION_MATRIX_PASS`
- Four `PATH_A_SETTLEMENT_SCENARIO_PASS` records and `PATH_A_SETTLEMENT_MATRIX_PASS`
- The Path A increased-damage overage diagnostic

The isolated runtime raises only the burst and single-hit limits in the test server's in-memory plugin configuration so the matrices test reservation settlement rather than an intentionally restrictive cap. No production configuration or balance value is changed.

## Safety and limitations

- The helper and headless client run only in CI.
- The spawned zombies are removed during cleanup.
- The test Player is disconnected after both matrices or after a failure.
- The QA plugin and protocol client are never included in the production artifact.
- Path B validates real Paper damage calls, health deltas and burst settlement. Path A validates event-listener settlement. Together they do not claim full formula correctness, PvP balance, or full production combat parity.
- This is an isolated GitHub Actions Paper 26.2 runtime, not a test performed on the production RASKOL server.
