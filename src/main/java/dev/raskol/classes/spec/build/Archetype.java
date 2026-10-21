// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.build;

import java.util.Locale;

/**
 * 1.14.15: архетипы кураторских билдов raskolspec+.
 * На каждую спеку ровно два шаблона: PvP (дуэли, контроль, анти-бурст)
 * и PvE (фарм, AoE, выживаемость).
 */
public enum Archetype {
    PVP("PvP"),
    PVE("PvE");

    private final String displayName;

    Archetype(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Строка конфига → архетип; несовпадение → null. */
    public static Archetype fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        return switch (norm) {
            case "pvp", "pvp-duel", "duel" -> PVP;
            case "pve", "pve-farm", "farm", "raid" -> PVE;
            default -> null;
        };
    }

    /** Ключ в builds.yml: "pvp" / "pve". */
    public String yamlKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
