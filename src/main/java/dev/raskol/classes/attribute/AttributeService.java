// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.attribute;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.hook.GearHook;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.9.3-r: единый источник правды по единицам HP (план B) + gear-HP из GearHook.
 * maxHp = формула(base+STR×perStr+level×perLevel+STR-main×level×bonus) + GearHook.hpBonus.
 */
public final class AttributeService {

    public static final double VANILLA_MAX_HEALTH_CAP = 1024.0;

    private static final Attribute MAX_HEALTH = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ATTRIBUTE)
            .get(NamespacedKey.minecraft("max_health"));

    public static Attribute maxHealthAttr() {
        return MAX_HEALTH;
    }

    public record Modifier(String source, double str, double agi, double intel, long expiresAt) {
        public boolean isPermanent() {
            return expiresAt == Long.MAX_VALUE;
        }
    }

    private static final class Cache {
        long tick = -1;
        double str;
        double agi;
        double intel;
    }

    private final RaskolClasses plugin;
    private final Map<UUID, List<Modifier>> modifiers = new ConcurrentHashMap<>();
    private final Map<UUID, Cache> cache = new ConcurrentHashMap<>();

    public AttributeService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    /* -------------------------------- значения -------------------------------- */

    public AttributeType mainOf(PlayerClass pc) {
        String cfg = plugin.getConfig().getString("attributes.classes." + pc.name() + ".main");
        AttributeType parsed = AttributeType.fromId(cfg);
        if (parsed != null) {
            return parsed;
        }
        return switch (pc) {
            case WARRIOR -> AttributeType.STR;
            case HUNTER, ROGUE -> AttributeType.AGI;
            case MAGE, PRIEST -> AttributeType.INT;
        };
    }

    private double baseOf(PlayerClass pc, AttributeType type) {
        double v = plugin.getConfig().getDouble(
                "attributes.classes." + pc.name() + ".base-" + type.name().toLowerCase(),
                defaultBase(pc, type));
        return Double.isFinite(v) && v >= 0 ? v : defaultBase(pc, type);
    }

    private double growthOf(PlayerClass pc, AttributeType type) {
        double v = plugin.getConfig().getDouble(
                "attributes.classes." + pc.name() + ".growth-" + type.name().toLowerCase(),
                defaultGrowth(pc, type));
        return Double.isFinite(v) && v >= 0 ? v : defaultGrowth(pc, type);
    }

    private static double defaultBase(PlayerClass pc, AttributeType type) {
        return switch (pc) {
            case WARRIOR -> type == AttributeType.STR ? 12 : type == AttributeType.AGI ? 6 : 4;
            case HUNTER -> type == AttributeType.STR ? 6 : type == AttributeType.AGI ? 12 : 4;
            case ROGUE -> type == AttributeType.STR ? 7 : type == AttributeType.AGI ? 11 : 4;
            case MAGE -> type == AttributeType.STR ? 4 : type == AttributeType.AGI ? 6 : 12;
            case PRIEST -> type == AttributeType.STR ? 5 : type == AttributeType.AGI ? 5 : 12;
        };
    }

    private static double defaultGrowth(PlayerClass pc, AttributeType type) {
        return switch (pc) {
            case WARRIOR -> type == AttributeType.STR ? 1.2 : type == AttributeType.AGI ? 0.5 : 0.3;
            case HUNTER -> type == AttributeType.STR ? 0.5 : type == AttributeType.AGI ? 1.2 : 0.3;
            case ROGUE -> type == AttributeType.STR ? 0.6 : type == AttributeType.AGI ? 1.1 : 0.3;
            case MAGE -> type == AttributeType.STR ? 0.3 : type == AttributeType.AGI ? 0.5 : 1.2;
            case PRIEST -> type == AttributeType.STR ? 0.4 : type == AttributeType.AGI ? 0.4 : 1.2;
        };
    }

    public double levelOf(UUID uuid, PlayerClass pc) {
        String source = plugin.getConfig().getString("attributes.level-source", "character");
        if ("vanilla".equalsIgnoreCase(source)) {
            Player player = Bukkit.getPlayer(uuid);
            return player != null ? Math.max(0, player.getLevel()) : 0;
        }
        if ("class-skill".equalsIgnoreCase(source)) {
            int level = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
            return level == SkillLevelProvider.NO_SKILL_SYSTEM ? 0 : Math.max(0, level);
        }
        return plugin.getCharacterLevels().characterLevel(uuid);
    }

    public double value(UUID uuid, AttributeType type) {
        Cache c = cacheOf(uuid);
        return switch (type) {
            case STR -> c.str;
            case AGI -> c.agi;
            case INT -> c.intel;
        };
    }

    private Cache cacheOf(UUID uuid) {
        Cache c = cache.computeIfAbsent(uuid, k -> new Cache());
        long tick = Bukkit.getCurrentTick();
        if (c.tick != tick) {
            recompute(uuid, c);
            c.tick = tick;
        }
        return c;
    }

    private void recompute(UUID uuid, Cache c) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        if (pc == null) {
            c.str = 0;
            c.agi = 0;
            c.intel = 0;
            return;
        }
        double level = levelOf(uuid, pc);
        double str = baseOf(pc, AttributeType.STR) + growthOf(pc, AttributeType.STR) * level;
        double agi = baseOf(pc, AttributeType.AGI) + growthOf(pc, AttributeType.AGI) * level;
        double intel = baseOf(pc, AttributeType.INT) + growthOf(pc, AttributeType.INT) * level;
        long now = System.currentTimeMillis();
        List<Modifier> list = modifiers.get(uuid);
        if (list != null) {
            synchronized (list) {
                for (Modifier m : list) {
                    if (m.isPermanent() || m.expiresAt() > now) {
                        str += m.str();
                        agi += m.agi();
                        intel += m.intel();
                    }
                }
            }
        }
        c.str = Math.max(0.0, str);
        c.agi = Math.max(0.0, agi);
        c.intel = Math.max(0.0, intel);
    }

    public void invalidate(UUID uuid) {
        Cache c = cache.get(uuid);
        if (c != null) {
            c.tick = -1;
        }
        HpAttributeSync sync = plugin.getHpSync();
        if (sync != null) {
            sync.syncByUuid(uuid);
        }
    }

    /* ----------------------------- производные ----------------------------- */

    /**
     * ПОЛНАЯ формула HP (1.9.3) + gear-HP из GearHook:
     * HP = base + STR×perStr + level×perLevel + (STR-main ? level×mainBonus : 0) + gearHp.
     */
    public double maxHp(UUID uuid) {
        double baseHp = cfgD("attributes.hp.base-hp", 100.0);
        double perStr = cfgD("attributes.hp.per-str", 20.0);
        double perLevel = cfgD("attributes.hp.per-level", 5.0);
        double mainBonus = cfgD("attributes.hp.main-str-bonus", 8.0);
        Player player = Bukkit.getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        double formula;
        if (pc == null) {
            formula = AttributeMath.maxHp(0.0, 0.0, false, baseHp, perStr, perLevel, 0.0);
        } else {
            double level = levelOf(uuid, pc);
            boolean strMain = mainOf(pc) == AttributeType.STR;
            formula = AttributeMath.maxHp(value(uuid, AttributeType.STR), level, strMain,
                    baseHp, perStr, perLevel, mainBonus);
        }
        GearHook gear = plugin.getGearHook();
        if (gear != null && gear.isAvailable()) {
            double gearHp = gear.hpBonus(uuid);
            if (Double.isFinite(gearHp) && gearHp > 0.0) {
                formula += gearHp;
            }
        }
        return Math.max(1.0, formula);
    }

    public double critMeleeChance(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        if (pc == null) {
            return 0.0;
        }
        return AttributeMath.critMelee(value(uuid, AttributeType.AGI),
                cfgD("attributes.crit.melee-base", 5.0),
                cfgD("attributes.crit.melee-per-agi", 0.05),
                cfgD("attributes.crit.melee-cap", 40.0));
    }

    public double critSpellChance(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        if (pc == null) {
            return 0.0;
        }
        boolean intMain = mainOf(pc) == AttributeType.INT;
        return AttributeMath.critSpell(value(uuid, AttributeType.INT), intMain,
                cfgD("attributes.crit.spell-base", 5.0),
                cfgD("attributes.crit.spell-per-int", 0.03),
                cfgD("attributes.crit.spell-main-mult", 1.5),
                cfgD("attributes.crit.spell-cap", 35.0));
    }

    public double[] effectiveAvoidance(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        if (pc == null) {
            return new double[]{0.0, 0.0};
        }
        double agi = value(uuid, AttributeType.AGI);
        double str = value(uuid, AttributeType.STR);
        boolean agiMain = mainOf(pc) == AttributeType.AGI;

        double dodge = AttributeMath.dodgeRaw(agi, cfgD("avoidance.dodge-k", 100.0));
        double parryFull = AttributeMath.parryRaw(str, cfgD("avoidance.parry-k", 300.0));
        double micro = cfgD("avoidance.agi-main-parry-micro", 0.5);
        if (agiMain) {
            dodge += Math.max(0.0, parryFull - micro)
                    * cfgD("avoidance.agi-main-dodge-refund", 0.5);
        }
        dodge *= cfgD("avoidance.dodge-mult", 0.5);

        double parryChance;
        if (agiMain) {
            parryChance = micro;
        } else {
            parryChance = parryFull;
        }

        double[] avoidB = plugin.getTalentService().avoidBonus(uuid);
        dodge += avoidB[0];
        parryChance += avoidB[1];

        double total = dodge + parryChance;
        if (total <= 0.0) {
            return new double[]{0.0, 0.0};
        }
        double eff = AttributeMath.applyDR(total,
                cfgD("avoidance.soft-cap", 60.0),
                cfgD("avoidance.dr-factor", 0.5),
                cfgD("avoidance.hard-cap", 75.0));
        return AttributeMath.splitEff(dodge, parryChance, eff);
    }

    public double dodgeChance(UUID uuid) {
        return effectiveAvoidance(uuid)[0];
    }

    public double parryChance(UUID uuid) {
        return effectiveAvoidance(uuid)[1];
    }

    /* --------------- 1.9.3-r: ЕДИНЫЕ ЕДИНИЦЫ HP (план B) --------------- */

    public double carrierMaxHp(Player player) {
        if (player == null || MAX_HEALTH == null) {
            return 20.0;
        }
        AttributeInstance inst = player.getAttribute(MAX_HEALTH);
        double v = inst != null ? inst.getValue() : 20.0;
        return Double.isFinite(v) && v > 0.0 ? v : 20.0;
    }

    public double targetCarrier(UUID uuid) {
        return Math.max(1.0, Math.min(maxHp(uuid), VANILLA_MAX_HEALTH_CAP));
    }

    public double scale(Player player) {
        double formula = maxHp(player.getUniqueId());
        if (formula <= 0.0) {
            return 1.0;
        }
        double s = carrierMaxHp(player) / formula;
        return (Double.isFinite(s) && s > 0.0) ? s : 1.0;
    }

    public double currentFormulaHp(Player player) {
        double s = scale(player);
        return s > 0.0 ? player.getHealth() / s : player.getHealth();
    }

    public void healFormula(LivingEntity target, double formulaAmount) {
        if (target == null || target.isDead() || formulaAmount <= 0.0 || MAX_HEALTH == null) {
            return;
        }
        double carrier;
        double formula;
        if (target instanceof Player p) {
            carrier = carrierMaxHp(p);
            formula = maxHp(p.getUniqueId());
        } else {
            AttributeInstance inst = target.getAttribute(MAX_HEALTH);
            carrier = inst != null ? inst.getValue() : 20.0;
            formula = carrier;
        }
        if (!Double.isFinite(carrier) || carrier <= 0.0) {
            carrier = 20.0;
        }
        if (!Double.isFinite(formula) || formula <= 0.0) {
            formula = carrier;
        }
        double s = formula > 0.0 ? carrier / formula : 1.0;
        double add = formulaAmount * s;
        double newHp = Math.min(carrier, target.getHealth() + add);
        target.setHealth(Math.max(0.0, newHp));
    }

    /* ------------------------------- модификаторы ------------------------------- */

    public void addTimedModifier(UUID uuid, String source,
                                 double str, double agi, double intel, long millis) {
        removeModifiersBySource(uuid, source);
        List<Modifier> list = modifiers.computeIfAbsent(uuid, k -> new ArrayList<>());
        synchronized (list) {
            list.add(new Modifier(source, str, agi, intel, System.currentTimeMillis() + millis));
        }
        invalidate(uuid);
    }

    public void addPermanentModifier(UUID uuid, String source,
                                     double str, double agi, double intel) {
        removeModifiersBySource(uuid, source);
        List<Modifier> list = modifiers.computeIfAbsent(uuid, k -> new ArrayList<>());
        synchronized (list) {
            list.add(new Modifier(source, str, agi, intel, Long.MAX_VALUE));
        }
        invalidate(uuid);
    }

    public void removeModifiersBySource(UUID uuid, String source) {
        List<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return;
        }
        synchronized (list) {
            list.removeIf(m -> m.source().equals(source));
        }
        if (list.isEmpty()) {
            modifiers.remove(uuid);
        }
        invalidate(uuid);
    }

    public boolean hasModifier(UUID uuid, String source) {
        List<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        synchronized (list) {
            for (Modifier m : list) {
                if (m.source().equals(source) && (m.isPermanent() || m.expiresAt() > now)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Modifier> activeModifiers(UUID uuid) {
        List<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        synchronized (list) {
            List<Modifier> out = new ArrayList<>();
            for (Modifier m : list) {
                if (m.isPermanent() || m.expiresAt() > now) {
                    out.add(m);
                }
            }
            return out;
        }
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        modifiers.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                entry.getValue().removeIf(m -> !m.isPermanent() && m.expiresAt() <= now);
                return entry.getValue().isEmpty();
            }
        });
        cache.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null
                && !modifiers.containsKey(uuid));
    }

    public void clear(UUID uuid) {
        modifiers.remove(uuid);
        cache.remove(uuid);
    }
}
