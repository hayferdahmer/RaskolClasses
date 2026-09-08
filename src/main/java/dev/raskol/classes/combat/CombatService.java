// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

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
 *
 * 1.6.8: spectator/creative-гарды. Путь A: зрители пропускаются до вычислений.
 * Путь B: урон по зрителям и креативщикам не применяется вовсе (ваниль делает
 * их неуязвимыми) — не тратим вызовы и не рискуем флагами; согласовано с
 * правилом 1.5.1 «таргет-каст не тратит ресурс на creative/spectator».
 */
public final class CombatService implements Listener {

    /** Маркер «этот урон уже посчитан CombatService» (свои вызовы пути B). */
    private static final ThreadLocal<Boolean> SUPPRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Кэш ванильного типа урона minecraft:magic из реестра Paper. */
    private static volatile org.bukkit.damage.DamageType magicTypeCache;

    private final RaskolClasses plugin;
    private final ResistService resists;

    public CombatService(RaskolClasses plugin, ResistService resists) {
        this.plugin = plugin;
        this.resists = resists;
    }

    public ResistService resists() {
        return resists;
    }

    /** Тип урона minecraft:magic из реестра Paper (без класса-констант). */
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

    /** Путь A: ванильный урон по игроку режется резистом своего типа. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (Boolean.TRUE.equals(SUPPRESS.get())) {
            SUPPRESS.set(Boolean.FALSE);
            return; // урон уже посчитан dealDamage — резисты не применяем повторно
        }
        if (!(event.getEntity() instanceof Player target)) {
            return; // резисты пока только у игроков
        }
        // 1.6.8: зрители не получают урон — нечего и резистить
        if (target.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        DamageType type = typeOf(event.getCause());
        if (type == DamageType.TRUE) {
            return; // чистый урон резистами не режется
        }
        UUID uuid = target.getUniqueId();
        double factor = type == DamageType.PHYSICAL
                ? resists.physicalFactor(uuid)
                : resists.magicFactor(uuid);
        if (factor >= 1.0) {
            return;
        }
        event.setDamage(event.getDamage() * factor);
    }

    /**
     * Путь B: наш урон с профилем. Возвращает фактически нанесённый урон.
     * Формула: физ×(1−физрезист/100) + маг×(1−магрезист/100) + чистый.
     * Мобы резистов не имеют (урон проходит целиком).
     * 1.6.8: зрители и креативщики пропускаются (ванильная неуязвимость).
     */
    public double dealDamage(LivingEntity target, Entity source, DamageProfile profile) {
        if (profile == null || profile.isEmpty() || target == null || target.isDead()) {
            return 0.0;
        }
        if (target instanceof Player tp) {
            GameMode gm = tp.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) {
                return 0.0;
            }
        }
        double physPart;
        double magicTruePart;
        if (target instanceof Player p) {
            UUID uuid = p.getUniqueId();
            physPart = profile.physical() * resists.physicalFactor(uuid);
            magicTruePart = profile.magic() * resists.magicFactor(uuid)
                    + profile.trueDamage();
        } else {
            physPart = profile.physical();
            magicTruePart = profile.magic() + profile.trueDamage();
        }
        double taken = physPart + magicTruePart;
        debugLog(target, source, profile, taken);
        if (physPart > 0.0) {
            SUPPRESS.set(Boolean.TRUE);
            try {
                if (source != null) {
                    target.damage(physPart, source);
                } else {
                    target.damage(physPart);
                }
            } finally {
                // 1.6.1 (B3): событие диспатчится синхронно внутри damage();
                // пост-сброс безопасен и закрывает утечку, если события не было
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

    /**
     * 1.6.4: боевой лог нашего урона (гейт combat.debug-damage, дефолт false).
     * Пишет в чат всем онлайн-админам с raskolclasses.debug серой строкой:
     * профиль (физ/маг/чистый) → дошло → резисты цели.
     */
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

    /** Тип причины с учётом конфиг-оверрайдов damage-types.vanilla-map. */
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
}
