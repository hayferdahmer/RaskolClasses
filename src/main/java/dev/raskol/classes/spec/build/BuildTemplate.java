// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.build;

import java.util.Map;

/**
 * 1.14.15: immutable-модель кураторского шаблона билда raskolspec+.
 *
 * Поля:
 *  - spec      id спеки (ключ Spec2Registry, например "arms");
 *  - archetype PVP | PVE;
 *  - name      отображаемое имя в GUI, например "Оружие · Дуэлянт";
 *  - tagline   короткое описание роли;
 *  - ranks     ранги узлов: id → rank (1..maxRank), узлы без ранга отсутствуют;
 *  - updated   строка версии патча, для которой шаблон актуален (например "1.14.15");
 *              используется для маркировки «актуально для патча X» в GUI и для
 *              предупреждений, когда шаблон отстаёт от текущего jar.
 *
 * Валидация структуры выполняется в BuildTemplates при загрузке; здесь — только
 * иммутабельность и базовые null-checks в фабрике of(...).
 */
public record BuildTemplate(
        String spec,
        Archetype archetype,
        String name,
        String tagline,
        Map<String, Integer> ranks,
        String updated) {

    /**
     * Фабрика с базовой защитой от мусора. Валидация против реестра деревьев —
     * в BuildTemplates.validate().
     */
    public static BuildTemplate of(String spec, Archetype archetype, String name,
                                    String tagline, Map<String, Integer> ranks,
                                    String updated) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("spec is blank");
        }
        if (archetype == null) {
            throw new IllegalArgumentException("archetype is null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is blank");
        }
        if (tagline == null) {
            throw new IllegalArgumentException("tagline is null");
        }
        if (ranks == null) {
            throw new IllegalArgumentException("ranks is null");
        }
        // Защитная копия: ранги — маленькие (≤ ~25), аллокация несущественна.
        Map<String, Integer> snapshot = Map.copyOf(ranks);
        for (Map.Entry<String, Integer> e : snapshot.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                throw new IllegalArgumentException("rank key blank in spec=" + spec);
            }
            if (e.getValue() == null || e.getValue() < 0) {
                throw new IllegalArgumentException("rank invalid: " + e + " in spec=" + spec);
            }
        }
        return new BuildTemplate(spec, archetype,
                name, tagline, snapshot,
                updated == null ? "" : updated);
    }

    /** Сумма очков, вложенных в шаблон. */
    public int spentPoints() {
        int sum = 0;
        for (int r : ranks.values()) {
            sum += r;
        }
        return sum;
    }

    /** Число ненулевых узлов в шаблоне. */
    public int nodeCount() {
        int count = 0;
        for (int r : ranks.values()) {
            if (r > 0) count++;
        }
        return count;
    }
}
