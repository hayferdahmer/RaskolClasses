// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.config.RaskolConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Реестр способностей пяти классов.
 * Две карты кастеров:
 *  - casters: обычные способности (каст в себя);
 *  - targetedCasters: точечные жреца (каст в цель — ЛКМ со свитком).
 * Длительности эффектов — через RaskolConfig.durationSeconds(...).
 */
public final class AbilityRegistry {

    @FunctionalInterface
    public interface Caster {
        boolean cast(Player player, AbilityDef def);
    }

    @FunctionalInterface
    public interface TargetedCaster {
        boolean cast(Player caster, LivingEntity target, AbilityDef def);
    }

    private final RaskolClasses plugin;
    private final Map<PlayerClass, List<AbilityDef>> byClass = new EnumMap<>(PlayerClass.class);
    private final Map<String, Caster> casters = new HashMap<>();
    private final Map<String, TargetedCaster> targetedCasters = new HashMap<>();
    private final Set<String> targetedIds = new HashSet<>();

    public AbilityRegistry(RaskolClasses plugin) {
        this.plugin = plugin;
        registerCasters();
    }

    private void registerCasters() {
        WarriorAbilities warrior = new WarriorAbilities(plugin);
        HunterAbilities hunter = new HunterAbilities(plugin);
        PriestAbilities priest = new PriestAbilities(plugin);
        MageAbilities mage = new MageAbilities(plugin);
        RogueAbilities rogue = new RogueAbilities(plugin);

        // Воин (все — в себя)
        casters.put("steel_skin", warrior::steelSkin);
        casters.put("shield_bash", warrior::shieldBash);
        casters.put("blood_fury", warrior::bloodFury);
        casters.put("war_god", warrior::warGod);

        // Охотник (все — в себя / AoE)
        casters.put("aimed_shot", hunter::aimedShot);
        casters.put("cheetah_aspect", hunter::cheetahAspect);
        casters.put("multi_shot", hunter::multiShot);
        casters.put("barrage", hunter::barrage);

        // Жрец: точечные (ЛКМ по цели) + AoE (в себя)
        targetedCasters.put("lesser_heal", priest::lesserHeal);
        targetedCasters.put("flash_heal", priest::flashHeal);
        targetedCasters.put("pw_shield", priest::pwShield);
        targetedIds.add("lesser_heal");
        targetedIds.add("flash_heal");
        targetedIds.add("pw_shield");
        casters.put("circle_of_prayer", priest::circleOfPrayer);
        casters.put("smite", priest::smite);

        // Маг
        casters.put("firebolt", mage::firebolt);
        casters.put("blink", mage::blink);
        casters.put("frost_nova", mage::frostNova);
        casters.put("arcane_burst", mage::arcaneBurst);

        // Разбойник
        casters.put("stealth", rogue::stealth);
        casters.put("fan_of_knives", rogue::fanOfKnives);
        casters.put("cheap_shot", rogue::cheapShot);
        casters.put("evasion", rogue::evasion);
    }

    public void loadFromConfig(RaskolConfig cfg) {
        byClass.clear();
        for (PlayerClass pc : PlayerClass.values()) {
            List<String> ids = cfg.abilityIds(pc);
            int slot = 1;
            java.util.List<AbilityDef> list = new java.util.ArrayList<>();
            for (String id : ids) {
                String name = cfg.abilityName(pc, id, id);
                int unlock = cfg.abilityUnlock(pc, id, 1);
                int cost = cfg.abilityCost(pc, id, 10);
                int cooldownSec = cfg.abilityCooldown(pc, id, 10);
                list.add(new AbilityDef(id, name, slot, unlock, cost,
                        cooldownSec * 1000L));
                slot++;
            }
            byClass.put(pc, List.copyOf(list));
        }
    }

    public List<AbilityDef> getAbilities(PlayerClass pc) {
        List<AbilityDef> list = byClass.get(pc);
        return list != null ? list : List.of();
    }

    public AbilityDef getBySlot(PlayerClass pc, int slot) {
        List<AbilityDef> list = byClass.get(pc);
        if (list == null) {
            return null;
        }
        for (AbilityDef def : list) {
            if (def.slot() == slot) {
                return def;
            }
        }
        return null;
    }

    /** Поиск абилки по id внутри класса. */
    public AbilityDef findById(PlayerClass pc, String id) {
        List<AbilityDef> list = byClass.get(pc);
        if (list == null || id == null) {
            return null;
        }
        for (AbilityDef def : list) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }

    /** Алиас для совместимости с BindListener Пакета 3. */
    public AbilityDef getById(PlayerClass pc, String id) {
        return findById(pc, id);
    }

    /** Способность принимает явную цель (ЛКМ по игроку со свитком). */
    public boolean isTargeted(String id) {
        return targetedIds.contains(id);
    }

    public boolean tryCast(Player player, AbilityDef def) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text(
                    "Класс не выбран — способности недоступны", NamedTextColor.RED));
            return false;
        }
        UUID uuid = player.getUniqueId();
        int level = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
        if (level != SkillLevelProvider.NO_SKILL_SYSTEM && level < def.unlockLevel()) {
            player.sendMessage(Component.text("«" + def.displayName() + "» откроется на уровне "
                    + def.unlockLevel() + " (у вас " + level + ")", NamedTextColor.RED));
            return false;
        }
        if (plugin.getCooldowns().isOnCooldown(uuid, def.id())) {
            long remaining = plugin.getCooldowns().getRemainingMillis(uuid, def.id());
            player.sendMessage(Component.text("«" + def.displayName() + "»: перезарядка ещё "
                    + (remaining / 1000L + 1L) + "с", NamedTextColor.GRAY));
            return false;
        }
        if (!plugin.getResources().consume(uuid, def.cost())) {
            player.sendMessage(Component.text("Не хватает ресурса «" + pc.getResourceName()
                    + "»: нужно " + def.cost() + ", у вас "
                    + (int) plugin.getResources().getValue(uuid), NamedTextColor.RED));
            return false;
        }
        plugin.getCooldowns().start(uuid, def.id(), def.cooldownMillis());
        Caster caster = casters.get(def.id());
        if (caster != null) {
            caster.cast(player, def);
        }
        player.sendMessage(Component.text("«" + def.displayName() + "» — активирована",
                NamedTextColor.GREEN));
        return true;
    }

    /** Каст точечной способности в конкретную цель (для ЛКМ со свитком). */
    public boolean tryCastOn(Player caster, AbilityDef def, LivingEntity target) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(caster);
        if (pc == null) {
            return false;
        }
        UUID uuid = caster.getUniqueId();
        int level = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
        if (level != SkillLevelProvider.NO_SKILL_SYSTEM && level < def.unlockLevel()) {
            return false;
        }
        if (plugin.getCooldowns().isOnCooldown(uuid, def.id())) {
            return false;
        }
        if (!plugin.getResources().consume(uuid, def.cost())) {
            return false;
        }
        plugin.getCooldowns().start(uuid, def.id(), def.cooldownMillis());
        TargetedCaster tc = targetedCasters.get(def.id());
        if (tc != null) {
            tc.cast(caster, target, def);
        }
        caster.sendMessage(Component.text("«" + def.displayName() + "» → "
                + target.getName(), NamedTextColor.GREEN));
        return true;
    }
}
