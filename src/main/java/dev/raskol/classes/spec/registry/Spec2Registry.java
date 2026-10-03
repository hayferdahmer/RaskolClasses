// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.registry;

import dev.raskol.classes.spec.model.Spec2Tree;
import dev.raskol.classes.spec.registry.trees.WarriorTrees;

import java.util.HashMap;
import java.util.Map;

/**
 * 1.14.0 «Спек 2.0»: реестр деревьев. Наполняется из per-class файлов
 * registry/trees/*Trees.java (мелкие файлы, по классу):
 * WarriorTrees (Б1: arms; 1.14.4: fury/guard), HunterTrees (1.14.5),
 * RogueTrees (1.14.5), MageTrees (1.14.6), PriestTrees (1.14.6), WarlockTrees (1.14.7).
 */
public final class Spec2Registry {

    private static final Map<String, Spec2Tree> TREES = new HashMap<>();

    static {
        WarriorTrees.register(TREES);
    }

    private Spec2Registry() {
    }

    public static Spec2Tree treeOf(String specId) {
        return TREES.get(specId);
    }

    public static Map<String, Spec2Tree> all() {
        return Map.copyOf(TREES);
    }
}
