// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hud;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.classsystem.PlayerClass;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.NamespacedKey;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 1.7.0 пакет 2: HP-реформа в Dota-стиле.
 *
 * Слои отображения (hp-display.mode):
 *  - bossbar (дефолт): персональный босс-бар СВЕРХУ «❬ ❤ 734/1250 ▰▰▰▱▱ ❭»
 *    с цветом по доле (зелёный >60% → жёлтый >30% → красный); ресурс остаётся
 *    в actionbar СНИЗУ (HudService без изменений) — два слоя в одном стиле ❬ ❭;
 *  - actionbar: ОДНА совмещённая строка actionbar (HP-гейдж + ресурс-гейдж),
 *    задача HudService при этом НЕ стартует (решается в RaskolClasses);
 *  - vanilla: бар не рисуется, сердца возвращаются.
 *
 * Скрытие ванильных сердец: setHealthScaled(true) + setHealthScale(hearts-scale)
 * (дефолт 0.0 — сердца не рисуются вовсе); ключ hearts-scale страховочный,
 * если клиент окажется капризным (0..20).
 *
 * Применение maxHP: AttributeModifier ADD_NUMBER с ключом raskolclasses:max_hp;
 * пересчёт каждые hp-display.update-period-ticks (дефолт 10, как у HUD) —
 * покрывает все триггеры без событий: смена класса, рост уровня скилла,
 * выбор/респец спеки, модификаторы атрибутов, /rc reload.
 * Здоровье клампится сверху при уменьшении maxHP (хилы/урон не ломаются).
 */
public final class HpBarService implements Listener {

    private final RaskolClasses plugin;
    private final NamespacedKey maxHpKey;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final AtomicBoolean heartsWarned = new AtomicBoolean(false);

    public HpBarService(RaskolClasses plugin) {
        this.plugin = plugin;
        this.maxHpKey = new NamespacedKey(plugin, "max_hp");
    }

    /* -------------------------------- конфиг -------------------------------- */

    private String mode() {
        return plugin.getConfig().getString("hp-display.mode", "bossbar").toLowerCase(Locale.ROOT);
    }

    private boolean hideHearts() {
        return plugin.getConfig().getBoolean("hp-display.hide-vanilla-hearts", true);
    }

    private double heartsScale() {
        double v = plugin.getConfig().getDouble("hp-display.hearts-scale", 0.0);
        if (!Double.isFinite(v) || v < 0.0) {
            return 0.0;
        }
        return Math.min(20.0, v);
    }

    private int period() {
        return Math.max(1, plugin.getConfig().getInt("hp-display.update-period-ticks", 10));
    }

    private String format() {
        return plugin.getConfig().getString("hp-display.format", "❤ {hp}/{max}");
    }

    /* -------------------------------- задачи -------------------------------- */

    public BukkitTask start() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period(), period());
    }

    private void tick() {
        String mode = mode();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                hideBar(player);
                continue;
            }
            applyMaxHealth(player);
            applyHearts(player);
            if ("vanilla".equals(mode)) {
                hideBar(player);
                continue;
            }
            if ("actionbar".equals(mode)) {
                hideBar(player);
                sendCombinedActionbar(player);
            } else {
                updateBar(player);
            }
        }
        // гигиена: бары оффлайн-игроков не держим
        bars.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
    }

    /* ----------------------------- применение maxHP ----------------------------- */

    /** Ставит/обновляет AttributeModifier так, чтобы maxHealth == AttributeService.maxHp. */
    private void applyMaxHealth(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.MAX_HEALTH);
        if (instance == null) {
            return;
        }
        double target = plugin.getAttributes().maxHp(player.getUniqueId());
        if (!Double.isFinite(target) || target < 1.0) {
            target = 20.0;
        }
        double base = instance.getBaseValue();
        double delta = target - base;

        AttributeModifier existing = null;
        for (AttributeModifier m : instance.getModifiers()) {
            if (maxHpKey.equals(m.getKey())) {
                existing = m;
                break;
            }
        }
        if (existing == null || Math.abs(existing.getAmount() - delta) > 0.01) {
            if (existing != null) {
                instance.removeModifier(existing);
            }
            if (Math.abs(delta) > 0.01) {
                instance.addModifier(new AttributeModifier(
                        maxHpKey, delta, AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.ANY));
            }
        }
        double max = instance.getValue();
        if (player.getHealth() > max) {
            player.setHealth(max);
        }
    }

    /** Скрытие/возврат ванильных сердец через health-scale. */
    private void applyHearts(Player player) {
        boolean wantHidden = hideHearts() && !"vanilla".equals(mode());
        if (wantHidden) {
            double scale = heartsScale();
            if (!player.isHealthScaled() || Math.abs(player.getHealthScale() - scale) > 0.001) {
                try {
                    player.setHealthScaled(true);
                    player.setHealthScale(scale);
                } catch (IllegalArgumentException e) {
                    if (heartsWarned.compareAndSet(false, true)) {
                        plugin.getLogger().warning("HpBarService: клиентский health-scale "
                                + scale + " отклонён ядром — сердца остаются видимыми");
                    }
                    player.setHealthScaled(false);
                }
            }
        } else if (player.isHealthScaled()) {
            player.setHealthScaled(false);
        }
    }

    /* -------------------------------- рендер -------------------------------- */

    private void updateBar(Player player) {
        double max = maxOf(player);
        double hp = Math.max(0.0, player.getHealth());
        float progress = max <= 0.0
                ? 0.0f
                : (float) Math.max(0.0, Math.min(1.0, hp / max));
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossBar created = BossBar.bossBar(Component.empty(), 1.0f,
                    BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
            player.showBossBar(created);
            return created;
        });
        bar.progress(progress);
        bar.color(ratioColor(progress));
        bar.name(Component.text(gauge(hp, max),
                TextColor.fromHexString(AttributeMath.hpFractionColor(hp, max))));
    }

    /** Режим actionbar: совмещённая строка HP + ресурс в одном стиле ❬ ❭. */
    private void sendCombinedActionbar(Player player) {
        double max = maxOf(player);
        double hp = Math.max(0.0, player.getHealth());
        double res = plugin.getResources().getValue(player.getUniqueId());
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        String resName = pc != null ? pc.getResourceName() : "Ресурс";
        String hpText = format()
                .replace("{hp}", String.valueOf((int) hp))
                .replace("{max}", String.valueOf((int) max));
        String line = "❬ " + hpText + " " + segments(max <= 0 ? 0 : hp / max) + " ❭"
                + " ❬ " + resName + " " + segments(res / 100.0)
                + " " + (int) res + "/100 ❭";
        player.sendActionBar(Component.text(line,
                TextColor.fromHexString(AttributeMath.hpFractionColor(hp, max))));
    }

    private double maxOf(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.MAX_HEALTH);
        double max = instance != null ? instance.getValue() : 20.0;
        return Double.isFinite(max) && max > 0.0 ? max : 20.0;
    }

    /** «❬ ❤ 734/1250 ▰▰▰▱▱ ❭» — числа + 10-сегментный гейдж. */
    private String gauge(double hp, double max) {
        String numbers = format()
                .replace("{hp}", String.valueOf((int) hp))
                .replace("{max}", String.valueOf((int) max));
        return "❬ " + numbers + " " + segments(max <= 0 ? 0 : hp / max) + " ❭";
    }

    private static String segments(double fraction) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        int filled = (int) Math.round(clamped * 10);
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? '▰' : '▱');
        }
        return sb.toString();
    }

    private static BossBar.Color ratioColor(float progress) {
        if (progress > 0.6f) {
            return BossBar.Color.GREEN;
        }
        if (progress > 0.3f) {
            return BossBar.Color.YELLOW;
        }
        return BossBar.Color.RED;
    }

    private void hideBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    /** Выключение плагина: убрать бары у онлайна. */
    public void shutdown() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            hideBar(player);
        }
        bars.clear();
    }

    /* -------------------------------- события -------------------------------- */

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SPECTATOR) {
            applyMaxHealth(player);
            applyHearts(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hideBar(event.getPlayer());
    }
}
