// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.service;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 1.14.0 «Спек 2.0» (Б2): применение постоянных модификаторов агрегата Spec2Service
 * к живым сервисам: атрибуты (STR/AGI/INT) и резисты (phys/magic) через
 * AttributeService/ResistService с source="spec2" (идемпотентно: сначала снять свои).
 *
 * Пен/DoT/CC-бонусы модификаторами НЕ хранятся — их читают хот-путём
 * CCService/DotService/PenTraits(Б3) напрямую из агрегата Spec2Service.
 * Вызывается: reconcile (онлайн-игрок), join, после chooseMain/resetTree.
 *
 * 1.14.1 (Волна 1): добавлена обработка heal_out_pct для роли HEALER.
 */
public final class Spec2EffectsApplier {

    public static final String SOURCE = "spec2";
    public static final String SOURCE_HEAL = "spec2_heal";

    private final RaskolClasses plugin;

    public Spec2EffectsApplier(RaskolClasses plugin) {
        this.plugin = plugin;
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
        // 1.14.1 (Волна 1): heal_out_pct для роли HEALER
        if (agg.healOutPct != 0.0) {
            plugin.getAttributes().addPermanentModifier(
                    uuid, SOURCE_HEAL, 0.0, 0.0, 0.0, agg.healOutPct);
        }
    }

    /** Снять все модификаторы источника spec2 (quit, сброс, reconcile-пусто). */
    public void remove(UUID uuid) {
        plugin.getAttributes().removeModifiersBySource(uuid, SOURCE);
        plugin.getAttributes().removeModifiersBySource(uuid, SOURCE_HEAL);
        plugin.getResists().removeModifiersBySource(uuid, SOURCE);
    }
}
