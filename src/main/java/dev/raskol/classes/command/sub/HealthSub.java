// © 2026 hayferdahmer — RASKOL Proprietary License v1.0. See LICENSE.
package dev.raskol.classes.command.sub;

import dev.raskol.classes.RaskolClasses;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/** 1.11.4 (P4d): /rc health — MSPT/TPS, purge, аптайм, онлайн, Fx-счётчики. */
public final class HealthSub implements CommandSub {

    private final RaskolClasses plugin;

    public HealthSub(RaskolClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "health";
    }

    @Override
    public String permission() {
        return "raskolclasses.debug";
    }

    @Override
    public boolean execute(CommandSender sender, String[] tailArgs) {
        double mspt = plugin.getServer().getAverageTickTime();
        double[] tpsArr = plugin.getServer().getTPS();
        double tps = tpsArr != null && tpsArr.length > 0 ? tpsArr[0] : 0.0;
        long purgeAge = System.currentTimeMillis() - plugin.getLastPurgeMillis();
        long uptimeSec = (System.currentTimeMillis() - plugin.getEnabledAtMillis()) / 1000L;
        NamedTextColor color = mspt < 50.0 ? NamedTextColor.GREEN
                : mspt < 55.0 ? NamedTextColor.YELLOW : NamedTextColor.RED;
        sender.sendMessage(Component.text("=== Здоровье RaskolClasses ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("MSPT: " + SubUtil.fmt1(mspt) + " · TPS: " + SubUtil.fmt1(tps), color));
        sender.sendMessage(Component.text("Аптайм плагина: " + uptimeSec + " с", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Последний purge: " + (purgeAge / 1000L) + " с назад",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Онлайн: " + Bukkit.getOnlinePlayers().size() + " игроков",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Fx: активных " + plugin.getFx().activeCount()
                + ", битых ключей " + plugin.getFx().brokenSoundCount(), NamedTextColor.GRAY));
        return true;
    }
}
