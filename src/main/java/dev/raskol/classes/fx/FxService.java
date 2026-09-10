// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.fx;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VFX/SFX-каталог (1.5.0) + бюджет звуков (1.5.7) + proc-теги (1.5.6).
 * Записи конфига: vfx.<abilityId>.cast-sound / cast-particle;
 * vfx.proc.<procId>.cast-sound / cast-particle / tag; vfx.trails.*.
 *
 * 1.7.4.1 фикс 5 (баг «нет звуков способностей»):
 *  - если у способности нет записи vfx.<id>.* — onAttempt проигрывает фолбэк
 *    ENTITY_PLAYER_LEVELUP и пишет ОДНОРАЗОВЫЙ WARNING с путём ключа;
 *  - RaskolConfig.addDefault + copyDefaults(true) дописывают недостающие записи
 *    в config.yml при /rc reload — миграция без ручных правок;
 *  - resolveSound устойчив к именам в стиле конфига (алиасы + нормализация).
 */
public final class FxService {

    /** Алиасы конфиговых имён → minecraft-ключи (кейсы прошлых сезонов). */
    private static final Map<String, String> SOUND_ALIASES = Map.of(
            "BLOCK_ENCHANTMENT_TABLE_USE", "block.enchanting_table.use",
            "BLOCK_ENCHANTING_TABLE_USE", "block.enchanting_table.use",
            "ENTITY_ENCHANTING_TABLE_USE", "block.enchanting_table.use");

    private final RaskolClasses plugin;
    /** Пер-игрок КД proc-визуала (3 с), чтобы теги не спамили (1.5.6). */
    private final Map<UUID, Long> procVisualCd = new ConcurrentHashMap<>();
    /** Одноразовые WARNING по отсутствующим vfx-записям (фикс 5). */
    private final Set<String> warnedMissing = ConcurrentHashMap.newKeySet();
    /** Счётчик звуков, сброшенных бюджетом (метрика /rc health). */
    private final AtomicInteger droppedSounds = new AtomicInteger();

    private long budgetTick = -1;
    private int budgetUsed;

    public FxService(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    /* -------------------------------- резолв имён -------------------------------- */

    /** Имя из конфига → Sound или null. Устойчив к регистрам/алиасам. */
    public Sound resolveSound(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        String alias = SOUND_ALIASES.get(upper);
        if (alias != null) {
            Sound aliased = Registry.SOUNDS.get(NamespacedKey.minecraft(alias));
            if (aliased != null) {
                return aliased;
            }
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String dotted = lower.contains(".") ? lower : lower.replace('_', '.');
        Sound direct = Registry.SOUNDS.get(NamespacedKey.minecraft(dotted));
        if (direct != null) {
            return direct;
        }
        return Registry.SOUNDS.get(NamespacedKey.minecraft(lower));
    }

    /** Имя из конфига → Particle или null (фолбэк при битом имени). */
    public Particle resolveParticle(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /* ------------------------------ бюджет звуков ------------------------------ */

    private boolean budgetAllows() {
        int budget = plugin.getConfig().getInt("performance.sound-budget-per-tick", 8);
        if (budget <= 0) {
            return false;
        }
        long tick = Bukkit.getCurrentTick();
        if (tick != budgetTick) {
            budgetTick = tick;
            budgetUsed = 0;
        }
        if (budgetUsed >= budget) {
            droppedSounds.incrementAndGet();
            return false;
        }
        budgetUsed++;
        return true;
    }

    /** Проиграть звук с учётом бюджета (падение бюджета = метрика, не краш). */
    public void playSound(Location loc, Sound sound, float volume, float pitch) {
        if (sound == null || loc == null || loc.getWorld() == null) {
            return;
        }
        if (!budgetAllows()) {
            return;
        }
        loc.getWorld().playSound(loc, sound, volume, pitch);
    }

    /* ------------------------------ каст способности ------------------------------ */

    /**
     * Визуал каста способности: звук + партикл из vfx.<id>.*.
     * Фикс 5: при отсутствии записи — фолбэк-звук + одноразовый WARNING.
     */
    public void onAttempt(Player player, String abilityId, long cooldownMillis) {
        if (player == null || abilityId == null) {
            return;
        }
        String soundKey = plugin.getConfig()
                .getString("vfx." + abilityId + ".cast-sound", null);
        String particleKey = plugin.getConfig()
                .getString("vfx." + abilityId + ".cast-particle", null);

        Sound sound = resolveSound(soundKey);
        if (sound == null) {
            warnMissing(abilityId);
            sound = Sound.ENTITY_PLAYER_LEVELUP; // киты никогда не молчат
        }
        playSound(player.getLocation(), sound, 0.5f, 1.1f);

        Particle particle = resolveParticle(particleKey);
        if (particle != null) {
            player.spawnParticle(particle,
                    player.getLocation().add(0.0, 1.0, 0.0), 12, 0.4, 0.6, 0.4, 0.05);
        }
    }

    /**
     * Алиас для каста спек-активок: SpecActiveCaster вызывает onCast(player, id),
     * когда кулдаун-аргумент не нужен (FxService его внутри onAttempt не читает).
     */
    public void onCast(Player player, String abilityId) {
        onAttempt(player, abilityId, 0L);
    }

    /** Одноразовый WARNING по отсутствующей vfx-записи (фикс 5). */
    private void warnMissing(String abilityId) {
        if (warnedMissing.add(abilityId)) {
            plugin.getLogger().warning("vfx: нет записи vfx." + abilityId
                    + ".* в config.yml — проигран фолбэк-звук. "
                    + "Выполни /rc reload: RaskolConfig допишет дефолты сам.");
        }
    }

    /* -------------------------------- proc-теги -------------------------------- */

    /**
     * Proc-тег пассивки/спеки: субтайтл + звук + партикл, не чаще 1 раза в 3 с
     * на игрока (1.5.6). Гейт vfx.proc-actionbar выключает только субтайтл.
     */
    public void procByKey(Player player, String fallbackTag, String procId) {
        if (player == null || procId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long prev = procVisualCd.get(player.getUniqueId());
        if (prev != null && now - prev < 3000L) {
            return;
        }
        procVisualCd.put(player.getUniqueId(), now);

        if (plugin.getConfig().getBoolean("vfx.proc-actionbar", true)) {
            String tag = plugin.getConfig()
                    .getString("vfx.proc." + procId + ".tag", fallbackTag);
            player.showTitle(Title.title(
                    Component.empty(),
                    Component.text(tag, NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(50),
                            Duration.ofMillis(600), Duration.ofMillis(150))));
        }
        Sound sound = resolveSound(plugin.getConfig()
                .getString("vfx.proc." + procId + ".cast-sound", ""));
        if (sound != null) {
            playSound(player.getLocation(), sound, 0.5f, 1.2f);
        }
        Particle particle = resolveParticle(plugin.getConfig()
                .getString("vfx.proc." + procId + ".cast-particle", ""));
        if (particle != null) {
            player.spawnParticle(particle,
                    player.getLocation().add(0.0, 1.0, 0.0), 10, 0.3, 0.5, 0.3, 0.05);
        }
    }

    /* -------------------------------- трейлы -------------------------------- */

    public boolean trailsFireball() {
        return plugin.getConfig().getBoolean("vfx.trails.fireball", true);
    }

    public boolean trailsArrows() {
        return plugin.getConfig().getBoolean("vfx.trails.arrows", true);
    }

    /* ------------------------------ валидация ------------------------------ */

    /**
     * Проверка каталога при старте и /rc reload: каждое имя звука/партикла
     * в vfx.* (кроме proc/trails) должно резолвиться; иначе WARNING с путём.
     */
    public void validateConfig() {
        var section = plugin.getConfig().getConfigurationSection("vfx");
        if (section == null) {
            plugin.getLogger().warning("vfx: секция отсутствует — все касты на фолбэк-звуке");
            return;
        }
        int problems = 0;
        for (String key : section.getKeys(false)) {
            if (key.equals("proc") || key.equals("trails") || key.equals("proc-actionbar")) {
                continue;
            }
            String soundKey = section.getString(key + ".cast-sound", "");
            if (soundKey != null && !soundKey.isEmpty() && resolveSound(soundKey) == null) {
                problems++;
                plugin.getLogger().warning("vfx." + key + ".cast-sound = " + soundKey
                        + " — имя не распознано, будет фолбэк");
            }
            String particleKey = section.getString(key + ".cast-particle", "");
            if (particleKey != null && !particleKey.isEmpty() && resolveParticle(particleKey) == null) {
                problems++;
                plugin.getLogger().warning("vfx." + key + ".cast-particle = " + particleKey
                        + " — имя не распознано, партикл пропущен");
            }
        }
        if (problems == 0) {
            plugin.getLogger().info("vfx: каталог валиден");
        }
    }

    /* ------------------------------ метрики/чистка ------------------------------ */

    public int procVisualCdSize() {
        return procVisualCd.size();
    }

    public int pollDroppedSounds() {
        return droppedSounds.getAndSet(0);
    }

    /** Чистка proc-КД оффлайн-игроков (purge-таск). */
    public void purgeStale() {
        procVisualCd.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    /** Отладка: по списку id печатает резолв звука/партикла или «нет записи». */
    public void appendDebug(CommandSender sender, List<String> ids) {
        sender.sendMessage(Component.text("VFX-каталог:", NamedTextColor.AQUA));
        for (String id : ids) {
            String soundKey = plugin.getConfig().getString("vfx." + id + ".cast-sound", "");
            String particleKey = plugin.getConfig().getString("vfx." + id + ".cast-particle", "");
            boolean hasEntry = (soundKey != null && !soundKey.isEmpty())
                    || (particleKey != null && !particleKey.isEmpty());
            String status = hasEntry
                    ? "звук=" + (resolveSound(soundKey) != null ? soundKey : "БИТЫЙ:" + soundKey)
                    + " партикл=" + (resolveParticle(particleKey) != null ? particleKey : "—")
                    : "НЕТ ЗАПИСИ (фолбэк-звук)";
            sender.sendMessage(Component.text("  • " + id + ": " + status,
                    NamedTextColor.GRAY));
        }
    }
}
