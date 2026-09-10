// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.Targeting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * 1.7.2: КИТ ОХОТНИКА (средневековье других вселенных: Ведьмак/Skyrim-вайб).
 * Все способности масштабируются от Силы оружия (WP): base + WP×coeff.
 *
 * Кит (слоты 1–5):
 *  1. «Метка Волка» (wolf_mark)      — рей 20: урон + Slowness I 3 с + Glowing 6 с;
 *  2. «Ласточка» (swallow)           — Speed II + Regeneration I на 8 с;
 *  3. «Пронзающий выстрел» (piercing_shot) — рей 20: тяжёлый одиночный урон;
 *  4. «Веер стрел» (arrow_fan)       — 3 стрелы конусом, урон каждой base+WP×0.35;
 *  5. «Дождь стрел» (arrow_rain)     — AoE радиус 5 (LOS + фракции), урон base+WP×0.5.
 *
 * 1.7.4.1 фикс 4 (баг «стрелы возвращаются»): стрелы «Веера» больше нельзя
 * подобрать и они не висят в мире вечно:
 *  - pickupStatus = DISALLOWED выставляется сразу И повторно на 1-м и 2-м тиках
 *    (Paper может перезатереть статус, выставленный в тик спавна);
 *  - lifetime 600 тиков (30 с): застрявшая стрела исчезает сама;
 *  - PDC-метка raskolclasses:fan_arrow для отладки/будущих гардов.
 */
public final class HunterAbilities {

    private static final PlayerClass PC = PlayerClass.HUNTER;

    private final RaskolClasses plugin;
    private final NamespacedKey fanArrowKey;

    public HunterAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
        this.fanArrowKey = new NamespacedKey(plugin, "fan_arrow");
    }

    /* ------------------------------ конфиг-хелперы ------------------------------ */

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private double base(AbilityDef def, double defv) {
        return cfgD("classes.HUNTER.abilities." + def.id() + ".base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return cfgD("classes.HUNTER.abilities." + def.id() + ".coeff", defv);
    }

    private String power(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.HUNTER.abilities." + def.id() + ".power", "wp");
    }

    private int duration(AbilityDef def, int defv) {
        int v = plugin.getConfig().getInt(
                "classes.HUNTER.abilities." + def.id() + ".duration", defv);
        return v > 0 ? v : defv;
    }

    private double dmg(Player p, AbilityDef def, double defBase, double defCoeff) {
        return plugin.getCombat().powers().abilityDamage(
                p.getUniqueId(), power(def), base(def, defBase), coeff(def, defCoeff));
    }

    private LivingEntity rayTarget(Player p, double range) {
        Entity e = p.getTargetEntity((int) range);
        return e instanceof LivingEntity le ? le : null;
    }

    private void noTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "cheap-shot-no-target", "Нет цели в радиусе действия"), NamedTextColor.GRAY));
    }

    /* -------------------------------- способности -------------------------------- */

    /** 1. «Метка Волка» — урон + Slowness I 3 с + подсветка цели 6 с. */
    public boolean wolfMark(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        double dmg = dmg(p, def, 8.0, 0.5);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 3 * 20, 0));
        t.setGlowing(true);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (t.isValid()) {
                t.setGlowing(false);
            }
        }, 6 * 20L);
        return true;
    }

    /** 2. «Ласточка» — Speed II + Regeneration I на 8 с (ведьмачье зелье). */
    public boolean swallow(Player p, AbilityDef def) {
        int secs = duration(def, 8);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, secs * 20, 1));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, secs * 20, 0));
        return true;
    }

    /** 3. «Пронзающий выстрел» — тяжёлый одиночный выстрел (рей 20). */
    public boolean piercingShot(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        double dmg = dmg(p, def, 12.0, 0.9);
        plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
        return true;
    }

    /** 4. «Веер стрел» — 3 стрелы конусом; стрелы неподбираемы и самоисчезают (фикс 4). */
    public boolean arrowFan(Player p, AbilityDef def) {
        double dmgEach = dmg(p, def, 6.0, 0.35);
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        for (int i = -1; i <= 1; i++) {
            Vector v = dir.clone().rotateAroundY(i * 0.22).multiply(2.2);
            launchFanArrow(p, v, dmgEach);
        }
        return true;
    }

    /**
     * Спавн стрелы веера с гарантированным DISALLOWED-подбором и коротким lifetime.
     * Повторное выставление статуса на 1-м и 2-м тиках закрывает перезапись Paper
     * в тик спавна — стрела больше никогда не попадает в инвентарь и не «возвращается».
     */
    private void launchFanArrow(Player p, Vector velocity, double damage) {
        AbstractArrow arrow = p.launchProjectile(AbstractArrow.class, velocity);
        arrow.setShooter(p);
        arrow.setDamage(damage);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setLifetime(600); // 30 с: застрявшая стрела исчезает сама, не висит в мире
        arrow.getPersistentDataContainer().set(fanArrowKey, PersistentDataType.BYTE, (byte) 1);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (arrow.isValid()) {
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            }
        }, 1L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (arrow.isValid()) {
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            }
        }, 2L);
    }

    /** 5. «Дождь стрел» — AoE радиус 5 с LOS и фракционным фильтром. */
    public boolean arrowRain(Player p, AbilityDef def) {
        double radius = cfgD("classes.HUNTER.abilities." + def.id() + ".radius", 5.0);
        double dmg = dmg(p, def, 10.0, 0.5);
        boolean hit = false;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity t) || e.equals(p)) {
                continue;
            }
            if (!Targeting.isValidDamageTarget(plugin, p, t)) {
                continue;
            }
            if (!Targeting.hasLineOfSight(plugin, p, t)) {
                continue;
            }
            plugin.getCombat().dealDamage(t, p, DamageProfile.physical(dmg));
            hit = true;
        }
        return hit;
    }
}
