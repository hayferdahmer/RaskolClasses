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
                def("smite", "Кара", 5, 7
