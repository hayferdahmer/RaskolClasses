// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.attribute;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import dev.raskol.classes.classsystem.SkillLevelProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 1.7.0: сервис классовых атрибутов.
 * Значение атрибута = base(class) + growth(class)×PlayerLevel + модификаторы
 * (спек-пассивки, эффекты, будущий шмот). Кэш на игрока на тик (паттерн
 * ResistService): повторные чтения в одном тике бесплатны, любое изменение
 * модификаторов инвалидирует кэш немедленно.
 *
 * PlayerLevel по умолчанию = уровень профильного скилла класса (SkillLevelProvider);
 * переключатель attributes.level-source: class-skill | vanilla.
 * Вся математика — в AttributeMath (pure), здесь только данные и кэш.
 */
public final class AttributeService {

    /** Временной или постоянный модификатор атрибутов (дельты). */
    public record Modifier(String source, double str, double agi, double intel, long expiresAt) {
        public boolean isPermanent() {
            return expiresAt == Long.MAX_VALUE;
        }
    }

    /** Кэш значений на тик; main-thread only. */
    private static final class Cache {
        long tick = -1;
        double str;
        double agi;
        double intel;
    }

    private final RaskolClasses plugin;
    private final Map<UUID, CopyOnWriteArrayList<Modifier>> modifiers = new ConcurrentHashMap<>();
    private final Map<UUID, Cache> cache = new ConcurrentHashMap<>();

    public AttributeService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------ конфигурация ------------------------------ */

    /** Основной атрибут класса (конфиг attributes.classes.<PC>.main, фолбэк по классу). */
    public AttributeType mainOf(PlayerClass pc) {
        String cfg = plugin.getConfig().getString("attributes.classes." + pc.name() + ".main");
        AttributeType parsed = AttributeType.fromId(cfg);
        if (parsed != null) {
            return parsed;
        }
        return switch (pc) {
            case WARRIOR -> AttributeType.STR;
            case HUNTER, ROGUE -> AttributeType.AGI;
            case MAGE, PRIEST -> AttributeType.INT;
        };
    }

    private double baseOf(PlayerClass pc, AttributeType type) {
        double v = plugin.getConfig().getDouble(
                "attributes.classes." + pc.name() + ".base-" + type.name().toLowerCase(),
                defaultBase(pc, type));
        return Double.isFinite(v) ? Math.max(0.0, v) : defaultBase(pc, type);
    }

    private double growthOf(PlayerClass pc, AttributeType type) {
        double v = plugin.getConfig().getDouble(
                "attributes.classes." + pc.name() + ".growth-" + type.name().toLowerCase(),
                defaultGrowth(pc, type));
        return Double.isFinite(v) ? Math.max(0.0, v) : defaultGrowth(pc, type);
    }

    private static double defaultBase(PlayerClass pc, AttributeType type) {
        return switch (pc) {
            case WARRIOR -> type == AttributeType.STR ? 12 : type == AttributeType.AGI ? 6 : 4;
            case HUNTER -> type == AttributeType.STR ? 6 : type == AttributeType.AGI ? 12 : 4;
            case ROGUE -> type == AttributeType.STR ? 7 : type == AttributeType.AGI ? 11 : 4;
            case MAGE -> type == AttributeType.STR ? 4 : type == AttributeType.AGI ? 6 : 12;
            case PRIEST -> type == AttributeType.STR ? 5 : type == AttributeType.AGI ? 5 : 12;
        };
    }

    private static double defaultGrowth(PlayerClass pc, AttributeType type) {
        return switch (pc) {
            case WARRIOR -> type == AttributeType.STR ? 1.2 : type == AttributeType.AGI ? 0.5 : 0.3;
            case HUNTER -> type == AttributeType.STR ? 0.5 : type == AttributeType.AGI ? 1.2 : 0.3;
            case ROGUE -> type == AttributeType.STR ? 0.6 : type == AttributeType.AGI ? 1.1 : 0.3;
            case MAGE -> type == AttributeType.STR ? 0.3 : type == AttributeType.AGI ? 0.5 : 1.2;
            case PRIEST -> type == AttributeType.STR ? 0.4 : type == AttributeType.AGI ? 0.4 : 1.2;
        };
    }

    /** PlayerLevel для формул: class-skill (дефолт) или vanilla. */
    public double levelOf(UUID uuid, PlayerClass pc) {
        String source = plugin.getConfig().getString("attributes.level-source", "class-skill");
        if ("vanilla".equalsIgnoreCase(source)) {
            Player player = Bukkit.getPlayer(uuid);
            return player != null ? Math.max(0, player.getLevel()) : 0;
        }
        int level = plugin.getSkillLevels().getLevel(uuid, pc.profileSkillName());
        return level == SkillLevelProvider.NO_SKILL_SYSTEM ? 0 : Math.max(0, level);
    }

    /* -------------------------------- значения -------------------------------- */

    /** Итоговое значение атрибута: base + growth×level + модификаторы. */
    public double value(UUID uuid, AttributeType type) {
        Cache c = cacheOf(uuid);
        return switch (type) {
            case STR -> c.str;
            case AGI -> c.agi;
            case INT -> c.intel;
        };
    }

    private Cache cacheOf(UUID uuid) {
        Cache c = cache.computeIfAbsent(uuid, k -> new Cache());
        long tick = Bukkit.getCurrentTick();
        if (c.tick != tick) {
            recompute(uuid, c);
            c.tick = tick;
        }
        return c;
    }

    private void recompute(UUID uuid, Cache c) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        if (pc == null) {
            c.str = 0;
            c.agi = 0;
            c.intel = 0;
            return;
        }
        double level = levelOf(uuid, pc);
        double str = baseOf(pc, AttributeType.STR) + growthOf(pc, AttributeType.STR) * level;
        double agi = baseOf(pc, AttributeType.AGI) + growthOf(pc, AttributeType.AGI) * level;
        double intel = baseOf(pc, AttributeType.INT) + growthOf(pc, AttributeType.INT) * level;
        long now = System.currentTimeMillis();
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        if (list != null) {
            for (Modifier m : list) {
                if (m.isPermanent() || m.expiresAt() > now) {
                    str += m.str();
                    agi += m.agi();
                    intel += m.intel();
                }
            }
        }
        c.str = Math.max(0.0, str);
        c.agi = Math.max(0.0, agi);
        c.intel = Math.max(0.0, intel);
    }

    /** Инвалидация кэша (изменение модификаторов, смена класса). */
    public void invalidate(UUID uuid) {
        Cache c = cache.get(uuid);
        if (c != null) {
            c.tick = -1;
        }
    }

    /* --------------------------- производные величины -------------------------- */

    /** Максимум HP игрока по формуле пакета 1 (применение — в пакете 2). */
    public double maxHp(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        if (pc == null) {
            return 20.0;
        }
        double str = value(uuid, AttributeType.STR);
        double level = levelOf(uuid, pc);
        boolean strMain = mainOf(pc) == AttributeType.STR;
        double perStr = plugin.getConfig().getDouble("attributes.hp.per-str", 2.0);
        double perLevel = plugin.getConfig().getDouble("attributes.hp.per-level", 1.0);
        double mainBonus = plugin.getConfig().getDouble("attributes.hp.main-str-bonus", 1.0);
        return AttributeMath.maxHp(str, level, strMain, perStr, perLevel, mainBonus);
    }

    /** Шанс крита мили, % (применение — в пакете 4). */
    public double critMeleeChance(UUID uuid) {
        double agi = value(uuid, AttributeType.AGI);
        return AttributeMath.critMelee(agi,
                plugin.getConfig().getDouble("attributes.crit.melee-base", 5.0),
                plugin.getConfig().getDouble("attributes.crit.melee-per-agi", 0.05),
                plugin.getConfig().getDouble("attributes.crit.melee-cap", 40.0));
    }

    /** Шанс крита магии, % (применение — в пакете 4). */
    public double critSpellChance(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        PlayerClass pc = player != null ? plugin.getClassProvider().getClassOf(player) : null;
        boolean intMain = pc != null && mainOf(pc) == AttributeType.INT;
        double intel = value(uuid, AttributeType.INT);
        return AttributeMath.critSpell(intel, intMain,
                plugin.getConfig().getDouble("attributes.crit.spell-base", 5.0),
                plugin.getConfig().getDouble("attributes.crit.spell-per-int", 0.03),
                plugin.getConfig().getDouble("attributes.crit.spell-main-mult", 1.5),
                plugin.getConfig().getDouble("attributes.crit.spell-cap", 35.0));
    }

    /* ------------------------------- модификаторы ------------------------------ */

    public void addTimedModifier(UUID uuid, String source,
                                 double str, double agi, double intel, long millis) {
        removeModifiersBySource(uuid, source);
        modifiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new Modifier(source, str, agi, intel, System.currentTimeMillis() + millis));
        invalidate(uuid);
    }

    public void addPermanentModifier(UUID uuid, String source,
                                     double str, double agi, double intel) {
        removeModifiersBySource(uuid, source);
        modifiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>())
                .add(new Modifier(source, str, agi, intel, Long.MAX_VALUE));
        invalidate(uuid);
    }

    public void removeModifiersBySource(UUID uuid, String source) {
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return;
        }
        list.removeIf(m -> m.source().equals(source));
        if (list.isEmpty()) {
            modifiers.remove(uuid);
        }
        invalidate(uuid);
    }

    public boolean hasModifier(UUID uuid, String source) {
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Modifier m : list) {
            if (m.source().equals(source) && (m.isPermanent() || m.expiresAt() > now)) {
                return true;
            }
        }
        return false;
    }

    /** Активные модификаторы для отображения (Книга, /rc debug). */
    public List<Modifier> activeModifiers(UUID uuid) {
        CopyOnWriteArrayList<Modifier> list = modifiers.get(uuid);
        if (list == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        return list.stream()
                .filter(m -> m.isPermanent() || m.expiresAt() > now)
                .toList();
    }

    /** Чистка истёкших модификаторов и кэшей оффлайна (purge-таск). */
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        modifiers.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(m -> !m.isPermanent() && m.expiresAt() <= now);
            return entry.getValue().isEmpty();
        });
        cache.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null
                && !modifiers.containsKey(uuid));
    }

    /** Полная чистка игрока (PlayerQuit). */
    public void clear(UUID uuid) {
        modifiers.remove(uuid);
        cache.remove(uuid);
    }
}
