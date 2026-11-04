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
import dev.raskol.classes.spec.service.Spec2Service;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 1.12.4–1.12.6: DoT-ядро (реестр, тик 1 с, атрибуция, стеки, кап DPS, триггеры среды).
 * 1.14.0 (Б2): spec2-тюнинг источника — узлы dot_dur/dot_stacks/dot_mult дерева
 *         кастера меняют длительность/стеки/dps накладываемого им DoT (specTuned).
 * 1.14.0 (Б7): wither (SHADOW) в дефолтах реестра + партиклы школ SHADOW/HOLY/
 *         ARCANE/TRUE в defaultDotParticle.
 * 1.14.7 (Sprint 3, P0-6A): applyByIdWithDpsBonus — DoT с надбавкой к dps из
 *         proc-узла (deep_wounds: +0.5 dps за ранг). Надбавка фиксируется в def
 *         при ПЕРВОМ наложении; повторные применения того же owner+id освежают
 *         стеки/время, но не меняют dps уже висящего экземпляра (осознанно).
 * 1.14.13 (Sprint 9, Волна 13B-b, P0-10-03/04): конструктор без побочных эффектов —
 *         registerEvents и runTaskTimer вынесены в registerListeners()/startTickTask();
 *         создаётся CombatService в конструкторе (чистая ссылка), регистрация/запуск
 *         задачи — из RaskolClasses.onEnable, ручка задачи в activeTasks.
 * 1.14.14 (фаза 1, BC_VACUOUS-фикс): в onProjectileHit убран vacuous instanceof —
 *         event.getEntity() по контракту ProjectileHitEvent возвращает Projectile,
 *         поэтому проверка instanceof была всегда true для non-null значения.
 *         Заменено на прямое присваивание + null-guard.
 * 1.15.5 (Волна 3, аудит 2.6-30): tick() переписан через чистую функцию computeTicks.
 *         Итерация по списку DoT-инстансов больше не мутирует коллекцию: пары
 *         (inst, damage) собираются в отдельный список, удаление expired/extinguished/
 *         invalid инстансов происходит отдельным проходом после вычислений.
 * 1.15.6 (Волна 4, аудит 2.6-2): ЕДИНАЯ ПОЛИТИКА ОКНА НЕУЯЗВИМОСТИ — DoT-тик
 *         сбрасывает vanilla noDamageTicks перед damage(...). Без этого DoT-урон
 *         клипался окном от авто-атаки владельца в тот же тик (период DoT = 20 тиков
 *         совпадает с окном): каждый второй тик burning/poison терял урон.
 *         Путь A (авто-атаки) окно СОХРАНЯЕТ — политика только для B и DoT.
 */
public final class DotService implements Listener {

    private static final long TICK_MILLIS = 1000L;

    private final RaskolClasses plugin;
    private final CombatService combat;
    private final NamespacedKey dotKey;
    private final Map<UUID, CopyOnWriteArrayList<DotInstance>> dots = new ConcurrentHashMap<>();

    /**
     * Конструктор без побочных эффектов (1.14.13, Волна 13B-b): только инициализация
     * полей. Регистрацию слушателей и запуск тик-задачи выполняет владелец
     * (CombatService создаёт ссылку, RaskolClasses.onEnable регистрирует и запускает).
     */
    public DotService(RaskolClasses plugin, CombatService combat) {
        this.plugin = plugin;
        this.combat = combat;
        this.dotKey = new NamespacedKey(plugin, "rc_dot");
    }

    /** 1.14.13 (Волна 13B-b): регистрация слушателей — вызов из RaskolClasses.onEnable. */
    public void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** 1.14.13 (Волна 13B-b): тик-задача 1 с (20 тиков); ручка в activeTasks владельца. */
    public BukkitTask startTickTask() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /* ------------------------------ реестр определений ------------------------------ */

    private static School defaultSchool(String id) {
        return switch (id) {
            case "burning", "burning_passive" -> School.FIRE;
            case "poison", "poison_passive" -> School.NATURE;
            case "bleed", "bleed_passive" -> School.PHYSICAL;
            case "chilled" -> School.FROST;
            case "wither" -> School.SHADOW;
            default -> School.ARCANE;
        };
    }

    private static double defaultDps(String id) {
        return switch (id) {
            case "burning" -> 4.0;
            case "burning_passive" -> 3.0;
            case "poison" -> 3.0;
            case "poison_passive" -> 2.5;
            case "bleed" -> 2.5;
            case "bleed_passive" -> 2.0;
            case "chilled" -> 2.0;
            case "wither" -> 2.5;
            default -> 1.0;
        };
    }

    private static int defaultDurationSeconds(String id) {
        return switch (id) {
            case "burning" -> 3;
            case "burning_passive" -> 2;
            case "poison" -> 5;
            case "poison_passive" -> 2;
            case "bleed" -> 4;
            case "bleed_passive" -> 3;
            case "chilled" -> 4;
            case "wither" -> 6;
            default -> 3;
        };
    }

    private static int defaultStacks(String id) {
        return switch (id) {
            case "burning", "poison" -> 3;
            case "bleed", "chilled" -> 2;
            case "wither" -> 3;
            default -> 1;
        };
    }

    public DotDef defById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        String base = "dots." + id + ".";
        School school = School.fromId(plugin.getConfig().getString(base + "school", ""));
        if (school == null) {
            school = defaultSchool(id);
        }
        double dps = plugin.getConfig().getDouble(base + "dps", defaultDps(id));
        if (!Double.isFinite(dps) || dps <= 0.0) {
            dps = defaultDps(id);
        }
        int dur = plugin.getConfig().getInt(base + "duration", defaultDurationSeconds(id));
        if (dur <= 0) {
            dur = defaultDurationSeconds(id);
        }
        int stacks = plugin.getConfig().getInt(base + "max-stacks", defaultStacks(id));
        if (stacks <= 0) {
            stacks = defaultStacks(id);
        }
        return DotDef.of(id, school, dps, dur * 1000L, stacks, id);
    }

    /* ------------------------------ публичный API ------------------------------ */

    public boolean applyById(Player owner, LivingEntity target, String dotId) {
        DotDef def = defById(dotId);
        if (def == null) {
            return false;
        }
        apply(owner, target, def);
        return true;
    }

    /**
     * 1.14.7 (P0-6A): DoT с надбавкой к dps (deep_wounds: +0.5 dps за ранг).
     * Надбавка добавляется к базовому dps из реестра ДО specTuned (узлы dot_mult
     * владельца умножат итог). Повторное наложение тем же владельцем освежает
     * существующий экземпляр (apply), dps которого зафиксирован при первом наложении.
     */
    public boolean applyByIdWithDpsBonus(Player owner, LivingEntity target, String dotId, double dpsBonus) {
        DotDef def = defById(dotId);
        if (def == null) {
            return false;
        }
        if (Double.isFinite(dpsBonus) && dpsBonus > 0.0) {
            def = DotDef.of(def.id(), def.school(), def.dps() + dpsBonus,
                    def.durationMillis(), def.maxStacks(), def.sourceAbility());
        }
        apply(owner, target, def);
        return true;
    }

    public void apply(Player owner, LivingEntity target, DotDef def) {
        if (owner == null || target == null || def == null || target.isDead()) {
            return;
        }
        if (!combat.canHit(owner, target)) {
            return;
        }
        // 1.14.0 (Б2): spec2-тюнинг источника (dot_dur/dot_stacks/dot_mult)
        def = specTuned(owner, def);
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

    /** 1.14.0 (Б2): ранги узлов дерева кастера тюнят его DoT. */
    private DotDef specTuned(Player owner, DotDef def) {
        Spec2Service spec2 = plugin.getSpec2Service();
        if (spec2 == null) {
            return def;
        }
        UUID uuid = owner.getUniqueId();
        double durSec = spec2.dotDurBonus(uuid, def.id());
        double stacks = spec2.dotStacksBonus(uuid, def.id());
        double multPct = spec2.dotMultBonus(uuid, def.id());
        if (durSec == 0.0 && stacks == 0.0 && multPct == 0.0) {
            return def;
        }
        long durationMillis = def.durationMillis() + (long) (durSec * 1000.0);
        int maxStacks = (int) Math.max(1, def.maxStacks() + stacks);
        double dps = def.dps() * (1.0 + multPct / 100.0);
        return DotDef.of(def.id(), def.school(), dps, durationMillis, maxStacks, def.sourceAbility());
    }

    public void removeAllOn(UUID targetUuid) {
        dots.remove(targetUuid);
    }

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

    /* ------------------------------ 1.12.6: HUD-API ------------------------------ */

    public List<DotInstance> activeDotsOf(UUID targetUuid) {
        CopyOnWriteArrayList<DotInstance> list = dots.get(targetUuid);
        return list == null ? Collections.emptyList() : List.copyOf(list);
    }

    public static long remainingSeconds(DotInstance inst, long nowMillis) {
        long left = inst.expiresAt() - nowMillis;
        if (left <= 0L) {
            return 0L;
        }
        return (left + 999L) / 1000L;
    }

    /* ------------------------------ средовые триггеры ------------------------------ */

    public static boolean shouldExtinguish(School school, Material block) {
        if (school == null || block == null) {
            return false;
        }
        if (school == School.FIRE) {
            return block == Material.WATER || block == Material.BUBBLE_COLUMN
                    || block == Material.POWDER_SNOW;
        }
        if (school == School.FROST) {
            return block == Material.FIRE || block == Material.SOUL_FIRE
                    || block == Material.LAVA;
        }
        return false;
    }

    /* ------------------------------ снарядные Dot'ы ------------------------------ */

    /**
     * 1.14.14 (фаза 1, BC_VACUOUS-фикс): event.getEntity() по контракту
     * ProjectileHitEvent возвращает Projectile (override от EntityEvent,
     * фактический тип всегда Projectile); instanceof был vacuous для non-null
     * значения. Заменено на прямое присваивание + null-guard.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        if (proj == null) {
            return;
        }
        if (!proj.getPersistentDataContainer().has(dotKey, PersistentDataType.STRING)) {
            return;
        }
        String dotId = proj.getPersistentDataContainer().get(dotKey, PersistentDataType.STRING);
        if (dotId == null || dotId.isEmpty()) {
            return;
        }
        if (!(proj.getShooter() instanceof Player owner)) {
            return;
        }
        Entity hitEntity = event.getHitEntity();
        if (!(hitEntity instanceof LivingEntity hit)) {
            return;
        }
        applyById(owner, hit, dotId);
    }

    public NamespacedKey dotTagKey() {
        return dotKey;
    }

    /* ------------------------------ тик (1.15.5: чистая функция computeTicks) ------------------------------ */

    /**
     * 1.15.5 (Волна 3, аудит 2.6-30): пара (экземпляр, урон) для одного DoT-тика.
     * damage = 0 означает «инстанс нужно удалить» (expired/extinguished/invalid);
     * положительные значения — фактический dps за тик (после mitigation/cap).
     */
    private record TickOutcome(DotInstance inst, double damage) {
    }

    /**
     * 1.15.5 (Волна 3, аудит 2.6-30): чистая функция — вычисление тиков без мутации
     * списка инстансов. Принимает снапшот списка, возвращает список пар
     * (инстанс, damage). Мутация (удаление expired/extinguished/invalid)
     * выполняется отдельным проходом после этой функции.
     */
    private List<TickOutcome> computeTicks(LivingEntity target,
                                           List<DotInstance> snapshot,
                                           long now) {
        List<TickOutcome> outcomes = new ArrayList<>(snapshot.size());
        for (DotInstance inst : snapshot) {
            if (inst.expired(now)) {
                outcomes.add(new TickOutcome(inst, 0.0));
                continue;
            }
            if (shouldExtinguish(inst.def().school(),
                    target.getLocation().getBlock().getType())) {
                puff(target, inst.def().school());
                outcomes.add(new TickOutcome(inst, 0.0));
                continue;
            }
            Player owner = plugin.getServer().getPlayer(inst.ownerUuid());
            if (owner == null || !combat.canHit(owner, target)) {
                outcomes.add(new TickOutcome(inst, 0.0));
                continue;
            }
            School school = inst.def().school();
            double immunity = combat.schoolImmunity().multiplierFor(target.getType(), school);
            if (immunity <= 0.0) {
                outcomes.add(new TickOutcome(inst, 0.0));
                continue;
            }
            double baseDps = inst.def().dps() * inst.stacks();
            double seal = WarlockAbilities.sealAmplifyOf(target.getUniqueId());
            double dps = DotMath.withMults(baseDps,
                    combat.schoolConfig().multiplier(school), immunity, seal);
            dps *= 1.0 - mitigationFor(target, owner, school);
            outcomes.add(new TickOutcome(inst, dps));
        }
        return outcomes;
    }

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

            // 1.15.5 (2.6-30): снапшот перед вычислениями; мутация в конце
            List<DotInstance> snapshot = List.copyOf(list);
            List<TickOutcome> outcomes = computeTicks(target, snapshot, now);

            // Удаление невалидных инстансов отдельным проходом
            for (TickOutcome outcome : outcomes) {
                if (outcome.damage() <= 0.0) {
                    list.remove(outcome.inst());
                }
            }
            if (list.isEmpty()) {
                dots.remove(targetUuid);
                continue;
            }

            // Применение cap и нанесение урона
            double[] damages = new double[outcomes.size()];
            int validCount = 0;
            for (TickOutcome outcome : outcomes) {
                if (outcome.damage() > 0.0) {
                    damages[validCount++] = outcome.damage();
                }
            }
            if (validCount == 0) {
                continue;
            }

            double maxHp = combat.caps().formulaMaxOf(target);
            double capPct = plugin.getConfig().getDouble("combat.dot-dps-cap-pct", 6.0);
            double limit = DotMath.dpsLimit(maxHp, capPct);
            double[] pending = new double[validCount];
            System.arraycopy(damages, 0, pending, 0, validCount);
            double factor = DotMath.capFactor(pending, limit);
            double scale = combat.scaleOf(target);

            for (TickOutcome outcome : outcomes) {
                if (outcome.damage() <= 0.0) {
                    continue;
                }
                Player owner = plugin.getServer().getPlayer(outcome.inst().ownerUuid());
                if (owner == null) {
                    continue;
                }
                double amount = outcome.damage() * factor * (TICK_MILLIS / 1000.0) * scale;
                if (amount <= 0.0) {
                    continue;
                }
                CombatService.setSuppress(true);
                CombatService.beginReflect();
                try {
                    // 1.15.6 (2.6-2): DoT-тик bypass'ит vanilla-окно неуязвимости —
                    // период DoT (20 тиков) совпадает с окном, без сброса каждый
                    // второй тик терял урон от авто-атаки владельца в тот же тик
                    target.setNoDamageTicks(0);
                    target.damage(amount, owner);
                } finally {
                    CombatService.endReflect();
                    CombatService.setSuppress(false);
                }
                tickVfx(target, outcome.inst().def().school());
            }
            if (list.isEmpty()) {
                dots.remove(targetUuid);
            }
        }
    }

    private void tickVfx(LivingEntity target, School school) {
        Particle p = resolveParticle(plugin.getConfig().getString(
                "vfx.dot." + school.id() + ".particle", null), defaultDotParticle(school));
        if (p != null) {
            target.getWorld().spawnParticle(p, target.getLocation().add(0.0, 1.0, 0.0),
                    2, 0.2, 0.3, 0.2, 0.01);
        }
    }

    private void puff(LivingEntity target, School school) {
        Particle p = school == School.FIRE ? Particle.SMOKE : Particle.CLOUD;
        target.getWorld().spawnParticle(p, target.getLocation().add(0.0, 1.0, 0.0),
                6, 0.3, 0.4, 0.3, 0.01);
    }

    /** 1.14.0 (Б7): партиклы всех 8 школ (раньше SHADOW/HOLY/ARCANE/TRUE = null). */
    private Particle defaultDotParticle(School school) {
        return switch (school) {
            case FIRE -> Particle.FLAME;
            case FROST -> Particle.SNOWFLAKE;
            case NATURE -> Particle.COMPOSTER;
            case PHYSICAL -> Particle.DAMAGE_INDICATOR;
            case SHADOW -> Particle.SCULK_SOUL;
            case HOLY -> Particle.ENCHANT;
            case ARCANE -> Particle.REVERSE_PORTAL;
            case TRUE -> Particle.POOF;
        };
    }

    private Particle resolveParticle(String name, Particle fallback) {
        if (name == null || name.isEmpty()) {
            return fallback;
        }
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

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
                sc.penPctCap());
        double effChannel = pen.effectiveResist(channelResistPct, sc.penPctCap());
        double elResist = combat.elemental().resistOf(targetUuid, school);
        double schoolPen = combat.penTraits().schoolPenFraction(
                owner.getUniqueId(), school, sc.penPctCap());
        double effEl = CombatMath.effectiveResist(elResist, 0.0, schoolPen, sc.penPctCap());
        return SchoolMitigation.mitigationFor(effChannel, Penetration.NONE,
                sc.penPctCap(), effEl, sc.elementalEnabled(), sc.mitigationCap());
    }
}
