// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.service;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.combat.school.School;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * 1.14.0 «Спек 2.0» (Б2): применение постоянных модификаторов агрегата Spec2Service
 * к живым сервисам: атрибуты (STR/AGI/INT) и резисты (phys/magic) через
 * AttributeService/ResistService с source="spec2" (идемпотентно: сначала снять свои).
 *
 * 1.14.4 (Волна 4, П8): добавлен источник "spec2_el" для стихийных резистов из
 * узлов resist-<school>. Каждый элемент agg.elResist прокидывается в
 * ElementalResistService.addPermanent(uuid, "spec2_el", school, value).
 * Источник общий, снимается одним вызовом removeModifiersBySource — идемпотентно.
 */
public final class Spec2EffectsApplier {

    public static final String SOURCE = "spec2";
    /** 1.14.4 (П8): источник для стихийных резистов (resist-<school> узлы). */
    public static final String SOURCE_ELEMENTAL = "spec2_el";

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
        // 1.14.4 (П8): стихийные резисты
        if (!agg.elResist.isEmpty()) {
            var elem = plugin.getCombat().elemental();
            for (Map.Entry<School, Double> entry : agg.elResist.entrySet()) {
                School school = entry.getKey();
                double value = entry.getValue();
                if (value != 0.0 && school != null) {
                    elem.addPermanent(uuid, SOURCE_ELEMENTAL, school, value);
                }
            }
        }
    }

    /** Снять все модификаторы источников spec2 и spec2_el (quit, сброс, reconcile-пусто). */
    public void remove(UUID uuid) {
        plugin.getAttributes().removeModifiersBySource(uuid, SOURCE);
        plugin.getResists().removeModifiersBySource(uuid, SOURCE);
        // 1.14.4 (П8): стихийные резисты
        plugin.getCombat().elemental().removeBySource(uuid, SOURCE_ELEMENTAL);
    }
}
