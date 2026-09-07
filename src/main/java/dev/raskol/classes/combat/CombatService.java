// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
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
 * Путь A (ваниль): EntityDamageEvent по игроку — урон режется по типу причины
 *   (физ → физрезист, маг → магрезист; чистый не трогается). Ванильная броня
 *   применяется ПОСЛЕ нашего физрезиста штатно (cause ENTITY_ATTACK и т.п.).
 * Путь B (наши способности/инсталляции/спек-активки): dealDamage(target, source,
 *   profile) — сервис сам считает итог покомпонентно и применяет:
 *   физ-часть через обычную атаку (броня работает), маг+чистый — через
 *   DamageSource minecraft:magic (броню не трогает; резисты уже учтены нами).
 * FIX 1.6.0.2: НЕ импортируем org.bukkit.damage.DamageType — single-type import
 *   затенял наш enum DamageType из этого же пакета (TRUE/PHYSICAL «исчезали»).
 *   Ванильный тип magic держим полностью квалифицированным именем.
 * Двойного применения резиста нет: свои же вызовы помечаются ThreadLocal-флагом.
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
     */
    public double dealDamage(LivingEntity target, Entity source, DamageProfile profile) {
        if (profile == null || profile.isEmpty() || target == null || target.isDead()) {
            return 0.0;
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
        double dealt = 0.0;
        if (physPart > 0.0) {
            SUPPRESS.set(Boolean.TRUE);
            if (source != null) {
                target.damage(physPart, source);
            } else {
                target.damage(physPart);
            }
            dealt += physPart;
        }
        if (magicTruePart > 0.0) {
            SUPPRESS.set(Boolean.TRUE);
            org.bukkit.damage.DamageType magic = magicType();
            if (magic != null) {
                DamageSource.Builder builder = DamageSource.builder(magic);
                if (source != null) {
                    builder = builder.withDirectEntity(source).withCausingEntity(source);
                }
                target.damage(magicTruePart, builder.build());
            } else {
                // реестр недоступен (не должно случаться) — обычный урон без источника
                target.damage(magicTruePart);
            }
            dealt += magicTruePart;
        }
        return dealt;
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
