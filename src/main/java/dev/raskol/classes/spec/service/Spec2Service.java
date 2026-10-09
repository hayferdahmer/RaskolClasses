// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.service;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.hook.EconomyHook;
import dev.raskol.classes.spec.SpecRole;
import dev.raskol.classes.spec.SpecRoles;
import dev.raskol.classes.spec.model.Spec2Node;
import dev.raskol.classes.spec.model.Spec2Points;
import dev.raskol.classes.spec.model.Spec2Tree;
import dev.raskol.classes.spec.registry.Spec2Registry;
import dev.raskol.classes.spec.storage.Spec2Storage;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.14.0 «Спек 2.0»: рантайм деревьев путей.
 * 1.14.3 (3A): процентные геттеры видов из default-ветки accumulate.
 * 1.14.4 (Волна 4): resetNode (П7), resist-школы в agg.elResist (П8),
 *         Spec2Points.configure (П9), spec2-storage.yml (П10).
 * 1.14.6-fix (Sprint 1, P0-2): default-ветка accumulate срезает префикс "proc_".
 * 1.14.7 (Sprint 2, P0-6B): стоп-лист spec2.disabled-nodes (NODE_DISABLED + прунинг).
 * 1.14.7 (Sprint 3, P0-3 вариант C): agg.procAmp — сумма value2 proc-узлов
 *         (сила за ранг); ProcService.strength() читает её раньше конфига.
 * 1.14.7 (Sprint 3, P0-6A): agg.kitDur — бонус длительности способностей из
 *         kit_dur-узлов (ключ = abilityId, значение = секунды за ранг).
 */
public final class Spec2Service {

    public enum PurchaseResult {
        OK, DISABLED, RATE_LIMITED, NO_MAIN, WRONG_CLASS_TREE, TREE_NOT_FOUND,
        NODE_NOT_FOUND, NODE_DISABLED, ROW_GATE, PREREQ, MAX_RANK, NOT_ENOUGH_POINTS
    }

    public enum ResetResult {
        OK, RATE_LIMITED, NO_MAIN, TREE_NOT_FOUND, NO_RANKS, POOR, NO_ECONOMY
    }

    /** 1.14.4 (П7): результат респеца одного ранга узла. */
    public enum NodeResetResult {
        OK, RATE_LIMITED, NO_MAIN, TREE_NOT_FOUND, NODE_NOT_FOUND,
        NO_RANKS, POOR, NO_ECONOMY
    }

    /** Агрегат эффектов игрока (пересобирается только в reconcile). */
    public static final class Agg {
        public final Map<String, Double> base = new HashMap<>();
        public final Map<String, Double> coeff = new HashMap<>();
        public final Map<String, Double> cdPct = new HashMap<>();
        public final Map<String, Double> cdSec = new HashMap<>();
        public final Map<String, Double> proc = new HashMap<>();
        /** 1.14.7 (контракт C): сила проков за ранг (Σ value2), ключ = id без proc_. */
        public final Map<String, Double> procAmp = new HashMap<>();
        /** 1.14.7 (P0-6A): бонус длительности способностей из kit_dur-узлов, ключ = abilityId. */
        public final Map<String, Double> kitDur = new HashMap<>();
        public final Map<String, Double> dotDur = new HashMap<>();
        public final Map<String, Double> dotStacks = new HashMap<>();
        public final Map<String, Double> dotMult = new HashMap<>();
        /** 1.14.4 (П8): стихийный резист из узлов resist-<school>. */
        public final EnumMap<School, Double> elResist = new EnumMap<>(School.class);
        public final Set<String> unlocked = new java.util.HashSet<>();
        public double attrStr, attrAgi, attrInt;
        public double resPhys, resMagic;
        public double dodge, parry;
        public double regen;
        public double penPhys, penMagic;
        public double physDmgPct, magicDmgPct;
        public double critMelee, healOut, execThreshold;
        public double ccPower, ccResist, ccDur;
        public double wpPct, spPct, hpowPct;
    }

    private final RaskolClasses plugin;
    private final Spec2Storage storage;
    private final EconomyHook economy;
    private final Map<UUID, Agg> caches = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActionMs = new ConcurrentHashMap<>();

    public Spec2Service(RaskolClasses plugin, Spec2Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.economy = new EconomyHook(plugin);
    }

    public Spec2Storage storage() {
        return storage;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("spec2.enabled", true);
    }

    /* ------------------------------ 1.14.7 (P0-6B): стоп-лист узлов ------------------------------ */

    /** Узел в оперативном стоп-листе spec2.disabled-nodes («в разработке»)? */
    public boolean nodeDisabled(String nodeId) {
        if (nodeId == null) {
            return false;
        }
        List<String> list = plugin.getConfig().getStringList("spec2.disabled-nodes");
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (String s : list) {
            if (nodeId.equals(s == null ? null : s.trim())) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------ очки ------------------------------ */

    public int earnedPoints(UUID uuid) {
        return Spec2Points.earnedPoints(plugin.getCharacterLevels().characterLevel(uuid));
    }

    public int spentGlobal(UUID uuid) {
        int sum = 0;
        for (String treeId : classTreeIds(uuid)) {
            Spec2Tree tree = Spec2Registry.treeOf(treeId);
            if (tree != null) {
                sum += tree.spentInTree(storage.getRanks(uuid, treeId));
            }
        }
        return sum;
    }

    public int availablePoints(UUID uuid) {
        return Math.max(0, earnedPoints(uuid) - spentGlobal(uuid));
    }

    /* ------------------------------ основная спека и роль ------------------------------ */

    public String mainSpec(UUID uuid) {
        return storage.getMain(uuid);
    }

    public SpecRole roleOfOwner(UUID uuid) {
        String main = storage.getMain(uuid);
        return main == null ? null : SpecRoles.roleOf(main);
    }

    public boolean chooseMain(Player player, String specId) {
        if (!SpecRoles.isKnown(specId)) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        if (storage.getMain(uuid) != null) {
            return false;
        }
        if (plugin.getCharacterLevels().characterLevel(uuid) < Spec2Points.START_LEVEL) {
            return false;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null || !treesOf(pc).contains(specId)) {
            return false;
        }
        storage.setMain(uuid, specId);
        storage.save();
        reconcile(uuid);
        return true;
    }

    public List<String> classTreeIds(UUID uuid) {
        Player online = plugin.getServer().getPlayer(uuid);
        PlayerClass pc = online != null ? plugin.getClassProvider().getClassOf(online) : null;
        return pc == null ? List.of() : treesOf(pc);
    }

    private List<String> treesOf(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> List.of("arms", "fury", "guard");
            case HUNTER -> List.of("marksmanship", "survival", "beastmaster");
            case PRIEST -> List.of("discipline", "holy", "shadow");
            case MAGE -> List.of("arcane", "fire", "frost");
            case ROGUE -> List.of("assassination", "outlaw", "subtlety");
            case WARLOCK -> List.of("affliction", "destruction", "demonology");
        };
    }

    /* ------------------------------ rate-limit ------------------------------ */

    private boolean actionAllowed(UUID uuid) {
        long now = System.currentTimeMillis();
        Long prev = lastActionMs.get(uuid);
        long window = Math.max(50L, plugin.getConfig().getInt("performance.cast-click-cooldown-ms", 150));
        if (prev != null && now - prev < window) {
            return false;
        }
        lastActionMs.put(uuid, now);
        return true;
    }

    /* ------------------------------ покупка / сброс ------------------------------ */

    public PurchaseResult purchase(Player player, String treeId, String nodeId) {
        if (!enabled()) {
            return PurchaseResult.DISABLED;
        }
        UUID uuid = player.getUniqueId();
        if (!actionAllowed(uuid)) {
            return PurchaseResult.RATE_LIMITED;
        }
        if (storage.getMain(uuid) == null) {
            return PurchaseResult.NO_MAIN;
        }
        if (!classTreeIds(uuid).contains(treeId)) {
            return PurchaseResult.WRONG_CLASS_TREE;
        }
        Spec2Tree tree = Spec2Registry.treeOf(treeId);
        if (tree == null) {
            return PurchaseResult.TREE_NOT_FOUND;
        }
        Spec2Node node = tree.find(nodeId);
        if (node == null) {
            return PurchaseResult.NODE_NOT_FOUND;
        }
        // 1.14.7 (P0-6B): узел в стоп-листе — очки не тратятся
        if (nodeDisabled(nodeId)) {
            return PurchaseResult.NODE_DISABLED;
        }
        Map<String, Integer> ranks = storage.getRanks(uuid, treeId);
        if (!tree.canBuy(node, ranks, availablePoints(uuid))) {
            int spent = tree.spentInTree(ranks);
            if (ranks.getOrDefault(nodeId, 0) >= node.maxRank()) {
                return PurchaseResult.MAX_RANK;
            }
            if (!Spec2Points.rowUnlocked(node.row(), spent)) {
                return PurchaseResult.ROW_GATE;
            }
            if (!tree.prereqsMet(node, ranks)) {
                return PurchaseResult.PREREQ;
            }
            return PurchaseResult.NOT_ENOUGH_POINTS;
        }
        ranks.merge(nodeId, 1, Integer::sum);
        storage.setRanks(uuid, treeId, ranks);
        reconcile(uuid);
        storage.save();
        return PurchaseResult.OK;
    }

    /** Платный сброс дерева (очки возвращаются в пул). */
    public ResetResult resetTree(Player player, String treeId, boolean free) {
        UUID uuid = player.getUniqueId();
        if (!actionAllowed(uuid)) {
            return ResetResult.RATE_LIMITED;
        }
        if (storage.getMain(uuid) == null) {
            return ResetResult.NO_MAIN;
        }
        Spec2Tree tree = Spec2Registry.treeOf(treeId);
        if (tree == null) {
            return ResetResult.TREE_NOT_FOUND;
        }
        Map<String, Integer> ranks = storage.getRanks(uuid, treeId);
        if (ranks.isEmpty()) {
            return ResetResult.NO_RANKS;
        }
        if (!free) {
            int spent = tree.spentInTree(ranks);
            int cost = plugin.getConfig().getInt("spec2.respec-base-cost", 250)
                    + plugin.getConfig().getInt("spec2.respec-per-level", 10) * spent;
            if (!economy.available()) {
                return ResetResult.NO_ECONOMY;
            }
            if (economy.balance(uuid) < cost) {
                return ResetResult.POOR;
            }
            economy.withdraw(uuid, cost);
        }
        storage.setRanks(uuid, treeId, new HashMap<>());
        reconcile(uuid);
        storage.save();
        return ResetResult.OK;
    }

    /**
     * 1.14.4 (П7): респец одного ранга узла.
     * 1.14.7 (P0-6B): для disabled-узлов респец РАЗРЕШЁН — это путь вернуть очки,
     * вложенные до попадания узла в стоп-лист.
     */
    public NodeResetResult resetNode(Player player, String treeId, String nodeId, boolean free) {
        UUID uuid = player.getUniqueId();
        if (!actionAllowed(uuid)) {
            return NodeResetResult.RATE_LIMITED;
        }
        if (storage.getMain(uuid) == null) {
            return NodeResetResult.NO_MAIN;
        }
        Spec2Tree tree = Spec2Registry.treeOf(treeId);
        if (tree == null) {
            return NodeResetResult.TREE_NOT_FOUND;
        }
        Spec2Node node = tree.find(nodeId);
        if (node == null) {
            return NodeResetResult.NODE_NOT_FOUND;
        }
        Map<String, Integer> ranks = storage.getRanks(uuid, treeId);
        int currentRank = ranks.getOrDefault(nodeId, 0);
        if (currentRank <= 0) {
            return NodeResetResult.NO_RANKS;
        }
        if (!free) {
            int base = plugin.getConfig().getInt("spec2.node-respec-base", 150);
            int perRank = plugin.getConfig().getInt("spec2.node-respec-per-rank", 50);
            int cost = base + perRank * currentRank;
            if (!economy.available()) {
                return NodeResetResult.NO_ECONOMY;
            }
            if (economy.balance(uuid) < cost) {
                return NodeResetResult.POOR;
            }
            economy.withdraw(uuid, cost);
        }
        if (currentRank == 1) {
            ranks.remove(nodeId);
        } else {
            ranks.put(nodeId, currentRank - 1);
        }
        storage.setRanks(uuid, treeId, ranks);
        reconcile(uuid);
        storage.save();
        return NodeResetResult.OK;
    }

    /** 1.14.4 (П7): цена респеца одного ранга узла (для GUI-сообщения). */
    public int nodeResetCost(String treeId, String nodeId, UUID uuid) {
        Spec2Tree tree = Spec2Registry.treeOf(treeId);
        if (tree == null) {
            return 0;
        }
        int rank = storage.getRanks(uuid, treeId).getOrDefault(nodeId, 0);
        if (rank <= 0) {
            return 0;
        }
        int base = plugin.getConfig().getInt("spec2.node-respec-base", 150);
        int perRank = plugin.getConfig().getInt("spec2.node-respec-per-rank", 50);
        return base + perRank * rank;
    }

    /* ------------------------------ reconcile + агрегация ------------------------------ */

    public void reconcile(UUID uuid) {
        Agg agg = new Agg();
        for (String treeId : classTreeIds(uuid)) {
            Spec2Tree tree = Spec2Registry.treeOf(treeId);
            if (tree == null) {
                continue;
            }
            Map<String, Integer> raw = storage.getRanks(uuid, treeId);
            Map<String, Integer> kept = validate(tree, raw);
            if (kept.size() != raw.size()) {
                storage.setRanks(uuid, treeId, kept);
                plugin.getLogger().warning("spec2: ранги дерева " + treeId + " игрока " + uuid
                        + " прорежены до валидных (" + kept.size() + " из " + raw.size() + ")");
            }
            for (Map.Entry<String, Integer> e : kept.entrySet()) {
                Spec2Node node = tree.find(e.getKey());
                if (node == null || node.effect() == null) {
                    continue;
                }
                accumulate(agg, node, e.getValue());
            }
        }
        caches.put(uuid, agg);
        if (plugin.getSpec2Applier() != null) {
            plugin.getSpec2Applier().apply(uuid);
        }
    }

    /**
     * Валидация: ряд-гейт по накопленным очкам + ранговые пререквизиты + maxRank.
     * 1.14.7 (P0-6B): disabled-узлы не сохраняются — вложенные ранги прунятся,
     * очки возвращаются в пул (spentGlobal пересчитывается из kept).
     */
    private Map<String, Integer> validate(Spec2Tree tree, Map<String, Integer> raw) {
        Map<String, Integer> kept = new HashMap<>();
        List<Spec2Node> sorted = new ArrayList<>(tree.nodes());
        sorted.sort(Comparator.comparingInt(Spec2Node::row));
        for (Spec2Node node : sorted) {
            Integer rank = raw.get(node.id());
            if (rank == null || rank <= 0) {
                continue;
            }
            if (nodeDisabled(node.id())) {
                continue;
            }
            int r = Math.min(rank, node.maxRank());
            if (!Spec2Points.rowUnlocked(node.row(), tree.spentInTree(kept))) {
                continue;
            }
            if (!tree.prereqsMet(node, kept)) {
                continue;
            }
            kept.put(node.id(), r);
        }
        return kept;
    }

    private void accumulate(Agg agg, Spec2Node node, int rank) {
        var e = node.effect().scaledBy(rank);
        switch (e.kind()) {
            case "attr" -> {
                if ("str".equals(e.target())) agg.attrStr += e.value();
                else if ("agi".equals(e.target())) agg.attrAgi += e.value();
                else if ("int".equals(e.target())) agg.attrInt += e.value();
            }
            case "resist" -> {
                School school = School.fromId(e.target());
                if (school != null) {
                    agg.elResist.merge(school, e.value(), Double::sum);
                } else if ("both".equals(e.target())) {
                    agg.resPhys += e.value();
                    agg.resMagic += e.value2();
                } else if ("phys".equals(e.target())) {
                    agg.resPhys += e.value();
                } else if ("magic".equals(e.target())) {
                    agg.resMagic += e.value();
                }
            }
            case "kit_base" -> agg.base.merge(e.target(), e.value(), Double::sum);
            case "kit_mult" -> agg.coeff.merge(e.target(), e.value(), Double::sum);
            case "cd" -> agg.cdPct.merge(e.target(), e.value(), Double::sum);
            case "kit_cd" -> agg.cdSec.merge(e.target(), e.value(), Double::sum);
            // 1.14.7 (P0-6A): kit_dur — бонус длительности по ЦЕЛИ (abilityId)
            case "kit_dur" -> agg.kitDur.merge(e.target(), e.value(), Double::sum);
            case "regen" -> agg.regen += e.value();
            case "avoid" -> {
                if ("dodge".equals(e.target())) agg.dodge += e.value();
                else if ("parry".equals(e.target())) agg.parry += e.value();
            }
            case "proc" -> agg.proc.merge(e.target(), e.value(), Double::sum);
            case "pen_phys_pct" -> agg.penPhys += e.value();
            case "pen_magic_pct" -> agg.penMagic += e.value();
            case "phys_dmg_pct" -> agg.physDmgPct += e.value();
            case "magic_dmg_pct" -> agg.magicDmgPct += e.value();
            case "crit_melee_pct" -> agg.critMelee += e.value();
            case "heal_out_pct" -> agg.healOut += e.value();
            case "exec_threshold_pct" -> agg.execThreshold += e.value();
            case "dot_dur" -> agg.dotDur.merge(e.target(), e.value(), Double::sum);
            case "dot_stacks" -> agg.dotStacks.merge(e.target(), e.value(), Double::sum);
            case "dot_mult" -> agg.dotMult.merge(e.target(), e.value(), Double::sum);
            case "cc_power" -> agg.ccPower += e.value();
            case "cc_resist" -> agg.ccResist += e.value();
            case "cc_dur" -> agg.ccDur += e.value();
            case "wp_pct" -> agg.wpPct += e.value();
            case "sp_pct" -> agg.spPct += e.value();
            case "hpow_pct" -> agg.hpowPct += e.value();
            case "unlock_ability" -> agg.unlocked.add(e.target());
            // 1.14.6-fix (Sprint 1, P0-2): proc-узлы приходят как kind="proc_<id>",
            // ключ агрегата — БЕЗ префикса.
            // 1.14.7 (Sprint 3, контракт C): value → agg.proc (шанс за ранг),
            // value2 → agg.procAmp (сила за ранг; 0 = сила из конфига amp).
            default -> {
                String kind = e.kind();
                String key = kind.startsWith("proc_") ? kind.substring(5) : kind;
                agg.proc.merge(key, e.value(), Double::sum);
                if (e.value2() != 0.0) {
                    agg.procAmp.merge(key, e.value2(), Double::sum);
                }
            }
        }
    }

    public void clear(UUID uuid) {
        caches.remove(uuid);
        lastActionMs.remove(uuid);
    }

    /* ------------------------------ агрегат ------------------------------ */

    private Agg agg(UUID uuid) {
        Agg a = caches.get(uuid);
        if (a == null) {
            reconcile(uuid);
            a = caches.get(uuid);
        }
        return a == null ? new Agg() : a;
    }

    public Agg aggOf(UUID uuid) {
        return agg(uuid);
    }

    /* ------------------------------ хот-путь геттеры ------------------------------ */

    public double baseBonus(UUID uuid, String abilityId) {
        return agg(uuid).base.getOrDefault(abilityId, 0.0);
    }

    public double coeffMult(UUID uuid, String abilityId) {
        return 1.0 + agg(uuid).coeff.getOrDefault(abilityId, 0.0);
    }

    public double cooldownMult(UUID uuid, String abilityId) {
        return Math.max(0.1, 1.0 - agg(uuid).cdPct.getOrDefault(abilityId, 0.0));
    }

    public double cooldownSecBonus(UUID uuid, String abilityId) {
        return agg(uuid).cdSec.getOrDefault(abilityId, 0.0);
    }

    public double procBonus(UUID uuid, String procId) {
        return agg(uuid).proc.getOrDefault(procId, 0.0);
    }

    /** 1.14.7 (контракт C): Σ value2 proc-узла (сила за ранг); 0 = брать amp из конфига. */
    public double procAmpBonus(UUID uuid, String procId) {
        return agg(uuid).procAmp.getOrDefault(procId, 0.0);
    }

    /** 1.14.7 (P0-6A): Σ value kit_dur-узлов по способности (секунды). */
    public double kitDurBonus(UUID uuid, String abilityId) {
        return agg(uuid).kitDur.getOrDefault(abilityId, 0.0);
    }

    public double[] avoidBonus(UUID uuid) {
        Agg a = agg(uuid);
        return new double[]{a.dodge, a.parry};
    }

    public double regenBonus(UUID uuid) {
        return agg(uuid).regen;
    }

    /* ------------------------------ spec2-геттеры для сервисов ------------------------------ */

    public double penPercent(UUID uuid, String channel) {
        Agg a = agg(uuid);
        return "phys".equals(channel) ? a.penPhys : a.penMagic;
    }

    public double dotDurBonus(UUID uuid, String dotId) {
        return agg(uuid).dotDur.getOrDefault(dotId, 0.0);
    }

    public double dotStacksBonus(UUID uuid, String dotId) {
        return agg(uuid).dotStacks.getOrDefault(dotId, 0.0);
    }

    public double dotMultBonus(UUID uuid, String dotId) {
        return agg(uuid).dotMult.getOrDefault(dotId, 0.0);
    }

    public double ccResistBonus(UUID uuid) {
        double bonus = agg(uuid).ccResist;
        if (roleOfOwner(uuid) == SpecRole.TANK) {
            bonus += plugin.getConfig().getDouble("spec2.role-passives.TANK.cc-resist", 0.05);
        }
        return bonus;
    }

    public double ccPowerBonus(UUID uuid) {
        return agg(uuid).ccPower;
    }

    public double ccDurBonus(UUID uuid) {
        return agg(uuid).ccDur;
    }

    public double physDmgPercent(UUID uuid) {
        return agg(uuid).physDmgPct;
    }

    public double magicDmgPercent(UUID uuid) {
        return agg(uuid).magicDmgPct;
    }

    public double healOutPercent(UUID uuid) {
        return agg(uuid).healOut;
    }

    public double execThresholdBonus(UUID uuid) {
        return agg(uuid).execThreshold;
    }

    public double critMeleeBonus(UUID uuid) {
        return agg(uuid).critMelee;
    }

    public double wpPercent(UUID uuid) { return agg(uuid).wpPct; }
    public double spPercent(UUID uuid) { return agg(uuid).spPct; }
    public double hpowPercent(UUID uuid) { return agg(uuid).hpowPct; }

    /* ------------------------------ процентные геттеры видов из default-ветки ------------------------------ */

    public double hpPercent(UUID uuid) {
        return procBonus(uuid, "hp_pct");
    }

    public double resourceMaxBonus(UUID uuid) {
        return procBonus(uuid, "resource_max");
    }

    public double resourceRegenPercent(UUID uuid) {
        return procBonus(uuid, "resource_regen_pct");
    }

    public double blockPercent(UUID uuid) {
        return procBonus(uuid, "block_pct");
    }

    public double attackSpeedPercent(UUID uuid) {
        return procBonus(uuid, "attack_speed_pct");
    }

    public double moveSpeedPercent(UUID uuid) {
        return procBonus(uuid, "move_speed_pct");
    }

    public double healReceivedPercent(UUID uuid) {
        return procBonus(uuid, "heal_received_pct");
    }

    public double petHpPercent(UUID uuid) {
        return procBonus(uuid, "pet_hp_pct");
    }

    public double petDmgPercent(UUID uuid) {
        return procBonus(uuid, "pet_dmg_pct");
    }

    public Set<String> unlockedAbilities(UUID uuid) {
        return Set.copyOf(agg(uuid).unlocked);
    }

    public boolean hasUnlocked(UUID uuid, String abilityId) {
        return agg(uuid).unlocked.contains(abilityId);
    }
}
