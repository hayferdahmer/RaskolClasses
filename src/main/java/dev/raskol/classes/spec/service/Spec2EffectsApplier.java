// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.service;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 1.14.0 «Спек 2.0» (Б2): применение постоянных модификаторов агрегата Spec2Service
 * к живым сервисам: атрибуты (STR/AGI/INT) и резисты (phys/magic) через
 * AttributeService/ResistService с source="spec2" (идемпотентно: сначала снять свои).
 *
 * 1.14.1 (Волна 1): heal-модификаторы (heal_out_pct, роль HEALER) здесь НЕ
 * применяются — они событийные и живут в PassiveListener/Spec2RoleListener.
 * 1.14.4 (П8): источник "spec2_el" для стихийных резистов (resist-<school> узлы).
 * 1.14.7 (Sprint 3, P0-6A, A4): modifier attack_speed из Spec2Service.attackSpeedPercent
 *         (узлы fury_frenzy / mm_swift_quiver) — закрывает «геттер без вызовов».
 *         Операция MULTIPLY_SCALAR_1: итоговая скорость атаки × (1 + pct/100).
 *         1.14.7-fix: имя операции исправлено с несуществующего ADD_SCALAR_1
 *         на MULTIPLY_SCALAR_1 (legacy-enum Bukkit: ADD_NUMBER / ADD_SCALAR /
 *         MULTIPLY_SCALAR_1).
 *
 * 1.15.7 (Волна 7c, аудит 2.6-77): добавлен modifier movement_speed из
 *         Spec2Service.moveSpeedPercent (узлы ol_improved_sprint / su_fleet_footed).
 *         Раньше move_speed_pct агрегировался в Spec2Service, но нигде не
 *         применялся к живому атрибуту игрока — узлы не действовали.
 *         Операция MULTIPLY_SCALAR_1: итоговая скорость × (1 + pct/100).
 */
public final class Spec2EffectsApplier {

    public static final String SOURCE = "spec2";
    /** 1.14.4 (П8): источник для стихийных резистов (resist-<school> узлы). */
    public static final String SOURCE_ELEMENTAL = "spec2_el";

    private final RaskolClasses plugin;
    private final NamespacedKey attackSpeedKey;
    private final Attribute attackSpeedAttr;
    /** 1.15.7 (2.6-77): ключ и атрибут для movement_speed modifier. */
    private final NamespacedKey moveSpeedKey;
    private final Attribute moveSpeedAttr;

    public Spec2EffectsApplier(RaskolClasses plugin) {
        this.plugin = plugin;
        this.attackSpeedKey = new NamespacedKey(plugin, "spec2_attack_speed");
        this.moveSpeedKey = new NamespacedKey(plugin, "spec2_move_speed");

        Attribute resolvedAttack = null;
        Attribute resolvedMove = null;
        try {
            var registry = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ATTRIBUTE);
            resolvedAttack = registry.get(NamespacedKey.minecraft("attack_speed"));
            resolvedMove = registry.get(NamespacedKey.minecraft("movement_speed"));
        } catch (RuntimeException ex) {
            // оставим null — apply пропустит применение
        }
        this.attackSpeedAttr = resolvedAttack;
        this.moveSpeedAttr = resolvedMove;
        if (resolvedAttack == null) {
            plugin.getLogger().warning("Spec2EffectsApplier: атрибут attack_speed не резолвится — "
                    + "узлы attack_speed_pct не будут применяться");
        }
        if (resolvedMove == null) {
            plugin.getLogger().warning("Spec2EffectsApplier: атрибут movement_speed не резолвится — "
                    + "узлы move_speed_pct не будут применяться");
        }
    }

    /** Применить модификаторы агрегата игрока (только если он онлайн). */
    public void apply(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) {
            return;
        }
        remove(uuid);
        Spec2Service.Agg agg = plugin.getSpec2Service().aggOf(uuid);
        if (agg.attrStr != 0.0 || agg.attrAgi != 0.0 || agg.attrInt != 0.0) {
            plugin.getAttributes().addPermanentModifier(
                    uuid, SOURCE, agg.attrStr, agg.attrAgi, agg.attrInt);
        }
        if (agg.resPhys != 0.0 || agg.resMagic != 0.0) {
            plugin.getResists().addPermanentModifier(uuid, SOURCE, agg.resPhys, agg.resMagic);
        }
        // 1.14.4 (П8): стихийные резисты
        if (!agg.elResist.isEmpty()) {
            var elem = plugin.getCombat().elemental();
            for (var entry : agg.elResist.entrySet()) {
                var school = entry.getKey();
                double value = entry.getValue();
                if (value != 0.0 && school != null) {
                    elem.addPermanent(uuid, SOURCE_ELEMENTAL, school, value);
                }
            }
        }
        // 1.14.7 (P0-6A, A4): скорость атаки из узлов attack_speed_pct
        applyAttackSpeed(player, plugin.getSpec2Service().attackSpeedPercent(uuid));
        // 1.15.7 (2.6-77): скорость передвижения из узлов move_speed_pct
        applyMoveSpeed(player, plugin.getSpec2Service().moveSpeedPercent(uuid));
    }

    /**
     * MULTIPLY_SCALAR_1 modifier на attack_speed: amount = pct/100 (6% → 0.06),
     * т.е. итоговая скорость атаки = база × (1 + 0.06). Идемпотентно: старый
     * modifier с нашим ключом снимается в remove().
     */
    private void applyAttackSpeed(Player player, double pct) {
        if (attackSpeedAttr == null || !Double.isFinite(pct) || pct == 0.0) {
            return;
        }
        AttributeInstance inst = player.getAttribute(attackSpeedAttr);
        if (inst == null) {
            return;
        }
        inst.addModifier(new AttributeModifier(
                attackSpeedKey, pct / 100.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                org.bukkit.inventory.EquipmentSlotGroup.ANY));
    }

    /**
     * 1.15.7 (2.6-77): MULTIPLY_SCALAR_1 modifier на movement_speed.
     * amount = pct/100 (10% → 0.10), итоговая скорость = база × (1 + 0.10).
     * Идемпотентно: старый modifier снимается в remove().
     */
    private void applyMoveSpeed(Player player, double pct) {
        if (moveSpeedAttr == null || !Double.isFinite(pct) || pct == 0.0) {
            return;
        }
        AttributeInstance inst = player.getAttribute(moveSpeedAttr);
        if (inst == null) {
            return;
        }
        inst.addModifier(new AttributeModifier(
                moveSpeedKey, pct / 100.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                org.bukkit.inventory.EquipmentSlotGroup.ANY));
    }

    /** Снять все модификаторы источников spec2 и spec2_el (quit, сброс, reconcile-пусто). */
    public void remove(UUID uuid) {
        plugin.getAttributes().removeModifiersBySource(uuid, SOURCE);
        plugin.getResists().removeModifiersBySource(uuid, SOURCE);
        // 1.14.4 (П8): стихийные резисты
        plugin.getCombat().elemental().removeBySource(uuid, SOURCE_ELEMENTAL);
        // 1.14.7 (A4): attack-speed modifier
        if (attackSpeedAttr != null) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                AttributeInstance inst = player.getAttribute(attackSpeedAttr);
                if (inst != null) {
                    inst.getModifiers().stream()
                            .filter(m -> attackSpeedKey.equals(m.getKey()))
                            .toList()
                            .forEach(inst::removeModifier);
                }
            }
        }
        // 1.15.7 (2.6-77): move-speed modifier
        if (moveSpeedAttr != null) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                AttributeInstance inst = player.getAttribute(moveSpeedAttr);
                if (inst != null) {
                    inst.getModifiers().stream()
                            .filter(m -> moveSpeedKey.equals(m.getKey()))
                            .toList()
                            .forEach(inst::removeModifier);
                }
            }
        }
    }
}
