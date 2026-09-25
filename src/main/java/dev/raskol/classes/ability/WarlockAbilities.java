// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.10.0: КИТ ЧЕРНОКНИЖНИКА (5 способностей, power = SP).
 *
 * 1.10.0-fix (компиляция):
 *  - Печать Погибели больше НЕ лезет в ResistService (там нет амплификации):
 *    собственный статический реестр SEAL → CombatService домножает урон на (1+amp);
 *  - партиклы спавмятся напрямую world.spawnParticle (у FxService нет spawnParticles);
 *  - getNearbyEntities итерируется как Entity + instanceof LivingEntity;
 *  - анти-хил — статический реестр ANTIHEAL, проверяется в CombatService (ванильные
 *    события) и в HpBarService.healFormula (наш хил-пайплайн).
 *
 * Все хилы/дрейн идут через HpBarService.heal (formula-единицы плана B).
 * Откат 6.66% применяется централизованно в CombatService.dealDamage.
 */
public final class WarlockAbilities {

    private static final PlayerClass PC = PlayerClass.WARLOCK;

    /* Статические реестры дебафов (доступны CombatService/HpBarService без проводки). */
    private static final Map<UUID, Long> SEAL_EXPIRY = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> SEAL_AMP = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ANTIHEAL_EXPIRY = new ConcurrentHashMap<>();

    /** Амплификация урона по цели от «Печати Погибели» (0.0 если печати нет). */
    public static double sealAmplifyOf(UUID uuid) {
        Long expiry = SEAL_EXPIRY.get(uuid);
        if (expiry == null) {
            return 0.0;
        }
        if (System.currentTimeMillis() > expiry) {
            SEAL_EXPIRY.remove(uuid);
            SEAL_AMP.remove(uuid);
            return 0.0;
        }
        return SEAL_AMP.getOrDefault(uuid, 0.0);
    }

    /** true если цель под анти-хилом «Раскола Души». */
    public static boolean isAntihealed(UUID uuid) {
        Long expiry = ANTIHEAL_EXPIRY.get(uuid);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            ANTIHEAL_EXPIRY.remove(uuid);
            return false;
        }
        return true;
    }

    private static void purgeStaleDebuffs() {
        long now = System.currentTimeMillis();
        SEAL_EXPIRY.entrySet().removeIf(e -> e.getValue() < now);
        SEAL_AMP.keySet().removeIf(id -> !SEAL_EXPIRY.containsKey(id));
        ANTIHEAL_EXPIRY.entrySet().removeIf(e -> e.getValue() < now);
    }

    private final RaskolClasses plugin;

    public WarlockAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ конфиг-хелперы ------------------------------ */

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private double base(AbilityDef def, double defv) {
        return cfgD("classes.WARLOCK.abilities." + def.id() + ".base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return cfgD("classes.WARLOCK.abilities." + def.id() + ".coeff", defv);
    }

    private double drain(AbilityDef def, double defv) {
        return cfgD("classes.WARLOCK.abilities." + def.id() + ".drain", defv);
    }

    private double radius(AbilityDef def, double defv) {
        return cfgD("classes.WARLOCK.abilities." + def.id() + ".radius", defv);
    }

    /** Множитель урона: Скверна ≥ порога → ×1.2; ад (NETHER) → ×nether-mult. */
    private double damageMult(Player caster) {
        double mult = 1.0;
        double corruption = plugin.getResources().getValue(caster.getUniqueId());
        if (corruption >= plugin.getRaskolConfig().warlockThresholdOpen()) {
            mult *= 1.2;
        }
        if (caster.getWorld().getEnvironment() == World.Environment.NETHER) {
            mult *= plugin.getRaskolConfig().warlockNetherMult();
        }
        return mult;
    }

    private double spellDamage(Player caster, AbilityDef def, double defBase, double defCoeff) {
        double sp = plugin.getCombat().powers().spellPower(caster.getUniqueId());
        return (base(def, defBase) + sp * coeff(def, defCoeff)) * damageMult(caster);
    }

    private LivingEntity rayTarget(Player p, double range) {
        Entity e = p.getTargetEntity((int) range);
        return e instanceof LivingEntity le ? le : null;
    }

    private void burst(Location loc, Particle particle, int count, double spread) {
        if (loc.getWorld() != null) {
            loc.getWorld().spawnParticle(particle, loc.clone().add(0.0, 1.0, 0.0),
                    count, spread, 0.6, spread, 0.05);
        }
    }

    /* -------------------------------- способности -------------------------------- */

    /** 1. «Чёрное Слово» — дрейн-болт: маг-урон, 66.6% урона → HP себе. */
    public boolean blackWord(Player caster, LivingEntity target, AbilityDef def) {
        LivingEntity t = target != null ? target : rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            return false;
        }
        double dmg = spellDamage(caster, def, 18.0, 1.5);
        double dealt = plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
        if (dealt <= 0.0) {
            return false;
        }
        plugin.getHpBarService().heal(caster, dealt * drain(def, 0.666));
        plugin.getFx().playSound(t.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.5f, 1.0f);
        burst(t.getLocation(), Particle.SCULK_CHARGE, 12, 0.4);
        return true;
    }

    /** 2. «Печать Погибели» — амплификация +26% входящего урона, Glowing, неснимаемо. */
    public boolean ruinSeal(Player caster, LivingEntity target, AbilityDef def) {
        LivingEntity t = target != null ? target : rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            return false;
        }
        double durationSec = cfgD("classes.WARLOCK.abilities." + def.id() + ".duration", 66.6);
        double amplify = cfgD("classes.WARLOCK.abilities." + def.id() + ".amplify", 0.26);
        UUID id = t.getUniqueId();
        SEAL_EXPIRY.put(id, System.currentTimeMillis() + (long) (durationSec * 1000.0));
        SEAL_AMP.put(id, amplify);
        t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                (int) (durationSec * 20.0), 0, false, false, false));
        plugin.getFx().playSound(t.getLocation(), Sound.BLOCK_SOUL_SAND_BREAK, 0.6f, 0.8f);
        burst(t.getLocation(), Particle.SCULK_SOUL, 20, 0.5);
        return true;
    }

    /** 3. «Голод Скверны» — AoE r6: урон, 66.6% суммы → HP, +12 Скверны. */
    public boolean hungerCorruption(Player caster, AbilityDef def) {
        double radius = radius(def, 6.0);
        double dmg = spellDamage(caster, def, 20.0, 1.2);
        Location center = caster.getLocation();
        double totalDealt = 0.0;

        for (Entity e : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(e instanceof LivingEntity t) || t.isDead() || t.equals(caster)) {
                continue;
            }
            if (!plugin.getCombat().canHit(caster, t)) {
                continue;
            }
            totalDealt += plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
        }
        if (totalDealt <= 0.0) {
            return false;
        }
        plugin.getHpBarService().heal(caster, totalDealt * drain(def, 0.666));
        plugin.getResources().add(caster.getUniqueId(),
                cfgD("classes.WARLOCK.abilities." + def.id() + ".corruption-gain", 12.0));
        plugin.getFx().playSound(center, Sound.ENTITY_WARDEN_ROAR, 0.7f, 1.2f);
        burst(center, Particle.SCULK_CHARGE, 40, radius * 0.5);
        return true;
    }

    /** 4. «Небытие» — диспел положительных эффектов; урон +16 за каждый снятый. */
    public boolean unwriting(Player caster, LivingEntity target, AbilityDef def) {
        LivingEntity t = target != null ? target : rayTarget(caster, 20);
        if (t == null || t.isDead()) {
            return false;
        }
        if (!plugin.getCombat().canHit(caster, t)) {
            return false;
        }
        int purged = 0;
        for (PotionEffect effect : java.util.List.copyOf(t.getActivePotionEffects())) {
            PotionEffectType type = effect.getType();
            if (type == PotionEffectType.SPEED || type == PotionEffectType.STRENGTH
                    || type == PotionEffectType.REGENERATION || type == PotionEffectType.INVISIBILITY
                    || type == PotionEffectType.RESISTANCE || type == PotionEffectType.ABSORPTION
                    || type == PotionEffectType.FIRE_RESISTANCE || type == PotionEffectType.HASTE) {
                t.removePotionEffect(type);
                purged++;
            }
        }
        double perPurged = cfgD("classes.WARLOCK.abilities." + def.id() + ".per-purged", 16.0);
        double sp = plugin.getCombat().powers().spellPower(caster.getUniqueId());
        double dmg = (base(def, 16.0) + sp * coeff(def, 0.8) + perPurged * purged) * damageMult(caster);
        double dealt = plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg));
        if (dealt <= 0.0) {
            return purged > 0;
        }
        plugin.getFx().playSound(t.getLocation(), Sound.BLOCK_SCULK_BREAK, 0.6f, 1.0f);
        burst(t.getLocation(), Particle.SCULK_SOUL, 16, 0.5);
        return true;
    }

    /** 5. «Раскол Души» — канал 2.5 с: зона r8 тик-урон + анти-хил; финальный взрыв по missing-HP. */
    public boolean soulRift(Player caster, AbilityDef def) {
        double radius = radius(def, 8.0);
        double channelSec = cfgD("classes.WARLOCK.abilities." + def.id() + ".channel", 2.5);
        double antihealSec = cfgD("classes.WARLOCK.abilities." + def.id() + ".antiheal", 6.0);
        double missingBonus = cfgD("classes.WARLOCK.abilities." + def.id() + ".missing-hp-bonus", 0.666);
        int ticks = Math.max(1, (int) (channelSec * 20.0));

        for (int i = 1; i <= ticks; i += 5) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!caster.isOnline() || caster.isDead()) {
                    return;
                }
                Location center = caster.getLocation();
                double tickDmg = 6.0 * damageMult(caster);
                for (Entity e : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (!(e instanceof LivingEntity t) || t.isDead() || t.equals(caster)) {
                        continue;
                    }
                    if (!plugin.getCombat().canHit(caster, t)) {
                        continue;
                    }
                    plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(tickDmg));
                    ANTIHEAL_EXPIRY.put(t.getUniqueId(),
                            System.currentTimeMillis() + (long) (antihealSec * 1000.0));
                }
                burst(center, Particle.SCULK_CHARGE, 16, radius * 0.4);
            }, i);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!caster.isOnline() || caster.isDead()) {
                return;
            }
            Location center = caster.getLocation();
            double sp = plugin.getCombat().powers().spellPower(caster.getUniqueId());
            double baseDmg = (base(def, 30.0) + sp * coeff(def, 2.4)) * damageMult(caster);
            for (Entity e : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (!(e instanceof LivingEntity t) || t.isDead() || t.equals(caster)) {
                    continue;
                }
                if (!plugin.getCombat().canHit(caster, t)) {
                    continue;
                }
                double missing = Math.max(0.0, formulaMax(t) - currentFormulaHp(t));
                double dmg = baseDmg + missing * missingBonus;
                plugin.getCombat().dealDamage(t, caster, DamageProfile.magic(dmg), true);
                ANTIHEAL_EXPIRY.put(t.getUniqueId(),
                        System.currentTimeMillis() + (long) (antihealSec * 1000.0));
            }
            plugin.getFx().playSound(center, Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.7f);
            burst(center, Particle.SCULK_SOUL, 60, radius * 0.5);
            purgeStaleDebuffs();
        }, ticks + 1L);

        return true;
    }

    /* ------------------------------ unit-хелперы (план B) ------------------------------ */

    private double formulaMax(LivingEntity t) {
        if (t instanceof Player p) {
            return plugin.getAttributes().maxHp(p.getUniqueId());
        }
        double m = t.getMaxHealth();
        return Double.isFinite(m) && m > 0.0 ? m : 20.0;
    }

    private double currentFormulaHp(LivingEntity t) {
        if (t instanceof Player p) {
            return plugin.getAttributes().currentFormulaHp(p);
        }
        return t.getHealth();
    }
}
