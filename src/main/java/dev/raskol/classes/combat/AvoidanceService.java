// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.7.0 пакет 3: УКЛОНЕНИЕ и ПАРИРОВАНИЕ (avoidance).
 *
 * Модель (конфиг avoidance.*):
 *  - dodge raw  = 100×AGI/(AGI+dodge-k); у AGI-основных сверху + половина
 *    потерянного парирования (Правило 1: (parryFull − micro) × refund);
 *  - 1.7.6.1-fix: dodge ×= dodge-mult (дефолт 0.5) — уклонение урезано вдвое,
 *    высокие значения (30%+) достижимы только с кастомной бронёй (1.10.x);
 *  - parry raw  = 100×STR/(STR+parry-k); у AGI-основных заменено на micro;
 *  - условия парирования: в руке мили-оружие/щит И атака не в спину;
 *    полное парирование (не-AGI-основные) — только фронт ≤ front-angle;
 *    micro (AGI-основные) — любой угол, кроме спины ≥ back-angle;
 *  - сумма dodge+parry проходит DR (soft-cap/dr-factor/hard-cap) и делится
 *    пропорционально (AttributeMath.splitEff) — один ролл на оба события;
 *  - уклонение работает против ЛЮБОЙ физической атаки (мобы и игроки), любой угол;
 *  - TRUE-урон (падение/утопление/чистый) НЕ уклоняется и НЕ парируется;
 *  - боссы из attributes.boss-no-avoidance игнорируют avoidance целиком.
 *
 * Парирование даёт контратаку: атакующий-игрок получает дебафф attack-speed
 * (MULTIPLY_SCALAR_1, avoidance.parry-stagger-attack-speed) на parry-stagger-player-ms;
 * моб — Slowness I + knockback на parry-stagger-mob-ms.
 * Визуал: субтайтл-тег + звук + партикл (avoidance.visuals, теги/звуки в конфиге).
 */
public final class AvoidanceService {

    private final RaskolClasses plugin;
    private final NamespacedKey staggerKey;

    public AvoidanceService(RaskolClasses plugin) {
        this.plugin = plugin;
        this.staggerKey = new NamespacedKey(plugin, "parry_stagger");
    }

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private String cfgS(String path, String def) {
        String v = plugin.getConfig().getString(path, def);
        return v != null && !v.isEmpty() ? v : def;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("avoidance.enabled", true);
    }

    private boolean visuals() {
        return plugin.getConfig().getBoolean("avoidance.visuals", true);
    }

    /**
     * Ролл уклонения/парирования для входящего ФИЗИЧЕСКОГО урона.
     * true = атака обнулена (вызывающий обязан отменить событие).
     */
    public boolean tryAvoid(Player defender, EntityDamageEvent event) {
        if (!enabled()) {
            return false;
        }
        LivingEntity attacker = resolveAttacker(event);
        if (attacker == null || isBossImmune(attacker)) {
            return false;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(defender);
        if (pc == null) {
            return false;
        }
        UUID uuid = defender.getUniqueId();
        AttributeService attrs = plugin.getAttributes();
        double agi = attrs.value(uuid, AttributeType.AGI);
        double str = attrs.value(uuid, AttributeType.STR);
        boolean agiMain = attrs.mainOf(pc) == AttributeType.AGI;

        double dodge = AttributeMath.dodgeRaw(agi, cfgD("avoidance.dodge-k", 100.0));
        double parryFull = AttributeMath.parryRaw(str, cfgD("avoidance.parry-k", 150.0));
        double micro = cfgD("avoidance.agi-main-parry-micro", 0.5);
        if (agiMain) {
            dodge += Math.max(0.0, parryFull - micro)
                    * cfgD("avoidance.agi-main-dodge-refund", 0.5);
        }
        // 1.7.6.1-fix: уклонение урезано вдвое (дефолт 0.5); такие цифры — только со шмотом
        dodge *= cfgD("avoidance.dodge-mult", 0.5);

        double parryChance = 0.0;
        if (holdsMeleeOrShield(defender) && !isBack(defender, attacker)) {
            if (agiMain) {
                parryChance = micro;
            } else if (isFront(defender, attacker)) {
                parryChance = parryFull;
            }
        }

        double total = dodge + parryChance;
        if (total <= 0.0) {
            return false;
        }
        double eff = AttributeMath.applyDR(total,
                cfgD("avoidance.soft-cap", 60.0),
                cfgD("avoidance.dr-factor", 0.5),
                cfgD("avoidance.hard-cap", 75.0));
        double[] split = AttributeMath.splitEff(dodge, parryChance, eff);

        double roll = ThreadLocalRandom.current().nextDouble() * 100.0;
        if (roll < split[0]) {
            dodgeFeedback(defender);
            return true;
        }
        if (roll < split[0] + split[1]) {
            parryFeedback(defender);
            stagger(attacker, defender);
            return true;
        }
        return false;
    }

    /* ------------------------------- условия -------------------------------- */

    private static LivingEntity resolveAttacker(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return null; // FALL и прочая среда — не уклоняется (и тип TRUE)
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity sh) {
            return sh;
        }
        if (damager instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    private boolean isBossImmune(LivingEntity attacker) {
        return plugin.getConfig()
                .getStringList("attributes.boss-no-avoidance")
                .contains(attacker.getName());
    }

    private double angleTo(Player defender, LivingEntity attacker) {
        Vector facing = defender.getLocation().getDirection();
        Vector toAtt = attacker.getLocation().toVector()
                .subtract(defender.getLocation().toVector());
        return AttributeMath.angleToAttacker(facing.getX(), facing.getZ(),
                toAtt.getX(), toAtt.getZ());
    }

    private boolean isFront(Player defender, LivingEntity attacker) {
        return AttributeMath.isFront(angleTo(defender, attacker),
                cfgD("avoidance.front-angle", 90.0));
    }

    private boolean isBack(Player defender, LivingEntity attacker) {
        return AttributeMath.isBack(angleTo(defender, attacker),
                cfgD("avoidance.back-angle", 135.0));
    }

    /** Мили-оружие или щит в любой руке (явный список материалов — ноль риска API). */
    private static boolean holdsMeleeOrShield(Player player) {
        return isMelee(player.getInventory().getItemInMainHand().getType())
                || isMelee(player.getInventory().getItemInOffHand().getType());
    }

    private static boolean isMelee(Material m) {
        return switch (m) {
            case SHIELD, TRIDENT, MACE,
                 WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD,
                 WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> true;
            default -> false;
        };
    }

    /* --------------------------- контратака (стаггер) --------------------------- */

    private void stagger(LivingEntity attacker, Player defender) {
        if (attacker instanceof Player ap) {
            double mult = cfgD("avoidance.parry-stagger-attack-speed", -0.4);
            long ticks = Math.max(1L,
                    (long) (cfgD("avoidance.parry-stagger-player-ms", 600.0) / 50.0));
            AttributeInstance instance = ap.getAttribute(Attribute.ATTACK_SPEED);
            if (instance == null) {
                return;
            }
            removeStagger(instance);
            instance.addModifier(new AttributeModifier(staggerKey, mult,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (ap.isOnline()) {
                    AttributeInstance cur = ap.getAttribute(Attribute.ATTACK_SPEED);
                    if (cur != null) {
                        removeStagger(cur);
                    }
                }
            }, ticks);
        } else if (attacker instanceof Mob mob) {
            long ticks = Math.max(1L,
                    (long) (cfgD("avoidance.parry-stagger-mob-ms", 500.0) / 50.0));
            mob.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, (int) ticks, 0));
            Vector push = mob.getLocation().toVector()
                    .subtract(defender.getLocation().toVector());
            push.setY(0);
            if (push.lengthSquared() > 1e-9) {
                push.normalize().multiply(0.35);
                push.setY(0.1);
                mob.setVelocity(mob.getVelocity().add(push));
            }
        }
    }

    private void removeStagger(AttributeInstance instance) {
        for (AttributeModifier m : instance.getModifiers()) {
            if (staggerKey.equals(m.getKey())) {
                instance.removeModifier(m);
                return;
            }
        }
    }

    /* -------------------------------- визуал -------------------------------- */

    private void dodgeFeedback(Player defender) {
        if (!visuals()) {
            return;
        }
        tag(defender, cfgS("avoidance.dodge-tag", "💨 Уклонение!"), NamedTextColor.AQUA);
        Sound s = plugin.getFx().resolveSound(cfgS("avoidance.dodge-sound", "ENTITY_ENDERMAN_TELEPORT"));
        if (s != null) {
            plugin.getFx().playSound(defender.getLocation(), s, 0.5f, 1.3f);
        }
        defender.spawnParticle(Particle.CLOUD,
                defender.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.3, 0.3, 0.02);
    }

    private void parryFeedback(Player defender) {
        if (!visuals()) {
            return;
        }
        tag(defender, cfgS("avoidance.parry-tag", "⚔ Парирование!"), NamedTextColor.GOLD);
        Sound s = plugin.getFx().resolveSound(cfgS("avoidance.parry-sound", "ENTITY_SHIELD_BLOCK"));
        if (s != null) {
            plugin.getFx().playSound(defender.getLocation(), s, 0.6f, 1.1f);
        }
        defender.spawnParticle(Particle.SWEEP_ATTACK,
                defender.getLocation().add(0.0, 1.2, 0.0), 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static void tag(Player player, String text, NamedTextColor color) {
        player.showTitle(Title.title(
                Component.empty(),
                Component.text(text, color),
                Title.Times.times(Duration.ofMillis(60),
                        Duration.ofMillis(650), Duration.ofMillis(180))));
    }
}
