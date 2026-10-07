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
 * 1.14.1 (Волна 1): heal-модификаторы (heal_out_pct, роль HEALER) здесь НЕ
 * применяются — они событийные и живут в PassiveListener.onRegainHealth
 * (поле агрегата называется healOut, а не healOutPct; перманентных
 * heal-модификаторов в AttributeService нет). Applier трогает только attr/resist.
 */
public final class Spec2EffectsApplier {

    public static final String SOURCE = "spec2";

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
    }

    /** Снять все модификаторы источника spec2 (quit, сброс, reconcile-пусто). */
    public void remove(UUID uuid) {
        plugin.getAttributes().removeModifiersBySource(uuid, SOURCE);
        plugin.getResists().removeModifiersBySource(uuid, SOURCE);
    }
}
