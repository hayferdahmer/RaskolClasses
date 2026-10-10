// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.combat.DamageProfile;
import dev.raskol.classes.combat.school.School;
import dev.raskol.classes.pet.PetService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.7.4: КИТ ЖРЕЦА. 1.9.3 (план B): maxOf() читает formulaMaxHp();
 * heal() через HpBarService.heal().
 * 1.12.3: школа HOLY, каст/impact/execute-VFX конфиг-драйвен, аура Эгиды.
 * 1.12.5: очищение — успешный хил снимает Dot'ы школ NATURE и SHADOW с цели.
 * 1.14.0 (Б4): хуки baseBonus/coeffMult из Spec2Service.
 * 1.14.0 (контент-долг 5): +9 древесных способностей Жреца:
 *   discipline: purge, pain_suppression, spirit_shell (ульт);
 *   holy: flash_heal, lightwell, divine_hymn (ульт);
 *   shadow: withering_touch, mind_flay, shadowfiend.
 *   Гейт — treeUnlocked(); целевые хилы/клинзы — self-каст с внутренним
 *   ray-таргетом союзника (фолбэк на себя).
 * 1.14.0 (Б11.1.2-A): base/coeff/power/duration + radius(circle_elysium) +
 *   threshold/execute-mult(wrath_heaven) читаются через TreeAbilities.*OrKit
 *   (treeAbilities → abilities → код-дефолт), чтобы переносимые
 *   (circle_elysium/wrath_heaven) пережили резку abilities.
 * 1.14.1 (Волна 1): heal() передаёт кастера для роли HEALER (CustomHealEvent).
 * 1.14.3 (Волна 3, 3A): пред-множитель роли HEALER УБРАН из applyHealWith —
 *   исходящие heal-модификаторы (heal_out_pct + роль) применяет единой точкой
 *   Spec2RoleListener.onCustomHeal; иначе роль считалась дважды (1.05×1.05).
 * 1.14.6 (6b): shadowfiend — делегирование в PetService; inline-спавн Vex удалён.
 * 1.14.6-fix (Sprint 1, P0-8a): shadowfiend возвращает false на ЛЮБОЙ не-OK результат
 *   summon (включая ALREADY) — castOn делает refund и не запускает кулдаун.
 * 1.14.7 (Sprint 3, P0-6A): ho_surge_of_light — после успешного хила, если
 *   ProcService.rollFreeHeal возвращает true, стоимость способности возвращается
 *   в ресурс кастера (refund). Реализация через единый proc free_heal.
 * 1.14.7 (Sprint 3, P0-6A): aegisFaith читает длительность через
 *   TreeAbilities.durationWithSpec (kit_dur dis_shield_mastery: +2 с/ранг).
 * 1.14.7 (Sprint 4, P1-2): вызовы PassiveListener.markHealer УДАЛЕНЫ из
 *   applyHealWith и groupHeal — атрибуция лечения живёт в CustomHealEvent.getHealer(),
 *   глобальный маркер больше не существует.
 * 1.14.7 (Sprint 4, P1-6): удаление дубля treeUnlocked() — гейт hasUnlocked
 *   централизован в AbilityRegistry.castOn. Текст отказа идентичен
 *   («откроется узлом дерева путей Жреца.»), поведение игрока не изменилось.
 */
public final class PriestAbilities {

    private static final PlayerClass PC = PlayerClass.PRIEST;

    /** 1.14.0: последний успешный хил кастера (formula-единицы) для spirit_shell. */
    private static final Map<UUID, Double> LAST_HEAL = new ConcurrentHashMap<>();

    private final RaskolClasses plugin;

    public PriestAbilities(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ конфиг-хелперы ------------------------------ */

    private double cfgD(String path, double def) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? v : def;
    }

    private String cfgS(String path, String def) {
        String v = plugin.getConfig().getString(path, def);
        return v != null ? v : def;
    }

    // 1.14.0 (Б11.1.2-A): фолбэк treeAbilities → abilities → def
    private double base(AbilityDef def, double defv) {
        return TreeAbilities.numberOrKit(plugin, PC, def.id(), "base", defv);
    }

    private double coeff(AbilityDef def, double defv) {
        return TreeAbilities.numberOrKit(plugin, PC, def.id(), "coeff", defv);
    }

    private String power(AbilityDef def) {
        return TreeAbilities.stringOrKit(plugin, PC, def.id(), "power", "hpow");
    }

    private int duration(AbilityDef def, int defv) {
        return TreeAbilities.durationOrKit(plugin, PC, def.id(), defv);
    }

    private double tbase(AbilityDef def, double defv) {
        return cfgD("classes.PRIEST.treeAbilities." + def.id() + ".base", defv);
    }

    private double tcoeff(AbilityDef def, double defv) {
        return cfgD("classes.PRIEST.treeAbilities." + def.id() + ".coeff", defv);
    }

    private String tpower(AbilityDef def) {
        return plugin.getConfig().getString(
                "classes.PRIEST.treeAbilities." + def.id() + ".power", "hpow");
    }

    private double healAmount(Player caster, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = caster.getUniqueId();
        double b = base(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityHeal(uuid, b, c);
    }

    private double thealAmount(Player caster, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = caster.getUniqueId();
        double b = tbase(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = tcoeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityHeal(uuid, b, c);
    }

    private double dmg(Player caster, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = caster.getUniqueId();
        double b = base(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, power(def), b, c);
    }

    private double tdmg(Player caster, AbilityDef def, double defBase, double defCoeff) {
        UUID uuid = caster.getUniqueId();
        double b = tbase(def, defBase) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = tcoeff(def, defCoeff) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        return plugin.getCombat().powers().abilityDamage(uuid, tpower(def), b, c);
    }

    private void noTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "cheap-shot-no-target", "Нет цели в радиусе действия"), NamedTextColor.GRAY));
    }

    private void allyTarget(Player p) {
        p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                "ally.no-hit", "Союзника бить нельзя"), NamedTextColor.RED));
    }

    private boolean isAllyOrSelf(Player caster, Player target) {
        if (caster.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        if (plugin.getConfig().getBoolean("combat.friendly-fire", false)) {
            return true;
        }
        String f1 = plugin.getFactionHook().factionOf(caster.getUniqueId());
        String f2 = plugin.getFactionHook().factionOf(target.getUniqueId());
        return f1 != null && !f1.isEmpty() && f1.equals(f2);
    }

    /** Союзник под прицелом (до 20 блоков) или null. */
    private Player allyRayTarget(Player p, double range) {
        Entity e = p.getTargetEntity((int) range);
        if (e instanceof Player tp && isAllyOrSelf(p, tp)) {
            return tp;
        }
        return null;
    }

    private LivingEntity rayTarget(Player p, double range) {
        Entity e = p.getTargetEntity((int) range);
        return e instanceof LivingEntity le ? le : null;
    }

    private double maxOf(LivingEntity e) {
        if (e instanceof Player p) {
            return plugin.getHpBarService().formulaMaxHp(p.getUniqueId());
        }
        org.bukkit.attribute.AttributeInstance attr =
                e.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }

    /* ------------------------------ VFX-хелперы ------------------------------ */

    private void castFx(Player p, String id, String soundDef, String particleDef,
                        float volume, float pitch, int count) {
        Sound sound = plugin.getFx().resolveSound(cfgS("vfx." + id + ".cast-sound", soundDef));
        Location loc = p.getLocation().add(0.0, 1.0, 0.0);
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, volume, pitch);
        }
        Particle particle = resolveParticle(cfgS("vfx." + id + ".cast-particle", particleDef));
        if (particle != null) {
            p.getWorld().spawnParticle(particle, loc, count, 0.4, 0.6, 0.4, 0.02);
        }
    }

    private void impactFx(LivingEntity target, String id,
                          String soundDef, String particleDef,
                          float volume, float pitch, int count) {
        Sound sound = plugin.getFx().resolveSound(cfgS("vfx." + id + ".impact-sound", soundDef));
        Location loc = target.getLocation().add(0.0, 1.0, 0.0);
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, volume, pitch);
        }
        Particle particle = resolveParticle(cfgS("vfx." + id + ".impact-particle", particleDef));
        if (particle != null) {
            target.getWorld().spawnParticle(particle, loc, count, 0.3, 0.5, 0.3, 0.02);
        }
    }

    private void executeFx(LivingEntity target) {
        Sound sound = plugin.getFx().resolveSound(
                cfgS("vfx.wrath_heaven.execute-sound", "ENTITY_LIGHTNING_BOLT_IMPACT"));
        Location loc = target.getLocation().add(0.0, 1.0, 0.0);
        if (sound != null) {
            plugin.getFx().playSound(loc, sound, 0.9f, 0.7f);
        }
        Particle particle = resolveParticle(
                cfgS("vfx.wrath_heaven.execute-particle", "FLASH"));
        if (particle != null) {
            target.getWorld().spawnParticle(particle, loc, 3, 0.2, 0.3, 0.2, 0.0);
        }
    }

    private Particle resolveParticle(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /* -------------------------------- ядро лечения -------------------------------- */

    /**
     * 1.9.3 (план B) + 1.12.5 (очищение) + 1.14.0 (LAST_HEAL).
     * treePath=true читает base/coeff из classes.PRIEST.treeAbilities.*.
     * 1.14.3 (3A): ролевые/узловые множители лечения НЕ применяются здесь —
     * их ставит Spec2RoleListener.onCustomHeal на событии CustomHealEvent.
     * 1.14.7 (P0-6A): ho_surge_of_light — если rollFreeHeal=true после хила,
     * возвращаем стоимость способности в ресурс кастера (бесплатный хил).
     * 1.14.7 (Sprint 4, P1-2): markHealer удалён — атрибуция в CustomHealEvent.
     */
    private boolean applyHealWith(Player caster, Player target, AbilityDef def,
                                  double defBase, double defCoeff, boolean treePath) {
        if (!isAllyOrSelf(caster, target)) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-heal", "Цель не союзник"), NamedTextColor.GRAY));
            return false;
        }
        double formula = plugin.getHpBarService().formulaMaxHp(target.getUniqueId());
        double scale = plugin.getHpBarService().scale(target);
        double hpFormula = scale > 0.0 ? target.getHealth() / scale : target.getHealth();
        double missing = formula - hpFormula;
        if (missing <= 0.0) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "target-full-hp", "Цель здорова"), NamedTextColor.GRAY));
            return false;
        }
        double amount = Math.min(treePath
                ? thealAmount(caster, def, defBase, defCoeff)
                : healAmount(caster, def, defBase, defCoeff), missing);
        plugin.getHpBarService().heal(target, amount, caster);
        // 1.14.7 (P0-6A): ho_surge_of_light — бесплатный хил (refund стоимости)
        if (def.cost() > 0 && plugin.getCombat().procs().rollFreeHeal(caster)) {
            plugin.getResources().refund(caster.getUniqueId(), def.cost());
        }
        LAST_HEAL.put(caster.getUniqueId(), amount);

        UUID targetUuid = target.getUniqueId();
        int before = plugin.getCombat().dots().activeOn(targetUuid);
        plugin.getCombat().dots().removeSchoolOn(targetUuid, School.NATURE);
        plugin.getCombat().dots().removeSchoolOn(targetUuid, School.SHADOW);
        int after = plugin.getCombat().dots().activeOn(targetUuid);
        if (after < before) {
            target.getWorld().spawnParticle(Particle.ENCHANT,
                    target.getLocation().add(0.0, 1.0, 0.0), 6, 0.3, 0.4, 0.3, 0.01);
        }

        if (!target.getUniqueId().equals(caster.getUniqueId())) {
            target.sendMessage(Component.text(plugin.getRaskolConfig()
                    .message("healed-you", "{caster} исцелил тебя")
                    .replace("{caster}", caster.getName()), NamedTextColor.GREEN));
        }
        return true;
    }

    private boolean applyHeal(Player caster, Player target, AbilityDef def,
                              double defBase, double defCoeff) {
        return applyHealWith(caster, target, def, defBase, defCoeff, false);
    }

    /** Групповой хил без спама сообщений (lightwell/divine_hymn). */
    private int groupHeal(Player caster, double radius, double amount) {
        LAST_HEAL.put(caster.getUniqueId(), amount);
        int healed = 0;
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Player t) || !isAllyOrSelf(caster, t)) {
                continue;
            }
            double formula = plugin.getHpBarService().formulaMaxHp(t.getUniqueId());
            double scale = plugin.getHpBarService().scale(t);
            double hpFormula = scale > 0.0 ? t.getHealth() / scale : t.getHealth();
            double missing = formula - hpFormula;
            if (missing <= 0.0) {
                continue;
            }
            plugin.getHpBarService().heal(t, Math.min(amount, missing), caster);
            t.getWorld().spawnParticle(Particle.HEART,
                    t.getLocation().add(0.0, 1.2, 0.0), 2, 0.2, 0.3, 0.2, 0.0);
            healed++;
        }
        return healed;
    }

    private List<Player> alliesWithin(Player caster, double radius) {
        List<Player> out = new ArrayList<>();
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player t && isAllyOrSelf(caster, t)) {
                out.add(t);
            }
        }
        return out;
    }

    /* -------------------------------- базовые способности -------------------------------- */

    public boolean saintTear(Player caster, LivingEntity target, AbilityDef def) {
        if (!(target instanceof Player tp)) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-heal", "Цель не союзник"), NamedTextColor.GRAY));
            return false;
        }
        boolean ok = applyHeal(caster, tp, def, 10.0, 0.35);
        if (!ok) {
            return false;
        }
        castFx(caster, "saint_tear", "BLOCK_BELL_USE", "HEART", 0.5f, 1.1f, 10);
        impactFx(tp, "saint_tear", "BLOCK_BELL_USE", "HEART", 0.4f, 1.2f, 8);
        return true;
    }

    public boolean wordOfLife(Player caster, LivingEntity target, AbilityDef def) {
        if (!(target instanceof Player tp)) {
            caster.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "ally.no-heal", "Цель не союзник"), NamedTextColor.GRAY));
            return false;
        }
        boolean ok = applyHeal(caster, tp, def, 20.0, 0.6);
        if (!ok) {
            return false;
        }
        castFx(caster, "word_of_life", "BLOCK_AMETHYST_BLOCK_CHIME", "HEART", 0.6f, 1.0f, 14);
        impactFx(tp, "word_of_life", "BLOCK_AMETHYST_BLOCK_CHIME", "HEART", 0.5f, 1.1f, 12);
        return true;
    }

    /**
     * 1.14.7 (P0-6A): длительность через durationWithSpec (dis_shield_mastery: +2 с/ранг).
     */
    public boolean aegisFaith(Player p, AbilityDef def) {
        UUID uuid = p.getUniqueId();
        double b = base(def, 12.0) + plugin.getSpec2Service().baseBonus(uuid, def.id());
        double c = coeff(def, 0.04) * plugin.getSpec2Service().coeffMult(uuid, def.id());
        double grant = b + plugin.getCombat().powers().healPower(uuid) * c;
        int secs = TreeAbilities.durationWithSpec(plugin, PC, def.id(), 5, uuid);
        plugin.getResists().addTimedModifier(uuid, def.id(), grant, grant, secs * 1000L);
        castFx(p, "aegis_faith", "ITEM_ARMOR_EQUIP_DIAMOND", "ENCHANTED_HIT", 0.6f, 1.0f, 20);
        plugin.getFx().startAura(uuid, Particle.ENCHANTED_HIT, secs * 20, 3,
                cfgS("vfx.aegis_faith.expire-sound", "BLOCK_AMETHYST_BLOCK_CHIME"));
        p.sendMessage(Component.text("Эгида Веры: +" + (int) grant
                + "% физ/маг резиста на " + secs + " с", NamedTextColor.YELLOW));
        return true;
    }

    public boolean circleElysium(Player p, AbilityDef def) {
        // 1.14.0 (Б11.1.2-A): radius через фолбэк-хелпер (переносимая slot 4)
        double radius = TreeAbilities.numberOrKit(plugin, PC, def.id(), "radius", 6.0);
        List<Player> healed = new ArrayList<>();
        if (applyHeal(p, p, def, 15.0, 0.45)) {
            healed.add(p);
        }
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Player t) || t.getUniqueId().equals(p.getUniqueId())) {
                continue;
            }
            if (applyHeal(p, t, def, 15.0, 0.45)) {
                healed.add(t);
            }
        }
        if (healed.isEmpty()) {
            return false;
        }
        castFx(p, "circle_elysium", "BLOCK_BEACON_ACTIVATE", "HEART", 0.7f, 0.9f, 24);
        for (Player t : healed) {
            impactFx(t, "circle_elysium", "ENTITY_EXPERIENCE_ORB_PICKUP", "HEART", 0.4f, 1.2f, 8);
        }
        return true;
    }

    public boolean wrathHeaven(Player p, AbilityDef def) {
        Entity e = p.getTargetEntity(20);
        if (!(e instanceof LivingEntity t)) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "wrath_heaven", "ENTITY_LIGHTNING_BOLT_THUNDER", "FLASH", 0.8f, 0.8f, 10);
        // 1.14.0 (Б11.1.2-A): threshold/execute-mult через фолбэк-хелпер (переносимая slot 5)
        double threshold = TreeAbilities.numberOrKit(plugin, PC, def.id(), "threshold", 0.25);
        double max = maxOf(t);
        double frac = max > 0 ? t.getHealth() / max : 1.0;
        double dmg = dmg(p, def, 20.0, 1.6);
        if (frac < threshold) {
            dmg *= TreeAbilities.numberOrKit(plugin, PC, def.id(), "execute-mult", 3.0);
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg), true);
            executeFx(t);
            p.sendMessage(Component.text(plugin.getRaskolConfig().message(
                    "tag.execute-priest", "Кара Небес ×3!"), NamedTextColor.RED));
        } else {
            plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
            impactFx(t, "wrath_heaven", "ENTITY_FIREWORK_ROCKET_BLAST", "FLASH", 0.6f, 0.9f, 10);
        }
        return true;
    }

    /* --------------------- древесные способности (1.14.0, контент-долг 5) --------------------- */
    /* 1.14.7 (Sprint 4, P1-6): вызовы treeUnlocked() удалены — гейт централизован в castOn. */

    /** discipline T2: очищение союзника (или себя): все CC + все DoT. */
    public boolean purge(Player p, AbilityDef def) {
        Player target = allyRayTarget(p, 20);
        if (target == null) {
            target = p;
        }
        UUID tid = target.getUniqueId();
        plugin.getCC().removeAll(tid);
        plugin.getCombat().dots().removeAllOn(tid);
        castFx(p, "purge", "BLOCK_BELL_USE", "ENCHANTED_HIT", 0.7f, 1.1f, 16);
        impactFx(target, "purge", "BLOCK_BELL_USE", "ENCHANTED_HIT", 0.5f, 1.2f, 12);
        p.sendMessage(Component.text("Очищение: контроль и проклятия сняты с "
                + target.getName(), NamedTextColor.GREEN));
        return true;
    }

    /** discipline T4: −40% входящего урона цели на 5 с (Resistance II). */
    public boolean painSuppression(Player p, AbilityDef def) {
        Player target = allyRayTarget(p, 20);
        if (target == null) {
            target = p;
        }
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 5);
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, secs * 20, 1));
        castFx(p, "pain_suppression", "BLOCK_BEACON_ACTIVATE", "ENCHANTED_HIT", 0.7f, 0.9f, 16);
        impactFx(target, "pain_suppression", "BLOCK_BEACON_ACTIVATE", "ENCHANTED_HIT", 0.5f, 1.0f, 12);
        p.sendMessage(Component.text("Подавление боли: " + target.getName()
                + " получает −40% урона " + secs + " с", NamedTextColor.YELLOW));
        return true;
    }

    /** discipline T6 (ульт): щит-пул = 30% последнего хила, себе и союзникам r6, 8 с. */
    public boolean spiritShell(Player p, AbilityDef def) {
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 8);
        double last = LAST_HEAL.getOrDefault(p.getUniqueId(), 0.0);
        double shieldFormula = last * 0.30;
        if (shieldFormula < 4.0) {
            p.sendMessage(Component.text("Оболочка духа: сначала соверши лечение.", NamedTextColor.GRAY));
            return false;
        }
        double scale = plugin.getHpBarService().scale(p);
        int shieldCarrier = (int) Math.max(4.0, Math.round(shieldFormula * scale));
        int level = Math.max(0, (shieldCarrier + 3) / 4 - 1);
        List<Player> targets = alliesWithin(p, 6.0);
        if (!targets.contains(p)) {
            targets.add(p);
        }
        for (Player t : targets) {
            t.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, secs * 20, level));
            t.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
                    t.getLocation().add(0.0, 1.2, 0.0), 8, 0.3, 0.4, 0.3, 0.02);
        }
        castFx(p, "spirit_shell", "BLOCK_BEACON_ACTIVATE", "ENCHANTED_HIT", 0.9f, 0.8f, 26);
        p.sendMessage(Component.text("Оболочка духа: щит " + shieldCarrier + " HP на "
                + targets.size() + " целей, " + secs + " с", NamedTextColor.YELLOW));
        return true;
    }

    /** holy T2: быстрый хил союзника (или себя), сильнее Слезы, короче КД. */
    public boolean flashHeal(Player p, AbilityDef def) {
        Player target = allyRayTarget(p, 20);
        if (target == null) {
            target = p;
        }
        boolean ok = applyHealWith(p, target, def, 14.0, 0.5, true);
        if (!ok) {
            return false;
        }
        castFx(p, "flash_heal", "BLOCK_BELL_USE", "HEART", 0.6f, 1.2f, 12);
        impactFx(target, "flash_heal", "BLOCK_BELL_USE", "HEART", 0.5f, 1.3f, 10);
        return true;
    }

    /** holy T5: зона-хил: 4 тика по 2 с, +2 HP/с-эквивалент союзникам r4. */
    public boolean lightwell(Player p, AbilityDef def) {
        double radius = TreeAbilities.radiusOf(plugin, PC, def.id(), 4.0);
        double per = thealAmount(p, def, 6.0, 0.3);
        UUID pid = p.getUniqueId();
        Location spot = p.getLocation();
        castFx(p, "lightwell", "BLOCK_BEACON_ACTIVATE", "ENCHANTED_HIT", 0.7f, 1.0f, 20);
        for (int tick = 0; tick < 4; tick++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player caster = plugin.getServer().getPlayer(pid);
                if (caster == null || !caster.isOnline()) {
                    return;
                }
                caster.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
                        spot.clone().add(0.0, 1.0, 0.0), 10, radius * 0.5, 0.4, radius * 0.5, 0.02);
                groupHeal(caster, radius, per);
            }, tick * 40L);
        }
        p.sendMessage(Component.text("Колодец Света: зона лечения " + radius
                + " блоков на 8 с", NamedTextColor.YELLOW));
        return true;
    }

    /** holy T6 (ульт): канал 3 с: 3 тика группового хила r8 + очищение DoT NATURE/SHADOW. */
    public boolean divineHymn(Player p, AbilityDef def) {
        double radius = TreeAbilities.radiusOf(plugin, PC, def.id(), 8.0);
        double per = thealAmount(p, def, 10.0, 0.5);
        UUID pid = p.getUniqueId();
        castFx(p, "divine_hymn", "BLOCK_BELL_USE", "ENCHANTED_HIT", 0.9f, 0.8f, 28);
        for (int tick = 0; tick < 3; tick++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player caster = plugin.getServer().getPlayer(pid);
                if (caster == null || !caster.isOnline() || caster.isDead()) {
                    return;
                }
                caster.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
                        caster.getLocation().add(0.0, 1.2, 0.0), 16, radius * 0.5, 0.5, radius * 0.5, 0.03);
                int healed = groupHeal(caster, radius, per);
                for (Player t : alliesWithin(caster, radius)) {
                    plugin.getCombat().dots().removeSchoolOn(t.getUniqueId(), School.NATURE);
                    plugin.getCombat().dots().removeSchoolOn(t.getUniqueId(), School.SHADOW);
                }
                if (healed > 0) {
                    caster.sendMessage(Component.text("Гимн: исцелено " + healed, NamedTextColor.YELLOW));
                }
            }, tick * 20L);
        }
        return true;
    }

    /** shadow T2: маг-урон + DoT wither (SHADOW). */
    public boolean witheringTouch(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        castFx(p, "withering_touch", "ENTITY_WITHER_SPAWN", "SCULK_SOUL", 0.6f, 0.9f, 12);
        double dmg = tdmg(p, def, 8.0, 0.7);
        plugin.getCombat().dealDamage(t, p, DamageProfile.magic(dmg));
        plugin.getCombat().dots().applyById(p, t, "wither");
        impactFx(t, "withering_touch", "ENTITY_WITHER_HURT", "SCULK_SOUL", 0.4f, 0.9f, 10);
        return true;
    }

    /** shadow T4: канал 3 с: 3 тика маг-урона + SLOW (cc.types.SLOW.slow-mult) на всё время. */
    public boolean mindFlay(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        UUID pid = p.getUniqueId();
        UUID tid = t.getUniqueId();
        double per = tdmg(p, def, 5.0, 0.4);
        plugin.getCC().tryApply(p, t, CCTypeSlowHolder.SLOW_TYPE, 80);
        castFx(p, "mind_flay", "ENTITY_ENDERMAN_STARE", "REVERSE_PORTAL", 0.7f, 0.9f, 14);
        for (int tick = 0; tick < 3; tick++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Player caster = plugin.getServer().getPlayer(pid);
                Entity e = plugin.getServer().getEntity(tid);
                if (caster == null || !(e instanceof LivingEntity living) || living.isDead()) {
                    return;
                }
                if (!plugin.getCombat().canHit(caster, living)) {
                    return;
                }
                plugin.getCombat().dealDamage(living, caster, DamageProfile.magic(per));
                impactFx(living, "mind_flay", "ENTITY_ENDERMAN_HURT", "REVERSE_PORTAL", 0.35f, 1.0f, 6);
            }, tick * 20L);
        }
        return true;
    }

    /**
     * 1.14.6 (6b): shadow T5 — призыв тенескота через PetService;
     * +10 Света — китовый бонус (PetService про это не знает).
     * 1.14.6-fix (P0-8a): любой не-OK результат = false (refund + без кулдауна).
     */
    public boolean shadowfiend(Player p, AbilityDef def) {
        LivingEntity t = rayTarget(p, 20);
        if (t == null) {
            noTarget(p);
            return false;
        }
        if (!plugin.getCombat().canHit(p, t)) {
            allyTarget(p);
            return false;
        }
        PetService.SummonResult r = plugin.getPets().summon(p, "shadowfiend", t);
        if (r != PetService.SummonResult.OK) {
            return false;
        }
        // Китовый бонус: +10 Света при призыве
        plugin.getResources().add(p.getUniqueId(), 10.0);
        int secs = TreeAbilities.durationOf(plugin, PC, def.id(), 8);
        castFx(p, "shadowfiend", "ENTITY_VEX_CHARGE", "SCULK_SOUL", 0.7f, 0.9f, 16);
        p.sendMessage(Component.text("Тенескот призван на " + secs + " с (+10 Света)",
                NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    /** 1.14.0: ids древесных способностей для сверки с TreeAbilities. */
    public static List<String> treeAbilityIds() {
        return List.of("purge", "pain_suppression", "spirit_shell",
                "flash_heal", "lightwell", "divine_hymn",
                "withering_touch", "mind_flay", "shadowfiend");
    }

    /** Внутренний держатель CCType.SLOW без прямого импорта в сигнатурах выше. */
    private static final class CCTypeSlowHolder {
        static final dev.raskol.classes.cc.CCType SLOW_TYPE = dev.raskol.classes.cc.CCType.SLOW;
    }
}
