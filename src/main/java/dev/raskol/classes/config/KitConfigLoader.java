// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.config;

import dev.raskol.classes.RaskolClasses;
import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * 1.11.4 (P5): загрузчик per-class yml с фолбэком в основной config.yml.
 *
 * Источники (в порядке приоритета):
 *   1) kits/<class>.yml (если существует и валиден);
 *   2) секция classes.<CLASS> в plugins/RaskolClasses/config.yml.
 *
 * Обратная совместимость: если kits/<class>.yml не создан, поведение плагина
 * идентично 1.11.3 (всё читается из config.yml). Переход на per-class можно
 * делать по одному классу, без рестарта — /rc reload перечитает новые файлы.
 *
 * /rc reload перечитывает все per-class файлы заново (инвалидирует кэш).
 * Дублирование ключей в двух местах логируется WARN-ом при загрузке.
 */
public final class KitConfigLoader {

    private final RaskolClasses plugin;
    private final File kitsDir;
    private final Map<PlayerClass, FileConfiguration> loaded = new EnumMap<>(PlayerClass.class);

    public KitConfigLoader(RaskolClasses plugin) {
        this.plugin = plugin;
        this.kitsDir = new File(plugin.getDataFolder(), "kits");
        if (!kitsDir.exists() && !kitsDir.mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку kits/ — "
                    + "per-class конфиги недоступны, работаем через config.yml");
        }
        reload();
    }

    /** Полный reload (вызывается из RaskolClasses.reloadPlugin()). */
    public void reload() {
        loaded.clear();
        for (PlayerClass pc : PlayerClass.values()) {
            File file = new File(kitsDir, pc.name().toLowerCase(java.util.Locale.ROOT) + ".yml");
            if (!file.exists()) {
                continue;
            }
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            warnDuplicates(pc, cfg);
            loaded.put(pc, cfg);
        }
        int count = loaded.size();
        plugin.getLogger().info("Per-class конфиги: загружено " + count
                + " из " + PlayerClass.values().length
                + " (папка kits/, фолбэк — config.yml)");
    }

    /**
     * Возвращает ConfigurationSection для класса (per-class yml ИЛИ
     * classes.<CLASS> из config.yml). null если секции нет нигде.
     */
    public ConfigurationSection sectionOf(PlayerClass pc) {
        FileConfiguration perClass = loaded.get(pc);
        if (perClass != null && perClass.getKeys(false) != null
                && !perClass.getKeys(false).isEmpty()) {
            return perClass;
        }
        return plugin.getConfig().getConfigurationSection(
                "classes." + pc.name());
    }

    /** Прямой доступ к загруженному per-class yml (для диагностики). */
    public FileConfiguration perClassOf(PlayerClass pc) {
        return loaded.get(pc);
    }

    /** Число загруженных per-class файлов (для /rc health). */
    public int loadedCount() {
        return loaded.size();
    }

    /** Папка per-class конфигов (для диагностики). */
    public File kitsDir() {
        return kitsDir;
    }

    /** WARN-лог: ключ существует и в per-class yml, и в config.yml.classes.<CLASS>. */
    private void warnDuplicates(PlayerClass pc, FileConfiguration perClass) {
        ConfigurationSection legacy = plugin.getConfig().getConfigurationSection(
                "classes." + pc.name());
        if (legacy == null) {
            return;
        }
        int dupes = 0;
        for (String key : perClass.getKeys(false)) {
            if (legacy.contains(key)) {
                dupes++;
                if (dupes <= 3) {
                    plugin.getLogger().warning("kits/"
                            + pc.name().toLowerCase(java.util.Locale.ROOT) + ".yml: "
                            + "ключ «" + key + "» переопределяет classes." + pc.name()
                            + "." + key + " в config.yml");
                }
            }
        }
        if (dupes > 3) {
            plugin.getLogger().warning("kits/"
                    + pc.name().toLowerCase(java.util.Locale.ROOT) + ".yml: "
                    + "ещё " + (dupes - 3) + " дублей ключей");
        }
    }
}
