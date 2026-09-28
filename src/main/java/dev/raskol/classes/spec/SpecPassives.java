// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 1.11.4 (P4e): спек-резисты как генерик (F7): источник модификатора = spec.id(),
 * числа — из config resist.specs.<id>.physical/magic (guardian 10 физ, hell_channel 6 маг).
 * Self-reconcile каждые 20 тиков (F10): сверка модификаторов с фактической спекой
 * по всем онлайн-игрокам (админ-смена класса, ручные правки spec-choices.yml).
 */
public final class SpecPassives {

    private final RaskolClasses plugin;

    public SpecPassives(RaskolClasses plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                this::reconcilePassiveResists, 40L, 20L);
    }

    public double resistPhys(Spec spec) {
        double v = plugin.getConfig().getDouble(
                "resist.specs." + spec.id() + ".physical",
                spec == Spec.GUARDIAN ? 10.0 : 0.0);
        return Double.isFinite(v) ? v : 0.0;
    }

    public double resistMagic(Spec spec) {
        double v = plugin.getConfig().getDouble(
                "resist.specs." + spec.id() + ".magic",
                spec == Spec.HELL_CHANNEL ? 6.0 : 0.0);
        return Double.isFinite(v) ? v : 0.0;
    }

    private boolean hasResist(Spec spec) {
        return resistPhys(spec) != 0.0 || resistMagic(spec) != 0.0;
    }

    /** Снять ВСЕ спек-резисты игрока (респец, mismatch класса, фолиант). */
    public void removeAllSpecResists(UUID uuid) {
        for (Spec spec : Spec.values()) {
            plugin.getResists().removeModifiersBySource(uuid, spec.id());
        }
    }

    /** Применить резисты текущей спеки (идемпотентно: сначала снять свои). */
    public void applySpecResists(Player player, Spec spec) {
        UUID uuid = player.getUniqueId();
        plugin.getResists().removeModifiersBySource(uuid, spec.id());
        if (hasResist(spec)) {
            plugin.getResists().addPermanentModifier(uuid, spec.id(),
                    resistPhys(spec), resistMagic(spec));
        }
    }

    /** Восстановление на join (ResistService чистит модификаторы на quit). */
    public void restorePassiveResists(Player player) {
        Spec spec = plugin.getSpecService().getSpec(player.getUniqueId());
        if (spec == null) {
            removeAllSpecResists(player.getUniqueId());
            return;
        }
        applySpecResists(player, spec);
    }

    /** Сверка по онлайну: модификатор есть ровно тогда, когда спека активна и резист ненулевой. */
    public void reconcilePassiveResists() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Spec spec = plugin.getSpecService().getSpec(uuid); // валидация сама сбросит mismatch
            for (Spec s : Spec.values()) {
                boolean should = spec == s && hasResist(s);
                boolean has = plugin.getResists().hasModifier(uuid, s.id());
                if (should && !has) {
                    plugin.getResists().addPermanentModifier(uuid, s.id(),
                            resistPhys(s), resistMagic(s));
                } else if (!should && has) {
                    plugin.getResists().removeModifiersBySource(uuid, s.id());
                }
            }
        }
    }
}
