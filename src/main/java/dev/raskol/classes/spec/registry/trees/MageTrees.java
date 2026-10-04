package dev.raskol.classes.spec.registry.trees;

import dev.raskol.classes.model.RClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;

/**
 * Спека деревьев Мага.
 *
 * Профильные АураСкиллы: alchemy + sorcery.
 * Пассивные вехи привязаны к уровням класса (1/10/25/50/75) и маппятся
 * на абилки АураСкиллов, которые должны быть открыты на данной вехе.
 *
 * Деревья (из истории):
 *   alchemy: alchemist(1), brewer(10), splasher(25), lingering(50), wise_effect(75)
 *   sorcery: life_essence(1), sorcerer(10)
 */
public final class MageTrees {

    public static final RClass CLASS = RClass.MAGE;

    /** Профильные скиллы АураСкиллов для Мага. */
    public static final @NotNull @Unmodifiable List<String> PRIMARY_SKILLS =
            List.of("alchemy", "sorcery");

    /** Веха -> список абиллок АураСкиллов, открываемых на этой вехе. */
    public static final @NotNull @Unmodifiable Map<Integer, List<String>> PASSIVE_MILESTONES =
            Map.of(
                    1,  List.of("alchemist", "life_essence"),
                    10, List.of("brewer", "sorcerer"),
                    25, List.of("splasher"),
                    50, List.of("lingering"),
                    75, List.of("wise_effect")
            );

    /** Уровни вех в порядке возрастания. */
    public static final @NotNull @Unmodifiable List<Integer> MILESTONE_LEVELS =
            List.of(1, 10, 25, 50, 75);

    private MageTrees() {
        throw new UnsupportedOperationException("utility class");
    }

    /** Абиллки АураСкиллов, которые должны быть открыты на данном уровне класса. */
    public static @NotNull @Unmodifiable List<String> passivesAt(int level) {
        List<String> out = new java.util.ArrayList<>();
        for (int milestone : MILESTONE_LEVELS) {
            if (level >= milestone) {
                out.addAll(PASSIVE_MILESTONES.getOrDefault(milestone, List.of()));
            }
        }
        return java.util.Collections.unmodifiableList(out);
    }

    /** Является ли скилл АураСкиллов профильным для Мага. */
    public static boolean isPrimary(@NotNull String auraSkill) {
        return PRIMARY_SKILLS.contains(auraSkill.toLowerCase(java.util.Locale.ROOT));
    }
}
