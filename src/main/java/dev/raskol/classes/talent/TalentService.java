// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.talent;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.ResistService;
import dev.raskol.classes.spec.Spec;
import dev.raskol.classes.talent.TalentModel.TalentNode;
import dev.raskol.classes.talent.TalentModel.TalentTree;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.9.0: рантайм талантов.
 *
 * Обязанности:
 *  - earned/spent/available очков (чистая математика);
 *  - покупка узла с серверной валидацией (никакого доверия клиенту);
 *  - сброс дерева активной спеки (платный/бесплатный админский);
 *  - reconcile: пересборка модификаторов AttributeService/ResistService
 *    (source "talents") и кэш-карт для хот-пути боя;
 *  - кэш-карты (baseBonus/coeffMult/cooldownMult/procBonus/avoidBonus/regenBonus) —
 *    читаются кит-файлами и AbilityRegistry за O(1).
 *
 * Reconcile вызывается: onJoin, после purchase, после reset, после смены спеки,
 * после /rc reload. Рассинхрон «куплено ≠ применено» невозможен дольше одного события.
 */
public final class TalentService {

    /** Статический source модификаторов атрибутов/резистов для reconcile. */
    public static final String SOURCE = "talents";

    public enum PurchaseResult {
        OK, TALENTS_DISABLED, NO_SPEC, NODE_NOT_FOUND, WRONG_TREE,
        TIER_GATE, PREREQ_MISSING, NOT_ENOUGH_POINTS, ALREADY_OWNED, INTERNAL
    }

    public enum ResetResult {
        OK, NO_SPEC, NO_PURCHASED, POOR, NO_ECONOMY, ARMED_WAIT, INTERNAL
    }

    private final RaskolClasses plugin;
    private final TalentsStorage storage;

    /** Кэш-карты (пересобираются при reconcile). Volatile для видимости между потоками. */
    private volatile Map<UUID, Map<String, Double>> baseBonusMap = new HashMap<>();
    private volatile Map<UUID, Map<String, Double>> coeffMultMap = new HashMap<>();
    private volatile Map<UUID, Map<String, Double>> cooldownMultMap = new HashMap<>();
    private volatile Map<UUID, Map<String, Double>> procBonusMap = new HashMap<>();
    private volatile Map<UUID, double[]> avoidBonusMap = new HashMap<>(); // [dodge, parry]
    private volatile Map<UUID, Double> regenBonusMap = new HashMap<>();

    public TalentService(RaskolClasses plugin, TalentsStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    /* -------------------------------- экономика -------------------------------- */

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

    public int earnedPoints(UUID uuid) {
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        return TalentModel.earnedPoints(charLevel, startLevel(), perLevel(), maxPoints());
    }

    public int spentPoints(UUID uuid, String specId) {
        TalentTree tree = TalentsRegistry.treeOf(specId);
        if (tree == null) {
            return 0;
        }
        List<String> owned = storage.getPurchased(uuid, specId);
        int sum = 0;
        for (String nodeId : owned) {
            TalentNode n = tree.find(nodeId);
            if (n != null) {
                sum += n.cost();
            }
        }
        return sum;
    }

    public int availablePoints(UUID uuid, String specId) {
        return Math.max(0, earnedPoints(uuid) - spentPoints(uuid, specId));
    }

    public List<String> purchased(UUID uuid, String specId) {
        return storage.getPurchased(uuid, specId);
    }

    /* -------------------------------- покупка -------------------------------- */

    public PurchaseResult purchase(Player player, String nodeId) {
        if (!enabled()) {
            return PurchaseResult.TALENTS_DISABLED;
        }
        UUID uuid = player.getUniqueId();
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
        // серверная проверка: узел из дерева активной спеки (защита от подмены id)
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

    public ResetResult reset(Player player, boolean free) {
        UUID uuid = player.getUniqueId();
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
            int spent = spentPoints(uuid, specId);
            int cost = base + perPoint * spent;
            dev.raskol.classes.hook.EconomyHook eco = plugin.getEconomyHook();
            if (eco == null || !eco.isAvailable()) {
                return ResetResult.NO_ECONOMY;
            }
            if (!eco.has(player, cost)) {
                player.sendMessage(Component.text(plugin.getRaskolConfig().message(
                        "talents.reset.poor", "Не хватает монет на сброс талантов.")
                        .replace("{cost}", String.valueOf(cost)), NamedTextColor.RED));
                return ResetResult.POOR;
            }
            eco.withdraw(player, cost);
        }
        storage.clearSpec(uuid, specId);
        reconcile(uuid);
        storage.save();
        return ResetResult.OK;
    }

    /* -------------------------------- reconcile -------------------------------- */

    /**
     * Пересборка модификаторов и кэш-карт для игрока.
     * Читает активную спеку, суммирует эффекты купленных узлов,
     * пишет постоянные модификаторы (source "talents") и обновляет кэш.
     */
    public void reconcile(UUID uuid) {
        // очистить старые модификаторы талантов
        plugin.getAttributes().removeModifiersBySource(uuid, SOURCE);
        plugin.getResists().removeModifiersBySource(uuid, SOURCE);

        // обнулить кэш-карты
        Map<String, Double> base = new HashMap<>();
        Map<String, Double> coeff = new HashMap<>();
        Map<String, Double> cd = new HashMap<>();
        Map<String, Double> proc = new HashMap<>();
        double dodgeAdd = 0.0;
        double parryAdd = 0.0;
        double regenAdd = 0.0;
        double attrStr = 0.0, attrAgi = 0.0, attrInt = 0.0;
        double resPhys = 0.0, resMagic = 0.0;

        Spec spec = specOf(uuid);
        if (spec != null) {
            String specId = spec.id();
            TalentTree tree = TalentsRegistry.treeOf(specId);
            if (tree != null) {
                for (String nodeId : storage.getPurchased(uuid, specId)) {
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
                            switch (target) {
                                case "str" -> attrStr += v;
                                case "agi" -> attrAgi += v;
                                case "int" -> attrInt += v;
                            }
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
                        case "kit_mult" ->
                                coeff.merge(target, v, Double::sum); // 0.25 = +25%
                        case "cd" ->
                                cd.merge(target, v, Double::sum); // 0.15 = −15%
                        case "regen" -> regenAdd += v;
                        case "avoid" -> {
                            if ("dodge".equals(target)) dodgeAdd += v;
                            else if ("parry".equals(target)) parryAdd += v;
                        }
                        case "proc" -> proc.merge(target, v, Double::sum);
                    }
                }
            }
        }

        // применить постоянные модификаторы атрибутов
        if (attrStr != 0.0 || attrAgi != 0.0 || attrInt != 0.0) {
            plugin.getAttributes().addPermanentModifier(uuid, SOURCE, attrStr, attrAgi, attrInt);
        }
        // применить постоянные модификаторы резистов
        if (resPhys != 0.0 || resMagic != 0.0) {
            plugin.getResists().addPermanentModifier(uuid, SOURCE, resPhys, resMagic);
        }

        // обновить кэш-карты
        baseBonusMap.put(uuid, base);
        coeffMultMap.put(uuid, coeff);
        cooldownMultMap.put(uuid, cd);
        procBonusMap.put(uuid, proc);
        avoidBonusMap.put(uuid, new double[]{dodgeAdd, parryAdd});
        regenBonusMap.put(uuid, regenAdd);

        // инвалидировать кэш атрибутов (т.к. STR/AGI/INT изменились)
        plugin.getAttributes().invalidate(uuid);
    }

    private Spec specOf(UUID uuid) {
        Player p = plugin.getServer().getPlayer(uuid);
        if (p == null) {
            return null;
        }
        return plugin.getSpecService().getSpec(uuid);
    }

    /** Сбросить кэш игрока (logout). */
    public void clear(UUID uuid) {
        baseBonusMap.remove(uuid);
        coeffMultMap.remove(uuid);
        cooldownMultMap.remove(uuid);
        procBonusMap.remove(uuid);
        avoidBonusMap.remove(uuid);
        regenBonusMap.remove(uuid);
    }

    /* ------------------------------- кэш-карты ------------------------------- */

    /** +base к базовому урону/хилу абилки (0.0 если нет бонуса). */
    public double baseBonus(UUID uuid, String abilityId) {
        Map<String, Double> m = baseBonusMap.get(uuid);
        return m == null ? 0.0 : m.getOrDefault(abilityId, 0.0);
    }

    /** Множитель coeff абилки: 1.0 = нет бонуса, 1.25 = +25%. */
    public double coeffMult(UUID uuid, String abilityId) {
        Map<String, Double> m = coeffMultMap.get(uuid);
        if (m == null) {
            return 1.0;
        }
        double add = m.getOrDefault(abilityId, 0.0);
        return 1.0 + add;
    }

    /** Множитель кулдауна: 1.0 = нет бонуса, 0.85 = −15%. */
    public double cooldownMult(UUID uuid, String abilityId) {
        Map<String, Double> m = cooldownMultMap.get(uuid);
        if (m == null) {
            return 1.0;
        }
        double sub = m.getOrDefault(abilityId, 0.0);
        return Math.max(0.1, 1.0 - sub); // жёсткий пол -90%
    }

    /** +бонус к проке: для chance-проков это + к шансу, для sadism — + к flat-бонусу. */
    public double procBonus(UUID uuid, String procId) {
        Map<String, Double> m = procBonusMap.get(uuid);
        return m == null ? 0.0 : m.getOrDefault(procId, 0.0);
    }

    /** [dodge, parry] плоские % бонусы (0,0 если нет). */
    public double[] avoidBonus(UUID uuid) {
        double[] v = avoidBonusMap.get(uuid);
        return v == null ? new double[]{0.0, 0.0} : v;
    }

    /** +ресурс/с аддитивно к класс-регену. */
    public double regenBonus(UUID uuid) {
        Double v = regenBonusMap.get(uuid);
        return v == null ? 0.0 : v;
    }

    /* ------------------------------- selftest API ------------------------------- */

    /** Для selftest: купить узел без валидации (тест reconcile). */
    public void forcePurchaseForTest(UUID uuid, String specId, String nodeId) {
        storage.addNode(uuid, specId, nodeId);
        reconcile(uuid);
    }
}
