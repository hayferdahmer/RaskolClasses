# RaskolHUD Resource Pack Strategy

Status: architectural direction approved; implementation deferred to HUD-D.
Target platform: Minecraft client compatible with the RASKOL server release; exact pack format/version must be verified against the actual client protocol and Paper 26.2 deployment before release.

## 1. Product direction

RASKOL will have its own branded Resource Pack and its own build/generation pipeline. The goal is not to clone BetterHUD or depend on it at runtime. We will evaluate proven ideas and reusable patterns from established projects, then implement the capabilities that fit RASKOL's architecture, licensing, performance and UX requirements.

The pack remains optional (H-04). The HUD must remain usable when the pack is absent, declined, failed to download, or outdated.

## 2. What belongs where

### Plugin/runtime
- gameplay values and immutable HUD snapshots;
- widget layout and presentation state;
- selection/target state and notifications;
- capability detection and fallback selection;
- pack version/hash policy and server-side delivery configuration;
- no assumption that custom glyphs are available unless the client capability state allows them.

### Generated Resource Pack
- custom font providers and glyph mappings;
- HUD icons, decorative frames, textures and sprites;
- class/spec visual assets;
- optional visual effects and animation assets supported by the target client format;
- pack metadata and generated resource indexes.

The pack is a presentation asset bundle. It must not become the source of gameplay truth.

## 3. Generation pipeline

Prefer declarative source manifests and reproducible generation over hand-editing generated output.

Proposed pipeline:
1. Maintain human-authored source assets and manifests in a dedicated resource directory.
2. Validate identifiers, namespace ownership, glyph uniqueness, asset dimensions/formats, references and required fallbacks.
3. Generate the client resource directory structure and metadata for the exact supported Minecraft client version.
4. Build a deterministic ZIP artifact.
5. Calculate SHA-1 for Minecraft server pack delivery compatibility and a stronger internal SHA-256 digest for release provenance/integrity.
6. Produce a machine-readable asset manifest and build report.
7. Test pack acceptance, download, reload and degraded/declined states on a clean client.
8. Publish the pack artifact with the matching plugin release and immutable version/hash.

Generated files must be reproducible from committed source assets and tooling. Do not commit opaque generated output as the only source of truth.

## 4. Capability and fallback rules

- Baseline rendering works without the pack using standard Paper/Adventure-supported text and components.
- Enhanced rendering may use custom fonts, glyphs, icons and decorative textures only when the client has accepted the compatible pack.
- Missing/unknown glyphs must never hide critical values.
- HP, resources, XP, target identity/status and critical notifications always retain a readable fallback.
- Pack failure must not stop gameplay, snapshot capture, widgets or the plugin.
- Pack capability is presentation state, not character/profile data.

## 5. Engineering principles to evaluate from existing projects

The team may adopt proven approaches where they provide measurable value:
- declarative font/glyph definitions instead of hard-coded magic Unicode values;
- automatic resource-pack assembly and validation;
- reusable layout/widget definitions;
- separation of runtime renderer logic from static assets;
- versioned asset namespaces and generated manifests;
- explicit pack compatibility and fallback handling;
- preview tooling and automated validation.

No third-party project is a mandatory runtime dependency. Any code or assets reused must pass license review; prefer independent implementations and original RASKOL artwork.

## 6. Constraints

- Do not assume a custom pack can create an arbitrary client-side HUD canvas. Resource-pack capabilities are constrained by Minecraft's client rendering and UI surfaces.
- Do not promise WoW-equivalent screen composition from a resource pack alone. The plugin's Paper/Adventure output channels and the client's supported resource-pack features jointly define what is possible.
- No client mod is assumed.
- Do not make Scoreboard/BossBar ownership decisions implicitly through pack implementation.
- Exact delivery URL, prompt policy, force/optional behavior, supported client versions, update strategy and player preferences remain HUD-D/config decisions.

## 7. Acceptance criteria

- Reproducible generation from committed sources.
- Deterministic artifact with recorded version and digests.
- Validation fails on duplicate glyph mappings, missing assets, invalid references or missing required fallbacks.
- Pack-off and pack-declined modes retain all critical information.
- Compatible pack enables branded assets without changing gameplay semantics.
- Clean-client tests cover accepted, declined, failed download and outdated pack states.
- License provenance exists for every third-party asset or code component.
- No required client mod.

## 8. Decision record

The user approved the direction: build an original RASKOL resource-pack generation system, while studying and adopting proven techniques from mature projects where appropriate. BetterHUD is a reference for evaluating capabilities and workflow, not a product to copy or a required dependency.
