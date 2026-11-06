// © 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
// SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.Hp;
import dev.raskol.classes.config.ConfigReads;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.11.4 (P3): капы и оконные ограничения урона + carrier/formula-хелперы.
 *  - single-hit cap ≤35% carrier (путь A) / formula (путь B через вызов из фасада);
 *  - burst-окно: суммарно за 3 с ≤18% formula-max (лог на игрока);
 *  - env-lethal scale: среда (FALL/DROWNING/…) × carrier/20.
 * 1.14.10 (Sprint 7, Волна 7.3, P0-1-01): cfgD делегирует в ConfigReads.finiteDouble
 *         (аудит 1-01: единая NaN-проверка).
 * 1.14.12 (Sprint 8, Волна 12A, P0-3-02): carrierMaxOf читает максимум через Hp.max
 *         (атрибут MAX_HEALTH) вместо deprecated LivingEntity#getMaxHealth().
 * 1.15.6 (Волна 4, аудит 2.6-40): undoLastBurst — откат записи burst-лога, если
 *         событие урона отменено downstream (регион/другой плагин): окно больше
 *         не «съедает» бюджет уроном, который не прошёл.
 * 1.15.6 (Волна 4, аудит 2.6-52): purge-окно burst-лога читается из конфига
 *         combat.burst-window-seconds вместо зашитых 10 с.
 */
public final class DamageCaps {

    private static final List<String> DEFAULT_ENV =
            List.of("FALL", "DROWNING", "SUFFOCATION", "STARVATION");

    private static final List<String> DEFAULT_EXEMPT =
            List.of("FALL", "DROWNING", "SUFFOCATION", "STARVATION", "VOID", "SONIC_BOOM");

    private final RaskolClasses plugin;
    private final Map<UUID, Deque<double[]>> burstLog = new ConcurrentHashMap<>();

    public DamageCaps(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /** 1.14.10 (Волна 7.3, P0-1-01): делегат в ConfigReads.finiteDouble. */
    private double cfgD(String path, double def) {
        return ConfigReads.finiteDouble(plugin.getConfig(), path, def);
    }

    /* ------------------------------ carrier/formula ------------------------------ */

    /** 1.14.12 (Волна 12A, 3-02): не-игроки читаются через Hp.max (атрибут, NaN-guard). */
    public double carrierMaxOf(LivingEntity target) {
        if (target instanceof Player p) {
            return plugin.getHpBarService().carrierMaxHp(p);
        }
        double m = Hp.max(target);
        return Double.isFinite(m) && m > 0.0 ? m : 20.0;
    }

    public double formulaMaxOf(LivingEntity target) {
        if (target instanceof Player p) {
            return plugin.getHpBarService().formulaMaxHp(p.getUniqueId());
        }
        return carrierMaxOf(target);
    }

    public double scaleOf(Entity target) {
        if (target instanceof Player p) {
            return plugin.getHpBarService().scale(p);
        }
        return 1.0;
    }

    /* ------------------------------ env-lethal scale ------------------------------ */

    public void applyEnvLethalScale(EntityDamageEvent event, Player target) {
        if (!plugin.getConfig().getBoolean("damage-types.env-lethal-scale", true)) {
            return;
        }
        List<String> env = plugin.getConfig().getStringList("damage-types.env-lethal");
        if (env.isEmpty()) {
            env = DEFAULT_ENV;
        }
        if (!env.contains(event.getCause().name())) {
            return;
        }
        double carrier = carrierMaxOf(target);
        double scale = carrier / 20.0;
        if (scale > 1.0 && Double.isFinite(scale)) {
            event.setDamage(event.getDamage() * scale);
        }
    }

    /* ------------------------------ single-hit cap ------------------------------ */

    public void applySingleHitCapCarrier(EntityDamageEvent event, Player target) {
        double pct = cfgD("combat.max-single-hit-pct", 35.0);
        if (pct <= 0.0) {
            return;
        }
        List<String> exempt = plugin.getConfig().getStringList("combat.cap-exempt-causes");
        if (exempt.isEmpty()) {
            exempt = DEFAULT_EXEMPT;
        }
        if (exempt.contains(event.getCause().name())) {
            return;
        }
        double carrier = carrierMaxOf(target);
        double capped = CombatMath.cappedDamage(event.getDamage(), carrier, pct);
        if (capped != event.getDamage()) {
            event.setDamage(capped);
        }
    }

    /** Кап пути B (formula-единицы): вызывается фасадом напрямую. */
    public double capFormula(double damage, double formulaMax, double pct) {
        return CombatMath.cappedDamage(damage, formulaMax, pct);
    }

    /* ------------------------------ burst-окно ------------------------------ */

    public double applyBurstCap(Player target, double damage, double maxHp) {
        if (damage <= 0.0) {
            return damage;
        }
        double seconds = cfgD("combat.burst-window-seconds", 3.0);
        double pct = cfgD("combat.burst-window-pct", 18.0);
        if (seconds <= 0.0 || pct <= 0.0) {
            return damage;
        }
        double cap = maxHp * pct / 100.0;
        long now = System.currentTimeMillis();
        long windowMillis = (long) (seconds * 1000.0);
        Deque<double[]> dq = burstLog.computeIfAbsent(target.getUniqueId(), k -> new ArrayDeque<>());
        synchronized (dq) {
            while (!dq.isEmpty() && now - dq.peekFirst()[0] > windowMillis) {
                dq.pollFirst();
            }
            double sum = 0.0;
            for (double[] e : dq) {
                sum += e[1];
            }
            double allowed = Math.max(0.0, cap - sum);
            double finalDmg = Math.min(damage, allowed);
            if (finalDmg > 0.0) {
                dq.addLast(new double[]{now, finalDmg});
            }
            if (dq.isEmpty()) {
                burstLog.remove(target.getUniqueId());
            }
            return finalDmg;
        }
    }

    /**
     * 1.15.6 (Волна 4, аудит 2.6-40): откат ПОСЛЕДНЕЙ записи burst-лога на amount,
     * если событие урона отменено downstream. Вызывается из MONITOR-хэндлера
     * VanillaDamageListener. Защита от чужого отката: снимаем не больше последней
     * записи; если последняя запись меньше amount — убираем её целиком.
     */
    public void undoLastBurst(Player target, double amount) {
        if (amount <= 0.0) {
            return;
        }
        Deque<double[]> dq = burstLog.get(target.getUniqueId());
        if (dq == null) {
            return;
        }
        synchronized (dq) {
            double[] last = dq.peekLast();
            if (last == null) {
                return;
            }
            if (last[1] <= amount + 1e-9) {
                dq.pollLast();
            } else {
                last[1] -= amount;
            }
            if (dq.isEmpty()) {
                burstLog.remove(target.getUniqueId());
            }
        }
    }

    /** 1.15.6 (2.6-52): окно purge = combat.burst-window-seconds (ранее зашитые 10 с). */
    public void purge() {
        long now = System.currentTimeMillis();
        long windowMillis = (long) (cfgD("combat.burst-window-seconds", 3.0) * 1000.0);
        burstLog.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                entry.getValue().removeIf(e -> now - e[0] > windowMillis);
                return entry.getValue().isEmpty();
            }
        });
    }
}
