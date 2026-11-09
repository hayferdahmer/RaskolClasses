// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.spec.build;

import java.util.Map;

/**
 * 1.14.15: immutable-модель кураторского шаблона билда raskolspec+.
 *
 * Поля:
 *  - spec       id спеки (ключ Spec2Registry, например "arms");
 *  - archetype  PVP | PVE;
 *  - name       отображаемое имя в GUI, например "Оружие · Дуэлянт";
 *  - tagline    короткое описание роли;
 *  - rawRanks   ИСХОДНЫЕ ранги из YAML до auto-heal (1.15.6, 2.6-94);
 *               нужны для корректной маркировки stale (шаблон устарел после
 *               баланс-патча): флаг ставится по пересечению rawRanks со стоп-листом
 *               spec2.disabled-nodes, а не по нормализованным ranks (которые уже
 *               не содержат disabled-узлов после simulateApply).
 *  - ranks      НОРМАЛИЗОВАННЫЕ ранги — множество, пережившее reconcile
 *               (вырезаны узлы из стоп-листа, без невыполненных prereq-цепочек,
 *               ниже ряд-гейтов). Именно эти ранги применяются к игроку при
 *               applyBuild и отображаются в GUI.
 *  - updated    строка версии патча, для которой шаблон актуален (например "1.14.15");
 *               используется для маркировки «актуально для патча X» в GUI и для
 *               предупреждений, когда шаблон отстаёт от текущего jar.
 *
 * Валидация структуры выполняется в BuildTemplates при загрузке; здесь — только
 * иммутабельность и базовые null-checks в фабрике of(...).
 *
 * 1.15.6 (Волна 6 часть 2, аудит 2.6-94): добавлено поле rawRanks для корректной
 * stale-маркировки. Раньше hasStaleNode проверяла нормализованные ranks, из которых
 * auto-heal уже вырезал disabled-узлы — флаг почти всегда был пуст. Теперь проверка
 * идёт по rawRanks (то, что куратор писал в YAML), а ranks остаются чистым
 * применимым множеством.
 *
 * 1.15.6 (Волна 6 часть 2): javadoc переведён на русский.
 */
public record BuildTemplate(
        String spec,
        Archetype archetype,
        String name,
        String tagline,
        Map<String, Integer> rawRanks,
        Map<String, Integer> ranks,
        String updated) {
    public BuildTemplate {
        rawRanks = Map.copyOf(rawRanks);
        ranks = Map.copyOf(ranks);
    }

    @Override
    public Map<String, Integer> rawRanks() {
        return Map.copyOf(rawRanks);
    }

    @Override
    public Map<String, Integer> ranks() {
        return Map.copyOf(ranks);
    }

    /**
     * Основная фабрика с rawRanks (1.15.6, 2.6-94).
     * Валидация против реестра деревьев — в BuildTemplates.validate().
     *
     * @param rawRanks исходные ранги из YAML (до auto-heal) — для stale-маркировки
     * @param ranks    нормализованные ранги (после auto-heal) — для применения
     */
    public static BuildTemplate of(
            String spec,
            Archetype archetype,
            String name,
            String tagline,
            Map<String, Integer> rawRanks,
            Map<String, Integer> ranks,
            String updated) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("spec пустой");
        }
        if (archetype == null) {
            throw new IllegalArgumentException("archetype is null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name пустой");
        }
        if (tagline == null) {
            throw new IllegalArgumentException("tagline is null");
        }
        if (rawRanks == null) {
            throw new IllegalArgumentException("rawRanks is null");
        }
        if (ranks == null) {
            throw new IllegalArgumentException("ranks is null");
        }
        // Защитные копии: ранги — маленькие (≤ ~25), аллокация несущественна.
        Map<String, Integer> rawSnapshot = Map.copyOf(rawRanks);
        Map<String, Integer> ranksSnapshot = Map.copyOf(ranks);
        // Валидация обоих наборов — защита от мусора в YAML и от simulateApply-багов.
        validateRanks(rawSnapshot, spec, "rawRanks");
        validateRanks(ranksSnapshot, spec, "ranks");
        return new BuildTemplate(
                spec, archetype, name, tagline, rawSnapshot, ranksSnapshot, updated == null ? "" : updated);
    }

    /**
     * Convenience-фабрика для случая, когда rawRanks = ranks (нет auto-heal или
     * внешние точки создания). Делегирует в 7-параметровую версию.
     * Сохранена для обратной совместимости со старыми вызовами.
     */
    public static BuildTemplate of(
            String spec, Archetype archetype, String name, String tagline, Map<String, Integer> ranks, String updated) {
        return of(spec, archetype, name, tagline, ranks, ranks, updated);
    }

    /**
     * Приватная валидация карты рангов: ключи непустые, значения неотрицательные.
     * Вызывается для rawRanks и ranks отдельно.
     */
    private static void validateRanks(Map<String, Integer> snapshot, String spec, String label) {
        for (Map.Entry<String, Integer> e : snapshot.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                throw new IllegalArgumentException(label + ": ключ ранга пустой в spec=" + spec);
            }
            if (e.getValue() == null || e.getValue() < 0) {
                throw new IllegalArgumentException(label + ": некорректный ранг " + e + " в spec=" + spec);
            }
        }
    }

    /**
     * Сумма очков, вложенных в шаблон (по НОРМАЛИЗОВАННЫМ рангам — именно столько
     * будет потрачено при applyBuild).
     */
    public int spentPoints() {
        int sum = 0;
        for (int r : ranks.values()) {
            sum += r;
        }
        return sum;
    }

    /**
     * Число ненулевых узлов в шаблоне (по НОРМАЛИЗОВАННЫМ рангам).
     */
    public int nodeCount() {
        int count = 0;
        for (int r : ranks.values()) {
            if (r > 0) count++;
        }
        return count;
    }

    /**
     * Число ненулевых узлов в ИСХОДНОМ шаблоне (до auto-heal). Используется для
     * отображения «вырезано N узлов» в GUI и логах.
     */
    public int rawNodeCount() {
        int count = 0;
        for (int r : rawRanks.values()) {
            if (r > 0) count++;
        }
        return count;
    }

    /**
     * Сумма очков в ИСХОДНОМ шаблоне (до auto-heal). Может превышать
     * {@link dev.raskol.classes.spec.model.Spec2Points.Snapshot#maxPoints()} —
     * именно поэтому auto-heal вырезает узлы.
     */
    public int rawSpentPoints() {
        int sum = 0;
        for (int r : rawRanks.values()) {
            sum += r;
        }
        return sum;
    }
}
