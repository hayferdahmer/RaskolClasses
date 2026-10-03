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
 * 1.4.0 → 1.14.0: атрибутные модификаторы спек + внутренний КД заморозки FROST.
 * 1.14.0 (Б2b):
 *   - TRACKER (legacy) → SURVIVAL: скорость +0.10 (наследие следопыта);
 *   - BEAST_MASTER: +2 max HP (здоровье питомца-хозяина);
 *   - DISCIPLINE: +2 max HP (щиты и выносливость).
 * GUARDIAN armor +2 сохранён.
 */
public final class SpecEffects {

    private final RaskolClasses plugin;
    private final Map<UUID, Long> frostSlowUntil = new ConcurrentHashMap<>();

    public SpecEffects(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public boolean tryFrostSlow(UUID target, long cooldownMillis) {
        long now = System.currentTimeMillis();
        Long until = frostSlowUntil.get(target);
        if (until != null && until > now) {
            return false;
        }
        frostSlowUntil.put(target, now + cooldownMillis);
        return true;
    }

    public void applyAttributes(Player player, Spec spec) {
        removeAttributes(player);
        if (spec == Spec.GUARDIAN) {
            addModifier(player, "generic.armor", "spec_guardian_armor",
                    2.0, AttributeModifier.Operation.ADD_NUMBER);
        }
        if (spec == Spec.SURVIVAL) { // было TRACKER
            addModifier(player, "generic.movement_speed", "spec_survival_speed",
                    0.10, AttributeModifier.Operation.ADD_SCALAR);
        }
        if (spec == Spec.BEAST_MASTER) {
            addModifier(player, "generic.max_health", "spec_beast_master_hp",
                    2.0, AttributeModifier.Operation.ADD_NUMBER);
        }
        if (spec == Spec.DISCIPLINE) {
            addModifier(player, "generic.max_health", "spec_discipline_hp",
                    2.0, AttributeModifier.Operation.ADD_NUMBER);
        }
    }

    private void addModifier(Player player, String attrKey, String modKey,
                             double value, AttributeModifier.Operation op) {
        Attribute attr = Registry.ATTRIBUTE.get(new NamespacedKey("minecraft", attrKey));
        if (attr == null) {
            return;
        }
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.addModifier(new AttributeModifier(
                new NamespacedKey(plugin, modKey), value, op));
    }

    public void removeAttributes(Player player) {
        String ns = plugin.getName().toLowerCase();
        removeByNamespace(player, "generic.armor", ns);
        removeByNamespace(player, "generic.movement_speed", ns);
        removeByNamespace(player, "generic.max_health", ns);
    }

    private void removeByNamespace(Player player, String attrKey, String ns) {
        Attribute attr = Registry.ATTRIBUTE.get(new NamespacedKey("minecraft", attrKey));
        if (attr == null) {
            return;
        }
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.getModifiers().stream()
                .filter(m -> m.key().namespace().equals(ns))
                .toList()
                .forEach(inst::removeModifier);
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        frostSlowUntil.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
