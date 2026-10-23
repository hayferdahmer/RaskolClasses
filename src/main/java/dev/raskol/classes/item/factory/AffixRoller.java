// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.item.factory;

import dev.raskol.classes.item.model.Affix;
import dev.raskol.classes.item.model.ItemStat;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Deterministic weighted affix selection and level-scaled value rolls. */
public final class AffixRoller {
    private AffixRoller() { }
    public static java.util.Map<ItemStat, Double> roll(List<Affix> pool, int count, int itemLevel, long seed) {
        if (itemLevel < 1 || itemLevel > 60 || count < 0) throw new IllegalArgumentException("Invalid roll parameters");
        List<Affix> remaining = new ArrayList<>(pool);
        java.util.Map<ItemStat, Double> result = new java.util.EnumMap<>(ItemStat.class);
        SplittableRandom random = new SplittableRandom(seed);
        int n = Math.min(count, remaining.size());
        double lf = (itemLevel - 1) / 59.0;
        for (int i = 0; i < n; i++) {
            double totalWeight = remaining.stream().mapToDouble(Affix::weight).sum();
            double point = random.nextDouble(totalWeight);
            int selected = 0;
            for (; selected < remaining.size() - 1; selected++) {
                point -= remaining.get(selected).weight();
                if (point < 0) break;
            }
            Affix affix = remaining.remove(selected);
            double lo = affix.min() + (affix.max() - affix.min()) * 0.5 * lf;
            double hi = affix.min() + (affix.max() - affix.min()) * (0.5 + 0.5 * lf);
            double value = Math.round((lo + random.nextDouble() * (hi - lo)) * 10.0) / 10.0;
            result.put(affix.stat(), affix.stat().clamp(value));
        }
        return java.util.Map.copyOf(result);
    }
}
