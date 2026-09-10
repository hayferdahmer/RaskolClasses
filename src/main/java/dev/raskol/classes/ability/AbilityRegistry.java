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
 * 1.5.9: сообщение гейта каста читается из messages.gate.blocked.
 * 1.6.11: exists(id) для санитизации свитков.
 * 1.6.13: integrityProblems() для /rc selftest.
 * FIX 1.7.0-hotfix: при cost <= 0 проверка consume не вызывается —
 * бесплатные абилки (Аспект гепарда и т.п.) больше не получают
 * «Не хватает ресурса: нужно 0» (дефект ResourceState.consume(0) → false).
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

        casters.put("steel_skin", warrior::steelSkin);
        casters.put("shield_bash", warrior::shieldBash);
        casters.put("blood_fury", warrior::bloodFury);
        casters.put("war_god", warrior::warGod);

        casters.put("aimed_shot", hunter::aimedShot);
        casters.put("cheetah_aspect", hunter::cheetahAspect);
        casters.put("multi_shot", hunter::multiShot);
        casters.put("barrage", hunter::barrage);

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
        // 1.5.8: auth + creative гейт; 1.5.9: текст гейта из конфига
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
        // FIX 1.7.0-hotfix: бесплатные абилки (cost <= 0) не трогают ресурс —
        // обходим дефект ResourceState.consume(0) == false («Нужно 0, у вас N»)
        if (def.cost() > 0 && !plugin.getResources().consume(id, def.cost())) {
            caster.sendMessage(Component.text("Не хватает ресурса «" + pc.getResourceName()
                    + "»: нужно " + def.cost() + ", у вас "
                    + (int) plugin.getResources().getValue(id), NamedTextColor.RED));
            return false;
        }
        if (def.cost() > 0) {
            plugin.getCooldowns().start(id, def.id(), def.cooldownMillis());
        } else {
            plugin.getCooldowns().start(id, def.id(), def.cooldownMillis());
        }

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
