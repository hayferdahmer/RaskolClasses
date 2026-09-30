// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.cc;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.13.0: ядро системы контроля (CC) и убывающей отдачи (DR).
 *
 * Алгоритм tryApply (ТЗ п.5.4, адаптировано под наш стек):
 *  1) рубильник cc.enabled; 2) фракционный гейт canHit; 3) иммунитеты
 *  (теги BOSS/MINION_ELITE + явные ENTITY_* + CHARM на игроков при запрете);
 *  4) бросок ccResist (класс + Скверна≥75 у чернокнижника, кап cc.resist-cap);
 *  5) окно DR: истекло → стек 0; 6) стек ≥ длины multipliers → FAIL_DR_IMMUNE;
 *  7) duration = base × drMult × (1+ccPower) × (1−ccReduction), минимум 1 тик;
 *  8) стек +1; 9) экземпляр в реестр; 10) тик-поведение (ROOT/STUN — стоп-движение,
 *  FEAR — блуждание, SLOW — резист скорости).
 *
 * Запреты действий (каст/атака/хотбар) и прерывание кастов — батч 2 (шаги 4–6).
 * Ванильная обёртка — батч 3 (VanillaCCWrapper). HUD/фидбек — батчи 3–4.
 *
 * DR не персистится: окно 15 с переживает рестарт бессмысленно.
 * Смерть и выход снимают CC; смерть дополнительно сбрасывает DR (ТЗ п.5.5).
 *
 * 1.13.0-fix: удалена черновая строка-заглушка Set<EntityType-ish> (синтаксическая
 * ошибка строки 62); снятие атрибут-модификатора SLOW — через getModifiers()+
 * removeModifier(mod) со сравнением getKey() (removeModifier(NamespacedKey)
 * присутствует не во всех сборках Paper 1.21.4).
 */
public final class CCService implements Listener {

    /** Результат попытки наложения CC. */
    public enum CCResult {
        SUCCESS, FAIL_DISABLED, FAIL_ALLY, FAIL_IMMUNE, FAIL_RESIST, FAIL_DR_IMMUNE
    }

    /** Исход применения: результат + фактическая длительность + множитель DR. */
    public record ApplyResult(CCResult result, int appliedTicks, double drMultiplier) {
        public boolean ok() { return result == CCResult.SUCCESS && appliedTicks > 0; }
    }

    private final RaskolClasses plugin;
    private final Map<UUID, CopyOnWriteArrayList<CCInstance>> active = new ConcurrentHashMap<>();
    private final Map<UUID, EnumMap<DRCategory, DRState>> drStates = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextFearTurn = new ConcurrentHashMap<>();
    private final Map<UUID, Float> slowWalkBase = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> slowAttrApplied = new ConcurrentHashMap<>();
    private final NamespacedKey slowAttrKey;

    public CCService(RaskolClasses plugin) {
        this.plugin = plugin;
        this.slowAttrKey = new NamespacedKey(plugin, "cc_slow");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /* ------------------------------ конфиг-читалки ------------------------------ */

    public boolean enabled() {
        return plugin.getConfig().getBoolean("cc.enabled", true);
    }

    private long windowMillis() {
        double sec = plugin.getConfig().getDouble("cc.window-seconds", 15.0);
        return (long) ((Double.isFinite(sec) && sec > 0.0 ? sec : 15.0) * 1000.0);
    }

    private double[] multipliers() {
        List<Double> list = plugin.getConfig().getDoubleList("cc.dr-multipliers");
        if (list == null || list.isEmpty()) {
            return new double[]{1.0, 0.5, 0.25, 0.0};
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            double v = list.get(i);
            out[i] = Double.isFinite(v) && v >= 0.0 ? v : 0.0;
        }
        return out;
    }

    private double resistCap() {
        double v = plugin.getConfig().getDouble("cc.resist-cap", 0.60);
        return Double.isFinite(v) && v >= 0.0 && v <= 1.0 ? v : 0.60;
    }

    private double powerCap() {
        double v = plugin.getConfig().getDouble("cc.power-cap", 0.50);
        return Double.isFinite(v) && v >= 0.0 && v <= 1.0 ? v : 0.50;
    }

    private double reductionCap() {
        double v = plugin.getConfig().getDouble("cc.duration-reduction-cap", 0.50);
        return Double.isFinite(v) && v >= 0.0 && v <= 1.0 ? v : 0.50;
    }

    private double breakThreshold() {
        double v = plugin.getConfig().getDouble("cc.breaks-on-damage-threshold-pct", 0.05);
        return Double.isFinite(v) && v > 0.0 ? v : 0.05;
    }

    public int durationTicks(CCType type) {
        int v = plugin.getConfig().getInt("cc.types." + type.id() + ".duration-ticks", type.defaultTicks());
        return v > 0 ? v : type.defaultTicks();
    }

    public boolean breaksOnDamage(CCType type) {
        return plugin.getConfig().getBoolean("cc.types." + type.id() + ".breaks-on-damage", type.breaksOnDamage());
    }

    /** ccResist цели: класс (cc.class-resist.*) + Скверна≥порога у чернокнижника; кап resist-cap. */
    public double ccResistOf(LivingEntity target) {
        double base = 0.0;
        if (target instanceof Player p) {
            PlayerClass pc = plugin.getClassProvider().getClassOf(p);
            if (pc != null) {
                base += plugin.getConfig().getDouble("cc.class-resist." + pc.name(), defaultClassResist(pc));
            }
            if (pc == PlayerClass.WARLOCK
                    && plugin.getResources().getValue(p.getUniqueId())
                        >= plugin.getRaskolConfig().warlockThresholdOpen()) {
                base += plugin.getConfig().getDouble("cc.corruption-resist-bonus", 0.10);
            }
        }
        return Math.max(0.0, Math.min(base, resistCap()));
    }

    private double defaultClassResist(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> 0.20;
            case HUNTER, ROGUE, PRIEST -> 0.15;
            case MAGE, WARLOCK -> 0.10;
        };
    }

    /** ccPower кастера: Скверна≥порога у чернокнижника; кап power-cap. */
    public double ccPowerOf(Player caster) {
        double base = 0.0;
        if (caster != null
                && plugin.getClassProvider().getClassOf(caster) == PlayerClass.WARLOCK
                && plugin.getResources().getValue(caster.getUniqueId())
                    >= plugin.getRaskolConfig().warlockThresholdOpen()) {
            base += plugin.getConfig().getDouble("cc.corruption-power-bonus", 0.20);
        }
        return Math.max(0.0, Math.min(base, powerCap()));
    }

    /** Сокращение длительности у цели (1.14.0: спеки/роли; сейчас только конфиг-заготовка). */
    public double ccReductionOf(LivingEntity target) {
        double base = 0.0;
        if (target instanceof Player p) {
            PlayerClass pc = plugin.getClassProvider().getClassOf(p);
            if (pc != null) {
                base += plugin.getConfig().getDouble("cc.class-reduction." + pc.name(), 0.0);
            }
        }
        return Math.max(0.0, Math.min(base, reductionCap()));
    }

    /* ------------------------------ иммунитеты ------------------------------ */

    private static final Set<String> BOSS_TYPES = Set.of(
            "ENDER_DRAGON", "WITHER", "WARDEN", "ELDER_GUARDIAN");
    private static final Set<String> ELITE_TYPES = Set.of(
            "EVOKER", "VINDICATOR", "PIGLIN_BRUTE");

    private String tagOf(LivingEntity entity) {
        String name = entity.getType().name();
        if (BOSS_TYPES.contains(name)) {
            return "BOSS";
        }
        if (ELITE_TYPES.contains(name)) {
            return "MINION_ELITE";
        }
        return "MINION";
    }

    public boolean isImmune(LivingEntity target, CCType type) {
        if (type == CCType.CHARM && target instanceof Player
                && !plugin.getConfig().getBoolean("cc.charm.allow-on-players", false)) {
            return true;
        }
        String tag = tagOf(target);
        if (inImmunityList(tag, type)) {
            return true;
        }
        return inImmunityList("ENTITY_" + target.getType().name(), type);
    }

    private boolean inImmunityList(String key, CCType type) {
        List<String> list = plugin.getConfig().getStringList("cc.entity-immunity." + key);
        if (list == null) {
            return false;
        }
        for (String s : list) {
            if (type.id().equalsIgnoreCase(s == null ? "" : s.trim().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------ DR-математика (pure, selftest 76–78) ------------------------------ */

    /** Множитель DR для стека: вне диапазона → 0 (иммунитет). */
    public static double drMultiplier(int stack, double[] mults) {
        if (mults == null || mults.length == 0 || stack < 0) {
            return 1.0;
        }
        if (stack >= mults.length) {
            return 0.0;
        }
        double v = mults[stack];
        return Double.isFinite(v) && v >= 0.0 ? v : 0.0;
    }

    /** Стек ≥ длины списка множителей → DR-иммунитет (selftest-чек 78). */
    public static boolean isDrImmune(int stack, double[] mults) {
        return mults != null && stack >= mults.length;
    }

    /** Сброс стека по истечении окна (pure, selftest-чек 77). */
    public static int stackAfterWindow(long now, long lastApplied, long windowMillis, int current) {
        return (now - lastApplied) > windowMillis ? 0 : current;
    }

    /* ------------------------------ применение ------------------------------ */

    public ApplyResult tryApply(Player caster, LivingEntity target, CCType type) {
        return tryApply(caster, target, type, durationTicks(type));
    }

    public ApplyResult tryApply(Player caster, LivingEntity target, CCType type, int baseTicks) {
        if (type == null || target == null || target.isDead()) {
            return new ApplyResult(CCResult.FAIL_DISABLED, 0, 1.0);
        }
        if (!enabled()) {
            return new ApplyResult(CCResult.FAIL_DISABLED, 0, 1.0);
        }
        if (caster != null && !plugin.getCombat().canHit(caster, target)) {
            return new ApplyResult(CCResult.FAIL_ALLY, 0, 1.0);
        }
        if (isImmune(target, type)) {
            return new ApplyResult(CCResult.FAIL_IMMUNE, 0, 1.0);
        }
        double resist = ccResistOf(target);
        if (resist > 0.0 && ThreadLocalRandom.current().nextDouble() < resist) {
            return new ApplyResult(CCResult.FAIL_RESIST, 0, 1.0);
        }

        UUID targetUuid = target.getUniqueId();
        long now = System.currentTimeMillis();
        long window = windowMillis();
        double[] mults = multipliers();

        EnumMap<DRCategory, DRState> byCat =
                drStates.computeIfAbsent(targetUuid, k -> new EnumMap<>(DRCategory.class));
        DRCategory cat = type.category();
        DRState state = byCat.getOrDefault(cat, DRState.EMPTY);
        int stack = stackAfterWindow(now, state.lastAppliedAt(), window, state.stackCount());
        if (isDrImmune(stack, mults)) {
            byCat.put(cat, new DRState(stack, state.lastAppliedAt(), state.windowStart()));
            return new ApplyResult(CCResult.FAIL_DR_IMMUNE, 0, 0.0);
        }
        double drMult = drMultiplier(stack, mults);

        double power = ccPowerOf(caster);
        double reduction = ccReductionOf(target);
        double ticks = baseTicks * drMult * (1.0 + power) * (1.0 - reduction);
        int applied = (int) Math.max(1L, Math.round(ticks));
        if (drMult <= 0.0) {
            applied = 0;
        }

        byCat.put(cat, new DRState(stack + 1, now, stack == 0 ? now : state.windowStart()));

        if (applied <= 0) {
            return new ApplyResult(CCResult.SUCCESS, 0, drMult);
        }
        UUID sourceUuid = caster != null ? caster.getUniqueId() : null;
        active.computeIfAbsent(targetUuid, k -> new CopyOnWriteArrayList<>())
                .add(new CCInstance(type, sourceUuid, now, applied, drMult));
        onApplyBehavior(target, type);
        return new ApplyResult(CCResult.SUCCESS, applied, drMult);
    }

    /* ------------------------------ реестр / снятие ------------------------------ */

    public List<CCInstance> activeOf(UUID targetUuid) {
        CopyOnWriteArrayList<CCInstance> list = active.get(targetUuid);
        return list == null ? Collections.emptyList() : List.copyOf(list);
    }

    public boolean has(UUID targetUuid, CCType type) {
        long now = System.currentTimeMillis();
        for (CCInstance inst : activeOf(targetUuid)) {
            if (inst.type() == type && !inst.expired(now)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCategory(UUID targetUuid, DRCategory cat) {
        long now = System.currentTimeMillis();
        for (CCInstance inst : activeOf(targetUuid)) {
            if (inst.type().category() == cat && !inst.expired(now)) {
                return true;
            }
        }
        return false;
    }

    public void removeType(UUID targetUuid, CCType type) {
        CopyOnWriteArrayList<CCInstance> list = active.get(targetUuid);
        if (list == null) {
            return;
        }
        list.removeIf(inst -> inst.type() == type);
        if (list.isEmpty()) {
            active.remove(targetUuid);
        }
        clearBehavior(targetUuid, type);
    }

    public void removeAll(UUID targetUuid) {
        CopyOnWriteArrayList<CCInstance> list = active.remove(targetUuid);
        if (list != null) {
            for (CCInstance inst : list) {
                clearBehavior(targetUuid, inst.type());
            }
        }
        nextFearTurn.remove(targetUuid);
    }

    /** breaksOnDamage: урон ≥ порога (% от maxHP) снимает соответствующие CC. */
    public void breakOnDamage(LivingEntity target, double damageFormula, double maxHpFormula) {
        if (maxHpFormula <= 0.0 || damageFormula <= 0.0) {
            return;
        }
        double pct = damageFormula / maxHpFormula;
        if (pct < breakThreshold()) {
            return;
        }
        UUID uuid = target.getUniqueId();
        CopyOnWriteArrayList<CCInstance> list = active.get(uuid);
        if (list == null) {
            return;
        }
        for (CCInstance inst : list) {
            if (breaksOnDamage(inst.type())) {
                list.remove(inst);
                clearBehavior(uuid, inst.type());
            }
        }
        if (list.isEmpty()) {
            active.remove(uuid);
        }
    }

    /* ------------------------------ DR-состояние (команды/отладка) ------------------------------ */

    public DRState drState(UUID targetUuid, DRCategory cat) {
        EnumMap<DRCategory, DRState> byCat = drStates.get(targetUuid);
        return byCat == null ? DRState.EMPTY : byCat.getOrDefault(cat, DRState.EMPTY);
    }

    public void resetDr(UUID targetUuid, DRCategory cat) {
        EnumMap<DRCategory, DRState> byCat = drStates.get(targetUuid);
        if (byCat != null) {
            byCat.remove(cat);
        }
    }

    public void resetAllDr(UUID targetUuid) {
        drStates.remove(targetUuid);
    }

    /* ------------------------------ тик-поведения ------------------------------ */

    private void onApplyBehavior(LivingEntity target, CCType type) {
        if (type == CCType.SLOW) {
            applySlow(target);
        }
    }

    private void clearBehavior(UUID targetUuid, CCType type) {
        if (type != CCType.SLOW) {
            return;
        }
        Entity entity = plugin.getServer().getEntity(targetUuid);
        if (entity instanceof Player p) {
            Float base = slowWalkBase.remove(targetUuid);
            if (base != null) {
                p.setWalkSpeed(base);
            }
        } else if (entity instanceof LivingEntity le) {
            removeSlowModifier(le);
            slowAttrApplied.remove(targetUuid);
        }
    }

    /**
     * 1.13.0-fix: снятие модификатора SLOW без removeModifier(NamespacedKey) —
     * обходим копию getModifiers() и сравниваем getKey() (портативно для 1.21.4).
     */
    private void removeSlowModifier(LivingEntity le) {
        AttributeInstance ai = le.getAttribute(Attribute.MOVEMENT_SPEED);
        if (ai == null) {
            return;
        }
        for (AttributeModifier m : List.copyOf(ai.getModifiers())) {
            NamespacedKey key = m.getKey();
            if (key != null && key.equals(slowAttrKey)) {
                ai.removeModifier(m);
            }
        }
    }

    private double slowMult() {
        double v = plugin.getConfig().getDouble("cc.types.SLOW.slow-mult", 0.5);
        return Double.isFinite(v) && v > 0.0 && v <= 1.0 ? v : 0.5;
    }

    private void applySlow(LivingEntity target) {
        UUID uuid = target.getUniqueId();
        double mult = slowMult();
        if (target instanceof Player p) {
            slowWalkBase.computeIfAbsent(uuid, k -> p.getWalkSpeed());
            float base = slowWalkBase.get(uuid);
            p.setWalkSpeed((float) Math.max(0.0, base * mult));
        } else {
            AttributeInstance ai = target.getAttribute(Attribute.MOVEMENT_SPEED);
            if (ai != null && slowAttrApplied.putIfAbsent(uuid, Boolean.TRUE) == null) {
                ai.addModifier(new AttributeModifier(slowAttrKey, mult - 1.0,
                        AttributeModifier.Operation.ADD_SCALAR));
            }
        }
    }

    private void tick() {
        if (active.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, CopyOnWriteArrayList<CCInstance>> entry : active.entrySet()) {
            UUID uuid = entry.getKey();
            CopyOnWriteArrayList<CCInstance> list = entry.getValue();
            Entity entity = plugin.getServer().getEntity(uuid);
            if (!(entity instanceof LivingEntity target) || target.isDead()) {
                removeAll(uuid);
                continue;
            }
            boolean stunOrRoot = false;
            boolean fear = false;
            for (CCInstance inst : list) {
                if (inst.expired(now)) {
                    list.remove(inst);
                    clearBehavior(uuid, inst.type());
                    continue;
                }
                switch (inst.type()) {
                    case STUN, ROOT -> stunOrRoot = true;
                    case FEAR -> fear = true;
                    default -> { }
                }
            }
            if (list.isEmpty()) {
                active.remove(uuid);
                nextFearTurn.remove(uuid);
                continue;
            }
            if (stunOrRoot) {
                Vector v = target.getVelocity();
                target.setVelocity(new Vector(0.0, v.getY(), 0.0));
            }
            if (fear) {
                Long turnAt = nextFearTurn.get(uuid);
                if (turnAt == null || now >= turnAt) {
                    double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
                    Vector dir = new Vector(Math.cos(angle), 0.0, Math.sin(angle)).multiply(0.5);
                    target.setVelocity(dir.setY(0.0));
                    nextFearTurn.put(uuid, now + 50L * (20 + ThreadLocalRandom.current().nextInt(21)));
                }
            } else {
                nextFearTurn.remove(uuid);
            }
        }
    }

    /* ------------------------------ события ------------------------------ */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        removeAll(uuid);
        resetAllDr(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        removeAll(uuid);
        resetAllDr(uuid); // ТЗ п.5.5: смерть сбрасывает DRState
    }
}
