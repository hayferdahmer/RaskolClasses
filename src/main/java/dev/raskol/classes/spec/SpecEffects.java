// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Состояние боевых эффектов спек (1.4.0 → 1.11.4).
 * 1.11.4 (P4e, F8): удалены мёртвые с 1.7.5 ветки rageBurst/preciseArmed
 * (активки спек удалены, взводить их некому); остались:
 *  - внутренний КД заморозки (пассивка FROST);
 *  - атрибутные модификаторы GUARDIAN (броня) / TRACKER (скорость).
 */
public final class SpecEffects {

    private final RaskolClasses plugin;

    /** Мороз: внутренний кд замедления по цели. */
    private final Map<UUID, Long> frostSlowUntil = new ConcurrentHashMap<>();

    public SpecEffects(RaskolClasses plugin) {
        this.plugin = plugin;
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
            Attribute armorAttribute = Registry.ATTRIBUTE.get(
                    new NamespacedKey("minecraft", "generic.armor"));
            if (armorAttribute != null) {
                AttributeInstance armor = player.getAttribute(armorAttribute);
                if (armor != null) {
                    armor.addModifier(new AttributeModifier(
                            new NamespacedKey(plugin, "spec_guardian_armor"),
                            2.0, AttributeModifier.Operation.ADD_NUMBER));
                }
            }
        }
        if (spec == Spec.TRACKER) {
            Attribute speedAttribute = Registry.ATTRIBUTE.get(
                    new NamespacedKey("minecraft", "generic.movement_speed"));
            if (speedAttribute != null) {
                AttributeInstance speed = player.getAttribute(speedAttribute);
                if (speed != null) {
                    speed.addModifier(new AttributeModifier(
                            new NamespacedKey(plugin, "spec_tracker_speed"),
                            0.10, AttributeModifier.Operation.ADD_SCALAR));
                }
            }
        }
    }

    public void removeAttributes(Player player) {
        Attribute armorAttribute = Registry.ATTRIBUTE.get(
                new NamespacedKey("minecraft", "generic.armor"));
        if (armorAttribute != null) {
            AttributeInstance armor = player.getAttribute(armorAttribute);
            if (armor != null) {
                armor.getModifiers().stream()
                        .filter(m -> m.key().namespace().equals(plugin.getName().toLowerCase()))
                        .toList()
                        .forEach(armor::removeModifier);
            }
        }
        Attribute speedAttribute = Registry.ATTRIBUTE.get(
                new NamespacedKey("minecraft", "generic.movement_speed"));
        if (speedAttribute != null) {
            AttributeInstance speed = player.getAttribute(speedAttribute);
            if (speed != null) {
                speed.getModifiers().stream()
                        .filter(m -> m.key().namespace().equals(plugin.getName().toLowerCase()))
                        .toList()
                        .forEach(speed::removeModifier);
            }
        }
    }

    /** Вызывается из общего purge-таска. */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        frostSlowUntil.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
