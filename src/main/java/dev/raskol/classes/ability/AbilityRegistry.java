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

/**
 * Реестр способностей пяти классов.
 * Две карты кастеров:
 *  - casters: обычные способности (каст в себя);
 *  - targetedCasters: точечные жреца (каст в цель — ЛКМ со свитком).
 * Длительности эффектов — через RaskolConfig.durationSeconds(...).
 * Значения по умолчанию (unlock/cost/cooldown) хранятся в DEFAULTS;
 * конфиг (abilityName/Unlock/Cost/CooldownSeconds) переопределяет их.
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

    /** Дефолтные значения способностей (до переопределения конфигом). */
    private static final Map<PlayerClass, List<AbilityDef>> DEFAULTS = new EnumMap<>(PlayerClass.class);

    static {
        DEFAULTS.put(PlayerClass.WARRIOR, List.of(
                def("steel_skin", "Стальная кожа", 1, 10, 30, 45),
                def("shield_bash", "Удар щитом", 2, 25, 20, 25),
                def("blood_fury", "Кровавое безумие", 3, 50, 40, 30),
                def("war_god", "Бог войны", 4, 75, 100, 300)));
        DEFAULTS.put(PlayerClass.HUNTER, List.of(
                def("aimed_shot", "Прицельный выстрел", 1, 10, 20, 15),
                def("cheetah_aspect", "Аспект гепарда", 2, 25, 0, 60),
                def("multi_shot", "Мультивыстрел", 3, 50, 40, 25),
                def("barrage", "Заградительный огонь", 4, 75, 80, 120)));
        DEFAULTS.put(PlayerClass.PRIEST, List.of(
                def("lesser_heal", "Малое исцеление", 1, 1, 10, 3),
                def("flash_heal", "Быстрое исцеление", 2, 10, 20, 6),
                def("pw_shield", "Слово силы: Щит", 3, 25, 30, 30),
                def("circle_of_prayer", "Круг молитвы", 4, 50, 50, 60),
                def("smite", "Кара", 5, 75, 60, 90)));
        DEFAULTS.put(PlayerClass.MAGE, List.of(
                def("firebolt", "Огненная стрела", 1, 1, 10, 2),
                def("blink", "Скачок", 2, 25, 20, 20),
                def("frost_nova", "Кольцо льда", 3, 50, 40, 45),
                def("arcane_burst", "Чародейский взрыв", 4, 75, 100, 180)));
        DEFAULTS.put(PlayerClass.ROGUE, List.of(
                def("stealth", "Скрытность", 1, 10, 30, 30),
                def("fan_of_knives", "Веер ножей", 2, 25, 25, 15),
                def("cheap_shot", "Подлый удар", 3, 50, 40, 40),
                def("evasion", "Уклонение", 4, 75, 60, 120)));
    }

    private static AbilityDef def(String id, String name, int slot,
                                  int unlock, int cost, int cooldownSeconds) {
        return new AbilityDef(id, name, slot, unlock, cost, cooldownSeconds * 1000L);
    }

    private final RaskolClasses plugin;
    private final Map<PlayerClass, List<AbilityDef>> byClass = new EnumMap<>(PlayerClass.class);
    private final Map<String, Caster> casters = new HashMap<>();
    private final Map<String, TargetedCaster> targetedCasters = new HashMap<>();

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
        // FIX 1.5.0.7: метод в PriestAbilities называется powerWordShield, не pwShield
        targetedCasters.put("lesser_heal", priest::lesserHeal);
        targetedCasters.put("flash_heal", priest::flashHeal);
        targetedCasters.put("pw_shield", priest::powerWordShield);
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

    /**
     * Загрузка: берём дефолты из DEFAULTS и переопределяем значения
     * из конфига (abilityName/Unlock/Cost/CooldownSeconds).
     * В RaskolConfig нет метода abilityIds — порядок и состав задан DEFAULTS.
     */
    public void loadFromConfig(RaskolConfig cfg) {
        byClass.clear();
        for (PlayerClass pc : PlayerClass.values()) {
            List<AbilityDef> defs = DEFAULTS.get(pc).stream()
                    .map(base -> new AbilityDef(
                            base.id(),
                            cfg.abilityName(pc, base.id(), base.displayName()),
                            base.slot(),
                            cfg.abilityUnlock(pc, base.id(), base.unlockLevel()),
                            cfg.abilityCost(pc, base.id(), base.cost()),
                            cfg.abilityCooldownSeconds(pc, base.id(),
                                    (int) (base.cooldownMillis() / 1000L)) * 1000L
                    ))
                    .toList();
            byClass.put(pc, defs);
        }
    }

    public List<AbilityDef> getAbilities(PlayerClass pc) {
        return byClass.getOrDefault(pc, List.of());
    }

    public AbilityDef getBySlot(PlayerClass pc, int slot) {
        for (AbilityDef def : getAbilities(pc)) {
            if (def.slot() == slot) {
                return def;
            }
        }
        return null;
    }

    /** Поиск абилки по id внутри класса (для BindListener). */
    public AbilityDef findById(PlayerClass pc, String id) {
        if (id == null) {
            return null;
        }
        for (AbilityDef def : getAbilities(pc)) {
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
        return targetedCasters.containsKey(id);
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
