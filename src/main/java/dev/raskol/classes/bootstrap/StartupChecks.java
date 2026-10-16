// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.bootstrap;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.spec.model.Spec2Tree;
import dev.raskol.classes.spec.registry.Spec2Registry;
import org.bukkit.ChatColor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.14.10 (Sprint 7, Волна 7.2, P0-4-01): startup-проверки и сервисные действия
 * загрузки, вынесенные из RaskolClasses.onEnable (аудит 4-01: God Object).
 * Все методы статические, идемпотентные, без состояния: плагин передаётся параметром.
 *
 * Состав:
 *  - cleanupTmpFiles: удаление .yml.tmp старше часа (1.11.2 T5);
 *  - logCoreVersion: проверка совместимости RaskolCore (рефлексия, soft-depend);
 *  - warnLowCapacityTrees: startup-warning P0-4 для деревьев ниже tree-capacity-min;
 *  - printBanner: консольный баннер релиза.
 */
public final class StartupChecks {

    private StartupChecks() {
    }

    /** 1.11.2 (T5): удаляет .yml.tmp старше 1 часа в папке плагина. */
    public static void cleanupTmpFiles(RaskolClasses plugin) {
        File folder = plugin.getDataFolder();
        if (folder == null || !folder.isDirectory()) {
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml.tmp"));
        if (files == null || files.length == 0) {
            return;
        }
        long oneHourAgo = System.currentTimeMillis() - 3_600_000L;
        int removed = 0;
        for (File f : files) {
            if (f.lastModified() < oneHourAgo) {
                if (f.delete()) {
                    removed++;
                } else {
                    plugin.getLogger().warning("Не удалось удалить устаревший .tmp: " + f.getName());
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Удалено устаревших .yml.tmp файлов: " + removed);
        }
    }

    /** Soft-depend проверка RaskolCore: версия ядра для мгновенного reconcile паспорта. */
    public static void logCoreVersion(RaskolClasses plugin) {
        try {
            Class<?> api = Class.forName("dev.raskol.core.RaskolCoreAPI");
            Object versionObj = api.getMethod("coreVersion").invoke(null);
            String version = versionObj == null ? "" : versionObj.toString();
            if (version.isEmpty()) {
                plugin.getLogger().warning("RaskolCore: плагин не включён или версия недоступна — "
                        + "мгновенный reconcile на смену паспорта может не работать");
            } else {
                plugin.getLogger().info("RaskolCore: версия " + version);
                if ("1.0.0".equals(version) || "1.1.0".equals(version) || "1.2.0".equals(version)) {
                    plugin.getLogger().warning("RaskolCore: версия " + version + " устарела — "
                            + "рекомендуется обновить до 1.3.0 для мгновенного reconcile");
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().info("RaskolCore: не найден — плагин работает в автономном режиме "
                    + "(мгновенный reconcile на смену паспорта отключён)");
        }
    }

    /**
     * 1.14.7 (Sprint 2, P0-4): startup-warning для деревьев с ёмкостью ниже
     * spec2.tree-capacity-min. Не блокирует старт: это напоминание добить ранги.
     */
    public static void warnLowCapacityTrees(RaskolClasses plugin) {
        int capMin = plugin.getConfig().getInt("spec2.tree-capacity-min", 50);
        if (capMin < 1) {
            capMin = 50;
        }
        List<String> lowCap = new ArrayList<>();
        for (dev.raskol.classes.spec.Spec s : dev.raskol.classes.spec.Spec.values()) {
            Spec2Tree t = Spec2Registry.treeOf(s.id());
            if (t != null && t.capacity() < capMin) {
                lowCap.add(s.id() + "=" + t.capacity());
            }
        }
        if (!lowCap.isEmpty()) {
            plugin.getLogger().warning("P0-4: " + lowCap.size() + " деревьев ниже tree-capacity-min="
                    + capMin + ": " + String.join(", ", lowCap)
                    + " — добавьте maxRank существующим пассивным узлам");
        }
    }

    /** Консольный баннер релиза (ChatColor-арт, только в консоль). */
    public static void printBanner(RaskolClasses plugin) {
        String v = plugin.getPluginMeta().getVersion();
        String[] art = {
            "&8  ██████╗██╗     █████╗ ███████╗███████╗███████╗███████╗",
            "&8  ██╔════╝██║    ██╔══██╗██╔════╝██╔════╝██╔════╝██╔════╝",
            "&4  ██║     ██║    ███████║███████╗███████╗█████╗  ███████╗",
            "&4  ██║     ██║    ██╔══██║╚════██║╚════██║██╔══╝  ╚════██║",
            "&5  ╚██████╗███████╗██║  ██║███████║███████║███████╗███████║",
            "&5   ╚═════╝╚══════╝╚═╝  ╚═╝══════╝╚══════╝╚══════╝╚══════╝",
            "&8  ────────────────────────────────────────────────────",
            "&7     RASKOL &8· &7CLASSES    &8|    &5пять путей &8· &4одна война",
            "&8     ⚔ &4Воин &8· &2➳ Охотник &8· &f✚ Жрец &8· &9✦ Маг &8· &5☠ Разбойник",
            "&8  ────────────────────────────────────────────────────",
            "&8     by &fhayferdahmer &8· &7v" + v + " &8· &7Paper 1.21+ &8· &7Java 21",
            "&8  ────────────────────────────────────────────────────"
        };
        var console = plugin.getServer().getConsoleSender();
        for (String line : art) {
            console.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
    }
}
