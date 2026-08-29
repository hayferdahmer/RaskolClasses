// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.config.RaskolConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityRegistry {

    @FunctionalInterface
    public interface Caster {
        boolean cast(Player player, AbilityDef def);
    }

    private final RaskolClasses plugin;
    private final Map<PlayerClass, List<AbilityDef>> byClass = new EnumMap<>(PlayerClass.class);
    private final Map<String, Caster> casters = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastAttempts = new ConcurrentHashMap<>();

    private static final Map<PlayerClass, List<AbilityDef>> DEFAULTS;

    static {
        DEFAULTS = new EnumMap<>(PlayerClass.class);
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

        casters.put("steel_skin", warrior::steelSkin);
        casters.put("shield_bash", warrior::shieldBash);
        casters.put("blood_fury", warrior::bloodFury);
        casters.put("war_god", warrior::warGod);

        casters.put("aimed_shot", hunter::aimedShot);
        casters.put("cheetah_aspect", hunter::cheetahAspect);
        casters.put("multi_shot", hunter::multiShot);
        casters.put("barrage", hunter::barrage);

        casters.put("lesser_heal", priest::lesserHeal);
        casters.put("flash_heal", priest::flashHeal);
        casters.put("pw_shield", priest::powerWordShield);
        casters.put("circle_of_prayer", priest::circleOfPrayer);
        casters.put("smite", priest::smite);

        casters.put("firebolt", mage::firebolt);
        casters.put("blink", mage::blink);
        casters.put("frost_nova", mage::frostNova);
        casters.put("arcane_burst", mage::arcaneBurst);

        casters.put("stealth", rogue::stealth);
        casters.put("fan_of_knives", rogue::fanOfKnives);
        casters.put("cheap_shot", rogue::cheapShot);
        casters.put("evasion", rogue::evasion);
    }

    public void loadFromConfig(RaskolConfig config) {
        byClass.clear();
        for (PlayerClass pc : PlayerClass.values()) {
            List<AbilityDef> defs = DEFAULTS.get(pc).stream()
                    .map(base -> new AbilityDef(
                            base.id(),
                            config.abilityName(pc, base.id(), base.displayName()),
                            base.slot(),
                            config.abilityUnlock(pc, base.id(), base.unlockLevel()),
                            config.abilityCost(pc, base.id(), base.cost()),
                            config.abilityCooldownSeconds(pc, base.id(),
                                    (int) (base.cooldownMillis() / 1000L)) * 1000L))
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

    public boolean tryCast(Player player, AbilityDef def) {
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        if (pc == null) {
            player.sendMessage(Component.text("Класс не выбран — способности недоступны",
                    NamedTextColor.GRAY));
            return false;
        }
        Caster caster = casters.get(def.id());
        if (caster == null) {
            plugin.getLogger().warning("Способность " + def.id() + " не имеет реализации");
            return false;
        }
        UUID id = player.getUniqueId();

        long window = plugin.getRaskolConfig().castClickCooldownMillis();
        long now = System.currentTimeMillis();
        Map<String, Long> attempts = lastAttempts.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        Long previous = attempts.get(def.id());
        if (previous != null && now - previous < window) {
            return false;
        }
        attempts.put(def.id(), now);

        int level = plugin.getSkillLevels().getLevel(id, pc.profileSkillName());
        if (level != SkillLevelProvider.NO_SKILL_SYSTEM && level < def.unlockLevel()) {
            player.sendMessage(message(pc, "«" + def.displayName() + "» откроется на уровне "
                    + def.unlockLevel() + " (у вас " + level + ")"));
            return false;
        }

        long remaining = plugin.getCooldowns().getRemainingMillis(id, def.id());
        if (remaining > 0L) {
            player.sendMessage(message(pc, "«" + def.displayName() + "»: перезарядка ещё "
                    + (remaining / 1000L + 1L) + "с"));
            return false;
        }

        if (!plugin.getResources().consume(id, def.cost())) {
            player.sendMessage(message(pc, "Не хватает ресурса «" + pc.getResourceName()
                    + "»: нужно " + def.cost() + ", у вас "
                    + (int) plugin.getResources().getValue(id)));
            return false;
        }

        // Пакет 2: передаём displayName для ready-нотификации
        plugin.getCooldowns().start(id, def.id(), def.cooldownMillis(), def.displayName());
        if (!caster.cast(player, def)) {
            plugin.getResources().refund(id, def.cost());
            plugin.getCooldowns().cancel(id, def.id());
            return false;
        }

        RaskolConfig.ClassTheme theme = plugin.getRaskolConfig().themeOf(pc);
        player.getWorld().spawnParticle(theme.particle(),
                player.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0.02);
        player.playSound(player.getLocation(), theme.sound(), 0.6f, 1.2f);

        player.sendMessage(message(pc, "«" + def.displayName() + "» — активирована"));
        return true;
    }

    private static Component message(PlayerClass pc, String text) {
        return Component.text(text).color(pc == null ? NamedTextColor.GRAY : pc.getColor());
    }
}
