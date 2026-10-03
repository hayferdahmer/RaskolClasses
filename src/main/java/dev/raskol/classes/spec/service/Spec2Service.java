// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.spec.service;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.14.0 «Спек 2.0»: рантайм деревьев путей.
 * Покупка ранга = 1 очко из общего пула 46 (charLevel 15→60); инвестировать можно
 * в любое из 3 деревьев своего класса; основная спека выбирается один раз на 15
 * (chooseMain) и даёт роль (SpecRoles) + роли-пассивку (Spec2RoleListener).
 *
 * reconcile(uuid): валидация рангов (ряд-гейты по очкам дерева, ранговые
 * пререквизиты, maxRank) → агрегация эффектов ×ранг в кэш Agg → применение
 * постоянных модификаторов через Spec2EffectsApplier → хот-путь геттеры для
 * китов и сервисов (имена совпадают со старым TalentService, чтобы киты не менять).
 */
public final class Spec2Service {

    public enum PurchaseResult {
        OK, DISABLED, RATE_LIMITED, NO_MAIN, WRONG_CLASS_TREE, TREE_NOT_FOUND,
        NODE_NOT_FOUND, ROW_GATE, PREREQ, MAX_RANK, NOT_ENOUGH_POINTS
    }

    public enum ResetResult {
        OK, RATE_LIMITED, NO_MAIN, TREE_NOT_FOUND, NO_RANKS, POOR, NO_ECONOMY
    }

    /** Агрегат эффектов игрока (пересобирается только в reconcile). */
    public static final class Agg {
        public final Map<String, Double> base = new HashMap<>();
        public final Map<String, Double> coeff = new HashMap<>();
        public final Map<String, Double> cdPct = new HashMap<>();
        public final Map<String, Double> cdSec = new HashMap<>();
        public final Map<String, Double> proc = new HashMap<>();
        public final Map<String, Double> dotDur = new HashMap<>();
        public final Map<String, Double> dotStacks = new HashMap<>();
        public final Map<String, Double> dotMult = new HashMap<>();
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

    /** Роль основной спеки игрока (null если спека не выбрана). */
    public SpecRole roleOfOwner(UUID uuid) {
        String main = storage.getMain(uuid);
        return main == null ? null : SpecRoles.roleOf(main);
    }

    /** Выбор основной спеки: один раз, бесплатно, с charLevel ≥ 15, спека своего класса. */
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
            case WARLOCK -> List.of("witchcraft", "destruction", "demonology");
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

    /** Валидация: ряд-гейт по накопленным очкам + ранговые пререквизиты + maxRank. */
    private Map<String, Integer> validate(Spec2Tree tree, Map<String, Integer> raw) {
        Map<String, Integer> kept = new HashMap<>();
        List<Spec2Node> sorted = new ArrayList<>(tree.nodes());
        sorted.sort(Comparator.comparingInt(Spec2Node::row));
        for (Spec2Node node : sorted) {
            Integer rank = raw.get(node.id());
            if (rank == null || rank <= 0) {
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
                if ("both".equals(e.target())) { agg.resPhys += e.value(); agg.resMagic += e.value2(); }
                else if ("phys".equals(e.target())) agg.resPhys += e.value();
                else if ("magic".equals(e.target())) agg.resMagic += e.value();
            }
            case "kit_base" -> agg.base.merge(e.target(), e.value(), Double::sum);
            case "kit_mult" -> agg.coeff.merge(e.target(), e.value(), Double::sum);
            case "cd" -> agg.cdPct.merge(e.target(), e.value(), Double::sum);
            case "kit_cd" -> agg.cdSec.merge(e.target(), e.value(), Double::sum);
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
            default -> agg.proc.merge(e.kind(), e.value(), Double::sum);
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

    /** Публичный доступ к агрегату для Spec2EffectsApplier и хот-путей сервисов. */
    public Agg aggOf(UUID uuid) {
        return agg(uuid);
    }

    /* ------------------------------ хот-путь геттеры (имена старого TalentService) ------------------------------ */

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

    public double[] avoidBonus(UUID uuid) {
        Agg a = agg(uuid);
        return new double[]{a.dodge, a.parry};
    }

    public double regenBonus(UUID uuid) {
        return agg(uuid).regen;
    }

    /* ------------------------------ spec2-геттеры для сервисов 1.12–1.13 ------------------------------ */

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

    /** CC-резист: узлы cc_resist + роль TANK (+5% из spec2.role-passives). */
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

    /** Способности, открытые узлами деревьев (unlock_ability/ultimate). */
    public Set<String> unlockedAbilities(UUID uuid) {
        return Set.copyOf(agg(uuid).unlocked);
    }

    public boolean hasUnlocked(UUID uuid, String abilityId) {
        return agg(uuid).unlocked.contains(abilityId);
    }
}
