// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Состояние боевых эффектов спеков (1.4.0, Пакет 2):
 * временные бафы, armed-флаги, атрибутные модификаторы.
 * Чистится в общем purge-таске (purgeExpired).
 */
public final class SpecEffects {

    private final RaskolClasses plugin;

    /** Берсерк: «Вспышка ярости» — +N урона до момента. */
    private final Map<UUID, Long> rageBurstUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Double> rageBurstBonus = new ConcurrentHashMap<>();

    /** Стрелок: «Точный выстрел» — следующая стрела крит. */
    private final Map<UUID, Boolean> preciseArmed = new ConcurrentHashMap<>();

    /** Мороз: внутренний кд замедления по цели. */
    private final Map<UUID, Long> frostSlowUntil = new ConcurrentHashMap<>();

    public SpecEffects(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    // --- rage burst ---
    public void startRageBurst(UUID uuid, long millis, double bonus) {
        rageBurstUntil.put(uuid, System.currentTimeMillis() + millis);
        rageBurstBonus.put(uuid, bonus);
    }

    public double rageBurstBonus(UUID uuid) {
        Long until = rageBurstUntil.get(uuid);
        if (until == null || until <= System.currentTimeMillis()) {
            return 0.0;
        }
        return rageBurstBonus.getOrDefault(uuid, 0.0);
    }

    // --- precise shot ---
    public void armPrecise(UUID uuid) {
        preciseArmed.put(uuid, true);
    }

    public boolean consumePrecise(UUID uuid) {
        return preciseArmed.remove(uuid) != null;
    }

    // --- frost slow internal cd ---
    public boolean tryFrostSlow(UUID target, long cooldownMillis) {
        long now = System.currentTimeMillis();
        Long until = frostSlowUntil.get(target);
        if (until != null && until > now) {
            return false;
        }
        frostSlowUntil.put(target, now + cooldownMillis);
        return true;
    }

    // --- атрибуты (Страж: броня, Следопыт: скорость) ---
    public void applyAttributes(Player player, Spec spec) {
        removeAttributes(player);
        if (spec == Spec.GUARDIAN) {
            AttributeInstance armor = player.getAttribute(Attribute.GENERIC_ARMOR);
            if (armor != null) {
                armor.addModifier(new AttributeModifier(
                        new NamespacedKey(plugin, "spec_guardian_armor"),
                        2.0, AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        if (spec == Spec.TRACKER) {
            AttributeInstance speed = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speed != null) {
                speed.addModifier(new AttributeModifier(
                        new NamespacedKey(plugin, "spec_tracker_speed"),
                        0.10, AttributeModifier.Operation.ADD_SCALAR));
            }
        }
    }

    public void removeAttributes(Player player) {
        AttributeInstance armor = player.getAttribute(Attribute.GENERIC_ARMOR);
        if (armor != null) {
            armor.getModifiers().stream()
                    .filter(m -> m.key().getNamespace().equals(plugin.getName().toLowerCase()))
                    .toList()
                    .forEach(armor::removeModifier);
        }
        AttributeInstance speed = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.getModifiers().stream()
                    .filter(m -> m.key().getNamespace().equals(plugin.getName().toLowerCase()))
                    .toList()
                    .forEach(speed::removeModifier);
        }
    }

    /** Вызывается из общего purge-таска. */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        rageBurstUntil.entrySet().removeIf(e -> e.getValue() <= now);
        rageBurstBonus.entrySet().removeIf(e -> !rageBurstUntil.containsKey(e.getKey()));
        frostSlowUntil.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
