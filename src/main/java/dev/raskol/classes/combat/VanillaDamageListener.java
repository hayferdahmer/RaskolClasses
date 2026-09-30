// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.attribute.PowerService;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.school.Penetration;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.combat.school.SchoolConfig;
import dev.raskol.classes.combat.school.SchoolImmunity;
import dev.raskol.classes.combat.school.SchoolMitigation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * 1.11.4 (P3): ПУТЬ A — ванильные события урона.
 *  - ally-гейт фракций;
 *  - исходящий офенс (WP/SP × coeff, криты, пропуск оружия RaskolGear);
 *  - seal-амплификация «Печати Погибели» (универсально, до резистов);
 *  - резист-фактор канала, single-hit cap, burst-окно (через DamageCaps);
 *  - avoidance (dodge/parry) для PHYSICAL.
 * 1.12.1: иммунитеты/уязвимости сущностей к школам + глобальный множитель школы.
 * 1.12.2 (Блок 4): живая проводка пробития и стихийного слоя:
 *         канал-резист режется pen атакующего (flat→pct), стихийный резист цели
 *         режется school-pen атакующего, слои складываются мультипликативно
 *         (SchoolMitigation.mitigationFor, кап schools.mitigation-cap).
 * 1.13.0 (Б2): breaksOnDamage-хуки — ванильный урон (включая среду и урон по мобам)
 *         снимает ROOT/FEAR при превышении порога cc.breaks-on-damage-threshold-pct.
 *         Урон берётся ФИНАЛЬНЫЙ (после митигации/капов); suppressed-события (путь B)
 *         пропускаются — там CC ломает CombatService.dealDamage самостоятельно.
 *         Ранний возврат «factor >= 1.0» заменён на defenseMult=1.0, чтобы хук
 *         срабатывал и на игроков без резистов.
 * Регистрируется фасадом CombatService в конструкторе — RaskolClasses не трогаем.
 */
public final class VanillaDamageListener implements Listener {

    private final RaskolClasses plugin;
    private final CombatService combat;
    private final ResistService resists;
    private final AvoidanceService avoidance;
    private final PowerService powers;
    private final DamageCaps caps;
    private final SchoolConfig schoolConfig;
    private final SchoolImmunity immunity;

    public VanillaDamageListener(RaskolClasses plugin, CombatService combat,
                                 ResistService resists, AvoidanceService avoidance,
                                 PowerService powers, DamageCaps caps) {
        this.plugin = plugin;
        this.combat = combat;
        this.resists = resists;
        this.avoidance = avoidance;
        this.powers = powers;
        this.caps = caps;
        this.schoolConfig = new SchoolConfig(plugin);
        this.immunity = new SchoolImmunity(plugin);
    }

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    /* ------------------------------ путь A ------------------------------ */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        boolean suppressed = CombatService.consumeSuppress();

        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent by) {
            attacker = resolveAttacker(by);
        }

        if (!suppressed && attacker != null
                && event.getEntity() instanceof Player victimTarget
                && !attacker.getUniqueId().equals(victimTarget.getUniqueId())
                && !plugin.getConfig().getBoolean("combat.friendly-fire", false)
                && Targeting.isAlly(plugin, attacker.getUniqueId(), victimTarget.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!suppressed) {
            applyOutgoingOffense(event);
            // «Печать Погибели»: +26% урона от ВСЕХ источников (универсально)
            double amp = WarlockAbilities.sealAmplifyOf(event.getEntity().getUniqueId());
            if (amp > 0.0 && event.getDamage() > 0.0) {
                event.setDamage(event.getDamage() * (1.0 + amp));
            }
        }

        // 1.12.1: школы — иммунитеты/уязвимости сущности + глобальный множитель школы
        School school = schoolConfig.schoolOf(event.getCause());
        if (!suppressed && event.getEntity() instanceof LivingEntity ent) {
            double mult = immunity.multiplierFor(ent.getType(), school)
                    * schoolConfig.multiplier(school);
            if (mult <= 0.0) {
                event.setCancelled(true);
                return;
            }
            if (mult != 1.0 && event.getDamage() > 0.0) {
                event.setDamage(event.getDamage() * mult);
            }
        }

        // 1.13.0 (Б2): мобы — резист-митигации в пути A нет, урон финален уже здесь
        if (!(event.getEntity() instanceof Player target)) {
            if (!suppressed && event.getEntity() instanceof LivingEntity mob
                    && event.getDamage() > 0.0) {
                hookBreaksCarrier(mob, event.getDamage());
            }
            return;
        }
        if (target.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (resists.disabledIn(target.getWorld())) {
            // арена/мир без резистов: урон финален, CC ломаются как обычно
            if (!suppressed && event.getDamage() > 0.0) {
                hookBreaksCarrier(target, event.getDamage());
            }
            return;
        }
        DamageType type = typeOf(event.getCause());
        if (type == DamageType.TRUE) {
            if (!suppressed) {
                caps.applyEnvLethalScale(event, target);
                hookBreaksCarrier(target, event.getDamage());
            }
            return;
        }
        if (type == DamageType.PHYSICAL && avoidance.tryAvoid(target, event)) {
            // уклон/парирование: урона нет → CC не ломаются
            event.setCancelled(true);
            return;
        }
        if (suppressed) {
            return;
        }

        double cap = isPvp(event) ? resists.pvpCap() : resists.cap();
        UUID uuid = target.getUniqueId();
        double factor = type == DamageType.PHYSICAL
                ? resists.physicalFactor(uuid, cap)
                : resists.magicFactor(uuid, cap);

        // 1.12.2 (Блок 4): пробитие атакующего + стихийный слой цели.
        // 1.13.0 (Б2): невалидный/нулевой резист больше не выходит из метода —
        // defenseMult = 1.0, чтобы breaksOnDamage-хук сработал и без резистов.
        double defenseMult = 1.0;
        if (Double.isFinite(factor) && factor >= 0.0 && factor < 1.0) {
            double channelResistPct = (1.0 - factor) * 100.0;
            double mitigation;
            if (attacker != null && schoolConfig.enabled()) {
                UUID aUuid = attacker.getUniqueId();
                Penetration pen = combat.penTraits().channelPen(
                        aUuid, type == DamageType.PHYSICAL,
                        plugin.getGearHook(), schoolConfig.penPctCap());
                double effChannel = pen.effectiveResist(channelResistPct, schoolConfig.penPctCap());
                double elResist = combat.elemental().resistOf(uuid, school);
                double schoolPen = combat.penTraits().schoolPenFraction(
                        aUuid, school, plugin.getGearHook(), schoolConfig.penPctCap());
                double effEl = CombatMath.effectiveResist(
                        elResist, 0.0, schoolPen, schoolConfig.penPctCap());
                mitigation = SchoolMitigation.mitigationFor(effChannel, Penetration.NONE,
                        schoolConfig.penPctCap(), effEl,
                        schoolConfig.elementalEnabled(), schoolConfig.mitigationCap());
            } else {
                mitigation = channelResistPct / 100.0; // legacy-поведение
            }
            defenseMult = 1.0 - mitigation;
            if (defenseMult <= 0.0) {
                event.setCancelled(true);
                return;
            }
        }
        if (defenseMult < 1.0) {
            event.setDamage(event.getDamage() * defenseMult);
        }

        caps.applySingleHitCapCarrier(event, target);
        double scale = caps.scaleOf(target);
        double effective = scale > 0.0 ? event.getDamage() / scale : event.getDamage();
        effective = caps.applyBurstCap(target, effective, caps.formulaMaxOf(target));
        event.setDamage(effective * scale);

        // 1.13.0 (Б2): финальный урон по игроку (после митигации и капов) → breaksOnDamage
        hookBreaksCarrier(target, event.getDamage());
    }

    /**
     * 1.13.0 (Б2): конвертация carrier-урона в formula-единицы и передача
     * в CCService.breakOnDamage (порог сравнивается с formula-maxHP, план B).
     */
    private void hookBreaksCarrier(LivingEntity victim, double carrierDamage) {
        if (carrierDamage <= 0.0) {
            return;
        }
        double scale = caps.scaleOf(victim);
        double formulaDamage = scale > 0.0 ? carrierDamage / scale : carrierDamage;
        if (formulaDamage > 0.0) {
            plugin.getCC().breakOnDamage(victim, formulaDamage, caps.formulaMaxOf(victim));
        }
    }

    /* --------------------- исходящий офенс (1.7.1) --------------------- */

    private void applyOutgoingOffense(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent by)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Player attacker = resolveAttacker(by);
        if (attacker == null) {
            return;
        }
        if (hasGearWeaponTag(attacker)) {
            return;
        }
        DamageType type = typeOf(event.getCause());
        if (type == DamageType.TRUE) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        double add = 0.0;
        boolean crit = false;
        if (type == DamageType.PHYSICAL) {
            add += powers.weaponPower(uuid) * cfgD("attributes.offense.basic-coeff", 0.35);
            if (plugin.getConfig().getBoolean("attributes.offense.str-to-physical", false)) {
                add += plugin.getAttributes().value(uuid, dev.raskol.classes.attribute.AttributeType.STR)
                        + plugin.getAttributes().levelOf(uuid,
                        plugin.getClassProvider().getClassOf(attacker));
            }
            crit = rollMeleeCrit(attacker);
        } else {
            add += powers.spellPower(uuid) * cfgD("attributes.offense.basic-coeff-magic", 0.35);
            if (plugin.getConfig().getBoolean("attributes.offense.int-to-magic", false)) {
                add += plugin.getAttributes().value(uuid, dev.raskol.classes.attribute.AttributeType.INT)
                        + plugin.getAttributes().levelOf(uuid,
                        plugin.getClassProvider().getClassOf(attacker));
            }
            crit = rollSpellCrit(attacker);
        }
        add *= caps.scaleOf(by.getEntity());
        double base = event.getDamage();
        double total = base + add;
        if (crit) {
            total *= type == DamageType.PHYSICAL ? meleeMult() : spellMult();
            critFeedback(attacker, type == DamageType.PHYSICAL);
        }
        if (total != base && Double.isFinite(total) && total >= 0.0) {
            event.setDamage(total);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent by) {
        Entity damager = by.getDamager();
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player ps) {
            return ps;
        }
        return null;
    }

    private boolean hasGearWeaponTag(Player attacker) {
        var weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null) {
            return false;
        }
        var meta = weapon.getItemMeta();
        if (meta == null) {
            return false;
        }
        var pdc = meta.getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            if ("gear_type".equals(key.getKey())) {
                if ("WEAPON".equals(pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean rollMeleeCrit(Player player) {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0
                < plugin.getAttributes().critMeleeChance(player.getUniqueId());
    }

    private boolean rollSpellCrit(Player player) {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0
                < plugin.getAttributes().critSpellChance(player.getUniqueId());
    }

    private double meleeMult() {
        return cfgD("attributes.crit.melee-mult", 1.5);
    }

    private double spellMult() {
        return cfgD("attributes.crit.spell-mult", 1.5);
    }

    private void critFeedback(Player attacker, boolean melee) {
        if (!plugin.getConfig().getBoolean("attributes.crit.visuals", true)) {
            return;
        }
        String tag = melee
                ? plugin.getConfig().getString("attributes.crit.melee-tag", "⚡ Крит!")
                : plugin.getConfig().getString("attributes.crit.spell-tag", "✦ Магический крит!");
        attacker.showTitle(net.kyori.adventure.title.Title.title(
                Component.empty(),
                Component.text(tag, melee ? NamedTextColor.RED : NamedTextColor.LIGHT_PURPLE),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(50),
                        java.time.Duration.ofMillis(550),
                        java.time.Duration.ofMillis(150))));
        dev.raskol.classes.fx.FxService fx = plugin.getFx();
        String soundKey = melee
                ? plugin.getConfig().getString("attributes.crit.melee-sound", "ENTITY_PLAYER_ATTACK_CRIT")
                : plugin.getConfig().getString("attributes.crit.spell-sound", "ENTITY_EVOKER_CAST_SPELL");
        org.bukkit.Sound s = fx.resolveSound(soundKey);
        if (s != null) {
            fx.playSound(attacker.getLocation(), s, 0.5f, melee ? 0.9f : 1.2f);
        }
    }

    private static boolean isPvp(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Player) {
            return true;
        }
        return damager instanceof Projectile proj && proj.getShooter() instanceof Player;
    }

    private DamageType typeOf(EntityDamageEvent.DamageCause cause) {
        String override = plugin.getConfig()
                .getString("damage-types.vanilla-map." + cause.name());
        if (override != null && !override.isEmpty()) {
            try {
                return DamageType.valueOf(override.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return DamageType.defaultFor(cause);
    }
}
