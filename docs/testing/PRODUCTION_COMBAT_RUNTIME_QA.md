# Production Combat Runtime QA

© 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

## Purpose

The Watchtower workflow starts the real Paper 26.2 server with the freshly built RaskolClasses artifact. A temporary dependent QA plugin is compiled and installed only in this isolated CI server. It is not packaged into or published with RaskolClasses.

## Suppression scope probe

The helper performs two synchronous Paper damage calls against isolated, non-player living entities:

1. A damage call wrapped in `CombatService.beginSuppress()/endSuppress()`.
2. A normal damage call with no suppression scope.

A MONITOR listener observes both events after RaskolClasses has registered its listeners.

Pass conditions:

- The suppressed marker remains visible at MONITOR after the RaskolClasses NORMAL and HIGHEST listeners have processed the same event.
- The suppressed event is not cancelled by the RaskolClasses suppression/CC guard path.
- The ordinary event does not observe a stale suppression marker after the first scope exits.
- The probe emits `[RASKOL-COMBAT-QA] SUPPRESS_SCOPE_PASS`.

## Burst cancellation matrix

A headless Minecraft protocol client logs in to the isolated Paper server as a real Bukkit Player. The QA plugin obtains the initialized CombatService and runs hybrid Path B hits against that Player.

The four scenarios are:

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

The isolated runtime raises only the burst and single-hit limits in the test server's in-memory plugin configuration so the matrix tests reservation settlement rather than an intentionally restrictive cap. No production configuration or balance value is changed.

The final marker is `[RASKOL-COMBAT-QA] BURST_CANCELLATION_MATRIX_PASS`; any failed assertion emits `BURST_CANCELLATION_MATRIX_FAIL` and fails the Watchtower job.

## Safety and limitations

- The helper and headless client run only in CI.
- The bot is disconnected after the matrix; the spawned zombie is removed.
- The QA plugin is a temporary dependent plugin and is never included in the RaskolClasses artifact.
- This matrix validates the Path B event/health/ledger transaction on Paper 26.2. It does not claim full Path A/Path B combat parity, formula correctness, PvP balance, or the full production runtime matrix.
