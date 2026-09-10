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
 * 1.7.2: киты Воина (нордика) и Охотника (средневековье других вселенных) заменены
 * на масштабируемые от WP способности (base + WP×coeff); старые id воина/охотника
 * удалены — мёртвые свитки сжигает ScrollSanitizer на join (1.6.11).
 * Киты Мага/Разбойника/Жреца остаются на старых id до патчей 1.7.3/1.7.4.
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

        casters.put("lesser_heal", (p, d) -> priest.lesserHeal(p, p, d));
        casters.put("flash_heal", (p, d) -> priest.flashHeal(p, p, d));
        casters.put("pw_shield", (p, d) -> priest.powerWordShield(p, p, d));
        targetedCasters.put("lesser_heal", priest::lesserHeal);
        targetedCasters.put("flash_heal", priest::flashHeal);
        targetedCasters.put("pw_shield", priest::powerWordShield);
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

    public AbilityDef getById(PlayerClass pc, String id) {
        return findById(pc, id);
    }

    /** 1.6.11: есть ли способность с таким id хотя бы в одном классе. */
    public boolean exists(String id) {
        if (id == null) {
            return false;
        }
        for (PlayerClass pc : PlayerClass.values()) {
            if (findById(pc, id) != null) {
                return true;
            }
        }
        return false;
    }

    /** 1.6.13: целостность реестра для /rc selftest. */
    public List<String> integrityProblems() {
        List<String> problems = new ArrayList<>();
        Set<String> knownIds = new HashSet<>();
        for (PlayerClass pc : PlayerClass.values()) {
            for (AbilityDef def : getAbilities(pc)) {
                knownIds.add(def.id());
                if (!casters.containsKey(def.id()) && !targetedCasters.containsKey(def.id())) {
                    problems.add("способность " + def.id() + " (" + pc.name() + ") без кастера");
                }
            }
        }
        for (String id : targetedCasters.keySet()) {
            if (!casters.containsKey(id)) {
                problems.add("targeted-кастер " + id + " без self-кастера");
            }
            if (!knownIds.contains(id)) {
                problems.add("targeted-кастер " + id + " — сирота (нет в DEFAULTS)");
            }
        }
        for (String id : casters.keySet()) {
            if (!knownIds.contains(id)) {
                problems.add("кастер " + id + " — сирота (нет в DEFAULTS)");
            }
        }
        return problems;
    }

    public boolean isTargeted(String id) {
        return targetedCasters.containsKey(id);
    }

    public boolean tryCast(Player player, AbilityDef def) {
        return castOn(player, player, def, false);
    }

    public boolean tryCastTargeted(Player caster, LivingEntity target, AbilityDef def) {
        return castOn(caster, target, def, true);
    }

    private boolean castOn(Player caster, LivingEntity target, AbilityDef def, boolean targeted) {
        RaskolConfig cfg = plugin.getRaskolConfig();
        if (!AuthGate.canAct(plugin, caster)) {
            caster.sendMessage(Component.text(cfg.message("gate.blocked",
                    "Способности недоступны в этом режиме или до входа в аккаунт."),
                    NamedTextColor.RED));
            return false;
        }
        PlayerClass pc = plugin.getClassProvider().getClassOf(caster);
        if (pc == null) {
            caster.sendMessage(Component.text(cfg.message("no-class-cast",
                    "Класс не выбран — способности недоступны"), NamedTextColor.GRAY));
            return false;
        }
        Caster self = targeted ? null : casters.get(def.id());
        TargetedCaster tcast = targeted ? targetedCasters.get(def.id()) : null;
        if (self == null && tcast == null) {
            plugin.getLogger().warning("Способность " + def.id() + " не имеет реализации");
            return false;
        }
        UUID id = caster.getUniqueId();

        long window = cfg.castClickCooldownMillis();
        long now = System.currentTimeMillis();
        Map<String, Long> attempts = lastAttempts.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        Long previous = attempts.get(def.id());
        if (previous != null && now - previous < window) {
            return false;
        }
        attempts.put(def.id(), now);

        int level = plugin.getSkillLevels().getLevel(id, pc.profileSkillName());
        if (level != SkillLevelProvider.NO_SKILL_SYSTEM && level < def.unlockLevel()) {
            caster.sendMessage(Component.text("«" + def.displayName() + "» откроется на уровне "
                    + def.unlockLevel() + " (у вас " + level + ")", NamedTextColor.RED));
            return false;
        }
        if (plugin.getCooldowns().isOnCooldown(id, def.id())) {
            long remaining = plugin.getCooldowns().getRemainingMillis(id, def.id());
            caster.sendMessage(Component.text("«" + def.displayName() + "»: перезарядка ещё "
                    + (remaining / 1000L + 1L) + "с", NamedTextColor.GRAY));
            return false;
        }
        if (def.cost() > 0 && !plugin.getResources().consume(id, def.cost())) {
            caster.sendMessage(Component.text("Не хватает ресурса «" + pc.getResourceName()
                    + "»: нужно " + def.cost() + ", у вас "
                    + (int) plugin.getResources().getValue(id), NamedTextColor.RED));
            return false;
        }
        plugin.getCooldowns().start(id, def.id(), def.cooldownMillis());

        boolean ok = targeted ? tcast.cast(caster, target, def) : self.cast(caster, def);
        if (!ok) {
            if (def.cost() > 0) {
                plugin.getResources().refund(id, def.cost());
            }
            return false;
        }
        if (!targeted) {
            caster.sendMessage(Component.text("«" + def.displayName() + "» — активирована",
                    NamedTextColor.GREEN));
        }
        return true;
    }

    /** Полная чистка игрока (PlayerQuit). */
    public void clearAttempts(UUID uuid) {
        lastAttempts.remove(uuid);
    }

    /** Периодическая чистка записей старше 60 с (purge-таск). */
    public void purgeStaleAttempts() {
        long now = System.currentTimeMillis();
        lastAttempts.values().forEach(map ->
                map.entrySet().removeIf(entry -> now - entry.getValue() > 60_000L));
        lastAttempts.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
