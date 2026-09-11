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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.9.0: рантайм талантов.
 *
 * Обязанности:
 *  - earned/spent/available очков (чистая математика из TalentModel);
 *  - покупка узла с ПОЛНОЙ серверной валидацией (клиент не участвует в проверках);
 *  - сброс дерева активной спеки (платный через EconomyHook / бесплатный админский);
 *  - reconcile: пересборка постоянных модификаторов AttributeService/ResistService
 *    (source "talents") и кэш-карт хот-пути боя;
 *  - кэш-карты baseBonus/coeffMult/cooldownMult/procBonus/avoidBonus/regenBonus —
 *    читаются кит-файлами, AbilityRegistry, AttributeService, ResourceService,
 *    PassiveListener за O(1) без аллокаций.
 *
 * Reconcile вызывается: onJoin, после purchase, после reset, после смены спеки,
 * после /rc reload. Рассинхрон «куплено ≠ применено» невозможен дольше одного события.
 */
public final class TalentService {

    /** Source постоянных модификаторов атрибутов/резистов (для reconcile). */
    public static final String SOURCE = "talents";

    public enum PurchaseResult {
        OK, TALENTS_DISABLED, NO_SPEC, NODE_NOT_FOUND, WRONG_TREE,
        TIER_GATE, PREREQ_MISSING, NOT_ENOUGH_POINTS, ALREADY_OWNED
    }

    public enum ResetResult {
        OK, NO_SPEC, NO_PURCHASED, POOR, NO_ECONOMY
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

    /** Очки из сводного уровня персонажа (1.8.0): clamp(charLevel−39, 0, 21). */
    public int earnedPoints(UUID uuid) {
        int charLevel = plugin.getCharacterLevels().characterLevel(uuid);
        return TalentModel.earnedPoints(charLevel, startLevel(), perLevel(), maxPoints());
    }

    /** Потраченные очки в дереве спеки = сумма стоимостей купленных узлов. */
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

    public int availablePoints(UUID uuid, String specId) {
        return Math.max(0, earnedPoints(uuid) - spentPoints(uuid, specId));
    }

    public List<String> purchased(UUID uuid, String specId) {
        return storage.getPurchased(uuid, specId);
    }

    /* -------------------------------- покупка -------------------------------- */

    /**
     * Покупка узла. Все проверки серверные:
     * enabled → активная спека → узел существует → узел из ДЕРЕВА АКТИВНОЙ спеки →
     * не куплен → тир-гейт по charLevel → пререквизиты → стоимость.
     */
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
     * Платный сброс дерева АКТИВНОЙ спеки: очки возвращаются в пул,
     * узлы очищаются, модификаторы пересобираются. Цена: reset-base + reset-per-point×spent.
     * free=true — админский бесплатный сброс (команда с raskolclasses.admin).
     */
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
     * Пересборка: удалить модификаторы source="talents" → просуммировать эффекты
     * купленных узлов активной спеки → записать постоянные модификаторы и кэш-карты.
     */
    public void reconcile(UUID uuid) {
        plugin.getAttributes().removeModifiersBySource(uuid, SOURCE);
        plugin.getResists().removeModifiersBySource(uuid, SOURCE);

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

    /** Очистка кэша игрока (logout). */
    public void clear(UUID uuid) {
        baseBonusMap.remove(uuid);
        coeffMultMap.remove(uuid);
        cooldownMultMap.remove(uuid);
        procBonusMap.remove(uuid);
        avoidBonusMap.remove(uuid);
        regenBonusMap.remove(uuid);
    }

    /* ------------------------------- кэш-карты (хот-путь) ------------------------------- */

    /** +base к базовому урону/хилу абилки (0.0 если бонуса нет). */
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
        return 1.0 + m.getOrDefault(abilityId, 0.0);
    }

    /** Множитель кулдауна: 1.0 = нет бонуса, 0.85 = −15% (пол −90%). */
    public double cooldownMult(UUID uuid, String abilityId) {
        Map<String, Double> m = cooldownMultMap.get(uuid);
        if (m == null) {
            return 1.0;
        }
        return Math.max(0.1, 1.0 - m.getOrDefault(abilityId, 0.0));
    }

    /** +бонус проки: chance-прокам — к шансу, sadism — к flat-бонусу, grace — к множителю. */
    public double procBonus(UUID uuid, String procId) {
        Map<String, Double> m = procBonusMap.get(uuid);
        return m == null ? 0.0 : m.getOrDefault(procId, 0.0);
    }

    /** [dodge, parry] плоские % бонусы (нулевые если нет). */
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

    /** Тестовая покупка без валидации (чек 24: reconcile добавляет/удаляет узел). */
    public void forcePurchaseForTest(UUID uuid, String specId, String nodeId) {
        storage.addNode(uuid, specId, nodeId);
        reconcile(uuid);
    }
}
