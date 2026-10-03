// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.registry;

import dev.raskol.classes.spec.model.Spec2Tree;
import dev.raskol.classes.spec.registry.trees.HunterTrees;
import dev.raskol.classes.spec.registry.trees.MageTrees;
import dev.raskol.classes.spec.registry.trees.PriestTrees;
import dev.raskol.classes.spec.registry.trees.RogueTrees;
import dev.raskol.classes.spec.registry.trees.WarlockTrees;
import dev.raskol.classes.spec.registry.trees.WarriorTrees;

import java.util.HashMap;
import java.util.Map;

/**
 * 1.14.0: реестр деревьев spec2. Наполняется из per-class файлов trees/*Trees.java.
 * Б5: Warrior, Hunter, Rogue. Б6: Mage, Priest. Б7: Warlock (18/18 деревьев).
 */
public final class Spec2Registry {

    private static final Map<String, Spec2Tree> TREES = new HashMap<>();

    static {
        WarriorTrees.register(TREES);
        HunterTrees.register(TREES);
        RogueTrees.register(TREES);
        MageTrees.register(TREES);
        PriestTrees.register(TREES);
        WarlockTrees.register(TREES);
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
