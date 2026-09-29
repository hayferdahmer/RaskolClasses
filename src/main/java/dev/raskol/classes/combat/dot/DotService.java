// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat.dot;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.ability.WarlockAbilities;
import dev.raskol.classes.combat.CombatMath;
import dev.raskol.classes.combat.CombatService;
import dev.raskol.classes.combat.DamageType;
import dev.raskol.classes.combat.school.Penetration;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.combat.school.SchoolConfig;
import dev.raskol.classes.combat.school.SchoolMitigation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 1.12.4: DoT-ядро. Реестр живых Dot'ов по целям + тик-задача 1 с.
 *
 * Пайплайн тика по каждому экземпляру:
 *  1) цель жива, владелец онлайн, canHit(owner→target) — иначе снятие;
 *  2) иммунитет школы: multiplierFor == 0 → снятие молча (триггеры не работают);
 *  3) dps = def.dps × stacks × schoolMult × immunityMult × (1 + sealAmp(target));
 *  4) митигация по школе цели: канал-резист (pen владельца режет) ⊕ стихийный
 *     резист (school-pen владельца режет), кап schools.mitigation-cap;
 *  5) суммарный кап: Σ dps по цели ≤ combat.dot-dps-cap-pct% от formula-max
 *     (DotMath.capFactor масштабирует все Dot'ы пропорционально);
 *  6) применение: target.damage(dps × periodSec × factor × scale, owner) внутри
 *     setSuppress(true) + beginReflect()/endReflect() — путь A не применяет
 *     резисты/капы повторно, рефлект Чёрной Мессы на Dot-тики не срабатывает;
 *     burst-окно и single-hit кап DoT'ами не тратятся.
 *
 * Атрибуция: kill-кредит и статистика идут владельцу (damage с source=owner).
 * Стеки: повторное наложение тем же владельцем = +1 стек + refresh длительности.
 */
public final class DotService {

    private static final long TICK_MILLIS = 1000L;

    private final RaskolClasses plugin;
    private final CombatService combat;
    private final Map<UUID, CopyOnWriteArrayList<DotInstance>> dots = new ConcurrentHashMap<>();

    public DotService(RaskolClasses plugin, CombatService combat) {
        this.plugin = plugin;
        this.combat = combat;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /* ------------------------------ публичный API ------------------------------ */

    /** Наложить Dot на цель (стекирование по def.id + owner). */
    public void apply(Player owner, LivingEntity target, DotDef def) {
        if (owner == null || target == null || def == null || target.isDead()) {
            return;
        }
        if (!combat.canHit(owner, target)) {
            return;
        }
        UUID targetUuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        CopyOnWriteArrayList<DotInstance> list =
                dots.computeIfAbsent(targetUuid, k -> new CopyOnWriteArrayList<>());
        for (DotInstance inst : list) {
            if (inst.ownerUuid().equals(owner.getUniqueId())
                    && inst.def().id().equals(def.id())) {
                inst.refresh(now);
                return;
            }
        }
        list.add(new DotInstance(def, owner.getUniqueId(), now));
    }

    /** Снять все Dot'ы с цели (диспел, смерть, очистка). */
    public void removeAllOn(UUID targetUuid) {
        dots.remove(targetUuid);
    }

    /** Снять Dot'ы конкретной школы с цели (стихийный диспел 1.12.5). */
    public void removeSchoolOn(UUID targetUuid, School school) {
        CopyOnWriteArrayList<DotInstance> list = dots.get(targetUuid);
        if (list == null) {
            return;
        }
        list.removeIf(inst -> inst.def().school() == school);
        if (list.isEmpty()) {
            dots.remove(targetUuid);
        }
    }

    public int activeOn(UUID targetUuid) {
        CopyOnWriteArrayList<DotInstance> list = dots.get(targetUuid);
        return list == null ? 0 : list.size();
    }

    public int trackedTargets() {
        return dots.size();
    }

    /* ------------------------------ тик ------------------------------ */

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, CopyOnWriteArrayList<DotInstance>> entry : dots.entrySet()) {
            UUID targetUuid = entry.getKey();
            CopyOnWriteArrayList<DotInstance> list = entry.getValue();

            Entity entity = plugin.getServer().getEntity(targetUuid);
            if (!(entity instanceof LivingEntity target) || target.isDead()) {
                dots.remove(targetUuid);
                continue;
            }

            // фаза 1–4: валидация и предварительный dps по каждому экземпляру
            int n = list.size();
            double[] pending = new double[n];
            double sum = 0.0;
            int idx = 0;
            for (DotInstance inst : list) {
                if (inst.expired(now)) {
                    list.remove(inst);
                    pending[idx++] = 0.0;
                    continue;
                }
                Player owner = plugin.getServer().getPlayer(inst.ownerUuid());
                if (owner == null || !combat.canHit(owner, target)) {
                    list.remove(inst);
                    pending[idx++] = 0.0;
                    continue;
                }
                School school = inst.def().school();
                double immunity = combat.schoolImmunity().multiplierFor(target.getType(), school);
                if (immunity <= 0.0) {
                    list.remove(inst); // иммун: урон 0, триггеры молчат
                    pending[idx++] = 0.0;
                    continue;
                }
                double baseDps = inst.def().dps() * inst.stacks();
                double seal = WarlockAbilities.sealAmplifyOf(targetUuid);
                double dps = DotMath.withMults(baseDps,
                        combat.schoolConfig().multiplier(school), immunity, seal);
                dps *= 1.0 - mitigationFor(target, owner, school);
                pending[idx] = dps;
                sum += dps;
                idx++;
            }
            if (list.isEmpty()) {
                dots.remove(targetUuid);
                continue;
            }

            // фаза 5: суммарный кап DoT-DPS по цели
            double maxHp = combat.caps().formulaMaxOf(target);
            double capPct = plugin.getConfig().getDouble("combat.dot-dps-cap-pct", 6.0);
            double limit = DotMath.dpsLimit(maxHp, capPct);
            double factor = DotMath.capFactor(pending, limit);

            // фаза 6: применение
            double scale = combat.scaleOf(target);
            idx = 0;
            for (DotInstance inst : list) {
                double dps = pending[idx++];
                if (dps <= 0.0) {
                    continue;
                }
                Player owner = plugin.getServer().getPlayer(inst.ownerUuid());
                if (owner == null) {
                    continue;
                }
                double amount = dps * factor * (TICK_MILLIS / 1000.0) * scale;
                if (amount <= 0.0) {
                    continue;
                }
                CombatService.setSuppress(true);
                CombatService.beginReflect();
                try {
                    target.damage(amount, owner);
                } finally {
                    CombatService.endReflect();
                    CombatService.setSuppress(false);
                }
            }
            if (list.isEmpty()) {
                dots.remove(targetUuid);
            }
        }
    }

    /** Митигация по школе цели: канал-резист (pen) ⊕ стихийный резист (school-pen). */
    private double mitigationFor(LivingEntity target, Player owner, School school) {
        SchoolConfig sc = combat.schoolConfig();
        if (!(target instanceof Player tp) || combat.resists().disabledIn(tp.getWorld())) {
            return 0.0;
        }
        UUID targetUuid = tp.getUniqueId();
        DamageType channel = school.channel();
        if (channel == DamageType.TRUE) {
            return 0.0;
        }
        double cap = combat.resists().pvpCap();
        double factor = channel == DamageType.PHYSICAL
                ? combat.resists().physicalFactor(targetUuid, cap)
                : combat.resists().magicFactor(targetUuid, cap);
        if (!Double.isFinite(factor) || factor >= 1.0 || factor < 0.0) {
            factor = 1.0;
        }
        double channelResistPct = (1.0 - factor) * 100.0;
        Penetration pen = combat.penTraits().channelPen(
                owner.getUniqueId(), channel == DamageType.PHYSICAL,
                plugin.getGearHook(), sc.penPctCap());
        double effChannel = pen.effectiveResist(channelResistPct, sc.penPctCap());
        double elResist = combat.elemental().resistOf(targetUuid, school);
        double schoolPen = combat.penTraits().schoolPenFraction(
                owner.getUniqueId(), school, plugin.getGearHook(), sc.penPctCap());
        double effEl = CombatMath.effectiveResist(elResist, 0.0, schoolPen, sc.penPctCap());
        return SchoolMitigation.mitigationFor(effChannel, Penetration.NONE,
                sc.penPctCap(), effEl, sc.elementalEnabled(), sc.mitigationCap());
    }
}
