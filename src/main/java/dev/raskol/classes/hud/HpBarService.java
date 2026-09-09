// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.hud;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.attribute.AttributeMath;
import dev.raskol.classes.classsystem.PlayerClass;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/**
 * 1.7.0 пакет 2 (редизайн после провала v1): HP-реформа без босс-баров и без
 * глифов, которых нет в шрифте клиента.
 *
 * Итоговый вид (mode=actionbar, дефолт): ОДНА совмещённая строка actionbar
 *   ❬ ❤ 82/234 ❭ ❬ Концентрация 70/100 ❭
 * HP-сегмент красится по доле (зелёный >60% → жёлтый >30% → красный),
 * ресурс — цветом класса. Задача HudService в этом режиме НЕ стартует
 * (решается в RaskolClasses), поэтому двойной строки ресурса нет.
 *
 * Сердца: healthScale = hearts-scale (дефолт 20.0) — ровно ОДИН ряд из 10
 * сердец на весь пул HP (визуальная доля без многорядного месива).
 * hearts-scale: 0 — неподдерживаемый эксперимент «скрыть сердца» (на части
 * сборок не работает, оставлен на свой страх).
 * mode=villa... vanilla: строка не шлётся, сердца возвращаются в дефолт,
 * ресурсную строку снова рисует HudService (стартует в RaskolClasses).
 *
 * Применение maxHP: AttributeModifier ADD_NUMBER с ключом raskolclasses:max_hp;
 * пересчёт каждые hp-display.update-period-ticks (дефолт 10) покрывает все
 * триггеры: смена класса, рост уровня, спек, модификаторы, reload.
 * Здоровье клампится сверху при уменьшении maxHP.
 *
 * FIX 1.7.0-p2.1: Attribute резолвится через RegistryAccess (Paper 1.21.4).
 * REDesign 1.7.0-p2.2: убраны босс-бар и глифы ▰▱ (артефакты шрифта на клиенте).
 */
public final class HpBarService implements Listener {

    /** max_health из реестра атрибутов Paper (1.21.4-safe). */
    private static final Attribute MAX_HEALTH = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ATTRIBUTE)
            .get(NamespacedKey.minecraft("max_health"));

    private final RaskolClasses plugin;
    private final NamespacedKey maxHpKey;

    public HpBarService(RaskolClasses plugin) {
        this.plugin = plugin;
        this.maxHpKey = new NamespacedKey(plugin, "max_hp");
    }

    /* -------------------------------- конфиг -------------------------------- */

    private String mode() {
        return plugin.getConfig().getString("hp-display.mode", "actionbar")
                .toLowerCase(Locale.ROOT);
    }

    private double heartsScale() {
        double v = plugin.getConfig().getDouble("hp-display.hearts-scale", 20.0);
        if (!Double.isFinite(v) || v < 0.0) {
            return 20.0;
        }
        return Math.min(20.0, v);
    }

    private int period() {
        return Math.max(1, plugin.getConfig().getInt("hp-display.update-period-ticks", 10));
    }

    private String format() {
        return plugin.getConfig().getString("hp-display.format", "❤ {hp}/{max}");
    }

    /* -------------------------------- задача -------------------------------- */

    public BukkitTask start() {
        return plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period(), period());
    }

    private void tick() {
        boolean unified = !"vanilla".equals(mode());
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue; // зрителям ни строки, ни scale-правкок
            }
            applyMaxHealth(player);
            applyHearts(player, unified);
            if (unified) {
                sendUnifiedActionbar(player);
            }
        }
    }

    /* ----------------------------- применение maxHP ----------------------------- */

    private void applyMaxHealth(Player player) {
        if (MAX_HEALTH == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(MAX_HEALTH);
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

    /**
     * Сердца: в unified-режиме — ровно один ряд (scale 20 по умолчанию);
     * в vanilla — возвращаем клиенту дефолтное отображение.
     */
    private void applyHearts(Player player, boolean unified) {
        if (!unified) {
            if (player.isHealthScaled()) {
                player.setHealthScaled(false);
            }
            return;
        }
        double scale = heartsScale();
        if (!player.isHealthScaled() || Math.abs(player.getHealthScale() - scale) > 0.001) {
            try {
                player.setHealthScaled(true);
                player.setHealthScale(scale);
            } catch (IllegalArgumentException e) {
                // ядро отклонило scale — оставляем дефолт, строка всё равно несёт числа
                player.setHealthScaled(false);
            }
        }
    }

    /* --------------------------- совмещённая строка --------------------------- */

    /**
     * ❬ ❤ 82/234 ❭ ❬ Концентрация 70/100 ❭ — только фонто-безопасные символы.
     * HP-сегмент красится по доле, ресурс — цветом класса.
     */
    private void sendUnifiedActionbar(Player player) {
        double max = maxOf(player);
        double hp = Math.max(0.0, player.getHealth());
        double res = plugin.getResources().getValue(player.getUniqueId());
        PlayerClass pc = plugin.getClassProvider().getClassOf(player);
        String resName = pc != null ? pc.getResourceName() : "Ресурс";
        TextColor resColor = pc != null ? pc.getColor() : NamedTextColor.AQUA;

        String hpText = format()
                .replace("{hp}", String.valueOf((int) hp))
                .replace("{max}", String.valueOf((int) max));

        Component line = Component.text("❬ ", NamedTextColor.DARK_GRAY)
                .append(Component.text(hpText,
                        TextColor.fromHexString(AttributeMath.hpFractionColor(hp, max))))
                .append(Component.text(" ❭ ", NamedTextColor.DARK_GRAY))
                .append(Component.text("❬ ", NamedTextColor.DARK_GRAY))
                .append(Component.text(resName + " " + (int) res + "/100", resColor))
                .append(Component.text(" ❭", NamedTextColor.DARK_GRAY));
        player.sendActionBar(line);
    }

    private double maxOf(Player player) {
        if (MAX_HEALTH == null) {
            return 20.0;
        }
        AttributeInstance instance = player.getAttribute(MAX_HEALTH);
        double max = instance != null ? instance.getValue() : 20.0;
        return Double.isFinite(max) && max > 0.0 ? max : 20.0;
    }

    /* -------------------------------- события -------------------------------- */

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SPECTATOR) {
            applyMaxHealth(player);
            applyHearts(player, !"vanilla".equals(mode()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // состояний на игрока больше не держим (бары удалены) — чистка не нужна
    }
}
