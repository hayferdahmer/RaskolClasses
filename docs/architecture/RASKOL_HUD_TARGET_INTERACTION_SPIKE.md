# RaskolHUD — HUD-C.1.4 Paper Target Interaction API Spike

## Status

**API/documentation spike completed; no target-selection runtime code is implemented or wired.**

- Repository: `hayferdahmer/RaskolClasses`
- Branch: `refactor/a03-foundation-lifecycle`
- Platform: Paper 26.2 / Java 25
- Decision H-06: right-click an eligible entity (ПКМ) is the primary target-lock interaction.
- Parent: [RASKOL_HUD_ARCHITECTURE.md](RASKOL_HUD_ARCHITECTURE.md)
- Runtime ownership: [RASKOL_HUD_RUNTIME_OWNERSHIP.md](RASKOL_HUD_RUNTIME_OWNERSHIP.md)
- Data contracts: [RASKOL_HUD_DATA_CONTRACTS.md](RASKOL_HUD_DATA_CONTRACTS.md)

This spike selects the Paper event path and defines its safety boundary. It does not claim a controlled live-server test has been run. Event delivery with the exact deployed Paper build and third-party NPC plugins remains an integration-test requirement.

## 1. Paper API findings

### 1.1. Primary event

Use `org.bukkit.event.player.PlayerInteractAtEntityEvent` as the primary Paper interaction event for the target-selection adapter.

The Paper 26.2 Javadocs expose `PlayerInteractEntityEvent` as the base right-click event and mark it `@Obsolete`. Its API note says that the base event is no longer called without being a `PlayerInteractAtEntityEvent` and recommends listening to the latter, which carries the interaction position. `PlayerInteractAtEntityEvent` inherits the right-clicked entity, hand and cancellation methods from the base class and adds the clicked position.

References:
- Paper 26.2: [PlayerInteractEntityEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/player/PlayerInteractEntityEvent.html)
- Paper 26.2: [Player event package](https://jd.papermc.io/paper/26.2/org/bukkit/event/player/package-summary.html)
- Paper 26.1.2 API reference for the inherited `PlayerInteractAtEntityEvent` contract: [PlayerInteractAtEntityEvent](https://jd.papermc.io/paper/26.1.2/org/bukkit/event/player/PlayerInteractAtEntityEvent.html)

The direct API documentation lookup for the Paper 26.2 `PlayerInteractAtEntityEvent` page was not available during this spike; the class is present in the Paper 26.2 package and class indexes. The 26.1.2 page is used only to inspect the stable inherited method contract, not to claim runtime behavior for build 135.

### 1.2. Events not used as the primary path

- `PlayerInteractEvent` represents interaction with a block or air; it is not the entity-target event.
- Do not register independent target-selection flows for both `PlayerInteractEntityEvent` and `PlayerInteractAtEntityEvent`. The base event is marked obsolete for direct listening in Paper 26.2; using both as separate business inputs risks duplicate selection if event behavior changes.
- `PlayerArmorStandManipulateEvent` is a separate subclass of `PlayerInteractEntityEvent` for armor-stand equipment manipulation. Armor stands are not valid combat targets for the initial target policy and must be excluded. No armor-stand manipulation should be cancelled or repurposed as target selection.

## 2. Event adapter policy

The future Paper adapter should observe `PlayerInteractAtEntityEvent` with:

- `priority = EventPriority.MONITOR`;
- `ignoreCancelled = true`;
- main hand only: `event.getHand() == EquipmentSlot.HAND`;
- no call to `event.setCancelled(...)` and no modification of the event.

Why:
- A cancelled interaction is not an accepted target-selection gesture.
- `MONITOR` observes the interaction after ordinary plugin handlers have had an opportunity to cancel it.
- The adapter must not interfere with vanilla interaction, item use, NPC dialogue or other plugin behavior.
- Off-hand events are ignored to avoid duplicate selection from dual-hand interactions.

The adapter forwards only stable identifiers and source metadata to the targeting application boundary, for example actor UUID, clicked entity UUID and interaction hand. It must not store Bukkit `Player` or `Entity` references in long-lived target state.

## 3. Candidate classification and eligibility

The event confirms an interaction, not target eligibility. The targeting application owner remains responsible for the decision.

Initial candidate baseline:
- living entities may be considered;
- armor stands are explicitly rejected;
- non-living decorative/interactable entities (for example item frames) are rejected;
- self-targeting is rejected;
- the event adapter does not itself decide PvP/PvE legality.

Before publishing a new locked target, the application policy must validate:
1. actor session is active; actor is online, alive and not in spectator mode;
2. clicked entity is still valid, alive and targetable;
3. actor and target are in the same world;
4. distance is within the configured target-selection limit (revalidated even though vanilla interaction itself is range-limited);
5. class, permission, PvP/PvE, safe-zone and territory rules permit selection;
6. the entity is not an excluded decoration, protected utility entity or unsupported NPC.

### NPC compatibility seam

No mandatory Citizens or other NPC plugin dependency is introduced by this spike.

An optional NPC classification adapter must be able to distinguish a real player from a player-shaped NPC and a targetable custom NPC from a decorative/interactive NPC. Do not infer NPC status from entity type alone. If a known NPC provider reports an entity as non-targetable, reject it. Exact provider integrations and the policy for unknown NPC markers remain a separate integration decision.

Territory and combat restrictions should be queried through ports/adapters (for example a future RaskolZones policy adapter), not through a hard dependency from the targeting domain on a plugin implementation.

## 4. Ownership and state flow

```text
Paper PlayerInteractAtEntityEvent
        |
        v
Paper target-interaction adapter
  - MONITOR / ignoreCancelled
  - main hand only
  - no event cancellation
        |
        v
Targeting application command
  - actor/session validation
  - target classification
  - world/range/state checks
  - PvP/PvE/territory policy
        |
        v
Target selection owner
  - stores target UUID / stable target identity
  - publishes immutable TargetView
        |
        v
RaskolHUD snapshot provider
  - reads TargetView only
```

The HUD does not subscribe to the interaction event and does not own selection state. Moving the crosshair does not change the locked target. Existing quit cleanup remains responsible for removing the departing player's HUD session; the targeting owner must independently clear that player's target state.

A provider failure is `UNAVAILABLE`, not `NONE`. A successful query with no selected target is `NONE`.

## 5. Required tests before runtime activation

### Adapter-level tests
- accepted, non-cancelled main-hand event is forwarded once;
- off-hand event is ignored;
- cancelled event is ignored;
- handler never cancels or mutates the interaction;
- armor-stand manipulation does not produce a target-selection command;
- only the chosen Paper event path is registered.

### Application-level tests
- dead, offline or spectator actor is rejected;
- dead, invalid, unsupported or non-living target is rejected;
- armor stand and decorative entity are rejected;
- cross-world and out-of-range target are rejected;
- same-world eligible living target is accepted;
- PvP-disabled/safe-zone/territory restrictions are enforced;
- NPC adapter can reject dialogue/decorative NPCs and classify supported combat NPCs;
- target change is explicit; camera/crosshair movement does not retarget;
- clear, target invalidation, player quit and session end clean up state;
- provider failure remains `UNAVAILABLE`, not `NONE`.

### Controlled Paper integration test still required
Run on the exact deployed Paper 26.2 build with representative vanilla living entities and every NPC plugin actually used by RASKOL. Confirm event delivery, hand value, cancellation ordering and interaction coexistence. Javadocs establish the API contract but do not replace this runtime test.

## 6. Decision

**Selected event path:** `PlayerInteractAtEntityEvent`.

**Selection gesture:** non-cancelled main-hand right-click on an eligible entity.

**Event priority:** `MONITOR`, `ignoreCancelled = true`.

**Event mutation:** none.

**Target ownership:** separate targeting/application owner; RaskolHUD consumes immutable read-only `TargetView`.

**Implementation status:** design/API spike only. No event listener, target-selection command, target store, command UX or runtime wiring is added in HUD-C.1.4.
