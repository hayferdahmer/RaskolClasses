// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.ability;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import dev.raskol.classes.compat.AuthGate;
import dev.raskol.classes.config.RaskolConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр способностей пяти классов.
 * 1.7.2: киты Воина (нордика) и Охотника (средневековье других вселенных).
 * 1.7.3: киты Мага (Греция) и Разбойника (средневековый реализм).
 * 1.7.4: кит Жреца (католика/паладинство) — хилы масштабируются от HPow.
 * Все киты масштабируются от WP/SP/HPow через PowerService (base + Power×coeff).
 *
 * 1.7.4.1: ФИКС — кулдаун стартует ПОСЛЕ успешного каста (раньше стартовал ДО,
 * из-за чего проваленный каст (нет цели/полный HP) всё равно уходил в КД).
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

    private static final Map<PlayerClass, List<AbilityDef>> DEFAULTS = new EnumMap<>(PlayerClass.class);

    static {
        // 1.7.2: нордика, лестница 10/25/50/65/75
        DEFAULTS.put(PlayerClass.WARRIOR, List.of(
                def("tyr_strike", "Удар Тира", 1, 10, 20, 8),
                def("balder_skin", "Шкура Бальдра", 2, 25, 25, 30),
                def("berserkergang", "Берсеркерганг", 3, 50, 35, 45),
                def("fenrir_blood", "Кровь Фенрира", 4, 65, 30, 25),
                def("ragnarok", "Рагнарёк", 5, 75, 60, 60)));
        // 1.7.2: Ведьмак/Skyrim-вайб, лестница 10/25/50/65/75
        DEFAULTS.put(PlayerClass.HUNTER, List.of(
                def("wolf_mark", "Метка Волка", 1, 10, 20, 12),
                def("swallow", "Ласточка", 2, 25, 15, 40),
                def("piercing_shot", "Пронзающий выстрел", 3, 50, 30, 20),
                def("arrow_fan", "Веер стрел", 4, 65, 35, 22),
                def("arrow_rain", "Дождь стрел", 5, 75, 60, 90)));
        // 1.7.4: католика/паладинство, лестница 10/25/50/65/75
        DEFAULTS.put(PlayerClass.PRIEST, List.of(
                def("saint_tear", "Слеза Святой", 1, 10, 10, 3),
                def("word_of_life", "Слово Жизни", 2, 25, 20, 6),
                def("aegis_faith", "Эгида Веры", 3, 50, 30, 30),
                def("circle_elysium", "Круг Элизия", 4, 65, 50, 60),
                def("wrath_heaven", "Кара Небес", 5, 75, 60, 90)));
        // 1.7.3: Греция, лестница 10/25/50/65/75
        DEFAULTS.put(PlayerClass.MAGE, List.of(
                def("fire_prometheus", "Огонь Прометея", 1, 10, 15, 6),
                def("hermes_step", "Шаг Гермеса", 2, 25, 20, 20),
                def("boreas_breath", "Дыхание Борея", 3, 50, 40, 45),
                def("athena_aegis", "Эгида Афины", 4, 65, 30, 30),
                def("zeus_wrath", "Гнев Зевса", 5, 75, 60, 90)));
        // 1.7.3: средневековый реализм, лестница 10/25/50/65/75
        DEFAULTS.put(PlayerClass.ROGUE, List.of(
                def("shadow_cloak", "Плащ теней", 1, 10, 30, 30),
                def("blade_fan", "Веер клинков", 2, 25, 25, 15),
                def("strangle", "Удушение палача", 3, 50, 40, 40),
                def("borgia_poison", "Яд Борджа", 4, 65, 35, 30),
                def("shadow_dance", "Танец теней", 5, 75, 60, 120)));
    }

    private static AbilityDef def(String id, String name, int slot,
                                  int unlock, int cost, int cooldownSeconds) {
        return new AbilityDef(id, name, slot, unlock, cost, cooldownSeconds * 1000L);
    }

    private final RaskolClasses plugin;
    private final Map<PlayerClass, List<AbilityDef>> byClass = new EnumMap<>(PlayerClass.class);
    private final Map<String, Caster> casters = new HashMap<>();
    private final Map<String, TargetedCaster> targetedCasters = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastAttempts = new ConcurrentHashMap<>();

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

        // 1.7.2: кит воина (нордика)
        casters.put("tyr_strike", warrior::tyrStrike);
        casters.put("balder_skin", warrior::balderSkin);
        casters.put("berserkergang", warrior::berserkergang);
        casters.put("fenrir_blood", warrior::fenrirBlood);
        casters.put("ragnarok", warrior::ragnarok);

        // 1.7.2: кит охотника (средневековье других вселенных)
        casters.put("wolf_mark", hunter::wolfMark);
        casters.put("swallow", hunter::swallow);
        casters.put("piercing_shot", hunter::piercingShot);
        casters.put("arrow_fan", hunter::arrowFan);
        casters.put("arrow_rain", hunter::arrowRain);

        // 1.7.4: кит жреца (католика/паладинство); таргет-хилы через targetedCasters
        casters.put("saint_tear", (p, d) -> priest.saintTear(p, p, d));
        casters.put("word_of_life", (p, d) -> priest.wordOfLife(p, p, d));
        targetedCasters.put("saint_tear", priest::saintTear);
        targetedCasters.put("word_of_life", priest::wordOfLife);
        casters.put("aegis_faith", priest::aegisFaith);
        casters.put("circle_elysium", priest::circleElysium);
        casters.put("wrath_heaven", priest::wrathHeaven);

        // 1.7.3: кит мага (Греция)
        casters.put("fire_prometheus", mage::firePrometheus);
        casters.put("hermes_step", mage::hermesStep);
        casters.put("boreas_breath", mage::boreasBreath);
        casters.put("athena_aegis", mage::athenaAegis);
        casters.put("zeus_wrath", mage::zeusWrath);

        // 1.7.3: кит разбойника (средневековый реализм)
        casters.put("shadow_cloak", rogue::shadowCloak);
        casters.put("blade_fan", rogue::bladeFan);
        casters.put("strangle", rogue::
