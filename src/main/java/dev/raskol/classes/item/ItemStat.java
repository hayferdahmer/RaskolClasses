package dev.raskol.classes.item;

import java.util.Locale;

/** Stats accepted by 1.15.0. Dodge, parry, block and lifesteal are deliberately unsupported. */
public enum ItemStat {
    ATTACK_DAMAGE("attack-damage", 0, 500), STR("str", 0, 200), AGI("agi", 0, 200), INT("int", 0, 200),
    WEAPON_POWER("weapon-power", 0, 1000), SPELL_POWER("spell-power", 0, 1000), HEAL_POWER("heal-power", 0, 1000),
    CRIT_MELEE("crit-melee", 0, 50), CRIT_SPELL("crit-spell", 0, 50), CRIT_DAMAGE("crit-damage", 0, 100),
    ATTACK_SPEED("attack-speed", 0, 50), PEN_PHYS_PCT("pen-phys-pct", 0, 40), PEN_MAGIC_PCT("pen-magic-pct", 0, 40),
    PEN_FIRE_PCT("pen-fire-pct", 0, 40), PEN_FROST_PCT("pen-frost-pct", 0, 40), PEN_NATURE_PCT("pen-nature-pct", 0, 40),
    PEN_SHADOW_PCT("pen-shadow-pct", 0, 40), PEN_HOLY_PCT("pen-holy-pct", 0, 40), PEN_ARCANE_PCT("pen-arcane-pct", 0, 40),
    PEN_PHYS_FLAT("pen-phys-flat", 0, 200), PEN_MAGIC_FLAT("pen-magic-flat", 0, 200),
    PHYS_RESIST("phys-resist", 0, 60), MAGIC_RESIST("magic-resist", 0, 60), RESIST_FIRE("resist-fire", 0, 60),
    RESIST_FROST("resist-frost", 0, 60), RESIST_NATURE("resist-nature", 0, 60), RESIST_SHADOW("resist-shadow", 0, 60),
    RESIST_HOLY("resist-holy", 0, 60), RESIST_ARCANE("resist-arcane", 0, 60), HP_BONUS("hp-bonus", 0, 2000),
    MAX_MANA("max-mana", 0, 200), MAX_RAGE("max-rage", 0, 200), MAX_ENERGY("max-energy", 0, 200),
    RESOURCE_REGEN("resource-regen", 0, 20), COOLDOWN_REDUCTION("cooldown-reduction", 0, 40),
    CC_RESIST("cc-resist", 0, 50), CC_POWER("cc-power", 0, 50), MOVEMENT_SPEED("movement-speed", 0, 30), REFLECT("reflect", 0, 25);

    private final String id;
    private final double min;
    private final double max;
    ItemStat(String id, double min, double max) { this.id = id; this.min = min; this.max = max; }
    public String id() { return id; }
    public double min() { return min; }
    public double max() { return max; }
    public static ItemStat fromId(String value) {
        if (value == null) return null;
        for (ItemStat stat : values()) if (stat.id.equals(value.toLowerCase(Locale.ROOT))) return stat;
        return null;
    }
    public double clamp(double value) { return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min; }
}
