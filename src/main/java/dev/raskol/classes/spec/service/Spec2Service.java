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
import java.util.HashSet;
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
 * 1.14.7 (Sprint 3, P0-3 вариант C): agg.procAmp — сумма value2 proc-узлов.
 * 1.14.7 (Sprint 3, P0-6A): agg.kitDur — бонус длительности по цели (abilityId).
 * 1.14.7 (Sprint 4, P1-5): resetNode отклоняется результатом HAS_DEPENDENTS, если
 *         после снятия ранга купленный включённый узел потеряет пререквизит или
 *         очки дерева упадут ниже гейта ряда с купленными узлами. Проверка на
 *         гипотетическом состоянии той же логикой, что validate/reconcile;
 *         выполняется ДО списания монет — состояние и экономика не меняются.
 *         Disabled-узлы зависимостями НЕ считаются (рангов держать не могут).
 *         Внимание: если disabled-узел служит пререквизитом купленному включённому,
 *         с 1.14.9 он считается выполненным (P0-6B), зависимый не прунится.
 * 1.14.7 (Sprint 4, P1-4): chooseMain читает Spec2Points.startLevel() (аксессор снимка).
 * 1.14.8 (Sprint 6, Волна 6.1, P0-6B): стоп-лист учитывается в пререквизитах:
 *         purchase/validate/hasDependents передают disabledSet() в prereqsMet/canBuy,
 *         поэтому отключённый пререквизит считается выполненным и включённые узлы
 *         (включая 6 ультов) снова достижимы. disabledSet() — единая точка чтения
 *         стоп-листа для сервиса, GUI и selftest-чека 122.
 * 1.14.8 (Sprint 6, Волна 6.3, P0-5-01): персист spec2-storage.yml переведён на
 *         периодическую autosave-задачу (storage.autosave-minutes) + onDisable;
 *         синхронные save() на каждый клик покупки/респеца/выбора спеки удалены.
 * 1.14.12 (Sprint 8, Волна 12A, P0-5-05): стоп-лист — неизменяемый снапшот
 *         (Set.copyOf): лениво строится на первом чтении, пересобирается
 *         refreshDisabledCache() на старте и /rc reload. nodeDisabled больше
 *         не читает конфиг и не аллоцирует на каждый вызов (hot-путь отрисовки книги).
 *         Поведенческая заметка: правка spec2.disabled-nodes в файле применяется
 *         после /rc reload (раньше — мгновенно на следующий вызов); это плата за
 *         отсутствие чтений конфига в hot-пути, согласуется с политикой reload.
 * 1.14.15 (raskolspec+): добавлен disabledNodesSnapshot() — говорящий алиас
 *         disabledSet() для BuildTemplates (stale-маркировка шаблонов).
 * 1.14.16 (Волна 15C): РЕАЛЬНАЯ apply-логика raskolspec+:
 *         - simulateApply(tree, ranks) — публичный симулятор validate(): единый
 *           источник правды «выживет ли набор рангов после reconcile» для
 *           BuildTemplates (валидация при загрузке) и applyBuild (пре-проверка);
 *         - applyBuild(player, treeId, ranks, free) — атомарное применение шаблона:
 *           пре-проверки (parity, очки, экономика) → списание респеца дерева →
 *           setRanks → reconcile → защитный rollback (PRUNED, недостижим из-за
 *           пре-проверок). Подписка НЕ проверяется здесь — сервис подписочно-
 *           независим, гейт держит GUI/команда.
 */
public final class Spec2Service {

    public enum PurchaseResult {
        OK, DISABLED, RATE_LIMITED, NO_MAIN, WRONG_CLASS_TREE, TREE_NOT_FOUND,
        NODE_NOT_FOUND, NODE_DISABLED, ROW_GATE, PREREQ, MAX_RANK, NOT_ENOUGH_POINTS
    }

    public enum ResetResult {
        OK, RATE_LIMITED, NO_MAIN, TREE_NOT_FOUND, NO_RANKS, POOR, NO_ECONOMY
    }

    /** 1.14.4 (П7): результат респеца одного ранга узла. 1.14.7 (P1-5): +HAS_DEPENDENTS. */
    public enum NodeResetResult {
        OK, RATE_LIMITED, NO_MAIN, TREE_NOT_FOUND, NODE_NOT_FOUND,
        NO_RANKS, POOR, NO_ECONOMY, HAS_DEPENDENTS
    }

    /**
     * 1.14.16 (Волна 15C): результат применения шаблона raskolspec+.
     *  - OK: ранги установлены и пережили reconcile;
     *  - RATE_LIMITED: окно 150 мс (actionAllowed);
     *  - NO_MAIN: основная спека не выбрана;
     *  - WRONG_CLASS_TREE: дерево не принадлежит классу игрока;
     *  - TREE_NOT_FOUND: дерева нет в реестре;
     *  - TEMPLATE_INVALID: simulateApply проредил набор (несоответствие row-gates/
     *    prereq-порядку) — для загруженных шаблонов недостижимо (валидатор загрузки
     *    имеет тот же parity-шаг), остаётся защитой от ручных/устаревших данных;
     *  - NOT_ENOUGH_POINTS: очков пула (available + spentTree) не хватает на шаблон;
     *  - NO_ECONOMY / POOR: Vault-экономика недоступна / не хватает монет на респец;
     *  - PRUNED: защитный rollback после коммита (теоретически недостижимо).
     */
    public enum BuildApplyResult {
        OK, RATE_LIMITED, NO_MAIN, WRONG_CLASS_TREE, TREE_NOT_FOUND,
        TEMPLATE_INVALID, NOT_ENOUGH_POINTS, NO_ECONOMY, POOR, PRUNED
    }

    /**
     * 1.14.16 (Волна 15C): исход применения шаблона с данными для сообщений GUI:
     * cost — списанная/требуемая стоимость респеца дерева; needPoints/poolPoints —
     * бюджет очков (для отказа NOT_ENOUGH_POINTS).
     */
    public record BuildApplyOutcome(BuildApplyResult result, int cost,
                                    int needPoints, int poolPoints) {
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
        public final Set<String> unlocked = new HashSet<>();
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

    /** 1.14.12 (12A, 5-05): снапшот стоп-листа; null = ещё не построен (лениво). */
    private volatile Set<String> disabledCache = null;

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

    /**
     * 1.14.12 (12A, 5-05): неизменяемый снапшот стоп-листа. Ленивая сборка +
     * пересборка в refreshDisabledCache(); без аллокаций и чтений конфига на вызов.
     * Возвращаемый Set иммутабелен (Set.copyOf) — потребители не могут испортить кэш.
     */
    public Set<String> disabledSet() {
        Set<String> local = disabledCache;
        if (local == null) {
            local = Set.copyOf(plugin.getConfig().getStringList("spec2.disabled-nodes"));
            disabledCache = local;
        }
        return local;
    }

    /**
     * 1.14.15 (raskolspec+): говорящий алиас disabledSet() для внешних потребителей
     * (BuildTemplates проверяет stale-маркировку шаблонов с узлом в стоп-листе).
     * Семантика идентична disabledSet(): иммутабельный снапшот, O(1).
     */
    public Set<String> disabledNodesSnapshot() {
        return disabledSet();
    }

    /**
     * 1.14.12 (12A, 5-05): пересборка снапшота стоп-листа.
     * Вызывается в RaskolClasses.onEnable (после ConfigMigrator) и reloadPlugin().
     */
    public void refreshDisabledCache() {
        disabledCache = Set.copyOf(plugin.getConfig().getStringList("spec2.disabled-nodes"));
    }

    /** Узел в оперативном стоп-листе spec2.disabled-nodes («в разработке»)? O(1), без аллокаций. */
    public boolean nodeDisabled(String nodeId) {
        return nodeId != null && disabledSet().contains(nodeId);
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
        // 1.14.7 (Sprint 4, P1-4): аксессор снимка вместо deprecated-поля
        if (plugin.getCharacterLevels().characterLevel(uuid) < Spec2Points.startLevel()) {
            return false;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null || !treesOf(pc).contains(specId)) {
            return false;
        }
        storage.setMain(uuid, specId);
        // 1.14.8 (Волна 6.3, P0-5-01): синхронный save убран — персист в autosave/onDisable
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

    /**
     * 1.14.8-fix (Волна 6.1, P0-6B): canBuy и дискриминация отказа получают стоп-лист —
     * отключённый пререквизит считается выполненным, включённые узлы достижимы.
     * 1.14.8 (Волна 6.3, P0-5-01): синхронный save убран — персист в autosave/onDisable.
     */
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
        Set<String> disabled = disabledSet();
        // узел в стоп-листе — очки не тратятся
        if (disabled.contains(nodeId)) {
            return PurchaseResult.NODE_DISABLED;
        }
        Map<String, Integer> ranks = storage.getRanks(uuid, treeId);
        if (!tree.canBuy(node, ranks, availablePoints(uuid), disabled)) {
            int spent = tree.spentInTree(ranks);
            if (ranks.getOrDefault(nodeId, 0) >= node.maxRank()) {
                return PurchaseResult.MAX_RANK;
            }
            if (!Spec2Points.rowUnlocked(node.row(), spent)) {
                return PurchaseResult.ROW_GATE;
            }
            if (!tree.prereqsMet(node, ranks, disabled)) {
                return PurchaseResult.PREREQ;
            }
            return PurchaseResult.NOT_ENOUGH_POINTS;
        }
        ranks.merge(nodeId, 1, Integer::sum);
        storage.setRanks(uuid, treeId, ranks);
        reconcile(uuid);
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
        // 1.14.8 (Волна 6.3, P0-5-01): синхронный save убран
        return ResetResult.OK;
    }

    /**
     * 1.14.4 (П7): респец одного ранга узла.
     * 1.14.7 (P0-6B): для disabled-узлов респец РАЗРЕШЁН — это путь вернуть очки,
     * вложенные до попадания узла в стоп-лист.
     * 1.14.7 (P1-5): HAS_DEPENDENTS — снятие ранга отклоняется, если купленный
     * включённый узел потеряет пререквизит или очки дерева упадут ниже гейта ряда
     * с купленными узлами. Проверка ДО экономики: монеты не списываются,
     * состояние и хранилище не меняются.
     * 1.14.8 (Волна 6.1): hasDependents учитывает стоп-лист в пререквизитах.
     * 1.14.8 (Волна 6.3, P0-5-01): синхронный save убран.
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
        // 1.14.7 (P1-5): гипотетическое состояние и проверка зависимостей
        if (hasDependents(tree, ranks, nodeId)) {
            return NodeResetResult.HAS_DEPENDENTS;
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
        return NodeResetResult.OK;
    }

    /**
     * 1.14.7 (P1-5): есть ли купленные включённые узлы, которые сломаются,
     * если у nodeId снять один ранг. Логика идентична validate():
     * пререквизиты + ряд-гейты по очкам дерева после снятия.
     * Disabled-узлы пропускаются: они не могут держать ранги (прунятся reconcile).
     * 1.14.8-fix (Волна 6.1): пререквизиты сверяются с учётом стоп-листа.
     */
    private boolean hasDependents(Spec2Tree tree, Map<String, Integer> ranks, String nodeId) {
        Set<String> disabled = disabledSet();
        Map<String, Integer> hyp = new HashMap<>(ranks);
        int reduced = hyp.getOrDefault(nodeId, 0) - 1;
        if (reduced <= 0) {
            hyp.remove(nodeId);
        } else {
            hyp.put(nodeId, reduced);
        }
        int spent = tree.spentInTree(hyp);
        for (Spec2Node n : tree.nodes()) {
            int owned = hyp.getOrDefault(n.id(), 0);
            if (owned <= 0) {
                continue;
            }
            if (disabled.contains(n.id())) {
                continue;
            }
            if (!tree.prereqsMet(n, hyp, disabled)) {
                return true;
            }
            if (!Spec2Points.rowUnlocked(n.row(), spent)) {
                return true;
            }
        }
        return false;
    }

    /** 1.14.4 (П7): цена респеца одного ранга узла (для GUI-сообщения). */
    public int nodeResetCost(String treeId, String nodeId, UUID uuid) {
        Spec2Tree tree = Spec2Registry.treeOf(treeId);
        if (tree == null) {
            return 0;
        }
        int rank = uuid == null ? 0 : storage.getRanks(uuid, treeId).getOrDefault(nodeId, 0);
        if (rank <= 0) {
            return 0;
        }
        int base = plugin.getConfig().getInt("spec2.node-respec-base", 150);
        int perRank = plugin.getConfig().getInt("spec2.node-respec-per-rank", 50);
        return base + perRank * rank;
    }

    /* ------------------------------ 1.14.16 (Волна 15C): применение шаблонов raskolspec+ ------------------------------ */

    /**
     * 1.14.16 (Волна 15C): публичный симулятор reconcile-валидации произвольного
     * набора рангов. Возвращает ранги, которые ПЕРЕЖИВУТ reconcile (ряд-гейты по
     * накопленным очкам в порядке рядов + пререквизиты по накопленному kept +
     * maxRank + disabled-прунинг). ЕДИНСТВЕННЫЙ источник правды для:
     *  - BuildTemplates.load() — parity-шаг валидатора загрузки (шаблон, который
     *    reconcile позже проредил бы, отклоняется на старте с warning);
     *  - applyBuild() — пре-проверка перед списанием монет (TEMPLATE_INVALID).
     * Тело идентично приватному validate(): дублирования логики нет.
     */
    public Map<String, Integer> simulateApply(Spec2Tree tree, Map<String, Integer> ranks) {
        return validate(tree, ranks);
    }

    /**
     * 1.14.16 (Волна 15C): атомарное применение шаблона raskolspec+ к дереву.
     *
     * Экономика: списывается ТОЛЬКО стоимость респека дерева
     * (spec2.respec-base-cost + spec2.respec-per-level × spentInTree(текущие ранги));
     * free=true (админ-путь raskolclasses.admin.plus) — без списания. Если дерево
     * пустое — респец не нужен, стоимость 0 даже при free=false.
     *
     * Очки: пул после сброса дерева = availablePoints(uuid) + spentInTree(current);
     * сумма рангов шаблона должна помещаться в пул (иначе NOT_ENOUGH_POINTS ДО списания).
     *
     * Порядок гарантий (инвариант «отказ не оставляет потерь»):
     *  1) rate-limit / main / класс-дерево / реестр;
     *  2) simulateApply-parity: шаблон должен переживать reconcile целиком;
     *  3) бюджет очков;
     *  4) экономика (available/balance) — списание ТОЛЬКО после пунктов 1–3;
     *  5) коммит: setRanks(шаблон) + reconcile;
     *  6) защитная сверка: если reconcile всё же проредил ранги (PRUNED, теоретически
     *     недостижимо из-за пункта 2) — rollback предыдущих рангов + reconcile +
     *     severe-лог; возврат монет НЕ выполняется (ситуация требует расследования
     *     админом, монеты списаны не будут, если отказ произошёл до пункта 4).
     *
     * Подписка raskolspec+ здесь НЕ проверяется: сервис подписочно-независим,
     * гейт держит PlusGui/команда (разделение ответственности 15A).
     */
    public BuildApplyOutcome applyBuild(Player player, String treeId,
                                        Map<String, Integer> ranks, boolean free) {
        UUID uuid = player.getUniqueId();
        if (!actionAllowed(uuid)) {
            return new BuildApplyOutcome(BuildApplyResult.RATE_LIMITED, 0, 0, 0);
        }
        if (storage.getMain(uuid) == null) {
            return new BuildApplyOutcome(BuildApplyResult.NO_MAIN, 0, 0, 0);
        }
        if (!classTreeIds(uuid).contains(treeId)) {
            return new BuildApplyOutcome(BuildApplyResult.WRONG_CLASS_TREE, 0, 0, 0);
        }
        Spec2Tree tree = Spec2Registry.treeOf(treeId);
        if (tree == null) {
            return new BuildApplyOutcome(BuildApplyResult.TREE_NOT_FOUND, 0, 0, 0);
        }
        // 2) parity: шаблон должен переживать reconcile целиком
        Map<String, Integer> kept = simulateApply(tree, ranks);
        if (!kept.equals(ranks)) {
            return new BuildApplyOutcome(BuildApplyResult.TEMPLATE_INVALID, 0, 0, 0);
        }
        // 3) бюджет очков после сброса дерева
        Map<String, Integer> current = storage.getRanks(uuid, treeId);
        int spentTree = tree.spentInTree(current);
        int pool = availablePoints(uuid) + spentTree;
        int need = 0;
        for (int rank : ranks.values()) {
            need += rank;
        }
        if (need > pool) {
            return new BuildApplyOutcome(BuildApplyResult.NOT_ENOUGH_POINTS, 0, need, pool);
        }
        // 4) экономика респеца — списание только после всех пре-проверок
        int cost = 0;
        if (!free && !current.isEmpty()) {
            cost = plugin.getConfig().getInt("spec2.respec-base-cost", 250)
                    + plugin.getConfig().getInt("spec2.respec-per-level", 10) * spentTree;
            if (!economy.available()) {
                return new BuildApplyOutcome(BuildApplyResult.NO_ECONOMY, cost, need, pool);
            }
            if (economy.balance(uuid) < cost) {
                return new BuildApplyOutcome(BuildApplyResult.POOR, cost, need, pool);
            }
        }
        if (cost > 0) {
            economy.withdraw(uuid, cost);
        }
        // 5) коммит
        storage.setRanks(uuid, treeId, new HashMap<>(ranks));
        reconcile(uuid);
        // 6) защитная сверка + rollback
        if (!storage.getRanks(uuid, treeId).equals(ranks)) {
            plugin.getLogger().severe("spec2: applyBuild PRUNED после пре-проверок — rollback: игрок "
                    + uuid + ", дерево " + treeId + " (сообщи разработчику: simulateApply/validate рассинхрон)");
            storage.setRanks(uuid, treeId, current);
            reconcile(uuid);
            return new BuildApplyOutcome(BuildApplyResult.PRUNED, cost, need, pool);
        }
        return new BuildApplyOutcome(BuildApplyResult.OK, cost, need, pool);
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
     * 1.14.8-fix (Волна 6.1): пререквизит из стоп-листа считается выполненным —
     * включённый зависимый узел больше не прунится из-за отключённого пререка.
     * 1.14.16 (Волна 15C): тело доступно публично через simulateApply() —
     * единый источник правды для BuildTemplates и applyBuild.
     */
    private Map<String, Integer> validate(Spec2Tree tree, Map<String, Integer> raw) {
        Map<String, Integer> kept = new HashMap<>();
        Set<String> disabled = disabledSet();
        List<Spec2Node> sorted = new ArrayList<>(tree.nodes());
        sorted.sort(Comparator.comparingInt(Spec2Node::row));
        for (Spec2Node node : sorted) {
            Integer rank = raw.get(node.id());
            if (rank == null || rank <= 0) {
                continue;
            }
            if (disabled.contains(node.id())) {
                continue;
            }
            int r = Math.min(rank, node.maxRank());
            if (!Spec2Points.rowUnlocked(node.row(), tree.spentInTree(kept))) {
                continue;
            }
            if (!tree.prereqsMet(node, kept, disabled)) {
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
            // 1.14.7 (Sprint 3, контракт C): value → agg.proc (шанс), value2 → agg.procAmp (сила).
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
