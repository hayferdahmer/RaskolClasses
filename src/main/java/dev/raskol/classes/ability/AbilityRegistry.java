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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AbilityRegistry {

    private final RaskolClasses plugin;
    private final Map<PlayerClass, Map<String, AbilityDef>> abilities = new EnumMap<>(PlayerClass.class);

    public AbilityRegistry(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    public void loadFromConfig(RaskolConfig cfg) {
        abilities.clear();
        for (PlayerClass pc : PlayerClass.values()) {
            Map<String, AbilityDef> classAbilities = new HashMap<>();
            List<String> ids = cfg.abilityIds(pc);
            int slot = 1;
            for (String id : ids) {
                String name = cfg.abilityName(pc, id, id);
                int unlock = cfg.abilityUnlock(pc, id, 1);
                int cost = cfg.abilityCost(pc, id, 10);
                int cooldown = cfg.abilityCooldown(pc, id, 10);
                int duration = cfg.abilityDuration(pc, id, 0);
                boolean targeted = cfg.abilityTargeted(pc, id, false);
                classAbilities.put(id, new AbilityDef(id, name, slot, unlock, cost,
                        cooldown, duration, targeted));
                slot++;
            }
            abilities.put(pc, classAbilities);
        }
    }

    public List<AbilityDef> getAbilities(PlayerClass pc) {
        Map<String, AbilityDef> map = abilities.get(pc);
        if (map == null) {
            return List.of();
        }
        return map.values().stream().toList();
    }

    public AbilityDef getBySlot(PlayerClass pc, int slot) {
        Map<String, AbilityDef> map = abilities.get(pc);
        if (map == null) {
            return null;
        }
        for (AbilityDef def : map.values()) {
            if (def.slot() == slot) {
                return def;
            }
        }
        return null;
    }

    /** FIX 1.5.0.5: поиск абилки по id (для BindListener). */
    public AbilityDef getById(PlayerClass pc, String id) {
        Map<String, AbilityDef> map = abilities.get(pc);
        if (map == null) {
            return null;
        }
        return map.get(id);
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
        cast(player, pc, def);
        player.sendMessage(Component.text("«" + def.displayName() + "» — активирована",
                NamedTextColor.GREEN));
        return true;
    }

    private void cast(Player player, PlayerClass pc, AbilityDef def) {
        switch (def.id()) {
            case "steel_skin" -> {
                player.addPotionEffect(plugin.getEffects().apply(
                        player, dev.raskol.classes.effect.EffectType.STEEL_SKIN, def.duration() * 20));
            }
            case "shield_bash" -> HunterAbilities.shieldBash(plugin, player, def);
            case "blood_fury" -> {
                player.addPotionEffect(plugin.getEffects().apply(
                        player, dev.raskol.classes.effect.EffectType.BLOOD_FURY, def.duration() * 20));
            }
            case "war_god" -> {
                player.addPotionEffect(plugin.getEffects().apply(
                        player, dev.raskol.classes.effect.EffectType.WAR_GOD, def.duration() * 20));
            }
            case "aimed_shot" -> HunterAbilities.aimedShot(plugin, player, def);
            case "cheetah_aspect" -> HunterAbilities.cheetahAspect(plugin, player, def);
            case "multi_shot" -> HunterAbilities.multiShot(plugin, player, def);
            case "barrage" -> HunterAbilities.barrage(plugin, player, def);
            case "lesser_heal", "flash_heal" -> HunterAbilities.healSelf(plugin, player, def);
            case "pw_shield" -> HunterAbilities.shieldSelf(plugin, player, def);
            case "circle_of_prayer" -> HunterAbilities.circleOfPrayer(plugin, player, def);
            case "smite" -> HunterAbilities.smite(plugin, player, def);
            case "firebolt" -> HunterAbilities.firebolt(plugin, player, def);
            case "blink" -> HunterAbilities.blink(plugin, player, def);
            case "frost_nova" -> HunterAbilities.frostNova(plugin, player, def);
            case "arcane_burst" -> HunterAbilities.arcaneBurst(plugin, player, def);
            case "stealth" -> {
                player.addPotionEffect(plugin.getEffects().apply(
                        player, dev.raskol.classes.effect.EffectType.STEALTH, def.duration() * 20));
            }
            case "fan_of_knives" -> HunterAbilities.fanOfKnives(plugin, player, def);
            case "cheap_shot" -> HunterAbilities.cheapShot(plugin, player, def);
            case "evasion" -> {
                player.addPotionEffect(plugin.getEffects().apply(
                        player, dev.raskol.classes.effect.EffectType.EVASION, def.duration() * 20));
            }
        }
    }
}
