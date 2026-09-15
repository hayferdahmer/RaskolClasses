// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.talent;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.hook.EconomyHook;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.talent.TalentModel.TalentNode;
import dev.raskol.classes.talent.TalentModel.TalentTree;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.9.0 → 1.9.2: рантайм талантов.
 *
 * 1.9.2 (эксплойт-свип):
 *  - ГЛОБАЛЬНЫЙ spent: доступные очки = earned − сумма потраченных по ВСЕМ деревьям
 *    игрока. Закрывает эксплойт «полное дерево в каждой спеке через респец»:
 *    очки теперь общий бюджет персонажа, а не бюджет активной спеки.
 *  - RECONCILE-ВАЛИДАЦИЯ хранилища: неизвестные id узлов и нарушения пререквизитов
 *    вырезаются из talents.yml самим сервером с WARNING (самолечение от ручных правок).
 *  - RATE-LIMIT покупок/сброса: не чаще performance.cast-click-cooldown-ms на игрока
 *    (защита от пакет-спама кликами по GUI).
 *
 * Reconcile вызывается: onJoin, после purchase, после reset, после смены спеки,
 * после /rc reload. Рассинхрон «куплено ≠ применено» невозможен дольше одного события.
 */
public final class TalentService {

    /** Source постоянных модификаторов атрибутов/резистов (для reconcile). */
    public static final String SOURCE = "talents";

    public enum PurchaseResult {
        OK, TALENTS_DISABLED, NO_SPEC, NODE_NOT_FOUND, WRONG_TREE,
        TIER_GATE, PREREQ_MISSING, NOT_ENOUGH_POINTS, ALREADY_OWNED, RATE_LIMITED
    }

    public enum ResetResult {
        OK, NO_SPEC, NO_PURCHASED, POOR, NO_ECONOMY, RATE_LIMITED
    }

    private final RaskolClasses plugin;
    private final TalentsStorage storage;
    private final EconomyHook economy;

    /** Кэш-карты бонусов (пересобираются только в reconcile). */
    private final Map<UUID, Map<String, Double>> baseBonusMap = new HashMap<>();
    private final Map<UUID, Map<String, Double>> coeffMultMap = new HashMap<>();
    private final Map<UUID, Map<String, Double>> cooldownMultMap = new HashMap<>();
    private final Map<UUID, Map<String, Double>> procBonusMap = new HashMap<>();
    private final Map<UUID, double[]> avoidBonusMap = new HashMap<>();
    private final Map<UUID, Double> regenBonusMap = new HashMap<>();

    /** 1.9.2: rate-limit действий талантов (покупка/сброс). */
    private final Map<UUID, Long> lastActionMs = new ConcurrentHashMap<>();

    public TalentService(RaskolClasses plugin, TalentsStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.economy = new EconomyHook(plugin);
    }

    /* -------------------------------- экономика очков -------------------------------- */

    private int startLevel() {
        return Math.max(1, plugin.getConfig().getInt("talents.start-level", 40));
    }

    private int perLevel() {
        return Math.max(0, plugin.getConfig().getInt("talents.points-per-level", 1));
    }

    private int maxPoints() {
        return Math.max(0, plugin.getConfig().getInt("talents.max-points", 21));
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("talents.enabled", true);
    }

    /** Очки из сводного уровня персонажа: clamp(charLevel − start + 1, 0, max) × perLevel. */
    public int earnedPoints(UUID uuid) {
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        return TalentModel.earnedPoints(charLevel, startLevel(), perLevel(), maxPoints());
    }

    /** Потраченные очки в конкретном дереве (для отображения ветки). */
    public int spentPoints(UUID uuid, String specId) {
        TalentTree tree = TalentsRegistry.treeOf(specId);
        if (tree == null) {
            return 0;
        }
        int sum = 0;
        for (String nodeId : storage.getPurchased(uuid, specId)) {
            TalentNode n = tree.find(nodeId);
            if (n != null) {
                sum += n.cost();
            }
        }
        return sum;
    }

    /**
     * 1.9.2: ГЛОБАЛЬНЫЕ потраченные очки — сумма по ВСЕМ деревьям игрока.
     * Очки = общий бюджет персонажа; респец не печатает новые очки.
     */
    public int spentGlobal(UUID uuid) {
        int sum = 0;
        for (Spec spec : Spec.values()) {
            sum += spentPoints(uuid, spec.id());
        }
        return sum;
    }

    /** 1.9.2: доступные очки = earned − spentGlobal (не по активной спеке). */
    public int availablePoints(UUID uuid, String specId) {
        return Math.max(0, earnedPoints(uuid) - spentGlobal(uuid));
    }

    public List<String> purchased(UUID uuid, String specId) {
        return storage.getPurchased(uuid, specId);
    }

    /* -------------------------------- rate-limit -------------------------------- */

    private long actionWindowMs() {
        return Math.max(50L, plugin.getConfig().getInt("performance.cast-click-cooldown-ms", 150));
    }

    private boolean actionAllowed(UUID uuid) {
        long now = System.currentTimeMillis();
        Long prev = lastActionMs.get(uuid);
        if (prev != null && now - prev < actionWindowMs()) {
            return false;
        }
        lastActionMs.put(uuid, now);
        return true;
    }

    /* -------------------------------- покупка -------------------------------- */

    /**
     * Покупка узла. Все проверки серверные:
     * enabled → rate-limit → активная спека → узел существует → узел из дерева
     * активной спеки → не куплен → тир-гейт по charLevel → пререквизиты →
     * стоимость против ГЛОБАЛЬНЫХ доступных очков.
     */
    public PurchaseResult purchase(Player player, String nodeId) {
        if (!enabled()) {
            return PurchaseResult.TALENTS_DISABLED;
        }
        UUID uuid = player.getUniqueId();
        if (!actionAllowed(uuid)) {
            return PurchaseResult.RATE_LIMITED;
        }
        Spec spec = plugin.getSpecService().getSpec(uuid);
        if (spec == null) {
            return PurchaseResult.NO_SPEC;
        }
        String specId = spec.id();
        TalentTree tree = TalentsRegistry.treeOf(specId);
        if (tree == null) {
            return PurchaseResult.WRONG_TREE;
        }
        TalentNode node = tree.find(nodeId);
        if (node == null) {
            return PurchaseResult.NODE_NOT_FOUND;
        }
        if (!node.specId().equals(specId)) {
            return PurchaseResult.WRONG_TREE;
        }
        List<String> owned = storage.getPurchased(uuid, specId);
        if (owned.contains(nodeId)) {
            return PurchaseResult.ALREADY_OWNED;
        }
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        if (charLevel < TalentModel.tierGate(node.tier(), startLevel())) {
            return PurchaseResult.TIER_GATE;
        }
        for (String prereq : node.prereqs()) {
            if (!owned.contains(prereq)) {
                return PurchaseResult.PREREQ_MISSING;
        }
        }
        if (node.cost() > availablePoints(uuid, specId)) {
            return PurchaseResult.NOT_ENOUGH_POINTS;
        }
        storage.addNode(uuid, specId, nodeId);
        reconcile(uuid);
        storage.save();
        return PurchaseResult.OK;
    }

    /* -------------------------------- сброс -------------------------------- */

    /**
     * Платный сброс дерева АКТИВНОЙ спеки: очки возвращаются в общий пул
     * (spentGlobal падает), узлы очищаются, модификаторы пересобираются.
     * Цена: reset-base + reset-per-point × потрачено в этом дереве.
     * free=true — админский бесплатный сброс.
     */
    public ResetResult reset(Player player, boolean free) {
        UUID uuid = player.getUniqueId();
        if (!actionAllowed(uuid)) {
            return ResetResult.RATE_LIMITED;
        }
        Spec spec = plugin.getSpecService().getSpec(uuid);
        if (spec == null) {
            return ResetResult.NO_SPEC;
        }
        String specId = spec.id();
        List<String> owned = storage.getPurchased(uuid, specId);
        if (owned.isEmpty()) {
            return ResetResult.NO_PURCHASED;
        }
        if (!free) {
            int base = plugin.getConfig().getInt("talents.reset-base", 500);
            int perPoint = plugin.getConfig().getInt("talents.reset-per-point", 25);
            int cost = base + perPoint * spentPoints(uuid, specId);
            if (!economy.available()) {
                return ResetResult.NO_ECONOMY;
            }
            if (economy.balance(uuid) < cost) {
                player.sendMessage(Component.text(plugin.getRaskolConfig().message(
                        "talents.reset.poor", "Не хватает монет на сброс талантов: нужно {cost}.")
                        .replace("{cost}", String.valueOf(cost)), NamedTextColor.RED));
                return ResetResult.POOR;
            }
            economy.withdraw(uuid, cost);
        }
        storage.clearSpec(uuid, specId);
        reconcile(uuid);
        storage.save();
        return ResetResult.OK;
    }

    /* -------------------------------- reconcile -------------------------------- */

    /**
     * Пересборка: валидация хранилища (1.9.2) → удалить модификаторы source="talents"
     * → просуммировать эффекты валидных купленных узлов активной спеки →
     * записать постоянные модификаторы и кэш-карты.
     */
    public void reconcile(UUID uuid) {
        plugin.getAttributes().removeModifiersBySource(uuid, SOURCE);
        plugin.getResists().removeModifiersBySource(uuid, SOURCE);

        // 1.9.2: самолечение хранилища по ВСЕМ деревьям (не только активному)
        for (Spec spec : Spec.values()) {
            TalentTree tree = TalentsRegistry.treeOf(spec.id());
            if (tree == null) {
                continue;
            }
            List<String> raw = storage.getPurchased(uuid, spec.id());
            List<String> kept = validatePurchased(uuid, spec.id(), tree, raw);
            if (kept.size() != raw.size()) {
                storage.setPurchased(uuid, spec.id(), kept);
            }
        }

        Map<String, Double> base = new HashMap<>();
        Map<String, Double> coeff = new HashMap<>();
        Map<String, Double> cd = new HashMap<>();
        Map<String, Double> proc = new HashMap<>();
        double dodgeAdd = 0.0;
        double parryAdd = 0.0;
        double regenAdd = 0.0;
        double attrStr = 0.0;
        double attrAgi = 0.0;
        double attrInt = 0.0;
        double resPhys = 0.0;
        double resMagic = 0.0;

        Player online = plugin.getServer().getPlayer(uuid);
        Spec spec = online != null ? plugin.getSpecService().getSpec(uuid) : null;
        if (spec != null) {
            TalentTree tree = TalentsRegistry.treeOf(spec.id());
            if (tree != null) {
                for (String nodeId : storage.getPurchased(uuid, spec.id())) {
                    TalentNode n = tree.find(nodeId);
                    if (n == null || n.effect() == null) {
                        continue;
                    }
                    String kind = n.effect().kind();
                    String target = n.effect().target();
                    double v = n.effect().value();
                    double v2 = n.effect().value2();
                    switch (kind) {
                        case "attr" -> {
                            if ("str".equals(target)) attrStr += v;
                            else if ("agi".equals(target)) attrAgi += v;
                            else if ("int".equals(target)) attrInt += v;
                        }
                        case "resist" -> {
                            if ("both".equals(target)) {
                                resPhys += v;
                                resMagic += v2;
                            } else if ("phys".equals(target)) {
                                resPhys += v;
                            } else if ("magic".equals(target)) {
                                resMagic += v;
                            }
                        }
                        case "kit_base" -> base.merge(target, v, Double::sum);
                        case "kit_mult" -> coeff.merge(target, v, Double::sum);
                        case "cd" -> cd.merge(target, v, Double::sum);
                        case "regen" -> regenAdd += v;
                        case "avoid" -> {
                            if ("dodge".equals(target)) dodgeAdd += v;
                            else if ("parry".equals(target)) parryAdd += v;
                        }
                        case "proc" -> proc.merge(target, v, Double::sum);
                        default -> {
                            // неизвестный kind игнорируется (защита от опечаток каталога)
                        }
                    }
                }
            }
        }

        if (attrStr != 0.0 || attrAgi != 0.0 || attrInt != 0.0) {
            plugin.getAttributes().addPermanentModifier(uuid, SOURCE, attrStr, attrAgi, attrInt);
        }
        if (resPhys != 0.0 || resMagic != 0.0) {
            plugin.getResists().addPermanentModifier(uuid, SOURCE, resPhys, resMagic);
        }

        baseBonusMap.put(uuid, base);
        coeffMultMap.put(uuid, coeff);
        cooldownMultMap.put(uuid, cd);
        procBonusMap.put(uuid, proc);
        avoidBonusMap.put(uuid, new double[]{dodgeAdd, parryAdd});
        regenBonusMap.put(uuid, regenAdd);

        plugin.getAttributes().invalidate(uuid);
    }

    /**
     * 1.9.2: валидация купленного списка: неизвестные id удаляются, пререквизиты
     * проверяются замыканием по тирам. Возвращает очищенный список.
     */
    private List<String> validatePurchased(UUID uuid, String specId, TalentTree tree, List<String> owned) {
        List<String> kept = new ArrayList<>();
        Set<String> keptSet = new HashSet<>();
        List<TalentNode> sorted = new ArrayList<>(tree.nodes());
        sorted.sort(Comparator.comparingInt(TalentNode::tier));
        for (TalentNode n : sorted) {
            if (!owned.contains(n.id())) {
                continue;
            }
            if (keptSet.containsAll(n.prereqs())) {
                kept.add(n.id());
                keptSet.add(n.id());
            } else {
                plugin.getLogger().warning("talents: узел " + n.id() + " дерева " + specId
                        + " игрока " + uuid + " удалён из хранилища: пререквизиты не выполнены");
            }
        }
        for (String id : owned) {
            if (tree.find(id) == null) {
                plugin.getLogger().warning("talents: неизвестный узел '" + id + "' дерева " + specId
                        + " игрока " + uuid + " удалён из хранилища");
            }
        }
        return kept;
    }

    /** Очистка кэша игрока (logout). */
    public void clear(UUID uuid) {
        baseBonusMap.remove(uuid);
        coeffMultMap.remove(uuid);
        cooldownMultMap.remove(uuid);
        procBonusMap.remove(uuid);
        avoidBonusMap.remove(uuid);
        regenBonusMap.remove(uuid);
        lastActionMs.remove(uuid);
    }

    /* ------------------------------- кэш-карты (хот-путь) ------------------------------- */

    public double baseBonus(UUID uuid, String abilityId) {
        Map<String, Double> m = baseBonusMap.get(uuid);
        return m == null ? 0.0 : m.getOrDefault(abilityId, 0.0);
    }

    public double coeffMult(UUID uuid, String abilityId) {
        Map<String, Double> m = coeffMultMap.get(uuid);
        if (m == null) {
            return 1.0;
        }
        return 1.0 + m.getOrDefault(abilityId, 0.0);
    }

    public double cooldownMult(UUID uuid, String abilityId) {
        Map<String, Double> m = cooldownMultMap.get(uuid);
        if (m == null) {
            return 1.0;
        }
        return Math.max(0.1, 1.0 - m.getOrDefault(abilityId, 0.0));
    }

    public double procBonus(UUID uuid, String procId) {
        Map<String, Double> m = procBonusMap.get(uuid);
        return m == null ? 0.0 : m.getOrDefault(procId, 0.0);
    }

    public double[] avoidBonus(UUID uuid) {
        double[] v = avoidBonusMap.get(uuid);
        return v == null ? new double[]{0.0, 0.0} : v;
    }

    public double regenBonus(UUID uuid) {
        Double v = regenBonusMap.get(uuid);
        return v == null ? 0.0 : v;
    }

    /* ------------------------------- selftest API ------------------------------- */

    /** Тестовая покупка без валидации (чеки 24/31). */
    public void forcePurchaseForTest(UUID uuid, String specId, String nodeId) {
        storage.addNode(uuid, specId, nodeId);
        reconcile(uuid);
    }
}
