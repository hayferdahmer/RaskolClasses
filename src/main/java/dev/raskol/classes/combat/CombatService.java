// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.PowerService;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.6.0: боевой сервис урона и резистов. Путь A (ваниль) + путь B (наши способности).
 * 1.7.1: производные статы и анти-ваншот. 1.7.6.1: burst-окно.
 * 1.8.1 (S1+S2): ЕДИНЫЙ фракционный гейт.
 * 1.9.3 FIX: applyEnvLethalScale читает HP из AttributeService (формула),
 * а НЕ из ванильного MAX_HEALTH (который не синхронизирован с формулой).
 * Константа MAX_HEALTH и импорты Attribute/AttributeInstance удалены.
 */
public final class CombatService implements Listener {

    private static final List<String> DEFAULT_ENV =
            List.of("FALL", "DROWNING", "SUFFOCATION", "STARVATION");

    private static final List<String> DEFAULT_EXEMPT =
            List.of("FALL", "DROWNING", "SUFFOCATION", "STARVATION", "VOID", "SONIC_BOOM");

    private static final ThreadLocal<Boolean> SUPPRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile org.bukkit.damage.DamageType magicTypeCache;

    private final RaskolClasses plugin;
    private final ResistService resists;
    private final AvoidanceService avoidance;
    private final PowerService powers;

    private final Map<UUID, Deque<double[]>> burstLog = new ConcurrentHashMap<>();

    public CombatService(RaskolClasses plugin, ResistService resists) {
        this.plugin = plugin;
        this.resists = resists;
        this.avoidance = new AvoidanceService(plugin);
        this.powers = new PowerService(plugin);
    }

    public ResistService resists() {
        return resists;
    }

    public AvoidanceService avoidance() {
        return avoidance;
    }

    public PowerService powers() {
        return powers;
    }

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private static org.bukkit.damage.DamageType magicType() {
        org.bukkit.damage.DamageType local = magicTypeCache;
        if (local == null) {
            local = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.DAMAGE_TYPE)
                    .get(NamespacedKey.minecraft("magic"));
            magicTypeCache = local;
        }
        return local;
    }

    private double trueCap() {
        double v = cfgD("combat.true-damage-cap-per-hit", 1000.0);
        return v >= 0.0 ? v : 1000.0;
    }

    private static boolean isPvp(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return false;
        }
        Entity damager = byEntity.getDamager();
        if (damager instanceof Player) {
            return true;
        }
        return damager instanceof Projectile proj && proj.getShooter() instanceof Player;
    }

    /* ------------------------- 1.8.1: фракционный гейт ------------------------- */

    public boolean canHit(Entity source, LivingEntity target) {
        if (target == null) {
            return false;
        }
        if (!(target instanceof Player tp)) {
            return true;
        }
        if (source == null) {
            return true;
        }
        if (!(source instanceof Player sp)) {
            return true;
        }
        if (sp.getUniqueId().equals(tp.getUniqueId())) {
            return true;
        }
        if (plugin.getConfig().getBoolean("combat.friendly-fire", false)) {
            return true;
        }
        return !Targeting.isAlly(plugin, sp.getUniqueId(), tp.getUniqueId());
    }

    /* ------------------------------ путь A (ваниль) ------------------------------ */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        boolean suppressed = Boolean.TRUE.equals(SUPPRESS.get());
        if (suppressed) {
            SUPPRESS.set(Boolean.FALSE);
        }

        if (!suppressed && event instanceof EntityDamageByEntityEvent by) {
            Player attacker = null;
            Entity damager = by.getDamager();
            if (damager instanceof Player pa) {
                attacker = pa;
            } else if (damager instanceof Projectile proj
                    && proj.getShooter() instanceof Player ps) {
                attacker = ps;
            }
            if (attacker != null
                    && event.getEntity() instanceof Player victimTarget
                    && !attacker.getUniqueId().equals(victimTarget.getUniqueId())
                    && !plugin.getConfig().getBoolean("combat.friendly-fire", false)
                    && Targeting.isAlly(plugin, attacker.getUniqueId(), victimTarget.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }

        if (!suppressed) {
            applyOutgoingOffense(event);
        }
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
        if (target.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (resists.disabledIn(target.getWorld())) {
            return;
        }
        DamageType type = typeOf(event.getCause());
        if (type == DamageType.TRUE) {
            if (!suppressed) {
                applyEnvLethalScale(event, target);
            }
            return;
        }
        if (type == DamageType.PHYSICAL && avoidance.tryAvoid(target, event)) {
            event.setCancelled(true);
            return;
        }
        if (suppressed) {
            return;
        }
        double cap = isPvp(event) ? resists.pvpCap() : resists.cap();
        UUID uuid = target.getUniqueId();
        double factor = type == DamageType.PHYSICAL
                ? resists.physicalFactor(uuid, cap)
                : resists.magicFactor(uuid, cap);
        if (!Double.isFinite(factor) || factor >= 1.0 || factor < 0.0) {
            return;
        }
        event.setDamage(event.getDamage() * factor);
        applySingleHitCap(event);
        event.setDamage(applyBurstCap(target, event.getDamage()));
    }

    /* --------------------- исходящий офенс (путь A, 1.7.1) --------------------- */

    private void applyOutgoingOffense(EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent by)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        Player attacker = null;
        Entity damager = by.getDamager();
        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player sh) {
            attacker = sh;
        }
        if (attacker == null) {
            return;
        }
        DamageType type = typeOf(event.getCause());
        if (type == DamageType.TRUE) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        double add = 0.0;
        boolean crit = false;
        if (type == DamageType.PHYSICAL) {
            add += powers.weaponPower(uuid) * cfgD("attributes.offense.basic-coeff", 0.35);
            if (plugin.getConfig().getBoolean("attributes.offense.str-to-physical", false)) {
                add += plugin.getAttributes().value(uuid, dev.raskol.classes.attribute.AttributeType.STR)
                        + plugin.getAttributes().levelOf(uuid,
                        plugin.getClassProvider().getClassOf(attacker));
            }
            crit = rollMeleeCrit(attacker);
        } else {
            add += powers.spellPower(uuid) * cfgD("attributes.offense.basic-coeff-magic", 0.35);
            if (plugin.getConfig().getBoolean("attributes.offense.int-to-magic", false)) {
                add += plugin.getAttributes().value(uuid, dev.raskol.classes.attribute.AttributeType.INT)
                        + plugin.getAttributes().levelOf(uuid,
                        plugin.getClassProvider().getClassOf(attacker));
            }
            crit = rollSpellCrit(attacker);
        }
        double base = event.getDamage();
        double total = base + add;
        if (crit) {
            total *= type == DamageType.PHYSICAL ? meleeMult() : spellMult();
            critFeedback(attacker, type == DamageType.PHYSICAL);
        }
        if (total != base && Double.isFinite(total) && total >= 0.0) {
            event.setDamage(total);
        }
    }

    private boolean rollMeleeCrit(Player player) {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0
                < plugin.getAttributes().critMeleeChance(player.getUniqueId());
    }

    private boolean rollSpellCrit(Player player) {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() * 100.0
                < plugin.getAttributes().critSpellChance(player.getUniqueId());
    }

    private double meleeMult() {
        return cfgD("attributes.crit.melee-mult", 1.5);
    }

    private double spellMult() {
        return cfgD("attributes.crit.spell-mult", 1.5);
    }

    private void critFeedback(Player attacker, boolean melee) {
        if (!plugin.getConfig().getBoolean("attributes.crit.visuals", true)) {
            return;
        }
        String tag = melee
                ? plugin.getConfig().getString("attributes.crit.melee-tag", "⚡ Крит!")
                : plugin.getConfig().getString("attributes.crit.spell-tag", "✦ Магический крит!");
        attacker.showTitle(net.kyori.adventure.title.Title.title(
                Component.empty(),
                Component.text(tag, melee ? NamedTextColor.RED : NamedTextColor.LIGHT_PURPLE),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(50),
                        java.time.Duration.ofMillis(550),
                        java.time.Duration.ofMillis(150))));
        dev.raskol.classes.fx.FxService fx = plugin.getFx();
        String soundKey = melee
                ? plugin.getConfig().getString("attributes.crit.melee-sound", "ENTITY_PLAYER_ATTACK_CRIT")
                : plugin.getConfig().getString("attributes.crit.spell-sound", "ENTITY_EVOKER_CAST_SPELL");
        org.bukkit.Sound s = fx.resolveSound(soundKey);
        if (s != null) {
            fx.playSound(attacker.getLocation(), s, 0.5f, melee ? 0.9f : 1.2f);
        }
    }

    /* ------------------------- летальная среда (1.7.0.2, 1.9.3 fix) ------------------------- */

    /**
     * 1.9.3 FIX: скейлинг от формульного HP (AttributeService), а не от ванильного
     * MAX_HEALTH. Раньше scale = 20/20 = 1 → падения не масштабировались для
     * воина с 2560 HP.
     */
    private void applyEnvLethalScale(EntityDamageEvent event, Player target) {
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
        double max = plugin.getAttributes().maxHp(target.getUniqueId());
        double scale = max / 20.0;
        if (scale > 1.0 && Double.isFinite(scale)) {
            event.setDamage(event.getDamage() * scale);
        }
    }

    /* --------------------- анти-ваншот одиночный (1.7.1) --------------------- */

    public static double cappedDamage(double damage, double maxHp, double pct) {
        if (!Double.isFinite(damage) || damage <= 0.0) {
            return 0.0;
        }
        if (!Double.isFinite(maxHp) || maxHp <= 0.0) {
            return damage;
        }
        if (!Double.isFinite(pct) || pct <= 0.0) {
            return damage;
        }
        double limit = maxHp * pct / 100.0;
        return damage > limit ? limit : damage;
    }

    private void applySingleHitCap(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)) {
            return;
        }
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
        double max = plugin.getAttributes().maxHp(target.getUniqueId());
        double capped = cappedDamage(event.getDamage(), max, pct);
        if (capped != event.getDamage()) {
            event.setDamage(capped);
        }
    }

    /* --------------------- burst-window cap (1.7.6.1) --------------------- */

    private double applyBurstCap(Player target, double damage) {
        if (damage <= 0.0) {
            return damage;
        }
        double seconds = cfgD("combat.burst-window-seconds", 3.0);
        double pct = cfgD("combat.burst-window-pct", 18.0);
        if (seconds <= 0.0 || pct <= 0.0) {
            return damage;
        }
        double max = plugin.getAttributes().maxHp(target.getUniqueId());
        double cap = max * pct / 100.0;
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

    public void purgeBurstLog() {
        long now = System.currentTimeMillis();
        burstLog.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                entry.getValue().removeIf(e -> now - e[0] > 10_000L);
                return entry.getValue().isEmpty();
            }
        });
    }

    /* ------------------------- симулятор (1.6.4) ------------------------- */

    public double simulateTaken(LivingEntity target, DamageProfile profile) {
        if (profile == null || target == null) {
            return 0.0;
        }
        DamageProfile safe = sanitize(profile);
        double truePart = Math.min(safe.trueDamage(), trueCap());
        if (resists.disabledIn(target.getWorld())) {
            return safe.physical() + safe.magic() + truePart;
        }
        if (target instanceof Player p) {
            UUID uuid = p.getUniqueId();
            double physFactor = resists.physicalFactor(uuid);
            double magicFactor = resists.magicFactor(uuid);
            double phys = Double.isFinite(physFactor) ? safe.physical() * physFactor : safe.physical();
            double magic = Double.isFinite(magicFactor) ? safe.magic() * magicFactor : safe.magic();
            return phys + magic + truePart;
        }
        return safe.physical() + safe.magic() + truePart;
    }

    /* ------------------------- путь B (наши способности) ------------------------- */

    public double dealDamage(LivingEntity target, Entity source, DamageProfile profile) {
        return dealDamage(target, source, profile, false);
    }

    public double dealDamage(LivingEntity target, Entity source, DamageProfile profile,
                             boolean allowOverCap) {
        if (profile == null || target == null || target.isDead()) {
            return 0.0;
        }
        if (!canHit(source, target)) {
            return 0.0;
        }
        DamageProfile safe = sanitize(profile);
        if (safe.isEmpty()) {
            return 0.0;
        }
        if (target instanceof Player tp) {
            GameMode gm = tp.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) {
                return 0.0;
            }
        }
        double truePart = Math.min(safe.trueDamage(), trueCap());
        double physBase = safe.physical();
        double magicBase = safe.magic();
        if (source instanceof Player sp) {
            if (physBase > 0.0 && rollMeleeCrit(sp)) {
                physBase *= meleeMult();
                critFeedback(sp, true);
            }
            if (magicBase > 0.0 && rollSpellCrit(sp)) {
                magicBase *= spellMult();
                critFeedback(sp, false);
            }
        }
        double physPart;
        double magicTruePart;
        if (resists.disabledIn(target.getWorld())) {
            physPart = physBase;
            magicTruePart = magicBase + truePart;
        } else if (target instanceof Player p) {
            UUID uuid = p.getUniqueId();
            double cap = source instanceof Player ? resists.pvpCap() : resists.cap();
            double physFactor = resists.physicalFactor(uuid, cap);
            double magicFactor = resists.magicFactor(uuid, cap);
            physPart = Double.isFinite(physFactor) ? physBase * physFactor : physBase;
            double magicScaled = Double.isFinite(magicFactor) ? magicBase * magicFactor : magicBase;
            magicTruePart = magicScaled + truePart;
        } else {
            physPart = physBase;
            magicTruePart = magicBase + truePart;
        }
        double taken = physPart + magicTruePart;
        if (!allowOverCap && target instanceof Player tp2) {
            double pct = cfgD("combat.max-single-hit-pct", 35.0);
            if (pct > 0.0 && taken > 0.0) {
                double limit = plugin.getAttributes().maxHp(tp2.getUniqueId()) * pct / 100.0;
                if (taken > limit) {
                    double f = limit / taken;
                    physPart *= f;
                    magicTruePart *= f;
                    taken = limit;
                }
            }
            double burstAllowed = applyBurstCap(tp2, taken);
            if (burstAllowed < taken && taken > 0.0) {
                double f = burstAllowed / taken;
                physPart *= f;
                magicTruePart *= f;
                taken = burstAllowed;
            }
        }
        debugLog(target, source, safe, taken);
        if (physPart > 0.0) {
            SUPPRESS.set(Boolean.TRUE);
            try {
                if (source != null) {
                    target.damage(physPart, source);
                } else {
                    target.damage(physPart);
                }
            } finally {
                SUPPRESS.set(Boolean.FALSE);
            }
        }
        if (magicTruePart > 0.0) {
            SUPPRESS.set(Boolean.TRUE);
            try {
                org.bukkit.damage.DamageType magic = magicType();
                if (magic != null) {
                    DamageSource.Builder builder = DamageSource.builder(magic);
                    if (source != null) {
                        builder = builder.withDirectEntity(source).withCausingEntity(source);
                    }
                    target.damage(magicTruePart, builder.build());
                } else {
                    target.damage(magicTruePart);
                }
            } finally {
                SUPPRESS.set(Boolean.FALSE);
            }
        }
        return taken;
    }

    private void debugLog(LivingEntity target, Entity source, DamageProfile profile, double taken) {
        if (!plugin.getConfig().getBoolean("combat.debug-damage", false)) {
            return;
        }
        String resistInfo;
        if (target instanceof Player tp) {
            UUID uuid = tp.getUniqueId();
            resistInfo = String.format(Locale.ROOT, "резисты: физ %.0f%% / маг %.0f%%",
                    resists.physicalResist(uuid), resists.magicResist(uuid));
        } else {
            resistInfo = "резисты: физ 0% / маг 0%";
        }
        String line = String.format(Locale.ROOT,
                "[dmg] %s → %s: профиль %.1f физ / %.1f маг / %.1f чист → дошло %.1f (%s)",
                source != null ? source.getName() : "env",
                target.getName(),
                profile.physical(), profile.magic(), profile.trueDamage(),
                taken, resistInfo);
        Component message = Component.text(line, NamedTextColor.DARK_GRAY);
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission("raskolclasses.debug")) {
                online.sendMessage(message);
            }
        }
    }

    private DamageType typeOf(EntityDamageEvent.DamageCause cause) {
        String override = plugin.getConfig()
                .getString("damage-types.vanilla-map." + cause.name());
        if (override != null && !override.isEmpty()) {
            try {
                return DamageType.valueOf(override.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // опечатка в конфиге — падаем в дефолт
            }
        }
        return DamageType.defaultFor(cause);
    }

    private static DamageProfile sanitize(DamageProfile p) {
        double phys = Double.isFinite(p.physical()) && p.physical() >= 0 ? p.physical() : 0.0;
        double magic = Double.isFinite(p.magic()) && p.magic() >= 0 ? p.magic() : 0.0;
        double truth = Double.isFinite(p.trueDamage()) && p.trueDamage() >= 0 ? p.trueDamage() : 0.0;
        if (phys == p.physical() && magic == p.magic() && truth == p.trueDamage()) {
            return p;
        }
        return new DamageProfile(phys, magic, truth);
    }
}
