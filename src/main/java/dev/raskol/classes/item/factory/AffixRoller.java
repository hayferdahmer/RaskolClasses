// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.item.factory;

import dev.raskol.classes.item.model.Affix;
import dev.raskol.classes.item.model.ItemStat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Детерминированный взвешенный выбор аффиксов и ролл значений, масштабированных уровнем.
 *
 * 1.15.6 (Волна 6 часть 2, аудит 2.6-28): порядок clamp → round.
 * Раньше значение округлялось до десятых, а потом clamp'алось в [min, max] —
 * если round сдвигал значение за границы, clamp возвращал его в max/min,
 * но финальное значение было «обрезанным», а не «округлённым».
 * Теперь: raw значение clamp'ается в [min, max] ПЕРВЫМ шагом, и только
 * потом округляется до десятых. Результат всегда внутри [min, max] и корректно
 * округлён.
 */
public final class AffixRoller {

    private AffixRoller() {
    }

    /**
     * Ролл аффиксов для предмета.
     *
     * @param pool      пул доступных аффиксов (из ItemDefinition.affixPool)
     * @param count     количество аффиксов для ролла (не больше pool.size)
     * @param itemLevel уровень предмета [1..60]
     * @param seed      сид детерминированного ролла (из ItemPdc.rollSeed)
     * @return неизменяемая Map аффиксов и их значений
     */
    public static Map<ItemStat, Double> roll(List<Affix> pool, int count, int itemLevel, long seed) {
        if (itemLevel < 1 || itemLevel > 60 || count < 0) {
            throw new IllegalArgumentException("Некорректные параметры ролла: level="
                    + itemLevel + " count=" + count);
        }
        List<Affix> remaining = new ArrayList<>(pool);
        Map<ItemStat, Double> result = new EnumMap<>(ItemStat.class);
        SplittableRandom random = new SplittableRandom(seed);
        int n = Math.min(count, remaining.size());
        double levelFraction = (itemLevel - 1) / 59.0;
        for (int i = 0; i < n; i++) {
            double totalWeight = remaining.stream().mapToDouble(Affix::weight).sum();
            double point = random.nextDouble(totalWeight);
            int selected = 0;
            for (; selected < remaining.size() - 1; selected++) {
                point -= remaining.get(selected).weight();
                if (point < 0) break;
            }
            Affix affix = remaining.remove(selected);
            // Уровень масштабирует нижнюю/верхнюю границу ролла:
            // lo = min + 50% диапазона × levelFraction (низкоруровневые предметы ближе к min)
            // hi = min + (50% + 50% × levelFraction) диапазона (высокоуровневые ближе к max)
            double lo = affix.min() + (affix.max() - affix.min()) * 0.5 * levelFraction;
            double hi = affix.min() + (affix.max() - affix.min()) * (0.5 + 0.5 * levelFraction);
            double raw = lo + random.nextDouble() * (hi - lo);
            // 1.15.6 (2.6-28): clamp ПЕРВЫМ шагом, round вторым
            double clamped = affix.stat().clamp(raw);
            double rounded = Math.round(clamped * 10.0) / 10.0;
            result.put(affix.stat(), rounded);
        }
        return Map.copyOf(result);
    }
}
