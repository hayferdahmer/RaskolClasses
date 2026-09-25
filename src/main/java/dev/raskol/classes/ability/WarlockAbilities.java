// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.config.RaskolConfig;
import dev.raskol.classes.passive.PassiveListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.10.0: способности Чернокнижника (5 слотов + пассивка «Чёрная Месса»).
 * Все способности используют SP (маг-урон), дрейн (lifesteal) и откат 6.66% от нанесённого.
 * В аду (Nether) урон ×6 (конфиг nether-mult).
 */
public final class WarlockAbilities {

    private final RaskolClasses plugin;
    private final Map<UUID, Long> antihealTargets = new ConcurrentHashMap<>();

    public WarlockAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /**
     * Слот 1: Чёрное Слово (black_word)
     * Маг-урон одной цели; 66.6% урона → HP; откат 6.66% от нанесённого.
     */
    public boolean blackWord(Player caster, LivingEntity target, AbilityDef def) {
        if (target == null || target.isDead()) {
            return false;
        }
        RaskolConfig cfg = plugin.getRaskolConfig();
        UUID uuid = caster.getUniqueId();
        double sp = plugin.getCombat().powers().spellPower(uuid);
        double drain = cfg.abilityDrain(dev.raskol.classes.classsystem.PlayerClass.WARLOCK, def.id());

        double baseDmg = 18 + sp * 1.5;
        double mult = getDamageMult(caster);
        baseDmg *= mult;

        DamageProfile profile = new DamageProfile(0, baseDmg, 0);
        double dealt = plugin.getCombat().dealDamage(target, caster, profile);

        if (dealt > 0) {
            double healAmount = dealt * drain;
            caster.setHealth(Math.min(caster.getMaxHealth(),
                    caster.getHealth() + healAmount));
            applyRecoil(caster, dealt, def.id());
            return true;
        }
        return false;
    }

    /**
     * Слот 2: Печать Погибели (ruin_seal)
     * Проклятие 66.6 с: цель получает +26% урона от всех источников (неснимаемо, кроме смерти).
     */
    public boolean ruinSeal(Player caster, LivingEntity target, AbilityDef def) {
        if (target == null || target.isDead()) {
            return false;
        }
        RaskolConfig cfg = plugin.getRaskolConfig();
        int duration = cfg.durationSeconds(dev.raskol.classes.classsystem.PlayerClass.WARLOCK, def.id(), 67);
        double amplify = cfg.abilityAmplify(dev.raskol.classes.classsystem.PlayerClass.WARLOCK, def.id());

        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration * 20, 0, false, false, false));
        plugin.getResists().addModifier(target.getUniqueId(), "ruin_seal",
                0, 0, duration * 20L, "ruin_seal");
        plugin.getFx().playSound(target.getLocation(), Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 0.8f);
        plugin.getFx().spawnParticles(target.getLocation().add(0, 1, 0),
                org.bukkit.Particle.SCULK_SOUL, 30, 0.5, 0.5, 0.5, 0.02);
        return true;
    }

    /**
     * Слот 3: Голод Скверны (hunger_corruption)
     * Маг-урон по площади 6 блоков; 66.6% урона → HP; +12 Скверны.
     */
    public boolean hungerCorruption(Player caster, AbilityDef def) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        UUID uuid = caster.getUniqueId();
        double sp = plugin.getCombat().powers().spellPower(uuid);
        double radius = 6.0;
        double drain = cfg.abilityDrain(dev.raskol.classes.classsystem.PlayerClass.WARLOCK, def.id());
        int corruptionGain = cfg.abilityCorruptionGain(dev.raskol.classes.classsystem.PlayerClass.WARLOCK, def.id());

        double baseDmg = 20 + sp * 1.2;
        double mult = getDamageMult(caster);
        baseDmg *= mult;

        Location center = caster.getLocation();
        double totalHeal = 0;

        for (LivingEntity entity : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Player p && !plugin.getCombat().canHit(caster, p)) {
                continue;
            }
            if (entity.isDead() || entity.equals(caster)) {
                continue;
            }
            DamageProfile profile = new DamageProfile(0, baseDmg, 0);
            double dealt = plugin.getCombat().dealDamage(entity, caster, profile);
            if (dealt > 0) {
                totalHeal += dealt * drain;
            }
        }

        if (totalHeal > 0) {
            caster.setHealth(Math.min(caster.getMaxHealth(),
                    caster.getHealth() + totalHeal));
            applyRecoil(caster, totalHeal / drain, def.id());
        }

        plugin.getResources().add(uuid, corruptionGain);
        plugin.getFx().playSound(center, Sound.ENTITY_WARDEN_ROAR, 1.0f, 1.2f);
        plugin.getFx().spawnParticles(center.add(0, 1, 0),
                org.bukkit.Particle.SCULK_CHARGE, 50, radius, 1, radius, 0.1);
        return totalHeal > 0;
    }

    /**
     * Слот 4: Небытие (unwriting)
     * Диспел положительных эффектов; маг-урон +16 за каждый снятый.
     */
    public boolean unwriting(Player caster, LivingEntity target, AbilityDef def) {
        if (target == null || target.isDead()) {
            return false;
        }
        RaskolConfig cfg = plugin.getRaskolConfig();
        UUID uuid = caster.getUniqueId();
        double sp = plugin.getCombat().powers().spellPower(uuid);
        int perPurged = cfg.abilityPerPurged(dev.raskol.classes.classsystem.PlayerClass.WARLOCK, def.id());

        int purgedCount = 0;
        for (PotionEffect effect : target.getActivePotionEffects()) {
            PotionEffectType type = effect.getType();
            if (type.equals(PotionEffectType.SPEED) ||
                type.equals(PotionEffectType.STRENGTH) ||
                type.equals(PotionEffectType.REGENERATION) ||
                type.equals(PotionEffectType.INVISIBILITY) ||
                type.equals(PotionEffectType.RESISTANCE) ||
                type.equals(PotionEffectType.ABSORPTION)) {
                target.removePotionEffect(type);
                purgedCount++;
            }
        }

        double baseDmg = 16 + sp * 0.8 + perPurged * purgedCount;
        double mult = getDamageMult(caster);
        baseDmg *= mult;

        DamageProfile profile = new DamageProfile(0, baseDmg, 0);
        double dealt = plugin.getCombat().dealDamage(target, caster, profile);

        if (dealt > 0) {
            applyRecoil(caster, dealt, def.id());
            return true;
        }
        return false;
    }

    /**
     * Слот 5: Раскол Души (soul_rift)
     * Канал 2.5 с r8: тик маг-урона + анти-хил 6 с; взрыв до +66.6% missing-HP.
     */
    public boolean soulRift(Player caster, AbilityDef def) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        UUID uuid = caster.getUniqueId();
        double sp = plugin.getCombat().powers().spellPower(uuid);
        double radius = 8.0;
        double channelSec = cfg.abilityChannel(dev.raskol.classes.classsystem.PlayerClass.WARLOCK, def.id());
        double antihealSec = cfg.abilityAntiheal(dev.raskol.classes.classsystem.PlayerClass.WARLOCK, def.id());
        double missingHpBonus = cfg.abilityMissingHpBonus(dev.raskol.classes.classsystem.PlayerClass.WARLOCK, def.id());

        int ticks = (int) (channelSec * 20);
        for (int i = 0; i < ticks; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (caster.isDead() || !caster.isOnline()) {
                    return;
                }
                Location center = caster.getLocation();
                double tickDmg = 6;
                double mult = getDamageMult(caster);
                tickDmg *= mult;

                for (LivingEntity entity : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (entity instanceof Player p && !plugin.getCombat().canHit(caster, p)) {
                        continue;
                    }
                    if (entity.isDead() || entity.equals(caster)) {
                        continue;
                    }
                    DamageProfile profile = new DamageProfile(0, tickDmg, 0);
                    plugin.getCombat().dealDamage(entity, caster, profile);
                    antihealTargets.put(entity.getUniqueId(),
                            System.currentTimeMillis() + (long) (antihealSec * 1000));
                }
                plugin.getFx().spawnParticles(center.add(0, 1, 0),
                        org.bukkit.Particle.SCULK_CHARGE, 20, radius, 1, radius, 0.05);
            }, i);
        }

        // Финальный взрыв
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (caster.isDead() || !caster.isOnline()) {
                return;
            }
            Location center = caster.getLocation();
            double baseDmg = 30 + sp * 2.4;
            double mult = getDamageMult(caster);
            baseDmg *= mult;

            for (LivingEntity entity : caster.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (entity instanceof Player p && !plugin.getCombat().canHit(caster, p)) {
                    continue;
                }
                if (entity.isDead() || entity.equals(caster)) {
                    continue;
                }
                double missingHp = Math.max(0, entity.getMaxHealth() - entity.getHealth());
                double bonus = missingHp * missingHpBonus;
                double finalDmg = baseDmg + bonus;

                DamageProfile profile = new DamageProfile(0, finalDmg, 0);
                double dealt = plugin.getCombat().dealDamage(entity, caster, profile);
                if (dealt > 0) {
                    applyRecoil(caster, dealt, def.id());
                }
            }
            plugin.getFx().playSound(center, Sound.ENTITY_WARDEN_DEATH, 1.5f, 0.8f);
            plugin.getFx().spawnParticles(center.add(0, 1, 0),
                    org.bukkit.Particle.SCULK_SOUL, 100, radius, 1, radius, 0.2);
        }, ticks);

        return true;
    }

    /**
     * Множитель урона: если WARLOCK и Скверна ≥ 75 → ×1.2; в аду ×6.
     */
    private double getDamageMult(Player caster) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        UUID uuid = caster.getUniqueId();
        double corruption = plugin.getResources().getValue(uuid);
        double thresholdOpen = cfg.warlockThresholdOpen();
        double netherMult = cfg.warlockNetherMult();

        double mult = 1.0;
        if (corruption >= thresholdOpen) {
            mult *= 1.2;
        }
        if (caster.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER) {
            mult *= netherMult;
        }
        return mult;
    }

    /**
     * Откат 6.66% от нанесённого урона (true-урон себе).
     * Предохранители: не ниже 1 HP, не более 30% maxHP за каст.
     */
    private void applyRecoil(Player caster, double dealt, String abilityId) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        double recoilPct = cfg.warlockRecoilPercent();
        double recoilCapPct = cfg.warlockRecoilCapPct();
        double recoilMinHp = cfg.warlockRecoilMinHp();

        double recoil = dealt * recoilPct / 100.0;
        double maxRecoil = caster.getMaxHealth() * recoilCapPct / 100.0;
        recoil = Math.min(recoil, maxRecoil);

        double newHp = caster.getHealth() - recoil;
        if (newHp < recoilMinHp) {
            newHp = recoilMinHp;
        }
        caster.setHealth(newHp);
    }

    /**
     * Проверка анти-хила: true если цель под анти-хилом от soul_rift.
     */
    public boolean isAntihealed(UUID targetUuid) {
        Long expiry = antihealTargets.get(targetUuid);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            antihealTargets.remove(targetUuid);
            return false;
        }
        return true;
    }
}
