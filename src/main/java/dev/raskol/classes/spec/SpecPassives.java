// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 1.11.4 (P4e): спек-резисты как генерик (F7): источник модификатора = spec.id(),
 * числа — из config resist.specs.<id>.physical/magic.
 * 1.14.0 (Б2b): поддержка 18 активных спек + алиасы legacy→новые
 * (DEMONOLOGY получает +6 маг как наследник HELL_CHANNEL; DISCIPLINE +5 маг,
 * AFFLICTION +4 маг, ARMOR GUARDIAN +10 физ сохранён).
 * Self-reconcile каждые 20 тиков (F10).
 */
public final class SpecPassives {

    private final RaskolClasses plugin;

    public SpecPassives(RaskolClasses plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                this::reconcilePassiveResists, 40L, 20L);
    }

    /**
     * Физ-резист спеки. Читаем из конфига; дефолты заданы для трёх исторических
     * «жёстких» спек (GUARDIAN 10 физ, AFFLICTION 4 маг как наследник black_mage
     * с усилением магической школы, DEMONOLOGY 6 маг как наследник hell_channel).
     */
    public double resistPhys(Spec spec) {
        double v = plugin.getConfig().getDouble(
                "resist.specs." + spec.id() + ".physical",
                spec == Spec.GUARDIAN ? 10.0 : 0.0);
        return Double.isFinite(v) ? v : 0.0;
    }

    public double resistMagic(Spec spec) {
        double v = plugin.getConfig().getDouble(
                "resist.specs." + spec.id() + ".magic",
                switch (spec) {
                    case DEMONOLOGY -> 6.0;   // наследие HELL_CHANNEL
                    case DISCIPLINE -> 5.0;   // спека щитов → маг-защита
                    case AFFLICTION -> 4.0;   // колдовство = магическая школа
                    default -> 0.0;
                });
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
            Spec spec = plugin.getSpecService().getSpec(uuid);
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
