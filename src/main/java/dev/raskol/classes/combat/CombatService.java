// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.combat;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.attribute.AttributeService;
import dev.raskol.classes.attribute.AttributeType;
import dev.raskol.classes.classsystem.PlayerClass;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.6.0: боевой сервис урона и резистов.
 * Путь A (ваниль): EntityDamageEvent по игроку (резисты) + исходящий офенс игрока.
 * Путь B (наши способности): dealDamage(target, source, DamageProfile).
 *
 * 1.7.0 пакет 4: НАСТУПАТЕЛЬНЫЕ АТРИБУТЫ:
 *  - плоские добавки исходящего урона игрока: STR+level → физ-компонента,
 *    INT+level → маг-компонента (гейты attributes.offense.str-to-physical /
 *    int-to-magic); применяются ДО резистов цели;
 *  - криты: мили (AGI, attributes.crit.melee-*) и магия (INT, spell-*),
 *    множители melee-mult/spell-mult, визуал (тег+звук, attributes.crit.visuals);
 *  - точки применения: path A — applyOutgoingOffense (ванильные удары/стрелы,
 *    только не-SUPPRESS события); path B — dealDamage (source-игрок);
 *    SUPPRESS-поток исключает двойное начисление;
 *  - TRUE-урон бонусов и критов НЕ получает.
 *
 * Порядок path A для PHYSICAL по игроку-цели: исходящий бонус/крит →
 * avoidance-ролл (уклонение/парирование, отмена) → резист-фактор цели.
 * 1.7.0.2: среда (FALL/DROWNING/…) = TRUE + масштаб летальности × maxHP/20.
 */
public final class CombatService implements Listener {

    private static final ThreadLocal<Boolean> SUPPRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile org.bukkit.damage.DamageType magicTypeCache;

    /** max_health из реестра атрибутов Paper (1.21.4-safe). */
    private static final Attribute MAX_HEALTH = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ATTRIBUTE)
            .get(NamespacedKey.minecraft("max_health"));

    private final RaskolClasses plugin;
    private final ResistService resists;
    private final AvoidanceService avoidance;

    public CombatService(RaskolClasses plugin, ResistService resists) {
        this.plugin = plugin;
        this.resists = resists;
        this.avoidance = new AvoidanceService(plugin);
    }

    public ResistService resists() {
        return resists;
    }

    /** 1.7.0 пакет 3: сервис уклонения/парирования (доступен для диагностики). */
    public AvoidanceService avoidance() {
        return avoidance;
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
        double v = plugin.getConfig().getDouble("combat.true-damage-cap-per-hit", 1000.0);
        return Double.isFinite(v) && v >= 0.0 ? v : 1000.0;
    }

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private String cfgS(String path, String def) {
        String v = plugin.getConfig().getString(path, def);
        return v != null && !v.isEmpty() ? v : def;
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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        boolean suppressed = Boolean.TRUE.equals(SUPPRESS.get());
        if (suppressed) {
            SUPPRESS.set(Boolean.FALSE);
        }
        // 1.7.0 пакет 4: исходящий офенс игрока (path A), до резистов цели;
        // SUPPRESS-события (path B) уже получили бонусы внутри dealDamage
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
            return; // чистый урон: ни резистов, ни avoidance
        }
        // 1.7.0 пакет 3: уклонение/парирование — только PHYSICAL, до резистов
        if (type == DamageType.PHYSICAL && avoidance.tryAvoid(target, event)) {
            event.setCancelled(true);
            return;
        }
        if (suppressed) {
            return; // резист уже учтён в dealDamage
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
    }

    /* --------------------- 1.7.0 пакет 4: исходящий офенс --------------------- */

    /**
     * Path A: ванильный урон игрока (удар/стрела) по живой цели.
     * Бонус STR/INT + крит-ролл ДО резистов цели. TRUE не трогает.
     */
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
        } else if (damager instanceof Projectile proj
                && proj.getShooter() instanceof Player sh) {
            attacker = sh;
        }
        if (attacker == null) {
            return;
        }
        DamageType type = typeOf(event.getCause());
        if (type == DamageType.TRUE) {
            return;
        }
        double add = 0.0;
        boolean crit;
        if (type == DamageType.PHYSICAL) {
            if (plugin.getConfig().getBoolean("attributes.offense.str-to-physical", true)) {
                add += strBonus(attacker);
            }
            crit = rollMeleeCrit(attacker);
        } else {
            if (plugin.getConfig().getBoolean("attributes.offense.int-to-magic", true)) {
                add += intBonus(attacker);
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

    /** Плоская добавка физ-урона: STR + PlayerLevel. */
    private double strBonus(Player player) {
        AttributeService attrs = plugin.getAttributes();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return 0.0;
        }
        UUID uuid = player.getUniqueId();
        return AttributeMath.physicalBonus(
                attrs.value(uuid, AttributeType.STR), attrs.levelOf(uuid, pc));
    }

    /** Плоская добавка маг-урона: INT + PlayerLevel. */
    private double intBonus(Player player) {
        AttributeService attrs = plugin.getAttributes();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return 0.0;
        }
        UUID uuid = player.getUniqueId();
        return AttributeMath.spellBonus(
                attrs.value(uuid, AttributeType.INT), attrs.levelOf(uuid, pc));
    }

    private boolean rollMeleeCrit(Player player) {
        AttributeService attrs = plugin.getAttributes();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return false;
        }
        double chance = AttributeMath.critMelee(
                attrs.value(player.getUniqueId(), AttributeType.AGI),
                cfgD("attributes.crit.melee-base", 5.0),
                cfgD("attributes.crit.melee-per-agi", 0.05),
                cfgD("attributes.crit.melee-cap", 40.0));
        return ThreadLocalRandom.current().nextDouble() * 100.0 < chance;
    }

    private boolean rollSpellCrit(Player player) {
        AttributeService attrs = plugin.getAttributes();
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        double chance = AttributeMath.critSpell(
                attrs.value(uuid, AttributeType.INT),
                attrs.mainOf(pc) == AttributeType.INT,
                cfgD("attributes.crit.spell-base", 5.0),
                cfgD("attributes.crit.spell-per-int", 0.03),
                cfgD("attributes.crit.spell-main-mult", 1.5),
                cfgD("attributes.crit.spell-cap", 35.0));
        return ThreadLocalRandom.current().nextDouble() * 100.0 < chance;
    }

    private double meleeMult() {
        return cfgD("attributes.crit.melee-mult", 1.5);
    }

    private double spellMult() {
        return cfgD("attributes.crit.spell-mult", 1.5);
    }

    /** Визуал крита: субтайтл-тег атакующему + звук. */
    private void critFeedback(Player attacker, boolean melee) {
        if (!plugin.getConfig().getBoolean("attributes.crit.visuals", true)) {
            return;
        }
        String tag = melee
                ? cfgS("attributes.crit.melee-tag", "⚡ Крит!")
                : cfgS("attributes.crit.spell-tag", "✦ Магический крит!");
        attacker.showTitle(Title.title(
                Component.empty(),
                Component.text(tag, melee ? NamedTextColor.RED : NamedTextColor.LIGHT_PURPLE),
                Title.Times.times(Duration.ofMillis(50),
                        Duration.ofMillis(550), Duration.ofMillis(150))));
        Sound s = plugin.getFx().resolveSound(melee
                ? cfgS("attributes.crit.melee-sound", "ENTITY_PLAYER_ATTACK_CRIT")
                : cfgS("attributes.crit.spell-sound", "ENTITY_EVOKER_CAST_SPELL"));
        if (s != null) {
            plugin.getFx().playSound(attacker.getLocation(), s, 0.5f, melee ? 0.9f : 1.2f);
        }
    }

    /* ------------------------- среда и прочее (path A) ------------------------- */

    /** 1.7.0.2: масштаб летальности среды × maxHP/20 (гейт env-lethal-scale). */
    private void applyEnvLethalScale(EntityDamageEvent event, Player target) {
        if (!plugin.getConfig().getBoolean("damage-types.env-lethal-scale", true)) {
            return;
        }
        List<String> env = plugin.getConfig().getStringList("damage-types.env-lethal");
        if (!env.contains(event.getCause().name())) {
            return;
        }
        if (MAX_HEALTH == null) {
            return;
        }
        AttributeInstance instance = target.getAttribute(MAX_HEALTH);
        if (instance == null) {
            return;
        }
        double scale = instance.getValue() / 20.0;
        if (scale > 1.0 && Double.isFinite(scale)) {
            event.setDamage(event.getDamage() * scale);
        }
    }

    /** 1.6.4: единая формула «сколько дойдёт» (симулятор /rc debug, целе-центричная). */
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

    /** Путь B: наш урон с профилем + офенс-бонусы и криты source-игрока. */
    public double dealDamage(LivingEntity target, Entity source, DamageProfile profile) {
        if (profile == null || target == null || target.isDead()) {
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
            // 1.7.0 пакет 4: бонусы и криты атакующего — ДО резистов цели
            if (plugin.getConfig().getBoolean("attributes.offense.str-to-physical", true)) {
                physBase += strBonus(sp);
            }
            if (plugin.getConfig().getBoolean("attributes.offense.int-to-magic", true)) {
                magicBase += intBonus(sp);
            }
            boolean critMelee = false;
            boolean critSpell = false;
            if (physBase > 0.0 && rollMeleeCrit(sp)) {
                critMelee = true;
                physBase *= meleeMult();
            }
            if (magicBase > 0.0 && rollSpellCrit(sp)) {
                critSpell = true;
                magicBase *= spellMult();
            }
            if (critMelee || critSpell) {
                critFeedback(sp, critMelee);
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

    /** NaN/Infinity/отрицательные компоненты профиля → 0. */
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
