// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.bootstrap.lifecycle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * Проверяет граф зависимостей модулей и строит детерминированный порядок запуска.
 *
 * <p>Зависимость A → B означает, что модуль A должен быть запущен до B.</p>
 */
public final class ModuleDependencyResolver {

    private ModuleDependencyResolver() {}

    /**
     * Возвращает неизменяемый порядок модулей с учётом зависимостей.
     * При равной готовности модулей используется лексикографический порядок id.
     *
     * @throws IllegalArgumentException при повторяющихся id, отсутствующих
     *                                  зависимостях или цикле
     */
    public static List<PluginModule> resolve(Collection<? extends PluginModule> modules) {
        Objects.requireNonNull(modules, "modules");

        Map<String, PluginModule> byId = new HashMap<>();
        Map<String, Set<String>> dependenciesById = new HashMap<>();

        for (PluginModule module : modules) {
            Objects.requireNonNull(module, "module");
            String id = requireId(module.id());
            if (byId.putIfAbsent(id, module) != null) {
                throw new IllegalArgumentException("Повторяющийся идентификатор модуля: " + id);
            }

            Set<String> dependencies = Objects.requireNonNull(
                    module.dependencies(), "dependencies модуля " + id);
            Set<String> normalized = new TreeSet<>();
            for (String dependency : dependencies) {
                normalized.add(requireId(dependency));
            }
            dependenciesById.put(id, Set.copyOf(normalized));
        }

        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();

        for (String id : byId.keySet()) {
            indegree.put(id, 0);
            dependents.put(id, new ArrayList<>());
        }

        for (Map.Entry<String, Set<String>> entry : dependenciesById.entrySet()) {
            String moduleId = entry.getKey();
            for (String dependencyId : entry.getValue()) {
                if (!byId.containsKey(dependencyId)) {
                    throw new IllegalArgumentException(
                            "Модуль " + moduleId + " зависит от отсутствующего модуля " + dependencyId);
                }
                if (moduleId.equals(dependencyId)) {
                    throw new IllegalArgumentException("Модуль не может зависеть от самого себя: " + moduleId);
                }
                indegree.compute(moduleId, (ignored, value) -> value + 1);
                dependents.get(dependencyId).add(moduleId);
            }
        }

        PriorityQueue<String> ready = new PriorityQueue<>(Comparator.naturalOrder());
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });

        List<PluginModule> ordered = new ArrayList<>(byId.size());
        while (!ready.isEmpty()) {
            String id = ready.remove();
            ordered.add(byId.get(id));

            for (String dependentId : dependents.get(id)) {
                int remaining = indegree.compute(dependentId, (ignored, value) -> value - 1);
                if (remaining == 0) {
                    ready.add(dependentId);
                }
            }
        }

        if (ordered.size() != byId.size()) {
            Set<String> cyclic = new TreeSet<>(byId.keySet());
            for (PluginModule module : ordered) {
                cyclic.remove(module.id());
            }
            throw new IllegalArgumentException("Обнаружен цикл зависимостей модулей: " + cyclic);
        }

        return List.copyOf(ordered);
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "module id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Идентификатор модуля не может быть пустым");
        }
        return id;
    }
}
