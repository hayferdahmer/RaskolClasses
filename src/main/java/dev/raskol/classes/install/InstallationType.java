// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.install;

import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.Material;

/**
 * Шесть классовых инсталляций (1.5.0, Пакет 2; 1.10.0 +HERESY_CIRCLE).
 * MINE — срабатывает один раз по шагу врага/моба и расходуется.
 * ZONE — тикает до конца TTL, влияет на союзников/врагов.
 */
public enum InstallationType {

    WAR_BANNER(PlayerClass.WARRIOR, Mode.ZONE, Material.RED_BANNER, "Знамя войны"),
    BEAR_TRAP(PlayerClass.HUNTER, Mode.MINE, Material.TRIPWIRE_HOOK, "Капкан"),
    LIGHT_WARD(PlayerClass.PRIEST, Mode.ZONE, Material.END_CRYSTAL, "Световой вард"),
    FROST_RUNE(PlayerClass.MAGE, Mode.MINE, Material.BLUE_ICE, "Ледяная руна"),
    SMOKE_BOMB(PlayerClass.ROGUE, Mode.MINE, Material.GUNPOWDER, "Дымовая шашка"),
    HERESY_CIRCLE(PlayerClass.WARLOCK, Mode.ZONE, Material.SOUL_CAMPFIRE, "Круг Хулы");

    public enum Mode { MINE, ZONE }

    private final PlayerClass playerClass;
    private final Mode mode;
    private final Material displayItem;
    private final String displayName;

    InstallationType(PlayerClass playerClass, Mode mode,
                     Material displayItem, String displayName) {
        this.playerClass = playerClass;
        this.mode = mode;
        this.displayItem = displayItem;
        this.displayName = displayName;
    }

    public PlayerClass playerClass() { return playerClass; }
    public Mode mode() { return mode; }
    public Material displayItem() { return displayItem; }
    public String displayName() { return displayName; }

    /** id для конфига: installations.<id>.* */
    public String id() { return name().toLowerCase(); }

    /** Одна инсталляция на класс. */
    public static InstallationType forClass(PlayerClass pc) {
        for (InstallationType t : values()) {
            if (t.playerClass == pc) {
                return t;
            }
        }
        return null;
    }
}
