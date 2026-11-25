# Production Combat Runtime QA

© 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0

## Purpose

The Watchtower workflow starts the real Paper 26.2 server with the freshly built RaskolClasses artifact. This document defines the first combat event probe executed only in that isolated CI server.

The QA helper under `tools/paper-qa` is compiled and installed by the workflow as a temporary dependent plugin. It is not packaged into or published with RaskolClasses.

## Suppression scope probe

The helper performs two synchronous Paper damage calls against isolated, non-player living entities:

1. A damage call wrapped in `CombatService.beginSuppress()/endSuppress()`.
2. A normal damage call with no suppression scope.

A MONITOR listener registered by the dependent QA plugin observes both events after RaskolClasses has registered its listeners.

Pass conditions:

- The suppressed marker remains visible at MONITOR after the RaskolClasses NORMAL and HIGHEST listeners have both processed the same event.
- The suppressed event is not cancelled by the RaskolClasses suppression/CC guard path.
- The ordinary event does not observe a stale suppression marker after the first scope exits.
- The probe emits `[RASKOL-COMBAT-QA] SUPPRESS_SCOPE_PASS`.

The test deliberately uses a non-player source: CCGuard checks the suppression context before its Player-only attacker gate, so this still verifies the multi-listener marker lifecycle without requiring a connected client.

## Current scope and limitations

This probe validates suppression visibility and scope cleanup on the real Paper event bus. It does not yet validate STUN/FEAR behavior for a real Player attacker, damage formulas, burst-cap rollback, or headless-vs-Paper parity.

Burst-cap cancellation is tracked as the next independent runtime fixture because the current Watchtower server has no connected Player and both production burst-cap entry points are Player-target-specific.

## Safety

The probe runs only in the isolated CI runtime. It creates temporary zombies at the world spawn, removes them after the test, and does not modify gameplay configuration or persistent player data.


## Path A burst settlement matrix

A pinned Minecraft 26.2 offline protocol client joins the isolated Paper server so the QA plugin can generate real Player-target damage events. A dependent QA listener changes damage at HIGHEST, after the production NORMAL reservation has been created.

The four required scenarios are:
- external listener decreases damage; final ledger usage must match the reduced event amount;
- external listener increases damage; the complete overage must be represented in the ledger and diagnosed;
- nested event succeeds while the outer event is cancelled; only the outer reservation is rolled back;
- nested and outer events both succeed while the outer amount is modified; each handle settles independently.

The QA plugin reads the ledger after synchronous event dispatch and asserts the exact expected reservation totals. The runtime client and temporary QA plugin are not packaged into the production artifact.
