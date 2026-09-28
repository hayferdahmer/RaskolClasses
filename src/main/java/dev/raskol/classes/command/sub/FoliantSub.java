// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command.sub;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** 1.11.4 (P4d): /rc foliant give <ник> — админ-выдача Фолианта Душ (тест/резерв). */
public final class FoliantSub implements CommandSub {

    private final RaskolClasses plugin;

    public FoliantSub(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "foliant";
    }

    @Override
    public String permission() {
        return "raskolclasses.admin";
    }

    @Override
    public boolean execute(CommandSender sender, String[] tailArgs) {
        if (tailArgs.length < 2 || !"give".equalsIgnoreCase(tailArgs[0])) {
            sender.sendMessage(Component.text("Использование: /rc foliant give <ник>",
                    NamedTextColor.GRAY));
            return true;
        }
        Player target = Bukkit.getPlayer(tailArgs[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Игрок «" + tailArgs[1] + "» не найден онлайн.",
                    NamedTextColor.RED));
            return true;
        }
        if (!plugin.getFoliantService().giveTo(target)) {
            sender.sendMessage(Component.text("У " + target.getName()
                    + " нет места в инвентаре.", NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("Фолиант Раскола выдан игроку "
                + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text(
                "Тебе вручили §5Фолиант Раскола§f. ПКМ — прочесть страницу.",
                NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    @Override
    public List<String> complete(CommandSender sender, String[] fullArgs) {
        if (fullArgs.length == 2) {
            return SubUtil.filter(List.of("give"), fullArgs[1]);
        }
        if (fullArgs.length == 3 && "give".equalsIgnoreCase(fullArgs[1])) {
            return SubUtil.filter(SubUtil.onlinePlayerNames(), fullArgs[2]);
        }
        return List.of();
    }
}
