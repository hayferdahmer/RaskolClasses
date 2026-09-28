// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.attribute;

import dev.raskol.classes.RaskolClasses;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 1.11.4 (P4c): ПЛАН B — виртуальный пул HP, вынесенный из AttributeService.
 *  - formula  — реальный боевой пул (не ограничен ничем), считает AttributeService.maxHp;
 *  - carrier  — ванильный max_health = min(formula, 1024), выставляется HpAttributeSync;
 *  - scale    = carrier / formula — перевод formula-единиц в carrier и обратно;
 *  - healFormula / currentFormulaHp / targetCarrier — единые точки входа для
 *    хилов, HUD и execute-порогов (1.9.3-r: ЕДИНЫЕ ЕДИНИЦЫ HP).
 * Мобы: carrier = их ванильный max, formula = carrier (scale 1).
 */
public final class HpPool {

    /** Движковый потолок ванильного max_health (без datapack-оверрайда). */
    public static final double VANILLA_CAP = 1024.0;

    private final RaskolClasses plugin;
    private final AttributeService formulas;

    public HpPool(RaskolClasses plugin, AttributeService formulas) {
        this.plugin = plugin;
        this.formulas = formulas;
    }

    /** Carrier игрока: текущее значение ванильного атрибута max_health. */
    public double carrierMaxHp(Player player) {
        if (player == null || AttributeService.maxHealthAttr() == null) {
            return 20.0;
        }
        AttributeInstance inst = player.getAttribute(AttributeService.maxHealthAttr());
        double v = inst != null ? inst.getValue() : 20.0;
        return Double.isFinite(v) && v > 0.0 ? v : 20.0;
    }

    /** Carrier для любых расчётов урона: formula, зажатая в движковый потолок. */
    public double targetCarrier(UUID uuid) {
        return Math.max(1.0, Math.min(formulas.maxHp(uuid), VANILLA_CAP));
    }

    /** scale = carrier / formula; 1.0 при вырожденных значениях. */
    public double scale(Player player) {
        double formula = formulas.maxHp(player.getUniqueId());
        if (formula <= 0.0) {
            return 1.0;
        }
        double s = carrierMaxHp(player) / formula;
        return (Double.isFinite(s) && s > 0.0) ? s : 1.0;
    }

    /** Текущее HP игрока в formula-единицах (для HUD и execute-порогов). */
    public double currentFormulaHp(Player player) {
        double s = scale(player);
        return s > 0.0 ? player.getHealth() / s : player.getHealth();
    }

    /**
     * Хил в formula-единицах: конвертируем в carrier через scale цели и клампим
     * по carrier. Мобы: scale = 1 (carrier == formula == ванильный max).
     */
    public void healFormula(LivingEntity target, double formulaAmount) {
        if (target == null || target.isDead() || formulaAmount <= 0.0
                || AttributeService.maxHealthAttr() == null) {
            return;
        }
        double carrier;
        double formula;
        if (target instanceof Player p) {
            carrier = carrierMaxHp(p);
            formula = formulas.maxHp(p.getUniqueId());
        } else {
            AttributeInstance inst = target.getAttribute(AttributeService.maxHealthAttr());
            carrier = inst != null ? inst.getValue() : 20.0;
            formula = carrier;
        }
        if (!Double.isFinite(carrier) || carrier <= 0.0) {
            carrier = 20.0;
        }
        if (!Double.isFinite(formula) || formula <= 0.0) {
            formula = carrier;
        }
        double s = formula > 0.0 ? carrier / formula : 1.0;
        double add = formulaAmount * s;
        double newHp = Math.min(carrier, target.getHealth() + add);
        target.setHealth(Math.max(0.0, newHp));
    }
}
