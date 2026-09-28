// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command.sub;

import dev.raskol.classes.classsystem.PlayerClass;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 1.11.4 (P4d): общие форматтеры/парсеры подкоманд. */
public final class SubUtil {

    private SubUtil() {
    }

    public static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    public static int parseLevel(String[] args, int idx) {
        if (idx < args.length) {
            try {
                int v = Integer.parseInt(args[idx]);
                if (v >= 1 && v <= 100) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 40;
    }

    public static PlayerClass parseClass(String tok) {
        try {
            return PlayerClass.valueOf(tok);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String shortName(PlayerClass pc) {
        return switch (pc) {
            case WARRIOR -> "воин";
            case HUNTER -> "охотник";
            case PRIEST -> "жрец";
            case MAGE -> "маг";
            case ROGUE -> "разбойник";
            case WARLOCK -> "чернокн.";
        };
    }

    public static List<String> onlinePlayerNames() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            out.add(p.getName());
        }
        return out;
    }

    public static List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (s.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(s);
            }
        }
        return out;
    }
}
