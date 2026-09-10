// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 1.6.0: боевой сервис урона и резистов.
 * Путь A (ваниль): EntityDamageEvent по игроку режется резистом своего типа
 * (карта причин → тип с конфиг-оверрайдами damage-types.vanilla-map).
 * Путь B (наши способности/инсталляции/спек-активки): dealDamage(target, source,
 * DamageProfile) — физ-компонента проходит броню, маг+чистый через DamageSource
 * minecraft:magic (броню не трогает). Двойного применения резиста нет
 * (ThreadLocal-маркер SUPPRESS, сброс в finally — 1.6.1 B3).
 * 1.6.4: simulateTaken() — единая формула для симулятора /rc debug.
 * 1.6.8: spectator/creative-гарды; CRAMMING/DRYOUT явно physical.
 * 1.6.9: resist.disabled-worlds / pvp-cap / true-damage-cap-per-hit; NaN-гарды.
 * 1.7.0.2: МАСШТАБ ЛЕТАЛЬНОСТИ СРЕДЫ — TRUE-урон из списка damage-types.env-lethal
 * (FALL, DROWNING, SUFFOCATION, STARVATION) умножается на maxHP/20 для игроков:
 * падение с высоты и утопление убивают так же, как в ванилле, независимо от
 * раздутого классового пула HP. Мобы не масштабируются (их пул ванильный).
 * Чары Protection/Feather Falling применяются ванилью ПОСЛЕ нашего масштаба —
 * контрплей сохраняется. VOID/SONIC_BOOM не масштабируются (уже летальны).
 */
public final class CombatService implements Listener {

    private static final ThreadLocal<Boolean> SUPPRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile org.bukkit.damage.DamageType magicTypeCache;

    /** max_health из реестра атрибутов Paper (1.21.4-safe). */
    private static final Attribute MAX_HEALTH = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ATTRIBUTE)
            .get(NamespacedKey.minecraft("max_health"));

    private final RaskolClasses plugin;
    private final ResistService resists;

    public CombatService(RaskolClasses plugin, ResistService resists) {
        this.plugin = plugin;
        this.resists = resists;
    }

    public ResistService resists() {
        return resists;
    }

    private static org.bukkit.damage.DamageType magicType() {
        org.bukkit.damage.DamageType local = magicTypeCache;
        if (local == null) {
            local = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.DAMAGE_TYPE)
                    .get(NamespacedKey.minecraft("magic"));
            magicTypeCache = local;
        }
        return local;
    }

    private double trueCap() {
        double v = plugin.getConfig().getDouble("combat.true-damage-cap-per-hit", 1000.0);
        return Double.isFinite(v) && v >= 0.0 ? v : 1000.0;
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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (Boolean.TRUE.equals(SUPPRESS.get())) {
            SUPPRESS.set(Boolean.FALSE);
            return;
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        if (target.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (resists.disabledIn(target.getWorld())) {
            return;
        }
        DamageType type = typeOf(event.getCause());
        if (type == DamageType.TRUE) {
            // 1.7.0.2: резисты не применяются; среда дополнительно масштабируется
            applyEnvLethalScale(event, target);
            return;
        }
        double cap = isPvp(event) ? resists.pvpCap() : resists.cap();
        UUID uuid = target.getUniqueId();
        double factor = type == DamageType.PHYSICAL
                ? resists.physicalFactor(uuid, cap)
                : resists.magicFactor(uuid, cap);
        if (!Double.isFinite(factor) || factor >= 1.0 || factor < 0.0) {
            return;
        }
        event.setDamage(event.getDamage() * factor);
    }

    /**
     * 1.7.0.2: урон неотвратимой среды (FALL/DROWNING/SUFFOCATION/STARVATION)
     * умножается на maxHP/20, чтобы летальность соответствовала ванильной
     * независимо от классового пула HP. Гейт: damage-types.env-lethal-scale.
     * Мобы и игроки без увеличенного пула (scale == 1) не затрагиваются.
     */
    private void applyEnvLethalScale(EntityDamageEvent event, Player target) {
        if (!plugin.getConfig().getBoolean("damage-types.env-lethal-scale", true)) {
            return;
        }
        List<String> env = plugin.getConfig().getStringList("damage-types.env-lethal");
        if (!env.contains(event.getCause().name())) {
            return;
        }
        if (MAX_HEALTH == null) {
            return;
        }
        AttributeInstance instance = target.getAttribute(MAX_HEALTH);
        if (instance == null) {
            return;
        }
        double scale = instance.getValue() / 20.0;
        if (scale > 1.0 && Double.isFinite(scale)) {
            event.setDamage(event.getDamage() * scale);
        }
    }

    /** 1.6.4: единая формула «сколько дойдёт» (симулятор /rc debug). */
    public double simulateTaken(LivingEntity target, DamageProfile profile) {
        if (profile == null || target == null) {
            return 0.0;
        }
        DamageProfile safe = sanitize(profile);
        double truePart = Math.min(safe.trueDamage(), trueCap());
        if (resists.disabledIn(target.getWorld())) {
            return safe.physical() + safe.magic() + truePart;
        }
        if (target instanceof Player p) {
            UUID uuid = p.getUniqueId();
            double physFactor = resists.physicalFactor(uuid);
            double magicFactor = resists.magicFactor(uuid);
            double phys = Double.isFinite(physFactor) ? safe.physical() * physFactor : safe.physical();
            double magic = Double.isFinite(magicFactor) ? safe.magic() * magicFactor : safe.magic();
            return phys + magic + truePart;
        }
        return safe.physical() + safe.magic() + truePart;
    }

    /** Путь B: наш урон с профилем. */
    public double dealDamage(LivingEntity target, Entity source, DamageProfile profile) {
        if (profile == null || target == null || target.isDead()) {
            return 0.0;
        }
        DamageProfile safe = sanitize(profile);
        if (safe.isEmpty()) {
            return 0.0;
        }
        if (target instanceof Player tp) {
            GameMode gm = tp.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) {
                return 0.0;
            }
        }
        double truePart = Math.min(safe.trueDamage(), trueCap());
        double physPart;
        double magicTruePart;
        if (resists.disabledIn(target.getWorld())) {
            physPart = safe.physical();
            magicTruePart = safe.magic() + truePart;
        } else if (target instanceof Player p) {
            UUID uuid = p.getUniqueId();
            double cap = source instanceof Player ? resists.pvpCap() : resists.cap();
            double physFactor = resists.physicalFactor(uuid, cap);
            double magicFactor = resists.magicFactor(uuid, cap);
            physPart = Double.isFinite(physFactor) ? safe.physical() * physFactor : safe.physical();
            double magicScaled = Double.isFinite(magicFactor) ? safe.magic() * magicFactor : safe.magic();
            magicTruePart = magicScaled + truePart;
        } else {
            physPart = safe.physical();
            magicTruePart = safe.magic() + truePart;
        }
        double taken = physPart + magicTruePart;
        debugLog(target, source, safe, taken);
        if (physPart > 0.0) {
            SUPPRESS.set(Boolean.TRUE);
            try {
                if (source != null) {
                    target.damage(physPart, source);
                } else {
                    target.damage(physPart);
                }
            } finally {
                SUPPRESS.set(Boolean.FALSE);
            }
        }
        if (magicTruePart > 0.0) {
            SUPPRESS.set(Boolean.TRUE);
            try {
                org.bukkit.damage.DamageType magic = magicType();
                if (magic != null) {
                    DamageSource.Builder builder = DamageSource.builder(magic);
                    if (source != null) {
                        builder = builder.withDirectEntity(source).withCausingEntity(source);
                    }
                    target.damage(magicTruePart, builder.build());
                } else {
                    target.damage(magicTruePart);
                }
            } finally {
                SUPPRESS.set(Boolean.FALSE);
            }
        }
        return taken;
    }

    private void debugLog(LivingEntity target, Entity source, DamageProfile profile, double taken) {
        if (!plugin.getConfig().getBoolean("combat.debug-damage", false)) {
            return;
        }
        String resistInfo;
        if (target instanceof Player tp) {
            UUID uuid = tp.getUniqueId();
            resistInfo = String.format(Locale.ROOT, "резисты: физ %.0f%% / маг %.0f%%",
                    resists.physicalResist(uuid), resists.magicResist(uuid));
        } else {
            resistInfo = "резисты: физ 0% / маг 0%";
        }
        String line = String.format(Locale.ROOT,
                "[dmg] %s → %s: профиль %.1f физ / %.1f маг / %.1f чист → дошло %.1f (%s)",
                source != null ? source.getName() : "env",
                target.getName(),
                profile.physical(), profile.magic(), profile.trueDamage(),
                taken, resistInfo);
        Component message = Component.text(line, NamedTextColor.DARK_GRAY);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission("raskolclasses.debug")) {
                online.sendMessage(message);
            }
        }
    }

    private DamageType typeOf(EntityDamageEvent.DamageCause cause) {
        String override = plugin.getConfig()
                .getString("damage-types.vanilla-map." + cause.name());
        if (override != null && !override.isEmpty()) {
            try {
                return DamageType.valueOf(override.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // опечатка в конфиге — падаем в дефолт
            }
        }
        return DamageType.defaultFor(cause);
    }

    /** 1.6.11/1.6.9: санитаризация профиля — NaN/Infinity/отрицательные → 0. */
    private static DamageProfile sanitize(DamageProfile p) {
        double phys = Double.isFinite(p.physical()) && p.physical() >= 0 ? p.physical() : 0.0;
        double magic = Double.isFinite(p.magic()) && p.magic() >= 0 ? p.magic() : 0.0;
        double truth = Double.isFinite(p.trueDamage()) && p.trueDamage() >= 0 ? p.trueDamage() : 0.0;
        if (phys == p.physical() && magic == p.magic() && truth == p.trueDamage()) {
            return p;
        }
        return new DamageProfile(phys, magic, truth);
    }
}
